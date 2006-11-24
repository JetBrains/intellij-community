/*
 * Copyright 2000-2005 JetBrains s.r.o.
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
package com.intellij.openapi.compiler.make;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.compiler.CompileContext;
import com.intellij.openapi.compiler.CompilerMessageCategory;
import com.intellij.openapi.compiler.CompilerBundle;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.deployment.DeploymentItem;
import com.intellij.openapi.deployment.VerificationException;
import com.intellij.openapi.deployment.DeploymentUtil;
import com.intellij.javaee.JavaeeModuleProperties;
import com.intellij.javaee.make.MakeUtil;
import org.jetbrains.annotations.NotNull;

import java.io.File;

public abstract class BuildParticipantBase implements BuildParticipant {
  @NotNull protected final Module myModule;

  protected BuildParticipantBase(@NotNull Module module) {
    myModule = module;
  }

  @NotNull
  public Module getModule() {
    return myModule;
  }

  public void registerBuildInstructions(final BuildRecipe instructions, final CompileContext context) {
    final DeploymentItem[] deploymentDescriptors = getDeploymentDescriptors();
    ApplicationManager.getApplication().runReadAction(new Runnable() {
      public void run() {
        for (DeploymentItem deploymentDescriptor : deploymentDescriptors) {
          if (deploymentDescriptor.isDescriptorOptional()) {
            continue;
          }

          try {
            deploymentDescriptor.checkIsValid();
            VirtualFile virtualFile = deploymentDescriptor.getVirtualFile();
            // still can be null for optional DDs
            if (virtualFile != null) {
              final File file = VfsUtil.virtualToIoFile(virtualFile);
              instructions.addFileCopyInstruction(file,
                                                  false,
                                                  myModule,
                                                  deploymentDescriptor.getOutputRelativePath(),
                                                  null);
            }

          }
          catch (VerificationException e) {
            String message = e.getMessage();
            if (message == null || message.length() == 0) {
              message = "";
            }
            final String moduleDescription = myModule.getModuleType().getName() + " '" + myModule.getName() + '\'';
            if (!deploymentDescriptor.isDescriptorOptional() && new File(VfsUtil.urlToPath(deploymentDescriptor.getUrl())).exists()) {
              context.addMessage(CompilerMessageCategory.ERROR,
                                 CompilerBundle.message("message.text.compiling.module.message", moduleDescription, message), deploymentDescriptor.getUrl(), -1, -1);
            }
            else {
              DeploymentUtil.getInstance().reportDeploymentDescriptorDoesNotExists(deploymentDescriptor, context, myModule);
            }
          }
        }
      }
    });
  }

  protected DeploymentItem[] getDeploymentDescriptors() {
    return JavaeeModuleProperties.getInstance(myModule).getDeploymentItems();
  }
}
