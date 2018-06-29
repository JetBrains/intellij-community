/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.execution;

import com.intellij.execution.configurations.*;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.execution.filters.Filter;
import com.intellij.execution.filters.TextConsoleBuilder;
import com.intellij.execution.filters.TextConsoleBuilderFactory;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.runners.ExecutionEnvironmentBuilder;
import com.intellij.execution.util.ExecutionErrorDialog;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.ClassUtil;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

/**
 * @author spleaner
 */
public class JavaExecutionUtil {
  private static final Logger LOG = Logger.getInstance(JavaExecutionUtil.class);

  private JavaExecutionUtil() {
  }

  public static Module findModule(final Module contextModule, final Set<String> patterns, final Project project, Condition<PsiClass> isTestMethod) {
    final Set<Module> modules = new HashSet<>();
    for (String className : patterns) {
      final PsiClass psiClass = findMainClass(project,
                                              className.contains(",") ? className.substring(0, className.indexOf(',')) : className,
                                              GlobalSearchScope.allScope(project));
      if (psiClass != null && isTestMethod.value(psiClass)) {
        modules.add(ModuleUtilCore.findModuleForPsiElement(psiClass));
      }
    }

    if (modules.size() == 1) {
      final Module nextModule = modules.iterator().next();
      if (nextModule != null) {
        return nextModule;
      }
    }
    if (contextModule != null && modules.size() > 1) {
      final HashSet<Module> moduleDependencies = new HashSet<>();
      ModuleUtilCore.getDependencies(contextModule, moduleDependencies);
      if (moduleDependencies.containsAll(modules)) {
        return contextModule;
      }
    }
    return null;
  }

  @Nullable
  public static String getRuntimeQualifiedName(@NotNull final PsiClass aClass) {
    return ClassUtil.getJVMClassName(aClass);
  }

  @Nullable
  public static String getPresentableClassName(@Nullable String rtClassName) {
    return getPresentableClassName(rtClassName, null);
  }

  /**
   * {@link JavaExecutionUtil#getPresentableClassName(java.lang.String)}
   */
  @Deprecated
  @Nullable
  public static String getPresentableClassName(@Nullable String rtClassName, JavaRunConfigurationModule configurationModule) {
    if (StringUtil.isEmpty(rtClassName)) {
      return null;
    }

    int lastDot = rtClassName.lastIndexOf('.');
    return lastDot == -1 || lastDot == rtClassName.length() - 1 ? rtClassName : rtClassName.substring(lastDot + 1);
  }

  public static Module findModule(@NotNull final PsiClass psiClass) {
    return ModuleUtilCore.findModuleForPsiElement(psiClass);
  }

  @Nullable
  public static PsiClass findMainClass(final Module module, final String mainClassName) {
    return findMainClass(module.getProject(), mainClassName, module.getModuleRuntimeScope(true));
  }

  @Nullable
  public static PsiClass findMainClass(final Project project, final String mainClassName, final GlobalSearchScope scope) {
    if (project.isDefault() || DumbService.isDumb(project) && !DumbService.getInstance(project).isAlternativeResolveEnabled()) return null;
    final PsiManager psiManager = PsiManager.getInstance(project);
    final String shortName = StringUtil.getShortName(mainClassName);
    final String packageName = StringUtil.getPackageName(mainClassName);
    final JavaPsiFacade psiFacade = JavaPsiFacade.getInstance(psiManager.getProject());
    final PsiClass psiClass = psiFacade.findClass(StringUtil.getQualifiedName(packageName, shortName.replace('$', '.')), scope);
    return psiClass == null ? psiFacade.findClass(mainClassName, scope) : psiClass;
  }


  public static boolean isNewName(final String name) {
    return name == null || name.startsWith(ExecutionBundle.message("run.configuration.unnamed.name.prefix"));
  }

  public static Location stepIntoSingleClass(@NotNull final Location location) {
    PsiElement element = location.getPsiElement();
    if (!(element instanceof PsiClassOwner)) {
      if (PsiTreeUtil.getParentOfType(element, PsiClass.class) != null) return location;
      element = PsiTreeUtil.getParentOfType(element, PsiClassOwner.class);
      if (element == null) return location;
    }
    final PsiClassOwner psiFile = (PsiClassOwner)element;
    final PsiClass[] classes = psiFile.getClasses();
    if (classes.length != 1) return location;
    if (classes[0].getTextRange() == null) return location;
    return PsiLocation.fromPsiElement(classes[0]);
  }

  public static String getShortClassName(@Nullable String fqName) {
    return fqName == null ? "" : StringUtil.getShortName(fqName);
  }

  public static void showExecutionErrorMessage(final ExecutionException e, final String title, final Project project) {
    ExecutionErrorDialog.show(e, title, project);
  }

  @Nullable
  public static String handleSpacesInAgentPath(@NotNull String agentPath,
                                               @NotNull String copyDirName,
                                               @Nullable String agentPathPropertyKey) {
    return handleSpacesInAgentPath(agentPath, copyDirName, agentPathPropertyKey, null);
  }

  @Nullable
  public static String handleSpacesInAgentPath(@NotNull String agentPath,
                                               @NotNull String copyDirName,
                                               @Nullable String agentPathPropertyKey,
                                               @Nullable FileFilter fileFilter) {
    String agentName = new File(agentPath).getName();
    String containingDir = handleSpacesInContainingDir(agentPath, copyDirName, agentPathPropertyKey, fileFilter);
    return containingDir == null ? null : FileUtil.join(containingDir, agentName);
  }

  @Nullable
  private static String handleSpacesInContainingDir(@NotNull String agentPath,
                                                    @NotNull String copyDirName,
                                                    @Nullable String agentPathPropertyKey,
                                                    @Nullable FileFilter fileFilter) {
    String agentContainingDir;
    String userDefined = agentPathPropertyKey == null ? null : System.getProperty(agentPathPropertyKey);
    if (userDefined != null && new File(userDefined).exists()) {
      agentContainingDir = userDefined;
    } else {
      agentContainingDir = new File(agentPath).getParent();
    }
    if (agentContainingDir.contains(" ")) {
      String res = tryCopy(agentContainingDir, new File(PathManager.getSystemPath(), copyDirName), fileFilter);
      if (res == null) {
        try {
          res = tryCopy(agentContainingDir, FileUtil.createTempDirectory(copyDirName, "jars"), fileFilter);
          if (res == null) {
            String message = "agent not used since the agent path contains spaces: " + agentContainingDir;
            if (agentPathPropertyKey != null) {
              message += "\nOne can move the agent libraries to a directory with no spaces in path and specify its path in idea.properties as " +
              agentPathPropertyKey + "=<path>";
            }
            LOG.info(message);
          }
        }
        catch (IOException e) {
          LOG.info(e);
        }
      }
      return res;
    }
    return agentContainingDir;
  }

  @Nullable
  private static String tryCopy(@NotNull String agentDir,
                                @NotNull File targetDir,
                                @Nullable FileFilter fileFilter) {
    if (targetDir.getAbsolutePath().contains(" ")) return null;
    try {
      LOG.info("Agent jars were copied to " + targetDir.getPath());
      if (fileFilter == null) {
        fileFilter = pathname -> FileUtilRt.extensionEquals(pathname.getPath(), "jar");
      }
      FileUtil.copyDir(new File(agentDir), targetDir, fileFilter);
      return targetDir.getPath();
    }
    catch (IOException e) {
      LOG.info(e);
      return null;
    }
  }
}
