// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.model.serialization.java.compiler;

import com.intellij.openapi.util.JDOMExternalizerUtil;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.JpsProject;
import org.jetbrains.jps.model.java.JpsJavaExtensionService;
import org.jetbrains.jps.model.java.compiler.JpsJavaCompilerConfiguration;
import org.jetbrains.jps.model.serialization.JpsProjectExtensionSerializer;

public final class JpsJavaCompilerWorkspaceConfigurationSerializer extends JpsProjectExtensionSerializer {
  public JpsJavaCompilerWorkspaceConfigurationSerializer() {
    super(WORKSPACE_FILE, "CompilerWorkspaceConfiguration");
  }

  @Override
  public void loadExtension(@NotNull JpsProject project, @NotNull Element componentTag) {
    JpsJavaCompilerConfiguration configuration = JpsJavaExtensionService.getInstance().getCompilerConfiguration(project);
    String assertNotNull = JDOMExternalizerUtil.readField(componentTag, "ASSERT_NOT_NULL");
    if (assertNotNull != null) {
      configuration.setAddNotNullAssertions(Boolean.parseBoolean(assertNotNull));
    }
    String clearOutputDirectory = JDOMExternalizerUtil.readField(componentTag, "CLEAR_OUTPUT_DIRECTORY");
    configuration.setClearOutputDirectoryOnRebuild(clearOutputDirectory == null || Boolean.parseBoolean(clearOutputDirectory));
  }
}
