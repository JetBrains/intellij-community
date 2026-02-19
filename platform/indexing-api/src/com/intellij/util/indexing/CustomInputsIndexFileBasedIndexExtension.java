// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing;

import com.intellij.util.io.DataExternalizer;
import org.jetbrains.annotations.ApiStatus.Internal;
import org.jetbrains.annotations.ApiStatus.OverrideOnly;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

/**
 * {@link IndexExtension index implementation} can implement this interface to override default {@link DataExternalizer}s
 * used for index storage.
 * <p>
 * By default, {@link IndexExtension} provides externalizers for Key and Value only -- and it is up to indexing framework
 * to implement externalizers for keys/values Collections, and Maps on top of those primitive externalizers (e.g.
 * {@link InputMapExternalizer}, {@link com.intellij.util.indexing.impl.InputIndexDataExternalizer}). Those default
 * externalizers provided by indexing framework could be non-optimal for some indexes -- such indexes could implement
 * this interface, and provide customized implementations. Customized externalizers could e.g. rely on known data
 * regularities to store data in a more compact form, and/or deserialize data in specialized collections implementation.
 * </p>
 * <p>
 * (Naturally, this interface should _extend_ {@link IndexExtension}, but {@link IndexExtension} is an abstract class,
 * not an interface)</p>
 *
 * TODO RC: this interface is to be replaced with CustomInputMapIndexExtension, see it's docs for discussion
 *
 * @see IndexExtension
 */
@OverrideOnly
@Internal
public interface CustomInputsIndexFileBasedIndexExtension<K> {
  @NotNull
  DataExternalizer<Collection<K>> createExternalizer();
}
