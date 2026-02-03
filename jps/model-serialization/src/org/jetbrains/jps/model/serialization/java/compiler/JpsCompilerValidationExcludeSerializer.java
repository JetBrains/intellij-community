// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.model.serialization.java.compiler;

import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.JpsProject;
import org.jetbrains.jps.model.java.JpsJavaExtensionService;
import org.jetbrains.jps.model.java.compiler.JpsJavaCompilerConfiguration;
import org.jetbrains.jps.model.serialization.JpsProjectExtensionSerializer;

public final class JpsCompilerValidationExcludeSerializer extends JpsProjectExtensionSerializer {
  public static final String COMPONENT_NAME = "ExcludeFromValidation";
  public static final String CONFIG_FILE_NAME = "excludeFromValidation.xml";

  public JpsCompilerValidationExcludeSerializer() {
    super(CONFIG_FILE_NAME, COMPONENT_NAME);
  }

  @Override
  public void loadExtension(@NotNull JpsProject project, @NotNull Element componentTag) {
    JpsJavaCompilerConfiguration configuration = JpsJavaExtensionService.getInstance().getCompilerConfiguration(project);
    JpsJavaCompilerConfigurationSerializer.readExcludes(componentTag, configuration.getValidationExcludes());
  }
}
