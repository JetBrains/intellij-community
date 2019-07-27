// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.junit;

import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.execution.configurations.ConfigurationType;
import com.intellij.execution.configurations.ModuleBasedConfiguration;
import com.intellij.execution.impl.RunManagerImpl;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import java.util.HashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

/**
 * @author spleaner
 */
public abstract class JavaRuntimeConfigurationProducerBase extends RuntimeConfigurationProducer {
  protected JavaRuntimeConfigurationProducerBase(@NotNull ConfigurationType configurationType) {
    super(configurationType);
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
                if (packagePrefixes.size() > 1) return null;
                return JavaPsiFacade.getInstance(project).findPackage(packagePrefixes.isEmpty() ? "" : packagePrefixes.iterator().next());
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
