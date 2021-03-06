/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.framework.detection.impl;

import com.intellij.framework.detection.FrameworkDetector;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.ControlFlowException;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.patterns.ElementPattern;
import com.intellij.util.EventDispatcher;
import com.intellij.util.containers.MultiMap;
import com.intellij.util.indexing.*;
import com.intellij.util.indexing.impl.MapReduceIndexMappingException;
import com.intellij.util.io.EnumeratorStringDescriptor;
import com.intellij.util.io.KeyDescriptor;
import com.intellij.util.io.externalizer.StringCollectionExternalizer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.stream.Collectors;

public class FrameworkDetectionIndex extends ScalarIndexExtension<String> {
  private static final Logger LOG = Logger.getInstance(FrameworkDetectionIndex.class);
  public static final ID<String, Void> NAME = ID.create("FrameworkDetectionIndex");

  private final EventDispatcher<FrameworkDetectionIndexListener> myDispatcher = EventDispatcher.create(FrameworkDetectionIndexListener.class);

  public static FrameworkDetectionIndex getInstance() {
    return EXTENSION_POINT_NAME.findExtension(FrameworkDetectionIndex.class);
  }

  @NotNull
  @Override
  public ID<String, Void> getName() {
    return NAME;
  }

  public void addListener(@NotNull FrameworkDetectionIndexListener listener, @NotNull Disposable parentDisposable) {
    myDispatcher.addListener(listener, parentDisposable);
  }

  @NotNull
  @Override
  public DataIndexer<String, Void, FileContent> getIndexer() {
    return new CompositeDataIndexer<String, Void, Collection<Pair<ElementPattern<FileContent>, String>>, List<String>> () {
      @Override
      public @Nullable Collection<Pair<ElementPattern<FileContent>, String>> calculateSubIndexer(@NotNull IndexedFile file) {
        MultiMap<FileType, Pair<ElementPattern<FileContent>, String>> map = FrameworkDetectorRegistry.getInstance().getDetectorsMap();
        Collection<Pair<ElementPattern<FileContent>, String>> indexerCandidates = map.get(file.getFileType());
        return indexerCandidates.isEmpty() ? null : indexerCandidates;
      }

      @Override
      public @NotNull List<String> getSubIndexerVersion(@NotNull Collection<Pair<ElementPattern<FileContent>, String>> pairs) {
        return pairs.stream().map(p -> p.getSecond()).sorted().collect(Collectors.toList());
      }

      @Override
      public @NotNull KeyDescriptor<List<String>> getSubIndexerVersionDescriptor() {
        return new StringCollectionExternalizer<>(ArrayList::new);
      }

      @NotNull
      @Override
      public Map<String, Void> map(@NotNull FileContent inputData,
                                    @NotNull Collection<Pair<ElementPattern<FileContent>, String>> pairs) {
        Map<String, Void> result = null;
        for (Pair<ElementPattern<FileContent>, String> pair : pairs) {
          ElementPattern<FileContent> pattern = pair.getFirst();
          String detectorId = pair.getSecond();
          boolean accepts;
          try {
            accepts = pattern.accepts(inputData);
          } catch (Exception e) {
            if (e instanceof ControlFlowException) throw e;
            FrameworkDetector frameworkDetector = FrameworkDetectorRegistry.getInstance().getDetectorById(detectorId);
            throw new MapReduceIndexMappingException(e, frameworkDetector != null ? frameworkDetector.getClass() : null);
          }
          if (accepts) {
            if (LOG.isDebugEnabled()) {
              LOG.debug(inputData.getFile() + " accepted by detector " + detectorId);
            }
            if (result == null) {
              result = new HashMap<>();
            }
            myDispatcher.getMulticaster().fileUpdated(inputData.getFile(), detectorId);
            result.put(detectorId, null);
          }
        }
        return result != null ? result : Collections.emptyMap();
      }
    };
  }

  @NotNull
  @Override
  public KeyDescriptor<String> getKeyDescriptor() {
    return EnumeratorStringDescriptor.INSTANCE;
  }

  @NotNull
  @Override
  public FileBasedIndex.InputFilter getInputFilter() {
    return new DefaultFileTypeSpecificInputFilter(FrameworkDetectorRegistry.getInstance().getAcceptedFileTypes()) {
      @Override
      public boolean acceptInput(@NotNull VirtualFile file) {
        return file.isInLocalFileSystem();
      }
    };
  }

  @Override
  public boolean dependsOnFileContent() {
    return true;
  }

  @Override
  public int getVersion() {
    return 65536;
  }
}
