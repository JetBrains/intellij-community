/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

package com.intellij.util.descriptors.impl;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.JavaUiBundle;
import com.intellij.ide.fileTemplates.FileTemplate;
import com.intellij.ide.fileTemplates.FileTemplateManager;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.*;
import com.intellij.util.containers.DisposableWrapperList;
import com.intellij.util.descriptors.*;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.List;

public final class ConfigFileFactoryImpl extends ConfigFileFactory implements Disposable {
  private static final Logger LOG = Logger.getInstance(ConfigFileFactoryImpl.class);
  private final DisposableWrapperList<ConfigFileContainerImpl> containers = new DisposableWrapperList<>();

  public ConfigFileFactoryImpl() {
    VirtualFileManager.getInstance().addAsyncFileListener(new ConfigFileVfsListener(), this);
  }

  @Override
  public ConfigFileMetaDataProvider createMetaDataProvider(final ConfigFileMetaData... metaData) {
    return new ConfigFileMetaDataRegistryImpl(metaData);
  }

  @Override
  public ConfigFileMetaDataRegistry createMetaDataRegistry() {
    return new ConfigFileMetaDataRegistryImpl();
  }

  @Override
  public ConfigFileInfoSet createConfigFileInfoSet(final ConfigFileMetaDataProvider metaDataProvider) {
    return new ConfigFileInfoSetImpl(metaDataProvider);
  }

  @Override
  public ConfigFileContainer createConfigFileContainer(final Project project, final ConfigFileMetaDataProvider metaDataProvider,
                                                       final ConfigFileInfoSet configuration) {
    var container = new ConfigFileContainerImpl(project, metaDataProvider, configuration);
    containers.add(container, container); // container will remove itself from the list on container disposal
    return container;
  }

  @Override
  public void dispose() {
    containers.clear();
  }

  void handleFileChanges(List<VirtualFile> filesToUpdate) {
    for (VirtualFile file : filesToUpdate) {
      for (ConfigFileContainerImpl container : containers) {
        container.fileChanged(file);
      }
    }
  }

  private static String getText(final String templateName, @Nullable Project project) throws IOException {
    final FileTemplateManager templateManager = project == null ? FileTemplateManager.getDefaultInstance() : FileTemplateManager.getInstance(project);
    final FileTemplate template = templateManager.getJ2eeTemplate(templateName);
    return template.getText(templateManager.getDefaultProperties());
  }

  @Override
  public @Nullable VirtualFile createFile(@Nullable Project project, String url, ConfigFileVersion version, final boolean forceNew) {
    return createFileFromTemplate(project, url, version.getTemplateName(), forceNew);
  }

  private @Nullable VirtualFile createFileFromTemplate(final @Nullable Project project, String url, final String templateName, final boolean forceNew) {
    final LocalFileSystem fileSystem = LocalFileSystem.getInstance();
    final File file = new File(VfsUtilCore.urlToPath(url));
    VirtualFile existingFile = fileSystem.refreshAndFindFileByIoFile(file);
    if (existingFile != null) {
      existingFile.refresh(false, false);
      if (!existingFile.isValid()) {
        existingFile = null;
      }
    }

    if (existingFile != null && !forceNew) {
      return existingFile;
    }
    try {
      String text = getText(templateName, project);
      final VirtualFile childData;
      if (existingFile == null || existingFile.isDirectory()) {
        final VirtualFile virtualFile;
        if (!FileUtil.createParentDirs(file) ||
            (virtualFile = fileSystem.refreshAndFindFileByIoFile(file.getParentFile())) == null) {
          throw new IOException(IdeBundle.message("error.message.unable.to.create.file", file.getPath()));
        }
        childData = virtualFile.createChildData(this, file.getName());
      }
      else {
        childData = existingFile;
      }
      VfsUtil.saveText(childData, text);
      return childData;
    }
    catch (final IOException e) {
      LOG.info(e);
      ApplicationManager.getApplication().invokeLater(
        () -> Messages.showErrorDialog(JavaUiBundle.message("message.text.error.creating.deployment.descriptor", e.getLocalizedMessage()),
                                     JavaUiBundle.message("message.text.creating.deployment.descriptor")));
    }
    return null;
  }

  @Override
  public ConfigFileContainer createSingleFileContainer(Project project, ConfigFileMetaData metaData) {
    final ConfigFileMetaDataProvider metaDataProvider = createMetaDataProvider(metaData);
    return createConfigFileContainer(project, metaDataProvider, createConfigFileInfoSet(metaDataProvider));
  }
}
