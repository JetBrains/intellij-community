// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution;

import com.intellij.execution.util.ExecutionErrorDialog;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.ClassUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.indexing.FileBasedIndex;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

public final class JavaExecutionUtil {
  private static final Logger LOG = Logger.getInstance(JavaExecutionUtil.class);

  private JavaExecutionUtil() {
  }

  public static Module findModule(final Module contextModule, final Set<String> patterns, final Project project, Condition<? super PsiClass> isTestMethod) {
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
      return null;
    }
    return contextModule;
  }

  /**
   * Returns the runtime-qualified (aka "binary") name of the specified class.
   * <p>
   * Below is a case where the <i>fully qualified name</i> of the class {@code NestedMain} differs from its <i>binary name</i>:
   *
   * <pre>{@code
   * package simple;
   *
   * public class Main {
   *   public static class NestedMain {
   *     public static void main(String[] args) {
   *       System.out.println("hello world");
   *     }
   *   }
   * }}
   *
   * Fully qualified name is {@code simple.Main.NestedMain}, but the binary name is {@code simple.Main$NestedMain}.
   * <p>
   * See also:
   * <ul>
   *   <li>JLS 6.7. Fully Qualified Names and Canonical Names</li>
   *   <li>JLS 13.1. The Form of a Binary</li>
   * </ul>
   */
  public static @Nullable String getRuntimeQualifiedName(final @NotNull PsiClass aClass) {
    return ClassUtil.getJVMClassName(aClass);
  }

  public static @Nullable @NlsSafe String getPresentableClassName(@Nullable String rtClassName) {
    if (StringUtil.isEmpty(rtClassName)) {
      return null;
    }

    int lastDot = rtClassName.lastIndexOf('.');
    return lastDot == -1 || lastDot == rtClassName.length() - 1 ? rtClassName : rtClassName.substring(lastDot + 1);
  }

  public static Module findModule(final @NotNull PsiClass psiClass) {
    return ModuleUtilCore.findModuleForPsiElement(psiClass.getContainingFile());
  }

  public static @Nullable PsiClass findMainClass(final Module module, final String mainClassName) {
    return findMainClass(module.getProject(), mainClassName, module.getModuleRuntimeScope(true));
  }

  public static @Nullable PsiClass findMainClass(final Project project, final String mainClassName, final GlobalSearchScope scope) {
    if (project.isDefault() ||
        (DumbService.isDumb(project) &&
         FileBasedIndex.getInstance().getCurrentDumbModeAccessType(project) == null &&
         !DumbService.getInstance(project).isAlternativeResolveEnabled())) {
      return null;
    }
    PsiManager psiManager = PsiManager.getInstance(project);
    String shortName = StringUtil.getShortName(mainClassName);
    String packageName = StringUtil.getPackageName(mainClassName);
    JavaPsiFacade psiFacade = JavaPsiFacade.getInstance(psiManager.getProject());
    try {
      PsiClass psiClass = psiFacade.findClass(StringUtil.getQualifiedName(packageName, shortName.replace('$', '.')), scope);
      return psiClass == null ? psiFacade.findClass(mainClassName, scope) : psiClass;
    }
    catch (IndexNotReadyException ex) {
      return null;
    }
  }


  public static boolean isNewName(final String name) {
    return name == null || name.startsWith(ExecutionBundle.message("run.configuration.unnamed.name.prefix"));
  }

  public static Location stepIntoSingleClass(final @NotNull Location location) {
    PsiElement element = location.getPsiElement();
    TextRange elementTextRange = element.getTextRange();
    if (!(element instanceof PsiClassOwner)) {
      if (PsiTreeUtil.getParentOfType(element, PsiClass.class) != null) return location;
      element = PsiTreeUtil.getParentOfType(element, PsiClassOwner.class);
      if (element == null) return location;
    }
    final PsiClassOwner psiFile = (PsiClassOwner)element;
    final PsiClass[] classes = psiFile.getClasses();
    if (classes.length != 1) return location;
    TextRange textRange = classes[0].getTextRange();
    if (textRange == null) return location;
    if (elementTextRange != null && textRange.contains(elementTextRange)) return location;
    return PsiLocation.fromPsiElement(classes[0]);
  }

  public static String getShortClassName(@Nullable String fqName) {
    return fqName == null ? "" : StringUtil.getShortName(fqName);
  }

  @SuppressWarnings("MissingDeprecatedAnnotation")
  @Deprecated(forRemoval = true)
  public static void showExecutionErrorMessage(ExecutionException e, @NlsContexts.DialogTitle String title, Project project) {
    ExecutionErrorDialog.show(e, title, project);
  }

  public static @Nullable String handleSpacesInAgentPath(@NotNull String agentPath,
                                                         @NotNull String copyDirName,
                                                         @Nullable String agentPathPropertyKey) {
    return handleSpacesInAgentPath(agentPath, copyDirName, agentPathPropertyKey, null);
  }

  public static @Nullable String handleSpacesInAgentPath(@NotNull String agentPath,
                                                         @NotNull String copyDirName,
                                                         @Nullable String agentPathPropertyKey,
                                                         @Nullable FileFilter fileFilter) {
    String agentName = new File(agentPath).getName();
    String containingDir = handleSpacesInContainingDir(agentPath, agentName, copyDirName, agentPathPropertyKey, fileFilter);
    return containingDir == null ? null : FileUtil.join(containingDir, agentName);
  }

  private static @Nullable String handleSpacesInContainingDir(@NotNull String agentPath,
                                                              @NotNull String agentName,
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
      String res = tryCopy(agentContainingDir, agentName, new File(PathManager.getSystemPath(), copyDirName), fileFilter);
      if (res == null) {
        try {
          res = tryCopy(agentContainingDir, agentName, FileUtil.createTempDirectory(copyDirName, "jars"), fileFilter);
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

  private static @Nullable String tryCopy(@NotNull String agentDir,
                                          @NotNull String agentName,
                                          @NotNull File targetDir,
                                          @Nullable FileFilter fileFilter) {
    if (targetDir.getAbsolutePath().contains(" ")) return null;
    try {
      if (fileFilter == null) {
        FileUtil.copy(new File(agentDir, agentName), new File(targetDir, agentName));
      }
      else {
        FileUtil.copyDir(new File(agentDir), targetDir, fileFilter);
      }
      LOG.info("Agent jars were copied to " + targetDir.getPath());
      return targetDir.getPath();
    }
    catch (IOException e) {
      LOG.info(e);
      return null;
    }
  }
}
