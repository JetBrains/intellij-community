// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.indexing.export;

import com.intellij.openapi.util.io.ByteArraySequence;
import com.intellij.util.Function;
import com.intellij.util.indexing.InvertedIndex;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.function.ToIntFunction;
import java.util.stream.Stream;

public interface IndexExporter<Key, Value, Input> {

  void export(@NotNull File dir,
              @NotNull InvertedIndex<Key, Value, Input> index,
              @NotNull Function<Input, ByteArraySequence> inputSubstitutor,
              @NotNull ToIntFunction<Input> inputEnumerator,
              @NotNull Stream<Input> inputs) throws IOException;

}
