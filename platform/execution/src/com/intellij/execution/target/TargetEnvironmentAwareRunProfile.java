// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.target;

import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.configurations.RunConfigurationBase;
import com.intellij.execution.configurations.RunProfile;
import com.intellij.execution.configurations.RuntimeConfigurationError;
import com.intellij.execution.configurations.RuntimeConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Base interface for run configuration that can be run on arbitrary {@link TargetEnvironmentConfiguration}.
 * <p>
 * As soon as a run configuration implements the interface, and returns non-null value from {@link #getDefaultLanguageRuntimeType()},
 * its Run configuration editor gets `Run on` combobox with the list of targets to run on.
 * <p>
 * Target environment to run on can be retrieve via {@link TargetEnvironmentsManager}.
 *
 * @see com.intellij.execution.RunOnTargetComboBox
 * @see TargetEnvironment
 */
public interface TargetEnvironmentAwareRunProfile extends RunProfile {
  boolean canRunOn(@NotNull TargetEnvironmentConfiguration target);

  /**
   * Returns language runtime type that should be configured for {@link TargetEnvironmentConfiguration} if it's
   * created for this particular run configuration.
   * <p>
   * That language runtime type will be used while creating {@link TargetEnvironmentConfiguration} using
   * wizard from {@link com.intellij.execution.RunOnTargetComboBox}
   *
   * @return null to dynamically disable targets functionality for given configuration instance
   * @see TargetEnvironmentType#createStepsForNewWizard
   */
  @Nullable
  LanguageRuntimeType<?> getDefaultLanguageRuntimeType();

  /**
   * Returns the identifier of the target environment to run on, which is either:
   * <ul>
   *   <li>{@code null} for the project default target (available via {@link TargetEnvironmentsManager#getDefaultTarget()});</li>
   *   <li>the display name of the target environment;</li>
   *   <li>the special value for the explicitly chosen local machine target
   *   ({@link com.intellij.execution.target.local.LocalTargetType#LOCAL_TARGET_NAME}).</li>
   * </ul>
   *
   * @return the display name of target environment to run on, the special value for explicitly chosen local machine target
   * or {@code null} if the project default target is chosen
   * @see TargetEnvironmentConfiguration#getDisplayName()
   * @see com.intellij.execution.target.local.LocalTargetType#LOCAL_TARGET_NAME
   */
  @Nullable
  String getDefaultTargetName();

  void setDefaultTargetName(@Nullable String targetName);

  /**
   * Takes into the account the project default target.
   *
   * @param project the project to validate with
   * @throws RuntimeConfigurationException if the validation fails
   * @see TargetEnvironmentsManager#getDefaultTarget()
   */
  default void validateRunTarget(@SuppressWarnings("unused") @NotNull Project project) throws RuntimeConfigurationException {
    String targetName = TargetEnvironmentConfigurations.getEffectiveTargetName(this, project);
    if (targetName == null) {
      return;
    }

    TargetEnvironmentConfiguration target = ContainerUtil.find(
      TargetEnvironmentsManager.getInstance(project).getTargets().resolvedConfigs(),
      next -> targetName.equals(next.getDisplayName()));

    if (target == null) {
      throw new RuntimeConfigurationError(
        ExecutionBundle.message("TargetEnvironmentAwareRunProfile.error.cannot.find.run.target", targetName));
    }
    try {
      target.validateConfiguration();
    }
    catch (RuntimeConfigurationException e) {
      throw new RuntimeConfigurationError(
        ExecutionBundle.message("TargetEnvironmentAwareRunProfile.error.run.target.error.0", e.getMessage()));
    }
  }

  default boolean needPrepareTarget() {
    Project project = (this instanceof RunConfigurationBase<?>)
                      ? ((RunConfigurationBase<?>)this).getProject()
                      : null;
    String targetName = TargetEnvironmentConfigurations.getEffectiveTargetName(this, project);
    if (targetName == null) return false;

    TargetEnvironmentType<?> type = TargetEnvironmentType.EXTENSION_NAME.findFirstSafe(t -> {
      return targetName.equals(t.getDisplayName());
    });
    return type == null || !type.isLocalTarget();
  }
}
