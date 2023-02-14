// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.util.projectWizard;

import com.intellij.openapi.ui.LabeledComponent;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.Pair;
import com.intellij.util.PathUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class WebProjectSettingsStepWrapper implements SettingsStep {
  private final List<Pair<String, JComponent>> myFields = new ArrayList<>();
  private final List<JComponent> myComponents = new ArrayList<>();
  private final @Nullable ProjectSettingsStepBase<?> myStepBase;

  /**
   * @deprecated Use {@link #WebProjectSettingsStepWrapper(ProjectSettingsStepBase)} instead
   */
  @Deprecated(forRemoval = true)
  public WebProjectSettingsStepWrapper() {
    this(null);
  }

  public WebProjectSettingsStepWrapper(@Nullable ProjectSettingsStepBase<?> stepBase) {
    myStepBase = stepBase;
  }

  public List<JComponent> getComponents() {
    return myComponents;
  }

  @Override
  @Nullable
  public WizardContext getContext() {
    return null;
  }

  public List<LabeledComponent<? extends JComponent>> getFields() {
    return ContainerUtil.map(myFields, (Pair<@NotNull @Nls String, @NotNull JComponent> pair) -> LabeledComponent.create(pair.second, pair.first));
  }

  @Override
  public void addSettingsField(@NotNull @NlsContexts.Label String label, @NotNull JComponent field) {
    myFields.add(Pair.create(label, field));
  }

  @Override
  public void addSettingsComponent(@NotNull JComponent component) {
    myComponents.add(component);
  }

  @Override
  public void addExpertPanel(@NotNull JComponent panel) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void addExpertField(@NotNull @NlsContexts.Label String label, @NotNull JComponent field) {
    throw new UnsupportedOperationException();
  }

  public boolean isEmpty() {
    return myFields.isEmpty() && myComponents.isEmpty();
  }

  @Override
  public @Nullable ModuleNameLocationSettings getModuleNameLocationSettings() {
    if (myStepBase == null) return null;
    return new ModuleNameLocationSettings() {
      @Override
      public @NotNull String getModuleName() {
        return PathUtil.getFileName(myStepBase.getProjectLocation());
      }

      @Override
      public void setModuleName(@NotNull String moduleName) {
        myStepBase.setLocation(PathUtil.getParentPath(myStepBase.getProjectLocation()) + File.separatorChar + moduleName);
      }

      @Override
      public @NotNull String getModuleContentRoot() {
        return myStepBase.getProjectLocation();
      }

      @Override
      public void setModuleContentRoot(@NotNull String path) {
        myStepBase.setLocation(path);
      }
    };
  }
}
