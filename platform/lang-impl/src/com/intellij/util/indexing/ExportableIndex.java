// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.indexing;

import com.intellij.openapi.util.io.ByteArraySequence;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.function.Function;
import java.util.stream.Stream;

public interface ExportableIndex<Key, Value, Input> extends InvertedIndex<Key, Value, Input> {

  /**
   * @param dir output index directory
   * @param fileIdSubstitutor intermediate key generator aka input hash
   */
  void exportInputs(@NotNull File dir, @NotNull Function<Input, ByteArraySequence> fileIdSubstitutor, Stream<Input> inputs);
}
