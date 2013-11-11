/*
 * Copyright 2000-2012 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.platform.templates;

import com.intellij.ide.util.projectWizard.ModuleBuilder;
import com.intellij.ide.util.projectWizard.WizardInputField;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.platform.ProjectTemplate;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.IOException;
import java.util.List;
import java.util.zip.ZipInputStream;

/**
 * @author Dmitry Avdeev
 *         Date: 11/14/12
 */
public abstract class ArchivedProjectTemplate implements ProjectTemplate {

  protected final String myDisplayName;
  @Nullable private final String myCategory;

  public ArchivedProjectTemplate(@NotNull String displayName, @Nullable String category) {
    myDisplayName = displayName;
    myCategory = category;
  }

  @NotNull
  @Override
  public String getName() {
    return myDisplayName;
  }

  public Icon getIcon() {
    return getModuleType().getIcon();
  }

  protected abstract ModuleType getModuleType();

  @NotNull
  @Override
  public ModuleBuilder createModuleBuilder() {
    return new TemplateModuleBuilder(this, getModuleType(), getInputFields());
  }

  public abstract List<WizardInputField> getInputFields();

  @Nullable
  @Override
  public ValidationInfo validateSettings() {
    return null;
  }

  public abstract ZipInputStream getStream() throws IOException;

  @Nullable
  public String getCategory() {
    return myCategory;
  }
}
