// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.cache.impl.id;

import com.intellij.util.indexing.InputMapExternalizer;
import com.intellij.util.io.DataExternalizer;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;

class IdEntryMapExternalizerTest {
  private static final IdIndexImpl INDEX_EXTENSION = new IdIndexImpl();


  private final InputMapExternalizer<IdIndexEntry, Integer> defaultMapExternalizer = new InputMapExternalizer<>(
    INDEX_EXTENSION.createExternalizer(),
    INDEX_EXTENSION.getValueExternalizer(),
    /*valueIsAbsent: */ false
  );
  private final IdIndexEntryMapExternalizer optimizedMapExternalizer = new IdIndexEntryMapExternalizer(defaultMapExternalizer);


  @Test
  void emptyMapSerializesIdenticallyByBothExternalizers() throws IOException {
    IdEntryToScopeMapImpl emptyMap = new IdEntryToScopeMapImpl();

    externalizersAreEquivalent(emptyMap, defaultMapExternalizer, optimizedMapExternalizer);
  }

  @ParameterizedTest
  @MethodSource("generateMaps")
  void generatedMapsSerializeIdenticallyByBothExternalizers(IdEntryToScopeMapImpl generatedMap) throws IOException {
    externalizersAreEquivalent(generatedMap, defaultMapExternalizer, optimizedMapExternalizer);
  }


  private static Stream<IdEntryToScopeMapImpl> generateMaps() {
    ThreadLocalRandom rnd = ThreadLocalRandom.current();
    return IntStream.range(0, 10_000)
      .mapToObj(size -> {
        IdEntryToScopeMapImpl map = new IdEntryToScopeMapImpl();
        for (int j = 0; j < size; j++) {
          int idHash = rnd.nextInt();
          int mask = rnd.nextInt(0, Byte.MAX_VALUE);
          map.updateMask(idHash, mask);
        }
        return map;
      });
  }


  private static <T> void externalizersAreEquivalent(@NotNull T valueToTest,
                                                     @NotNull DataExternalizer<T> defaultExternalizer,
                                                     @NotNull DataExternalizer<T> optimizedExternalizer) throws IOException {
    {
      T valueReadBack = deserializeFromArray(
        serializeToArray(valueToTest, defaultExternalizer),
        defaultExternalizer
      );

      assertThat("defaultExternalizer: value deserialized is equal to the value serialized",
                 valueReadBack,
                 equalTo(valueToTest));
    }
    {
      T valueReadBack = deserializeFromArray(
        serializeToArray(valueToTest, optimizedExternalizer),
        optimizedExternalizer
      );

      assertThat("optimizedExternalizer: value deserialized is equal to the value serialized",
                 valueReadBack,
                 equalTo(valueToTest));
    }

    {
      T valueReadBack = deserializeFromArray(
        serializeToArray(valueToTest, defaultExternalizer),
        optimizedExternalizer
      );

      assertThat("Value deserialized with optimizedExternalizer is equal to the value serialized with defaultExternalizer",
                 valueReadBack,
                 equalTo(valueToTest));
    }

    {
      T valueReadBack = deserializeFromArray(
        serializeToArray(valueToTest, optimizedExternalizer),
        defaultExternalizer
      );

      assertThat("Value deserialized with defaultExternalizer is equal to the value serialized with optimizedExternalizer",
                 valueReadBack,
                 equalTo(valueToTest));
    }
  }


  private static <T> T deserializeFromArray(byte[] array,
                                            @NotNull DataExternalizer<T> externalizer) throws IOException {
    return externalizer.read(new DataInputStream(new ByteArrayInputStream(array)));
  }

  private static <T> byte @NotNull [] serializeToArray(@NotNull T value,
                                                       @NotNull DataExternalizer<T> externalizer) throws IOException {
    ByteArrayOutputStream bos = new ByteArrayOutputStream();
    try (DataOutputStream output = new DataOutputStream(bos)) {
      externalizer.save(output, value);
    }
    return bos.toByteArray();
  }
}