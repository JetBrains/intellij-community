/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.util.indexing;

import com.intellij.openapi.util.io.ByteArraySequence;
import com.intellij.util.indexing.impl.*;
import com.intellij.util.io.ByteSequenceDataExternalizer;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;

class SharedMapBasedForwardIndex implements ForwardIndex {
  private final IndexId<?, ?> myIndexId;

  SharedMapBasedForwardIndex(IndexExtension<?, ?, ?> extension) {
    myIndexId = extension.getName();
  }

  @Nullable
  @Override
  public ByteArraySequence getInputData(int inputId) throws IOException {
    return SharedIndicesData.recallFileData(inputId, (ID<?, ?>)myIndexId, new ByteSequenceDataExternalizer());
  }

  @Override
  public void putInputData(int inputId, @Nullable ByteArraySequence data) throws IOException {
    SharedIndicesData.associateFileData(inputId, (ID<?, ?>)myIndexId, data, new ByteSequenceDataExternalizer());
  }
}
