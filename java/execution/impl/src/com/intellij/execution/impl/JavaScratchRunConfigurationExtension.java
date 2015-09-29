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

import com.intellij.debugger.DebuggerManager;
import com.intellij.debugger.engine.DebugProcess;
import com.intellij.debugger.engine.DebugProcessAdapter;
import com.intellij.debugger.engine.DebugProcessImpl;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.Location;
import com.intellij.execution.RunConfigurationExtension;
import com.intellij.execution.application.ApplicationConfiguration;
import com.intellij.execution.configurations.*;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.ide.scratch.ScratchFileType;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.compiler.CompilerManager;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileWithId;
import com.intellij.openapi.vfs.newvfs.ManagingFS;
import com.intellij.psi.PsiFile;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;

public class JavaScratchRunConfigurationExtension extends RunConfigurationExtension{

  private static final Key<Integer> SCRATCH_FILE_ID = Key.create("_scratch_file_id_");

  public void cleanUserData(RunConfigurationBase configuration) {
    super.cleanUserData(configuration);
    configuration.putCopyableUserData(SCRATCH_FILE_ID, null);
  }

  protected void extendCreatedConfiguration(@NotNull RunConfigurationBase configuration, @NotNull Location location) {
    final VirtualFile vFile = location.getVirtualFile();
    if (vFile instanceof VirtualFileWithId && vFile.getFileType() == ScratchFileType.INSTANCE) {
      final PsiFile psiFile = location.getPsiElement().getContainingFile();
      if (psiFile != null && psiFile.getLanguage() == JavaLanguage.INSTANCE) {
        configuration.putCopyableUserData(SCRATCH_FILE_ID, ((VirtualFileWithId)vFile).getId());
      }
    }
  }

  protected void validateConfiguration(@NotNull RunConfigurationBase configuration, boolean isExecution) throws Exception {
    super.validateConfiguration(configuration, isExecution);
  }

  public <T extends RunConfigurationBase> void updateJavaParameters(T configuration, JavaParameters params, RunnerSettings runnerSettings) throws ExecutionException {
    if (getScratchFileId(configuration) >= 0) {
      final File scrachesOutput = getScratchOutputDirectory(configuration.getProject());
      if (scrachesOutput != null) {
        params.getClassPath().add(scrachesOutput);
      }
    }
  }

  @Override
  protected void attachToProcess(@NotNull final RunConfigurationBase configuration, @NotNull final ProcessHandler handler, @Nullable RunnerSettings runnerSettings) {
    if (runnerSettings instanceof DebuggingRunnerData && getScratchFileId(configuration) >= 0) {
      final VirtualFile vFile = getScratchVirtualFile(configuration);
      if (vFile != null) {
        DebuggerManager.getInstance(configuration.getProject()).addDebugProcessListener(handler, new DebugProcessAdapter() {
          @Override
          public void processAttached(DebugProcess process) {
            if (vFile.isValid()) {
              process.appendPositionManager(new JavaScratchPositionManager((DebugProcessImpl)process, vFile));
            }
            process.removeDebugProcessListener(this);
          }
        });
      }
    }
  }

  @NotNull
  protected String getSerializationId() {
    return "java-scratch-properties";
  }

  @Nullable
  public static String getScratchFileUrl(RunConfiguration configuration) {
    final VirtualFile vFile = getScratchVirtualFile(configuration);
    return vFile != null? vFile.getUrl() : null;
  }

  @Nullable
  public static VirtualFile getScratchVirtualFile(RunConfiguration configuration) {
    int id = getScratchFileId(configuration);
    if (id < 0) {
      return null;
    }
    return ManagingFS.getInstance().findFileById(id);
  }

  private static int getScratchFileId(RunConfiguration configuration) {
    final Integer id =
      configuration instanceof RunConfigurationBase ? ((RunConfigurationBase)configuration).getCopyableUserData(SCRATCH_FILE_ID) : null;
    return id == null? -1 : id.intValue();
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
      final String idStr = sourceElement.getAttributeValue("fileId");
      if (idStr != null) {
        try {
          runConfiguration.putCopyableUserData(SCRATCH_FILE_ID, Integer.parseInt(idStr));
        }
        catch (NumberFormatException ignored) {
        }
      }
    }
  }

  protected void writeExternal(@NotNull RunConfigurationBase runConfiguration, @NotNull Element element) throws WriteExternalException {
    final int id = getScratchFileId(runConfiguration);
    if (id < 0) {
      super.writeExternal(runConfiguration, element);
    }
    else {
      final Element sourceElement = new Element("source");
      sourceElement.setAttribute("fileId", String.valueOf(id));
      element.addContent(sourceElement);
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
