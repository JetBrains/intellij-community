package com.intellij.compiler.options;

import com.intellij.openapi.compiler.CompileContext;
import com.intellij.openapi.compiler.Compiler;
import com.intellij.openapi.compiler.options.ExcludedEntriesConfiguration;
import com.intellij.openapi.components.*;
import com.intellij.openapi.project.Project;
import com.intellij.util.xmlb.XmlSerializerUtil;

import java.util.HashMap;
import java.util.Map;

/**
 * @author Dmitry Avdeev
 */
@State(
  name = "ValidationConfiguration",
  storages = {
    @Storage( file = "$WORKSPACE_FILE$"),
    @Storage( file = "$PROJECT_CONFIG_DIR$/validation.xml", scheme = StorageScheme.DIRECTORY_BASED)
  }
)
public class ValidationConfiguration implements PersistentStateComponent<ValidationConfiguration> {

  public boolean VALIDATE_ON_BUILD = false;
  public Map<String, Boolean> VALIDATORS = new HashMap<String, Boolean>();

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

  public static ExcludedEntriesConfiguration getExcludedEntriesConfiguration(Project project) {
    return ServiceManager.getService(project, ExcludedFromValidationConfiguration.class);
  }

  public ValidationConfiguration getState() {
    return this;
  }

  public void loadState(final ValidationConfiguration state) {
    XmlSerializerUtil.copyBean(state, this);
  }

  @State(
      name = "ExcludeFromValidation",
      storages = {
          @Storage( file = "$PROJECT_FILE$"),
          @Storage( file = "$PROJECT_CONFIG_DIR$/excludeFromValidation.xml", scheme = StorageScheme.DIRECTORY_BASED)
      }
  )
  public static class ExcludedFromValidationConfiguration extends ExcludedEntriesConfiguration {}
}
