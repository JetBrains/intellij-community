// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui;

/**
 * @deprecated this interface is temporary added to keep compatibility with code which was compiled against the old version of {@link PlaceProvider}
 * with generic parameter and therefore refers to the method with return type {@code Object} in its bytecode. This interfaces isn't supposed to
 * be used directly.
 */
@SuppressWarnings("DeprecatedIsStillUsed")
@Deprecated(forRemoval = true)
public interface PlaceProviderCompatibilityLayer {
  Object getPlace();
}
