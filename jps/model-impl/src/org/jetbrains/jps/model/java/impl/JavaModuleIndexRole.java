// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.model.java.impl;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.JpsElementParameterizedCreator;
import org.jetbrains.jps.model.ex.JpsElementChildRoleBase;
import org.jetbrains.jps.model.java.JavaModuleIndex;
import org.jetbrains.jps.model.java.compiler.JpsCompilerExcludes;

/**
 * @author Eugene Zhuravlev
 */
final class JavaModuleIndexRole extends JpsElementChildRoleBase<JavaModuleIndex> implements JpsElementParameterizedCreator<JavaModuleIndex, JpsCompilerExcludes>{
  static final JavaModuleIndexRole INSTANCE = new JavaModuleIndexRole();

  private JavaModuleIndexRole() {
    super("java module index");
  }

  @Override
  public @NotNull JavaModuleIndex create(@NotNull JpsCompilerExcludes excludes) {
    return new JavaModuleIndexImpl(excludes);
  }
}