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
package com.intellij.execution.impl;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.Location;
import com.intellij.execution.RunConfigurationExtension;
import com.intellij.execution.application.ApplicationConfiguration;
import com.intellij.execution.configurations.JavaParameters;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.configurations.RunConfigurationBase;
import com.intellij.execution.configurations.RunnerSettings;
import com.intellij.ide.scratch.ScratchFileType;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.compiler.CompilerManager;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;


/*
execution-impl -> lang-impl(scratches are here)
compiler-impl  -> lang-impl

java-impl ->execution-impl, compiler-impl
 */
public class JavaScratchRunConfigurationExtension extends RunConfigurationExtension{

  public static final Key<String> SCRATCH_FILE_URL = Key.create("_scratch_file_path_");

  public void cleanUserData(RunConfigurationBase configuration) {
    super.cleanUserData(configuration);
    configuration.putCopyableUserData(SCRATCH_FILE_URL, null);
  }

  protected void extendCreatedConfiguration(@NotNull RunConfigurationBase configuration, @NotNull Location location) {
    final VirtualFile vFile = location.getVirtualFile();
    if (vFile != null && vFile.getFileType() == ScratchFileType.INSTANCE) {
      final PsiFile psiFile = location.getPsiElement().getContainingFile();
      if (psiFile != null && psiFile.getLanguage() == JavaLanguage.INSTANCE) {
        configuration.putCopyableUserData(SCRATCH_FILE_URL, vFile.getUrl());
      }
    }
  }

  protected void validateConfiguration(@NotNull RunConfigurationBase configuration, boolean isExecution) throws Exception {
    super.validateConfiguration(configuration, isExecution);
  }

  public <T extends RunConfigurationBase> void updateJavaParameters(T configuration, JavaParameters params, RunnerSettings runnerSettings) throws ExecutionException {
    final File scrachesOutput = getScratchOutputDirectory(configuration.getProject());
    if (scrachesOutput != null) {
      params.getClassPath().add(scrachesOutput);
    }
  }

  @NotNull
  protected String getSerializationId() {
    return "java-scratch-properties";
  }

  @Nullable
  public static String getScratchFileUrl(RunConfiguration configuration) {
    return configuration instanceof RunConfigurationBase? ((RunConfigurationBase)configuration).getCopyableUserData(SCRATCH_FILE_URL) : null;
  }

  @Nullable
  public static File getScratchOutputDirectory(Project project) {
    final File root = CompilerManager.getInstance(project).getJavacCompilerWorkingDir();
    return root != null? new File(root, "scratches/out") : null;
  }

  @Nullable
  public static File getScratchTempDirectory(Project project) {
    final File root = CompilerManager.getInstance(project).getJavacCompilerWorkingDir();
    return root != null? new File(root, "scratches/src") : null;
  }


  protected void readExternal(@NotNull RunConfigurationBase runConfiguration, @NotNull Element element) throws InvalidDataException {
    final Element sourceElement = element.getChild("source");
    if (sourceElement != null) {
      final String scratchUrl = sourceElement.getAttributeValue("url");
      if (scratchUrl != null) {
        runConfiguration.putCopyableUserData(SCRATCH_FILE_URL, scratchUrl);
      }
    }
  }

  protected void writeExternal(@NotNull RunConfigurationBase runConfiguration, @NotNull Element element) throws WriteExternalException {
    final String url = getScratchFileUrl(runConfiguration);
    if (url == null) {
      super.writeExternal(runConfiguration, element);
    }
    else {
      final Element urlElement = new Element("source");
      urlElement.setAttribute("url", url);
      element.addContent(urlElement);
    }
  }

  @Nullable
  protected <P extends RunConfigurationBase> SettingsEditor<P> createEditor(@NotNull P configuration) {
    return null;
  }

  @Nullable
  protected String getEditorTitle() {
    return null;
  }

  protected boolean isApplicableFor(@NotNull RunConfigurationBase configuration) {
    return configuration instanceof ApplicationConfiguration;
  }
}
