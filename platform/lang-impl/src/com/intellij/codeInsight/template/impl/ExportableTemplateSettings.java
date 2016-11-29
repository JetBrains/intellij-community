/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
import com.intellij.openapi.components.RoamingType;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.util.SmartList;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;

@SuppressWarnings("deprecation")
@Deprecated
@State(
  name = "ExportableTemplateSettings",
  storages = @Storage(value = "template.settings.xml", roamingType = RoamingType.DISABLED)
)
final class ExportableTemplateSettings implements PersistentStateComponent<ExportableTemplateSettings> {
  public Collection<TemplateSettings.TemplateKey> deletedKeys = new SmartList<>();

  @Nullable
  @Override
  public ExportableTemplateSettings getState() {
    return this;
  }

  @Override
  public void loadState(ExportableTemplateSettings state) {
    TemplateSettings templateSettings = TemplateSettings.getInstance();
    List<TemplateSettings.TemplateKey> deletedTemplates = templateSettings.getDeletedTemplates();
    deletedTemplates.clear();
    deletedTemplates.addAll(state.deletedKeys);
    templateSettings.applyNewDeletedTemplates();
  }
}
