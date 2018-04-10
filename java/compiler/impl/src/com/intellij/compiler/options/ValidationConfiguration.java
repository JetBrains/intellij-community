// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.compiler.options;

import com.intellij.openapi.compiler.CompileContext;
import com.intellij.openapi.compiler.Compiler;
import com.intellij.openapi.compiler.options.ExcludedEntriesConfiguration;
import com.intellij.openapi.compiler.options.ExcludesConfiguration;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.project.Project;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.serialization.java.compiler.JpsCompilerValidationExcludeSerializer;

import java.util.HashMap;
import java.util.Map;

/**
 * @author Dmitry Avdeev
 */
@State(name = "ValidationConfiguration", storages = @Storage("validation.xml"))
public class ValidationConfiguration implements PersistentStateComponent<ValidationConfiguration> {

  public boolean VALIDATE_ON_BUILD = false;
  public Map<String, Boolean> VALIDATORS = new HashMap<>();

  public static boolean shouldValidate(Compiler validator, CompileContext context) {
    ValidationConfiguration configuration = getInstance(context.getProject());
    return (configuration.VALIDATE_ON_BUILD) && configuration.isSelected(validator);
  }

  public boolean isSelected(Compiler validator) {
    return isSelected(validator.getDescription());
  }

  public boolean isSelected(String validatorDescription) {
    final Boolean selected = VALIDATORS.get(validatorDescription);
    return selected == null || selected.booleanValue();
  }

  public void setSelected(Compiler validator, boolean selected) {
    setSelected(validator.getDescription(), selected);
  }

  public void setSelected(String validatorDescription, boolean selected) {
    VALIDATORS.put(validatorDescription, selected);
  }

  public static ValidationConfiguration getInstance(Project project) {
    return ServiceManager.getService(project, ValidationConfiguration.class);
  }

  public static ExcludesConfiguration getExcludedEntriesConfiguration(Project project) {
    return ServiceManager.getService(project, ExcludedFromValidationConfiguration.class);
  }

  public ValidationConfiguration getState() {
    return this;
  }

  public void loadState(@NotNull final ValidationConfiguration state) {
    XmlSerializerUtil.copyBean(state, this);
  }

  @State(
    name = JpsCompilerValidationExcludeSerializer.COMPONENT_NAME,
    storages = @Storage(JpsCompilerValidationExcludeSerializer.CONFIG_FILE_NAME)
  )
  public static class ExcludedFromValidationConfiguration extends ExcludedEntriesConfiguration {
    public ExcludedFromValidationConfiguration() {
      super(null);
    }
  }
}
