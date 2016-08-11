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
package com.intellij.execution.junit;

import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.execution.configurations.ConfigurationType;
import com.intellij.execution.configurations.ModuleBasedConfiguration;
import com.intellij.execution.impl.RunManagerImpl;
import com.intellij.execution.testframework.TestSearchScope;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.containers.hash.HashSet;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

/**
 * @author spleaner
 */
public abstract class JavaRuntimeConfigurationProducerBase extends RuntimeConfigurationProducer {

  protected JavaRuntimeConfigurationProducerBase(final ConfigurationType configurationType) {
    super(configurationType);
  }

  protected static PsiMethod getContainingMethod(PsiElement element) {
    while (element != null)
      if (element instanceof PsiMethod) break;
      else element = element.getParent();
    return (PsiMethod) element;
  }

  @Nullable
  public static PsiPackage checkPackage(final PsiElement element) {
    if (element == null || !element.isValid()) return null;
    final Project project = element.getProject();
    final ProjectFileIndex fileIndex = ProjectRootManager.getInstance(project).getFileIndex();
    if (element instanceof PsiPackage) {
      final PsiPackage aPackage = (PsiPackage)element;
      final PsiDirectory[] directories = aPackage.getDirectories(GlobalSearchScope.projectScope(project));
      for (final PsiDirectory directory : directories) {
        if (isSource(directory, fileIndex)) return aPackage;
      }
      return null;
    }
    else if (element instanceof PsiDirectory) {
      final PsiDirectory directory = (PsiDirectory)element;
      if (isSource(directory, fileIndex)) {
        return JavaDirectoryService.getInstance().getPackage(directory);
      }
      else {
        final VirtualFile virtualFile = directory.getVirtualFile();
        //choose default package when selection on content root
        if (virtualFile.equals(fileIndex.getContentRootForFile(virtualFile))) {
          final Module module = ModuleUtilCore.findModuleForFile(virtualFile, project);
          if (module != null) {
            for (ContentEntry entry : ModuleRootManager.getInstance(module).getContentEntries()) {
              if (virtualFile.equals(entry.getFile())) {
                final SourceFolder[] folders = entry.getSourceFolders();
                Set<String> packagePrefixes = new HashSet<>();
                for (SourceFolder folder : folders) {
                  packagePrefixes.add(folder.getPackagePrefix());
                }
                if (packagePrefixes.size() != 1) return null;
                return JavaPsiFacade.getInstance(project).findPackage(packagePrefixes.iterator().next());
              }
            }
          }
        }
        return null;
      }
    }
    else {
      return null;
    }
  }

  private static boolean isSource(final PsiDirectory directory, final ProjectFileIndex fileIndex) {
    final VirtualFile virtualFile = directory.getVirtualFile();
    return fileIndex.getSourceRootForFile(virtualFile) != null;
  }

  protected TestSearchScope setupPackageConfiguration(ConfigurationContext context, Project project, ModuleBasedConfiguration configuration, TestSearchScope scope) {
    if (scope != TestSearchScope.WHOLE_PROJECT) {
      if (!setupConfigurationModule(context, configuration)) {
        return TestSearchScope.WHOLE_PROJECT;
      }
    }
    return scope;
  }

  protected boolean setupConfigurationModule(@Nullable ConfigurationContext context, ModuleBasedConfiguration configuration) {
    if (context != null) {
      final RunnerAndConfigurationSettings template =
        ((RunManagerImpl)context.getRunManager()).getConfigurationTemplate(getConfigurationFactory());
      final Module contextModule = context.getModule();
      final Module predefinedModule = ((ModuleBasedConfiguration)template.getConfiguration()).getConfigurationModule().getModule();
      if (predefinedModule != null) {
        configuration.setModule(predefinedModule);
        return true;
      }
      final Module module = findModule(configuration, contextModule);
      if (module != null) {
        configuration.setModule(module);
        return true;
      }
    }
    return false;
  }
  
  protected Module findModule(ModuleBasedConfiguration configuration, Module contextModule) {
    if (configuration.getConfigurationModule().getModule() == null && contextModule != null) {
      return contextModule;
    }
    return null;
  }
}
