// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.cache.impl.idCache;

import com.intellij.ide.highlighter.JavaClassFileType;
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
import com.intellij.util.indexing.FileContent;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.util.Map;

import static com.intellij.psi.impl.cache.impl.BaseFilterLexerUtil.scanContentWithCheckCanceled;

public final class JavaIdIndexer implements IdIndexer {
  private static final Logger LOG = Logger.getInstance(JavaIdIndexer.class);
  static final String ENABLED_REGISTRY_KEY = "index.ids.from.java.sources.in.jar";
  static volatile boolean isEnabled = Registry.is("index.ids.from.java.sources.in.jar", true);

  @Override
  public @NotNull Map<IdIndexEntry, Integer> map(@NotNull FileContent inputData) {
    VirtualFile file = inputData.getFile();
    FileType fileType = file.getFileType();
    if (fileType.equals(JavaClassFileType.INSTANCE) && isEnabled) {
      Map<IdIndexEntry, Integer> idEntries = calculateIdEntriesParsingConstantPool(inputData);
      if (idEntries != null) return idEntries;
      return Map.of();
    }
    // we are skipping indexing of sources in libraries (we are going to index only the compiled library classes)
    if (isEnabled || JavaFileElementType.isInSourceContent(file)) {
      IdDataConsumer consumer = new IdDataConsumer();
      scanContentWithCheckCanceled(inputData, createIndexingLexer(new OccurrenceConsumer(consumer, false)));
      return consumer.getResult();
    }
    return Map.of();
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
        endExclList.add(startIncl);
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
      LOG.warn("Exception while handling file " + inputData.getFileName(), e);
      return null;
    }
    return consumer.getResult();
  }

  public static Lexer createIndexingLexer(OccurrenceConsumer consumer) {
    Lexer javaLexer = JavaParserDefinition.createLexer(LanguageLevel.JDK_1_3);
    return new JavaFilterLexer(javaLexer, consumer);
  }
}
