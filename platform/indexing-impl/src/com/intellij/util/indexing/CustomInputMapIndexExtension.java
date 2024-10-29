// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing;

import com.intellij.util.io.DataExternalizer;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

/**
 * Replacement for {@link CustomInputsIndexFileBasedIndexExtension}:
 * {@link CustomInputsIndexFileBasedIndexExtension} provides an externalizer for {@code Collection<Key>},
 * but this externalizer is used _only_ to create from it an externalizer for {@code Map<Key,Value>}.
 * (see {@link DefaultIndexStorageLayoutProviderKt#defaultMapExternalizerFor(IndexExtension)}).
 * So, ideally, we should keep this interface only, and drop {@link CustomInputsIndexFileBasedIndexExtension}
 * as archaic.
 * But:
 * 1. There are few impls of CustomInputsIndexFileBasedIndexExtension -- it takes time to re-implement them.
 * 2. Also, there is {@link HashBasedIndexGenerator.InputMapExternalizerToStableBinary}
 * which uses CustomInputsIndexFileBasedIndexExtension-provided {@code Collection<Key>}-externalizer to implement
 * stable-binary representation version of InputMapExternalizer.
 * It is not clear how to do the same with this interface {@code Map<Key, Value>}-externalizer alone.
 *  TODO RC: So, until those obstacles resolved, we need both interfaces: to customize input map serialization one must
 *  implement _either_ {@link CustomInputsIndexFileBasedIndexExtension}, or {@link CustomInputMapIndexExtension},
 *  (with {@link CustomInputMapIndexExtension#createExternalizer()} to be used for shared indexes stable-binary only).
 *  This is why this interface extends legacy {@link CustomInputsIndexFileBasedIndexExtension}
 */
@ApiStatus.Internal
public interface CustomInputMapIndexExtension<K, V> extends CustomInputsIndexFileBasedIndexExtension<K> {

  /** @see InputMapExternalizer default implementation */
  @NotNull DataExternalizer<Map<K, V>> createInputMapExternalizer();
}
