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
import com.intellij.ide.util.projectWizard.ModuleWizardStep;
import com.intellij.ide.util.projectWizard.WizardContext;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.module.ModuleTypeManager;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.io.StreamUtil;
import com.intellij.platform.ProjectTemplate;
import org.jdom.Document;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.IOException;
import java.net.URL;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * @author Dmitry Avdeev
 *         Date: 10/1/12
 */
public class ArchivedProjectTemplate implements ProjectTemplate {

  static final String DESCRIPTION_PATH = ".idea/description.html";

  private final String myDisplayName;
  private final URL myArchivePath;
  private final ModuleType myModuleType;
  private final WizardContext myContext;

  public ArchivedProjectTemplate(String displayName,
                                 URL archivePath,
                                 WizardContext context) {

    myDisplayName = displayName;
    myArchivePath = archivePath;
    myContext = context;
    myModuleType = computeModuleType(this);
  }

  @NotNull
  @Override
  public String getName() {
    return myDisplayName;
  }

  @Override
  public String getDescription() {
    return readEntry(new Condition<ZipEntry>() {
      @Override
      public boolean value(ZipEntry entry) {
        return entry.getName().endsWith(DESCRIPTION_PATH);
      }
    });
  }

  @Nullable
  String readEntry(Condition<ZipEntry> condition) {
    ZipInputStream stream = null;
    try {
      stream = getStream();
      ZipEntry entry;
      while ((entry = stream.getNextEntry()) != null) {
        if (condition.value(entry)) {
          return StreamUtil.readText(stream);
        }
      }
    }
    catch (IOException e) {
      return null;
    }
    finally {
      StreamUtil.closeStream(stream);
    }
    return null;
  }

  @NotNull
  @Override
  public ModuleBuilder createModuleBuilder() {
    return new TemplateModuleBuilder(this, myModuleType);
  }

  @NotNull
  private static ModuleType computeModuleType(ArchivedProjectTemplate template) {
    String iml = template.readEntry(new Condition<ZipEntry>() {
      @Override
      public boolean value(ZipEntry entry) {
        return entry.getName().endsWith(".iml");
      }
    });
    if (iml == null) return ModuleType.EMPTY;
    try {
      Document document = JDOMUtil.loadDocument(iml);
      String type = document.getRootElement().getAttributeValue(Module.ELEMENT_TYPE);
      return ModuleTypeManager.getInstance().findByID(type);
    }
    catch (Exception e) {
      return ModuleType.EMPTY;
    }
  }

  @Nullable
  @Override
  public ValidationInfo validateSettings() {
    return null;
  }

  ZipInputStream getStream() throws IOException {
    return new ZipInputStream(myArchivePath.openStream());
  }

  @Override
  public JComponent getSettingsPanel() {
    ModuleWizardStep step = myModuleType.createSettingsStep(myContext);
    return step == null ? null : step.getComponent();
  }
}
