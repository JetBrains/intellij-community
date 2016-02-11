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
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.project.Project;
import org.jdom.Element;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;

/**
 * Exportable part of file template settings. User-specific (local) settings are handled by FileTemplateManagerImpl.
 *
 * @author Rustam Vishnyakov
 */
@State(
  name = "ExportableFileTemplateSettings",
  storages = @Storage(ExportableFileTemplateSettings.EXPORTABLE_SETTINGS_FILE),
  additionalExportFile = FileTemplatesLoader.TEMPLATES_DIR
)
public class ExportableFileTemplateSettings implements PersistentStateComponent<Element> {
  public final static String EXPORTABLE_SETTINGS_FILE = "file.template.settings.xml";

  static final String ELEMENT_TEMPLATE = "template";
  static final String ATTRIBUTE_NAME = "name";
  static final String ATTRIBUTE_REFORMAT = "reformat";
  static final String ATTRIBUTE_LIVE_TEMPLATE = "live-template-enabled";
  static final String ATTRIBUTE_ENABLED = "enabled";
  private final Project myProject;

  public ExportableFileTemplateSettings(Project project) {
    myProject = project;
  }


  static ExportableFileTemplateSettings getInstance(Project project) {
    return ServiceManager.getService(project, ExportableFileTemplateSettings.class);
  }

  @Nullable
  @Override
  public Element getState() {
    Element element = null;
    for (FTManager manager : getAllManagers()) {
      Element templatesGroup = null;
      for (FileTemplateBase template : manager.getAllTemplates(true)) {
        // save only those settings that differ from defaults
        boolean shouldSave = template.isReformatCode() != FileTemplateBase.DEFAULT_REFORMAT_CODE_VALUE ||
                             template.isLiveTemplateEnabled() != template.isLiveTemplateEnabledByDefault();
        if (template instanceof BundledFileTemplate) {
          shouldSave |= ((BundledFileTemplate)template).isEnabled() != FileTemplateBase.DEFAULT_ENABLED_VALUE;
        }
        if (!shouldSave) {
          continue;
        }
        final Element templateElement = new Element(ELEMENT_TEMPLATE);
        templateElement.setAttribute(ATTRIBUTE_NAME, template.getQualifiedName());
        templateElement.setAttribute(ATTRIBUTE_REFORMAT, Boolean.toString(template.isReformatCode()));
        templateElement.setAttribute(ATTRIBUTE_LIVE_TEMPLATE, Boolean.toString(template.isLiveTemplateEnabled()));
        if (template instanceof BundledFileTemplate) {
          templateElement.setAttribute(ATTRIBUTE_ENABLED, Boolean.toString(((BundledFileTemplate)template).isEnabled()));
        }

        if (templatesGroup == null) {
          templatesGroup = new Element(getXmlElementGroupName(manager));
          if (element == null) {
            element = new Element("fileTemplateSettings");
          }
          element.addContent(templatesGroup);
        }
        templatesGroup.addContent(templateElement);
      }
    }
    return element;
  }

  private FTManager[] getAllManagers() {
    return FileTemplateManagerImpl.getInstanceImpl(myProject).getAllManagers();
  }

  @Override
  public void loadState(Element state) {
    doLoad(state);
  }

  private void doLoad(Element element) {
    for (final FTManager manager : getAllManagers()) {
      final Element templatesGroup = element.getChild(getXmlElementGroupName(manager));
      if (templatesGroup == null) {
        continue;
      }
      final List children = templatesGroup.getChildren(ELEMENT_TEMPLATE);

      for (final Object elem : children) {
        final Element child = (Element)elem;
        final String qName = child.getAttributeValue(ATTRIBUTE_NAME);
        final FileTemplateBase template = manager.getTemplate(qName);
        if (template == null) {
          continue;
        }
        template.setReformatCode(Boolean.TRUE.toString().equals(child.getAttributeValue(ATTRIBUTE_REFORMAT)));
        template.setLiveTemplateEnabled(Boolean.TRUE.toString().equals(child.getAttributeValue(ATTRIBUTE_LIVE_TEMPLATE)));
        if (template instanceof BundledFileTemplate) {
          final boolean enabled = Boolean.parseBoolean(child.getAttributeValue(ATTRIBUTE_ENABLED, "true"));
          ((BundledFileTemplate)template).setEnabled(enabled);
        }
      }
    }
  }

  private static String getXmlElementGroupName(FTManager manager) {
    return manager.getName().toLowerCase(Locale.US) + "_templates";
  }
}
