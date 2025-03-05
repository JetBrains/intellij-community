// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compiler.impl.rmiCompiler;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.project.Project;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.java.compiler.RmicCompilerOptions;

@State(name = "RmicSettings", storages = @Storage("compiler.xml"))
public class RmicConfiguration implements PersistentStateComponent<RmicCompilerOptions> {
  private final RmicCompilerOptions mySettings = new RmicCompilerOptions();

  @Override
  public @NotNull RmicCompilerOptions getState() {
    return mySettings;
  }

  @Override
  public void loadState(@NotNull RmicCompilerOptions state) {
    XmlSerializerUtil.copyBean(state, mySettings);
  }

  public static RmicCompilerOptions getOptions(Project project) {
    return project.getService(RmicConfiguration.class).getState();
  }
}