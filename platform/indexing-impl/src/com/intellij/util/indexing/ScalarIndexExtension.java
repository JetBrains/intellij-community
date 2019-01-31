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

package com.intellij.util.indexing;

import com.intellij.util.io.DataExternalizer;
import com.intellij.util.io.VoidDataExternalizer;
import org.jetbrains.annotations.NotNull;

/**
 * A specialization of FileBasedIndexExtension allowing to create a mapping [DataObject -> List of files containing this object]
 *
 */
public abstract class ScalarIndexExtension<K> extends FileBasedIndexExtension<K, Void>{

  /**
   * To remove in IDEA 2018.1. Use {@link VoidDataExternalizer#INSTANCE}
   */
  @Deprecated
  public static final DataExternalizer<Void> VOID_DATA_EXTERNALIZER = VoidDataExternalizer.INSTANCE;

  @NotNull
  @Override
  public final DataExternalizer<Void> getValueExternalizer() {
    return VoidDataExternalizer.INSTANCE;
  }
}