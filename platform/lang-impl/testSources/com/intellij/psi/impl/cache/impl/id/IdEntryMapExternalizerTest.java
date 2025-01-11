// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.cache.impl.id;

import com.intellij.util.indexing.InputMapExternalizer;
import com.intellij.util.io.DataExternalizer;
import com.intellij.util.io.IOUtil;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.io.*;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;

class IdEntryMapExternalizerTest {
  private static final IdIndexImpl INDEX_EXTENSION = new IdIndexImpl();
  private static final int ENOUGH_MAPS_TO_CHECK = 10_000;


  private final InputMapExternalizer<IdIndexEntry, Integer> defaultMapExternalizer = new InputMapExternalizer<>(
    INDEX_EXTENSION.createExternalizer(),
    INDEX_EXTENSION.getValueExternalizer(),
    /*valueIsAbsent: */ false
  );
  private final IdIndexEntryMapExternalizer optimizedMapExternalizer = new IdIndexEntryMapExternalizer(defaultMapExternalizer);


  @Test
  void emptyMapSerializesIdentically_ByBothExternalizers() throws IOException {
    IdEntryToScopeMapImpl emptyMap = new IdEntryToScopeMapImpl();

    externalizersAreEquivalent(emptyMap, defaultMapExternalizer, optimizedMapExternalizer);
  }

  @Test
  void generatedMapsSerializeIdentically_ByBothExternalizers() throws IOException {
    IdEntryToScopeMapImpl[] generatedMaps = generateMaps().toArray(IdEntryToScopeMapImpl[]::new);
    for (IdEntryToScopeMapImpl generatedMap : generatedMaps) {
      externalizersAreEquivalent(generatedMap, defaultMapExternalizer, optimizedMapExternalizer);
    }
  }

  @Test
  void generatedMapsSerializeIdentically_ByBothExternalizers_evenWithDefaultMapImplementation() throws IOException {
    IdEntryToScopeMapImpl[] generatedMaps = generateMaps().toArray(IdEntryToScopeMapImpl[]::new);
    for (IdEntryToScopeMapImpl generatedMap : generatedMaps) {
      //copy specialized IdEntryToScopeMapImpl to default Map impl: test fallback path
      Map<IdIndexEntry, Integer> defaultMapImpl = Map.copyOf(generatedMap);
      externalizersAreEquivalent(defaultMapImpl, defaultMapExternalizer, optimizedMapExternalizer);
    }
  }
  // ================================================ infra ================================================================ //

  private static Stream<IdEntryToScopeMapImpl> generateMaps() {
    ThreadLocalRandom rnd = ThreadLocalRandom.current();
    return IntStream.range(0, ENOUGH_MAPS_TO_CHECK)
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
    { //value -(defaultSerializer)-> byte[] -(defaultDeserializer)-> value
      T valueReadBack = deserializeFromBytes(
        serializeToBytes(valueToTest, defaultExternalizer),
        defaultExternalizer
      );

      assertThat("defaultExternalizer: value deserialized is equal to the value serialized",
                 valueReadBack,
                 equalTo(valueToTest));
    }
    { //value -(optimizedSerializer)-> byte[] -(optimizedDeserializer)-> value
      T valueReadBack = deserializeFromBytes(
        serializeToBytes(valueToTest, optimizedExternalizer),
        optimizedExternalizer
      );

      assertThat("optimizedExternalizer: value deserialized is equal to the value serialized",
                 valueReadBack,
                 equalTo(valueToTest));
    }

    { //value -(defaultSerializer)-> byte[] -(optimizedDeserializer)-> value
      T valueReadBack = deserializeFromBytes(
        serializeToBytes(valueToTest, defaultExternalizer),
        optimizedExternalizer
      );

      assertThat("Value deserialized with optimizedExternalizer is equal to the value serialized with defaultExternalizer",
                 valueReadBack,
                 equalTo(valueToTest));
    }

    { //value -(optimizedSerializer)-> byte[] -(defaultDeserializer)-> value
      T valueReadBack = deserializeFromBytes(
        serializeToBytes(valueToTest, optimizedExternalizer),
        defaultExternalizer
      );

      assertThat("Value deserialized with defaultExternalizer is equal to the value serialized with optimizedExternalizer",
                 valueReadBack,
                 equalTo(valueToTest));
    }
  }


  private static <T> T deserializeFromBytes(byte[] bytes,
                                            @NotNull DataExternalizer<T> externalizer) throws IOException {
    try (DataInputStream stream = new DataInputStream(new ByteArrayInputStream(bytes))) {
      T value = externalizer.read(stream);
      if (stream.available() > 0) {
        throw new IllegalStateException(
          "stream is not read fully: " + stream.available() + " bytes left out of " + bytes.length
          + " [" + IOUtil.toHexString(bytes) + "]");
      }
      return value;
    }
  }

  private static <T> byte @NotNull [] serializeToBytes(@NotNull T value,
                                                       @NotNull DataExternalizer<T> externalizer) throws IOException {
    ByteArrayOutputStream bos = new ByteArrayOutputStream();
    try (DataOutputStream output = new DataOutputStream(bos)) {
      externalizer.save(output, value);
    }
    return bos.toByteArray();
  }
}