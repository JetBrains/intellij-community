// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.jps.model.java.impl;

import com.intellij.openapi.util.Pair;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.JpsElementParameterizedCreator;
import org.jetbrains.jps.model.ex.JpsElementChildRoleBase;
import org.jetbrains.jps.model.java.JavaModuleIndex;
import org.jetbrains.jps.model.java.compiler.JpsJavaCompilerConfiguration;

import java.io.File;

/**
 * @author Eugene Zhuravlev
 * Date: 11-Oct-17
 */
public class JavaModuleIndexRole extends JpsElementChildRoleBase<JavaModuleIndex> implements JpsElementParameterizedCreator<JavaModuleIndex, Pair<JpsJavaCompilerConfiguration, File>>{
  public static final JavaModuleIndexRole INSTANCE = new JavaModuleIndexRole();
  
  public JavaModuleIndexRole() {
    super("java module index");
  }

  @NotNull
  public JavaModuleIndex create(@NotNull Pair<JpsJavaCompilerConfiguration, File> param) {
    return JavaModuleIndexImpl.load(param.second, param.first.getCompilerExcludes());
  }
}
