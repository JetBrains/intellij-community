/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.openapi.deployment;

import com.intellij.openapi.compiler.CompileContext;
import com.intellij.openapi.compiler.CompilerBundle;
import com.intellij.openapi.compiler.CompilerMessageCategory;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.psi.PsiFile;
import com.intellij.psi.xml.XmlDocument;
import com.intellij.psi.xml.XmlFile;
import com.intellij.util.descriptors.ConfigFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.util.Set;

/**
 * @author Alexey Kudravtsev
 */
public class DeploymentUtilImpl extends DeploymentUtil {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.deployment.MakeUtilImpl");

  public void copyFile(@NotNull final File fromFile,
                       @NotNull final File toFile,
                       @NotNull CompileContext context,
                       @Nullable Set<String> writtenPaths,
                       @Nullable FileFilter fileFilter) throws IOException {
    if (fileFilter != null && !fileFilter.accept(fromFile)) {
      if (LOG.isDebugEnabled()) {
        LOG.debug("Skipping " + fromFile.getAbsolutePath() + ": it wasn't accepted by filter " + fileFilter);
      }
      return;
    }
    checkPathDoNotNavigatesUpFromFile(fromFile);
    checkPathDoNotNavigatesUpFromFile(toFile);
    if (fromFile.isDirectory()) {
      final File[] fromFiles = fromFile.listFiles();
      toFile.mkdirs();
      for (File file : fromFiles) {
        copyFile(file, new File(toFile, file.getName()), context, writtenPaths, fileFilter);
      }
      return;
    }
    if (toFile.isDirectory()) {
      context.addMessage(CompilerMessageCategory.ERROR,
                         CompilerBundle.message("message.text.destination.is.directory", createCopyErrorMessage(fromFile, toFile)), null, -1, -1);
      return;
    }
    if (FileUtil.filesEqual(fromFile, toFile) || writtenPaths != null && !writtenPaths.add(toFile.getPath())) {
      if (LOG.isDebugEnabled()) {
        LOG.debug("Skipping " + fromFile.getAbsolutePath() + ": " + toFile.getAbsolutePath() + " is already written");
      }
      return;
    }
    if (!FileUtil.isFilePathAcceptable(toFile, fileFilter)) {
      if (LOG.isDebugEnabled()) {
        LOG.debug("Skipping " + fromFile.getAbsolutePath() + ": " + toFile.getAbsolutePath() + " wasn't accepted by filter " + fileFilter);
      }
      return;
    }
    context.getProgressIndicator().setText("Copying files");
    context.getProgressIndicator().setText2(fromFile.getPath());
    try {
      if (LOG.isDebugEnabled()) {
        LOG.debug("Copy file '" + fromFile + "' to '"+toFile+"'");
      }
      if (toFile.exists() && !SystemInfo.isFileSystemCaseSensitive) {
        File canonicalFile = toFile.getCanonicalFile();
        if (!canonicalFile.getAbsolutePath().equals(toFile.getAbsolutePath())) {
          FileUtil.delete(toFile);
        }
      }
      FileUtil.copy(fromFile, toFile);
    }
    catch (IOException e) {
      context.addMessage(CompilerMessageCategory.ERROR, createCopyErrorMessage(fromFile, toFile) + ": "+e.getLocalizedMessage(), null, -1, -1);
    }
  }

  // OS X is sensitive for that
  private static void checkPathDoNotNavigatesUpFromFile(File file) {
    String path = file.getPath();
    int i = path.indexOf("..");
    if (i != -1) {
      String filepath = path.substring(0,i-1);
      File filepart = new File(filepath);
      if (filepart.exists() && !filepart.isDirectory()) {
        LOG.error("Incorrect file path: '" + path + '\'');
      }
    }
  }

  private static String createCopyErrorMessage(final File fromFile, final File toFile) {
    return CompilerBundle.message("message.text.error.copying.file.to.file", FileUtil.toSystemDependentName(fromFile.getPath()),
                              FileUtil.toSystemDependentName(toFile.getPath()));
  }

  public void reportDeploymentDescriptorDoesNotExists(ConfigFile descriptor, CompileContext context, Module module) {
    final String description = ModuleType.get(module).getName() + " '" + module.getName() + '\'';
    String descriptorPath = VfsUtil.urlToPath(descriptor.getUrl());
    final String message =
      CompilerBundle.message("message.text.compiling.item.deployment.descriptor.could.not.be.found", description, descriptorPath);
    context.addMessage(CompilerMessageCategory.ERROR, message, null, -1, -1);
  }

  public void checkConfigFile(final ConfigFile descriptor, final CompileContext compileContext, final Module module) {
    if (new File(VfsUtil.urlToPath(descriptor.getUrl())).exists()) {
      String message = getConfigFileErrorMessage(descriptor);
      if (message != null) {
        final String moduleDescription = ModuleType.get(module).getName() + " '" + module.getName() + '\'';
        compileContext.addMessage(CompilerMessageCategory.ERROR,
                                CompilerBundle.message("message.text.compiling.module.message", moduleDescription, message),
                                  descriptor.getUrl(), -1, -1);
      }
    }
    else {
      DeploymentUtil.getInstance().reportDeploymentDescriptorDoesNotExists(descriptor, compileContext, module);
    }
  }

  @Nullable
  public String getConfigFileErrorMessage(final ConfigFile configFile) {
    if (configFile.getVirtualFile() == null) {
      String path = FileUtil.toSystemDependentName(VfsUtil.urlToPath(configFile.getUrl()));
      return CompilerBundle.message("mesage.text.deployment.descriptor.file.not.exist", path);
    }
    PsiFile psiFile = configFile.getPsiFile();
    if (psiFile == null || !psiFile.isValid()) {
      return CompilerBundle.message("message.text.deployment.description.invalid.file");
    }

    if (psiFile instanceof XmlFile) {
      XmlDocument document = ((XmlFile)psiFile).getDocument();
      if (document == null || document.getRootTag() == null) {
        return CompilerBundle.message("message.text.xml.file.invalid", FileUtil.toSystemDependentName(VfsUtil.urlToPath(configFile.getUrl())));
      }
    }
    return null;
  }

}
