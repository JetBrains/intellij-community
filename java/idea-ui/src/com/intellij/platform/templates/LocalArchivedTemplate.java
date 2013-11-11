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

import com.intellij.ide.util.projectWizard.WizardInputField;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.module.ModuleTypeManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.io.StreamUtil;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.Namespace;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.Collections;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * @author Dmitry Avdeev
 *         Date: 10/1/12
 */
public class LocalArchivedTemplate extends ArchivedProjectTemplate {

  public static final String DESCRIPTION_PATH = Project.DIRECTORY_STORE_FOLDER + "/description.html";
  static final String IDEA_INPUT_FIELDS_XML = Project.DIRECTORY_STORE_FOLDER + "/project-template.xml";

  private final URL myArchivePath;
  private final ModuleType myModuleType;
  private List<WizardInputField> myInputFields = Collections.emptyList();
  private Icon myIcon;

  public LocalArchivedTemplate(@NotNull URL archivePath,
                               @NotNull ClassLoader classLoader) {
    super(getTemplateName(archivePath), null);

    myArchivePath = archivePath;
    myModuleType = computeModuleType(this);
    String s = readEntry(new Condition<ZipEntry>() {
      @Override
      public boolean value(ZipEntry entry) {
        return entry.getName().endsWith(IDEA_INPUT_FIELDS_XML);
      }
    });
    if (s != null) {
      try {
        Element templateElement = JDOMUtil.loadDocument(s).getRootElement();
        myInputFields = RemoteTemplatesFactory.getFields(templateElement, Namespace.NO_NAMESPACE);
        String iconPath = templateElement.getChildText("icon-path");
        if (iconPath != null) {
          myIcon = IconLoader.findIcon(iconPath, classLoader);
        }
      }
      catch (Exception e) {
        throw new RuntimeException(e);
      }
    }
  }

  private static String getTemplateName(URL url) {
    String fileName = new File(url.getPath()).getName();
    return fileName.substring(0, fileName.length() - ArchivedTemplatesFactory.ZIP.length()).replace('_', ' ');
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

  @Override
  public Icon getIcon() {
    return myIcon == null ? super.getIcon() : myIcon;
  }

  @Nullable
  String readEntry(Condition<ZipEntry> condition) {
    ZipInputStream stream = null;
    try {
      stream = getStream();
      ZipEntry entry;
      while ((entry = stream.getNextEntry()) != null) {
        if (condition.value(entry)) {
          return StreamUtil.readText(stream, TemplateModuleBuilder.UTF_8);
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
  private static ModuleType computeModuleType(LocalArchivedTemplate template) {
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

  @Override
  protected ModuleType getModuleType() {
    return myModuleType;
  }

  @Override
  public List<WizardInputField> getInputFields() {
    return myInputFields;
  }

  @Override
  public ZipInputStream getStream() throws IOException {
    return new ZipInputStream(myArchivePath.openStream());
  }

  public URL getArchivePath() {
    return myArchivePath;
  }
}
