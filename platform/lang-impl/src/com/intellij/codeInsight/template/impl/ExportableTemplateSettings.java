/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.codeInsight.template.impl;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.components.StoragePathMacros;
import com.intellij.util.SmartList;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

/**
 * Contains exportable part of TemplateSettings (can be shared via export/import settings).
 * @author Rustam Vishnyakov
 */
@State(
  name = "ExportableTemplateSettings",
  storages = @Storage(file = StoragePathMacros.APP_CONFIG + "/template.settings.xml")
)
public class ExportableTemplateSettings implements PersistentStateComponent<ExportableTemplateSettings> {
  private Collection<TemplateSettings.TemplateKey> deletedKeys = new SmartList<TemplateSettings.TemplateKey>();
  private boolean isLoaded = false;
  private TemplateSettings parentSettings;

  @Nullable
  @Override
  public ExportableTemplateSettings getState() {
    if (parentSettings != null) {
      deletedKeys.clear();
      deletedKeys.addAll(parentSettings.getDeletedTemplates());
    }
    return this;
  }

  @Override
  public void loadState(ExportableTemplateSettings state) {
    XmlSerializerUtil.copyBean(state, this);
    isLoaded = true;
  }

  public boolean isLoaded() {
    return isLoaded;
  }

  @SuppressWarnings("UnusedDeclaration") // Property via reflection
  public Collection<TemplateSettings.TemplateKey> getDeletedKeys() {
    return deletedKeys;
  }

  @SuppressWarnings("UnusedDeclaration") // Property via reflection
  public void setDeletedKeys(Collection<TemplateSettings.TemplateKey> deletedKeys) {
    this.deletedKeys = deletedKeys;
  }

  void setParentSettings(TemplateSettings settings) {
    parentSettings = settings;
  }
}
