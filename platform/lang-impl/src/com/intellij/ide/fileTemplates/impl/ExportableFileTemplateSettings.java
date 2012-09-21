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
package com.intellij.ide.fileTemplates.impl;

import com.intellij.ide.IdeBundle;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.components.*;
import com.intellij.openapi.fileTypes.ex.FileTypeManagerEx;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.List;
import java.util.Locale;

/**
 * Exportable part of file template settings. User-specific (local) settings are handled by FileTemplateManagerImpl.
 *
 * @author Rustam Vishnyakov
 */
@State(
  name="ExportableFileTemplateSettings",
  storages= {
    @Storage(
      file = StoragePathMacros.APP_CONFIG + "/" + ExportableFileTemplateSettings.EXPORTABLE_SETTINGS_FILE
    )}
)
public class ExportableFileTemplateSettings extends FileTemplatesLoader implements PersistentStateComponent<Element>, ExportableComponent {

  public final static String EXPORTABLE_SETTINGS_FILE = "file.template.settings.xml";

  static final String ELEMENT_TEMPLATE = "template";
  static final String ATTRIBUTE_NAME = "name";
  static final String ATTRIBUTE_REFORMAT = "reformat";
  static final String ATTRIBUTE_ENABLED = "enabled";

  private boolean myLoaded = false;

  public ExportableFileTemplateSettings(@NotNull FileTypeManagerEx typeManager) {
    super(typeManager);
  }

  public static ExportableFileTemplateSettings getInstance() {
    return ServiceManager.getService(ExportableFileTemplateSettings.class);
  }


  @NotNull
  @Override
  public File[] getExportFiles() {
    File exportableSettingsFile =
      new File(PathManager.getOptionsPath() + File.separator + EXPORTABLE_SETTINGS_FILE);
    return new File[] {getDefaultTemplatesManager().getConfigRoot(false), exportableSettingsFile};
  }

  @NotNull
  @Override
  public String getPresentableName() {
    return IdeBundle.message("item.file.templates");
  }

  @Nullable
  @Override
  public Element getState() {
    Element element = new Element("fileTemplateSettings");
    for (FTManager manager : getAllManagers()) {
      final Element templatesGroup = new Element(getXmlElementGroupName(manager));
      element.addContent(templatesGroup);
      for (FileTemplateBase template : manager.getAllTemplates(true)) {
        // save only those settings that differ from defaults
        boolean shouldSave = template.isReformatCode() != FileTemplateBase.DEFAULT_REFORMAT_CODE_VALUE;
        if (template instanceof BundledFileTemplate) {
          shouldSave |= ((BundledFileTemplate)template).isEnabled() != FileTemplateBase.DEFAULT_ENABLED_VALUE;
        }
        if (!shouldSave) {
          continue;
        }
        final Element templateElement = new Element(ELEMENT_TEMPLATE);
        templateElement.setAttribute(ATTRIBUTE_NAME, template.getQualifiedName());
        templateElement.setAttribute(ATTRIBUTE_REFORMAT, Boolean.toString(template.isReformatCode()));
        if (template instanceof BundledFileTemplate) {
          templateElement.setAttribute(ATTRIBUTE_ENABLED, Boolean.toString(((BundledFileTemplate)template).isEnabled()));
        }
        templatesGroup.addContent(templateElement);
      }
    }
    return element;
  }

  @Override
  public void loadState(Element state) {
    doLoad(state);
    myLoaded = true;
  }

  public void doLoad(Element element) {
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
        final boolean reformat = Boolean.TRUE.toString().equals(child.getAttributeValue(ATTRIBUTE_REFORMAT));
        template.setReformatCode(reformat);
        if (template instanceof BundledFileTemplate) {
          final boolean enabled = Boolean.parseBoolean(child.getAttributeValue(ATTRIBUTE_ENABLED, "true"));
          ((BundledFileTemplate)template).setEnabled(enabled);
        }
      }
    }
  }

  private static String getXmlElementGroupName(FTManager manager) {
    return manager.getName().toLowerCase(Locale.US) + "_" + "templates";
  }

  public boolean isLoaded() {
    return myLoaded;
  }
}
