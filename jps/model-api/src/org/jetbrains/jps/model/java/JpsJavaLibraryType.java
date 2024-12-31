// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.model.java;

import org.jetbrains.jps.model.JpsDummyElement;
import org.jetbrains.jps.model.ex.JpsElementTypeWithDummyProperties;
import org.jetbrains.jps.model.library.JpsLibraryType;

public final class JpsJavaLibraryType extends JpsElementTypeWithDummyProperties implements JpsLibraryType<JpsDummyElement> {
  public static final JpsJavaLibraryType INSTANCE = new JpsJavaLibraryType();
}
