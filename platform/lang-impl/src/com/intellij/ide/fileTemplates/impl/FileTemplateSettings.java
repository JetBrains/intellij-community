// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.fileTemplates.impl;

import com.intellij.ide.fileTemplates.FileTemplate;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Exportable part of file template settings. User-specific (local) settings are handled by FileTemplateManagerImpl.
 */
@State(
  name = "ExportableFileTemplateSettings",
  storages = @Storage(FileTemplateSettings.EXPORTABLE_SETTINGS_FILE)
)
class FileTemplateSettings extends FileTemplatesLoader implements PersistentStateComponent<Element> {
  private static final Logger LOG = Logger.getInstance(FileTemplateSettings.class);
  static final String EXPORTABLE_SETTINGS_FILE = "file.template.settings.xml";

  private static final String ELEMENT_TEMPLATE = "template";
  private static final String ATTRIBUTE_NAME = "name";
  private static final String ATTRIBUTE_FILE_NAME = "file-name";
  private static final String ATTRIBUTE_REFORMAT = "reformat";
  private static final String ATTRIBUTE_LIVE_TEMPLATE = "live-template-enabled";
  private static final String ATTRIBUTE_ENABLED = "enabled";

  FileTemplateSettings(@Nullable Project project) {
    super(project);
  }

  @Override
  public @NotNull Element getState() {
    Element element = new Element("fileTemplateSettings");

    for (FTManager manager : getAllManagers()) {
      Element templatesGroup = null;
      for (FileTemplateBase template : manager.getAllTemplates(true)) {
        // save only those settings that differ from defaults
        if (!shouldSave(template)) continue;

        final Element templateElement = saveTemplate(template);
        for (FileTemplate child : template.getChildren()) {
          templateElement.addContent(saveTemplate((FileTemplateBase)child));
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

  private static boolean shouldSave(FileTemplateBase template) {
    boolean shouldSave = template.isReformatCode() != FileTemplateBase.DEFAULT_REFORMAT_CODE_VALUE ||
                         !template.getFileName().isEmpty() ||
                         // check isLiveTemplateEnabledChanged() first to avoid expensive loading all templates on exit
                         template.isLiveTemplateEnabledChanged() && template.isLiveTemplateEnabled() != template.isLiveTemplateEnabledByDefault();
    if (template instanceof BundledFileTemplate) {
      shouldSave |= ((BundledFileTemplate)template).isEnabled() != FileTemplateBase.DEFAULT_ENABLED_VALUE;
    }
    return shouldSave || ContainerUtil.or(template.getChildren(), child -> shouldSave((FileTemplateBase)child));
  }

  private static @NotNull Element saveTemplate(FileTemplateBase template) {
    final Element templateElement = new Element(ELEMENT_TEMPLATE);
    templateElement.setAttribute(ATTRIBUTE_NAME, template.getQualifiedName());
    if (!template.getFileName().isEmpty()) {
      templateElement.setAttribute(ATTRIBUTE_FILE_NAME, template.getFileName());
    }
    templateElement.setAttribute(ATTRIBUTE_REFORMAT, Boolean.toString(template.isReformatCode()));
    templateElement.setAttribute(ATTRIBUTE_LIVE_TEMPLATE, Boolean.toString(template.isLiveTemplateEnabled()));

    if (template instanceof BundledFileTemplate) {
      templateElement.setAttribute(ATTRIBUTE_ENABLED, Boolean.toString(((BundledFileTemplate)template).isEnabled()));
    }
    return templateElement;
  }

  @Override
  public void loadState(@NotNull Element state) {
    for (final FTManager manager : getAllManagers()) {
      final Element templatesGroup = state.getChild(getXmlElementGroupName(manager));
      if (templatesGroup == null) continue;

      for (Element element : templatesGroup.getChildren(ELEMENT_TEMPLATE)) {
        loadTemplate(element, manager);
        for (Element child : element.getChildren(ELEMENT_TEMPLATE)) {
          loadTemplate(child, manager);
        }
      }
    }
  }

  @Override
  protected void reloadTemplates() {
    Element state = getState();
    super.reloadTemplates();
    loadState(state);
  }

  private static void loadTemplate(Element element, FTManager manager) {
    final String qName = element.getAttributeValue(ATTRIBUTE_NAME);
    if (qName == null) return;
    final FileTemplateBase template = manager.getTemplate(qName);
    if (template == null) {
      LOG.warn("Template is missing: " + qName);
      return;
    }
    template.setFileName(StringUtil.notNullize(element.getAttributeValue(ATTRIBUTE_FILE_NAME)));
    template.setReformatCode(Boolean.parseBoolean(element.getAttributeValue(ATTRIBUTE_REFORMAT)));
    template.setLiveTemplateEnabled(Boolean.parseBoolean(element.getAttributeValue(ATTRIBUTE_LIVE_TEMPLATE)));

    if (template instanceof BundledFileTemplate) {
      ((BundledFileTemplate)template).setEnabled(Boolean.parseBoolean(element.getAttributeValue(ATTRIBUTE_ENABLED, "true")));
    }
  }

  private static String getXmlElementGroupName(@NotNull FTManager manager) {
    return StringUtil.toLowerCase(manager.getName()) + "_templates";
  }
}