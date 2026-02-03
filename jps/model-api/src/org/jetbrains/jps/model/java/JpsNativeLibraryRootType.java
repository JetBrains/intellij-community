// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.jps.model.java;

import org.jetbrains.jps.model.library.JpsOrderRootType;

public final class JpsNativeLibraryRootType extends JpsOrderRootType {
  public static final JpsNativeLibraryRootType INSTANCE = new JpsNativeLibraryRootType();

  private JpsNativeLibraryRootType() { }

  @Override
  public String toString() {
    return "native lib root";
  }
}
