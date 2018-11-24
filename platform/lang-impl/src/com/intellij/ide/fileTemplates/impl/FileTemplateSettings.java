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
package com.intellij.ide.fileTemplates.impl;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.fileTypes.ex.FileTypeManagerEx;
import com.intellij.openapi.project.Project;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/**
 * Exportable part of file template settings. User-specific (local) settings are handled by FileTemplateManagerImpl.
 *
 * @author Rustam Vishnyakov
 */
@State(
  name = "ExportableFileTemplateSettings",
  storages = @Storage(FileTemplateSettings.EXPORTABLE_SETTINGS_FILE)
)
public class FileTemplateSettings extends FileTemplatesLoader implements PersistentStateComponent<Element> {
  public static final String EXPORTABLE_SETTINGS_FILE = "file.template.settings.xml";

  private static final String ELEMENT_TEMPLATE = "template";
  private static final String ATTRIBUTE_NAME = "name";
  private static final String ATTRIBUTE_REFORMAT = "reformat";
  private static final String ATTRIBUTE_LIVE_TEMPLATE = "live-template-enabled";
  private static final String ATTRIBUTE_ENABLED = "enabled";

  public FileTemplateSettings(@NotNull FileTypeManagerEx typeManager, @Nullable Project project) {
    super(typeManager, project);
  }

  @Nullable
  @Override
  public Element getState() {
    Element element = new Element("fileTemplateSettings");

    for (FTManager manager : getAllManagers()) {
      Element templatesGroup = null;
      for (FileTemplateBase template : manager.getAllTemplates(true)) {
        // save only those settings that differ from defaults
        boolean shouldSave = template.isReformatCode() != FileTemplateBase.DEFAULT_REFORMAT_CODE_VALUE ||
                             template.isLiveTemplateEnabled() != template.isLiveTemplateEnabledByDefault();
        if (template instanceof BundledFileTemplate) {
          shouldSave |= ((BundledFileTemplate)template).isEnabled() != FileTemplateBase.DEFAULT_ENABLED_VALUE;
        }
        if (!shouldSave) continue;

        final Element templateElement = new Element(ELEMENT_TEMPLATE);
        templateElement.setAttribute(ATTRIBUTE_NAME, template.getQualifiedName());
        templateElement.setAttribute(ATTRIBUTE_REFORMAT, Boolean.toString(template.isReformatCode()));
        templateElement.setAttribute(ATTRIBUTE_LIVE_TEMPLATE, Boolean.toString(template.isLiveTemplateEnabled()));

        if (template instanceof BundledFileTemplate) {
          templateElement.setAttribute(ATTRIBUTE_ENABLED, Boolean.toString(((BundledFileTemplate)template).isEnabled()));
        }

        if (templatesGroup == null) {
          templatesGroup = new Element(getXmlElementGroupName(manager));
          element.addContent(templatesGroup);
        }
        templatesGroup.addContent(templateElement);
      }
    }

    return element;
  }

  @Override
  public void loadState(Element state) {
    for (final FTManager manager : getAllManagers()) {
      final Element templatesGroup = state.getChild(getXmlElementGroupName(manager));
      if (templatesGroup == null) continue;

      for (Element child : templatesGroup.getChildren(ELEMENT_TEMPLATE)) {
        final String qName = child.getAttributeValue(ATTRIBUTE_NAME);
        final FileTemplateBase template = manager.getTemplate(qName);
        if (template == null) continue;

        template.setReformatCode(Boolean.parseBoolean(child.getAttributeValue(ATTRIBUTE_REFORMAT)));
        template.setLiveTemplateEnabled(Boolean.parseBoolean(child.getAttributeValue(ATTRIBUTE_LIVE_TEMPLATE)));

        if (template instanceof BundledFileTemplate) {
          ((BundledFileTemplate)template).setEnabled(Boolean.parseBoolean(child.getAttributeValue(ATTRIBUTE_ENABLED, "true")));
        }
      }
    }
  }

  private static String getXmlElementGroupName(FTManager manager) {
    return manager.getName().toLowerCase(Locale.US) + "_templates";
  }
}