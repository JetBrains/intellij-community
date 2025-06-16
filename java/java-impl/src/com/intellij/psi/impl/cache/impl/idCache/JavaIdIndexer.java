// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.cache.impl.idCache;

import com.intellij.ide.highlighter.JavaClassFileType;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.lang.java.JavaParserDefinition;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.impl.cache.impl.OccurrenceConsumer;
import com.intellij.psi.impl.cache.impl.id.IdDataConsumer;
import com.intellij.psi.impl.cache.impl.id.IdIndexEntry;
import com.intellij.psi.impl.cache.impl.id.IdIndexer;
import com.intellij.psi.impl.source.JavaFileElementType;
import com.intellij.psi.search.UsageSearchContext;
import com.intellij.util.ThreeState;
import com.intellij.util.indexing.FileContent;
import com.intellij.util.indexing.IndexedFile;
import com.intellij.util.indexing.hints.FileTypeIndexingHint;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.util.Map;

import static com.intellij.psi.impl.cache.impl.BaseFilterLexerUtil.scanContentWithCheckCanceled;

/** @see com.intellij.psi.impl.cache.impl.id.IdIndexFilter */
public final class JavaIdIndexer implements IdIndexer, FileTypeIndexingHint {
  private static final Logger LOG = Logger.getInstance(JavaIdIndexer.class);

  //BEWARE: there are 2 instances of this class in a container, because it is registered twice: for .java and .class file types

  static final String SKIP_SOURCE_FILES_IN_LIBRARIES_REGISTRY_KEY = "ide.index.id.skip.java.sources.in.libs";
  /**
   * Option for optimization:
   * if true   -> .class-files in libraries are indexed, while .java-sources in libraries should be skipped
   * if false  -> .java-files in libraries are indexed
   */
  private final boolean skipSourceFilesInLibraries;

  public JavaIdIndexer() {
    this(Registry.is(SKIP_SOURCE_FILES_IN_LIBRARIES_REGISTRY_KEY, false));
  }

  public JavaIdIndexer(boolean skipSourceFilesInLibraries) {
    this.skipSourceFilesInLibraries = skipSourceFilesInLibraries;
    LOG.info("skipSourceFilesInLibraries: " + skipSourceFilesInLibraries
             + " (registry: '" + SKIP_SOURCE_FILES_IN_LIBRARIES_REGISTRY_KEY + "')");
  }

  @Override
  public @NotNull ThreeState acceptsFileTypeFastPath(@NotNull FileType fileType) {
    if (fileType.equals(JavaClassFileType.INSTANCE)) {
      //if we skip .java-files in libraries => we must at least index .class-files,
      // but if we don't skip .java-files   => we don't need to index .class-files
      //(In theory, we could go father, and mix .class and .java files indexing -- use one or another depending on availability.
      // But it is harder to implement correctly, so currently we don't bother)
      if (skipSourceFilesInLibraries) {
        return ThreeState.UNSURE; //need details to check if the file is in 'libraries'
      }
      return ThreeState.NO;
    }
    else if (fileType.equals(JavaFileType.INSTANCE)) {
      if (skipSourceFilesInLibraries) {
        return ThreeState.UNSURE;//need details to check if the file is in 'libraries'
      }
      return ThreeState.YES;
    }
    else {
      return ThreeState.NO;
    }
  }

  @Override
  public boolean slowPathIfFileTypeHintUnsure(@NotNull IndexedFile inputData) {
    VirtualFile file = inputData.getFile();
    FileType fileType = file.getFileType();

    if (fileType.equals(JavaClassFileType.INSTANCE)) {
      //TODO RC: Currently there is no regular way to find out is .class in libs or not.
      //         For .java-files we use a hack (see JavaFileElementType.isInSourceContent()), but there is no such hack for
      //         .class-files => we behave as-is '.class is always in libraries'
      return skipSourceFilesInLibraries; // && isClassInLibraries(file);
    }

    if (fileType.equals(JavaFileType.INSTANCE)) {
      return !skipSourceFilesInLibraries || isJavaInSourceTree(file);
    }

    return false;//really, we shouldn't come here
  }

  @Override
  public @NotNull Map<IdIndexEntry, Integer> map(@NotNull FileContent inputData) {
    VirtualFile file = inputData.getFile();
    FileType fileType = file.getFileType();

    if (fileType.equals(JavaClassFileType.INSTANCE)) {
      if (skipSourceFilesInLibraries) { //don't check isInLibraries(): filter must filter out .class-files that are not in libraries
        Map<IdIndexEntry, Integer> idEntries = calculateIdEntriesParsingConstantPool(inputData);
        if (idEntries != null) {
          return idEntries;
        }
      }
      //MAYBE RC: why skip indexing .class-files if source-files indexing is enabled?
      //          Even if .java-files indexing in libraries is enabled, it could be no .java-files in particular libraries
      //          => .class-file is the only option then.
      return Map.of();
    }

    if (fileType.equals(JavaFileType.INSTANCE)) {
      if (!skipSourceFilesInLibraries || isJavaInSourceTree(file)) {
        IdDataConsumer idCollector = new IdDataConsumer();
        scanContentWithCheckCanceled(inputData, createIndexingLexer(new OccurrenceConsumer(idCollector, /*needToDo:*/ false)));
        return idCollector.getResult();
      }
      return Map.of();
    }

    return Map.of();//really, we shouldn't come here
  }

  private static @Nullable Map<IdIndexEntry, Integer> calculateIdEntriesParsingConstantPool(@NotNull FileContent inputData) {
    IdDataConsumer consumer = new IdDataConsumer();
    // optimised to avoid creation of list for every UTF8 entry
    IntList startInclList = new IntArrayList();
    IntList endExclList = new IntArrayList();
    ConstantPoolParser parser = new ConstantPoolParser(new DataInputStream(new ByteArrayInputStream(inputData.getContent())), str -> {
      ProgressManager.checkCanceled();
      if (str.isEmpty()) return;
      // it is likely a method signature, we don't need to handle it because all its parts will it is possible to find in the other entries.
      if (str.charAt(0) == '(') return;

      if (JvmIdentifierUtil.collectJvmIdentifiers(str, (sequence, startIncl, endExcl) -> {
        startInclList.add(startIncl);
        endExclList.add(endExcl);
      })) {
        for (int i = 0; i < startInclList.size(); i++) {
          consumer.addOccurrence(str, startInclList.getInt(i), endExclList.getInt(i), UsageSearchContext.IN_CODE);
        }
      }
      startInclList.clear();
      endExclList.clear();
    });
    try {
      parser.parse();
    }
    catch (IOException | ConstantPoolParser.ClassFormatException e) {
      LOG.warn("Exception while handling file " + inputData.getFile().getPath(), e);
      return null;
    }
    return consumer.getResult();
  }

  public static Lexer createIndexingLexer(OccurrenceConsumer consumer) {
    Lexer javaLexer = JavaParserDefinition.createLexer(LanguageLevel.JDK_1_3);
    return new JavaFilterLexer(javaLexer, consumer);
  }

  /** @return true if the .java-file is located under one of project's source trees, false otherwise. */
  private static boolean isJavaInSourceTree(@NotNull VirtualFile file) {
    return JavaFileElementType.isInSourceContent(file);
  }
}
