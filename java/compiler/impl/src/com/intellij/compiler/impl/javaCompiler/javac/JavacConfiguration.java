// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compiler.impl.javaCompiler.javac;

import com.intellij.openapi.components.*;
import com.intellij.openapi.project.Project;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.java.compiler.JpsJavaCompilerOptions;

import java.util.HashMap;
import java.util.Map;

@Service(Service.Level.PROJECT)
@State(name = "JavacSettings", storages = @Storage("compiler.xml"))
public final class JavacConfiguration implements PersistentStateComponent<JpsJavaCompilerOptions> {
  private final JpsJavaCompilerOptions mySettings = new JpsJavaCompilerOptions();
  private final Project project;

  public JavacConfiguration(@NotNull Project project) {
    this.project = project;
  }

  @Override
  public @NotNull JpsJavaCompilerOptions getState() {
    final JpsJavaCompilerOptions state = new JpsJavaCompilerOptions();
    XmlSerializerUtil.copyBean(mySettings, state);
    // `copyBean` copies by reference, we need a map clone here
    state.ADDITIONAL_OPTIONS_OVERRIDE = new HashMap<>(state.ADDITIONAL_OPTIONS_OVERRIDE);
    final PathMacroManager macros = PathMacroManager.getInstance(project);
    state.ADDITIONAL_OPTIONS_STRING = macros.collapsePathsRecursively(state.ADDITIONAL_OPTIONS_STRING);
    for (Map.Entry<String, String> entry : state.ADDITIONAL_OPTIONS_OVERRIDE.entrySet()) {
      entry.setValue(macros.collapsePathsRecursively(entry.getValue()));
    }
    return state;
  }

  @Override
  public void loadState(@NotNull JpsJavaCompilerOptions state) {
    XmlSerializerUtil.copyBean(state, mySettings);
  }

  // we cannot change the signature of this method to preserve backward compatibility
  public static JpsJavaCompilerOptions getOptions(Project project, @SuppressWarnings("TypeParameterExtendsFinalClass") Class<? extends JavacConfiguration> aClass) {
    JavacConfiguration configuration = project.getService(aClass);
    return configuration.mySettings;
  }
}