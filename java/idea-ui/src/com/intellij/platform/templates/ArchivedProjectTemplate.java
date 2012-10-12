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

import com.intellij.ide.util.newProjectWizard.modes.ImportImlMode;
import com.intellij.ide.util.projectWizard.ModuleBuilder;
import com.intellij.ide.util.projectWizard.WizardContext;
import com.intellij.openapi.module.ModifiableModuleModel;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.module.ModuleWithNameAlreadyExists;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.io.StreamUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.RefreshQueue;
import com.intellij.platform.ProjectTemplate;
import com.intellij.platform.templates.github.ZipUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jdom.JDOMException;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.io.File;
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
  private final WizardContext myContext;

  public ArchivedProjectTemplate(String displayName,
                                 URL archivePath,
                                 WizardContext context) {

    myDisplayName = displayName;
    myArchivePath = archivePath;
    myContext = context;
  }

  @NotNull
  @Override
  public String getName() {
    return myDisplayName;
  }

  @Override
  public String getDescription() {
    try {
      ZipInputStream stream = getStream();
      ZipEntry entry;
      while ((entry = stream.getNextEntry()) != null) {
        if (entry.getName().endsWith(DESCRIPTION_PATH)) {
          return StreamUtil.readText(stream);
        }
      }
    }
    catch (IOException e) {
      return null;
    }
    return null;
  }

  @NotNull
  @Override
  public ModuleBuilder createModuleBuilder() {
    return new ModuleBuilder() {
      @Override
      public void setupRootModel(ModifiableRootModel modifiableRootModel) throws ConfigurationException {

      }

      @Override
      public ModuleType getModuleType() {
        return null;
      }

      @NotNull
      @Override
      public Module createModule(@NotNull ModifiableModuleModel moduleModel)
        throws InvalidDataException, IOException, ModuleWithNameAlreadyExists, JDOMException, ConfigurationException {
        final String path = getContentEntryPath();
        String iml;
        try {
          File dir = new File(path);
          ZipInputStream zipInputStream = getStream();
          ZipUtil.unzip(ProgressManager.getInstance().getProgressIndicator(), dir, zipInputStream);
          VirtualFile virtualFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(dir);
          iml = ContainerUtil.find(dir.list(), new Condition<String>() {
            @Override
            public boolean value(String s) {
              return s.endsWith(".iml");
            }
          });
          new File(path, iml).renameTo(new File(getModuleFilePath()));
          RefreshQueue.getInstance().refresh(false, true, null, virtualFile);
        }
        catch (IOException e) {
          throw new RuntimeException(e);
        }
        return ImportImlMode.setUpLoader(getModuleFilePath()).createModule(moduleModel);
      }
    };
  }

  private ZipInputStream getStream() throws IOException {
    return new ZipInputStream(myArchivePath.openStream());
  }

  @Override
  public JComponent getSettingsPanel() {
    return null;
  }
}
