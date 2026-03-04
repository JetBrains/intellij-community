// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.java.stubs.index;

import com.intellij.codeInsight.ExternalAnnotationsManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.util.ThreeState;
import com.intellij.util.indexing.DataIndexer;
import com.intellij.util.indexing.FileBasedIndex;
import com.intellij.util.indexing.FileBasedIndexExtension;
import com.intellij.util.indexing.FileContent;
import com.intellij.util.indexing.ID;
import com.intellij.util.indexing.IndexedFile;
import com.intellij.util.indexing.hints.BaseFileTypeInputFilter;
import com.intellij.util.indexing.hints.FileTypeSubstitutionStrategy;
import com.intellij.util.io.DataExternalizer;
import com.intellij.util.io.DataInputOutputUtil;
import com.intellij.util.io.EnumeratorStringDescriptor;
import com.intellij.util.io.IOUtil;
import com.intellij.util.io.KeyDescriptor;
import com.intellij.util.text.CharArrayUtil;
import com.intellij.util.xml.NanoXmlBuilder;
import com.intellij.util.xml.NanoXmlUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import com.intellij.util.Processor;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Index for external annotations ({@code annotations.xml} files).
 * <p>
 * Maps annotation FQN to the list of item external names that carry that annotation in a given file.
 * This enables reverse lookups: given an annotation class, find all externally annotated elements.
 *
 * @see ExternalAnnotationsManager
 */
public final class ExternalAnnotationsIndex extends FileBasedIndexExtension<String, List<String>> {
  private static final ID<String, List<String>> NAME = ID.create("java.external.annotations");

  private static final DataExternalizer<List<String>> VALUE_EXTERNALIZER = new DataExternalizer<>() {
    @Override
    public void save(@NotNull DataOutput out, List<String> value) throws IOException {
      DataInputOutputUtil.writeSeq(out, value, item -> IOUtil.writeUTF(out, item));
    }

    @Override
    public List<String> read(@NotNull DataInput in) throws IOException {
      return DataInputOutputUtil.readSeq(in, () -> IOUtil.readUTF(in));
    }
  };

  @Override
  public @NotNull ID<String, List<String>> getName() {
    return NAME;
  }

  @Override
  public int getVersion() {
    return 1;
  }

  @Override
  public @NotNull KeyDescriptor<String> getKeyDescriptor() {
    return EnumeratorStringDescriptor.INSTANCE;
  }

  @Override
  public @NotNull DataExternalizer<List<String>> getValueExternalizer() {
    return VALUE_EXTERNALIZER;
  }

  @Override
  public boolean dependsOnFileContent() {
    return true;
  }

  @Override
  public @NotNull FileBasedIndex.InputFilter getInputFilter() {
    return new BaseFileTypeInputFilter(FileTypeSubstitutionStrategy.BEFORE_SUBSTITUTION) {
      @Override
      public @NotNull ThreeState acceptFileType(@NotNull FileType fileType) {
        return fileType.getName().equals("XML") ? ThreeState.UNSURE : ThreeState.NO;
      }

      @Override
      public boolean slowPathIfFileTypeHintUnsure(@NotNull IndexedFile file) {
        return file.getFile().getName().equals(ExternalAnnotationsManager.ANNOTATIONS_XML);
      }
    };
  }

  @Override
  public @NotNull DataIndexer<String, List<String>, FileContent> getIndexer() {
    return inputData -> {
      Map<String, List<String>> result = new HashMap<>();
      NanoXmlUtil.parse(CharArrayUtil.readerFromCharSequence(inputData.getContentAsText()), new NanoXmlBuilder() {
        private String currentElement;
        private String currentItem;
        private String currentAnnotation;

        @Override
        public void startElement(@NonNls String name, @NonNls String nsPrefix, @NonNls String nsURI, String systemID, int lineNr) {
          currentElement = name;
          if ("item".equals(name)) {
            currentItem = null;
          }
          else if ("annotation".equals(name)) {
            currentAnnotation = null;
          }
        }

        @Override
        public void endElement(String name, String nsPrefix, String nsURI) {
          if ("annotation".equals(name)) {
            if (currentItem != null && currentAnnotation != null) {
              result.computeIfAbsent(currentAnnotation, k -> new ArrayList<>()).add(currentItem);
            }
          }
          else if ("item".equals(name)) {
            currentItem = null;
          }
        }

        @Override
        public void addAttribute(@NonNls String key, String nsPrefix, String nsURI, String value, String type) {
          if ("item".equals(currentElement) && "name".equals(key) && value != null && !value.isEmpty()) {
            currentItem = value;
          }
          else if ("annotation".equals(currentElement)) {
            if ("name".equals(key) && value != null && !value.isEmpty()) {
              currentAnnotation = value;
            }
            else if ("typePath".equals(key)) {
              currentAnnotation = null;
              currentElement = null;
            }
          }
        }
      });
      return result;
    };
  }

  /**
   * Retrieves a list of external items annotated with the specified annotation.
   *
   * @param annotationFQN the fully qualified name of the annotation to search for.
   * @param scope the scope within which to search for the annotated items.
   * @return a list of item names that are annotated with the specified annotation.
   * @see #processItemsByAnnotation(String, GlobalSearchScope, Processor)
   */
  public static List<String> getItemsByAnnotation(String annotationFQN, GlobalSearchScope scope) {
    List<String> items = new ArrayList<>();
    processItemsByAnnotation(annotationFQN, scope, item -> items.add(item));
    return items;
  }

  /**
   * Processes item external names annotated with the given annotation FQN.
   *
   * @return {@code false} if processing was stopped by the processor, {@code true} otherwise.
   * @see #getItemsByAnnotation(String, GlobalSearchScope)
   */
  public static boolean processItemsByAnnotation(@NotNull String annotationFQN,
                                                 @NotNull GlobalSearchScope scope,
                                                 @NotNull Processor<? super @NotNull String> processor) {
    return FileBasedIndex.getInstance().processValues(NAME, annotationFQN, null, (file, value) -> {
      for (String item : value) {
        if (!processor.process(item)) return false;
      }
      return true;
    }, scope);
  }
}
