// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package intellij.platform.onair.storage.api;

import org.jetbrains.annotations.NotNull;

public interface Storage {

  byte[] lookup(@NotNull Address address);

  Address alloc(@NotNull byte[] what);

  void store(@NotNull Address address, @NotNull byte[] bytes);
}
