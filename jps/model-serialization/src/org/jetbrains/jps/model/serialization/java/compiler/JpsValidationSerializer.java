// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.jps.model.serialization.java.compiler;

import com.intellij.util.xmlb.XmlSerializer;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.JpsProject;
import org.jetbrains.jps.model.java.JpsJavaExtensionService;
import org.jetbrains.jps.model.java.compiler.JpsJavaCompilerConfiguration;
import org.jetbrains.jps.model.serialization.JpsProjectExtensionSerializer;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class JpsValidationSerializer extends JpsProjectExtensionSerializer {
  public static final String COMPONENT_NAME = "ValidationConfiguration";
  public static final String CONFIG_FILE_NAME = "validation.xml";

  public JpsValidationSerializer() {
    super(CONFIG_FILE_NAME, COMPONENT_NAME);
  }

  @Override
  public void loadExtension(@NotNull JpsProject project, @NotNull Element componentTag) {
    JpsJavaCompilerConfiguration configuration = JpsJavaExtensionService.getInstance().getCompilerConfiguration(project);
    ValidationConfigurationState state = XmlSerializer.deserialize(componentTag, ValidationConfigurationState.class);
    Set<String> disabledValidators = state.VALIDATORS.entrySet().stream()
      .filter(e -> e.getValue() == Boolean.FALSE)
      .map(e -> e.getKey())
      .collect(Collectors.toSet());
    configuration.setValidationConfiguration(state.VALIDATE_ON_BUILD, disabledValidators);
  }

  public static class ValidationConfigurationState {
    public boolean VALIDATE_ON_BUILD = false;
    public Map<String, Boolean> VALIDATORS = new HashMap<>();
  }
}
