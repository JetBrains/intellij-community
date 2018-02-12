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
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.ClassUtil;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.HashSet;
import java.util.Set;

/**
 * @author spleaner
 */
public class JavaExecutionUtil {
  private JavaExecutionUtil() {
  }

  public static boolean executeRun(@NotNull final Project project, String contentName, Icon icon,
                      final DataContext dataContext) throws ExecutionException {
    return executeRun(project, contentName, icon, dataContext, null);
  }

  public static boolean executeRun(@NotNull final Project project, String contentName, Icon icon, DataContext dataContext, Filter[] filters) throws ExecutionException {
    final JavaParameters cmdLine = JavaParameters.JAVA_PARAMETERS.getData(dataContext);
    final DefaultRunProfile profile = new DefaultRunProfile(project, cmdLine, contentName, icon, filters);
    ExecutionEnvironmentBuilder builder = ExecutionEnvironmentBuilder.createOrNull(project, DefaultRunExecutor.getRunExecutorInstance(), profile);
    if (builder != null) {
      builder.buildAndExecute();
      return true;
    }
    return false;
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

  private static final class DefaultRunProfile implements RunProfile {
    private final JavaParameters myParameters;
    private final String myContentName;
    private final Filter[] myFilters;
    private final Project myProject;
    private final Icon myIcon;

    public DefaultRunProfile(final Project project, final JavaParameters parameters, final String contentName, final Icon icon, Filter[] filters) {
      myProject = project;
      myParameters = parameters;
      myContentName = contentName;
      myFilters = filters;
      myIcon = icon;
    }

    @Override
    public Icon getIcon() {
      return myIcon;
    }

    @Override
    public RunProfileState getState(@NotNull final Executor executor, @NotNull final ExecutionEnvironment env) {
      final JavaCommandLineState state = new JavaCommandLineState(env) {
        @Override
        protected JavaParameters createJavaParameters() {
          return myParameters;
        }
      };
      final TextConsoleBuilder builder = TextConsoleBuilderFactory.getInstance().createBuilder(myProject);
      if (myFilters != null) {
        builder.filters(myFilters);
      }
      state.setConsoleBuilder(builder);
      return state;
    }

    @Override
    public String getName() {
      return myContentName;
    }
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
    return lastDot == -1 || lastDot == rtClassName.length() - 1 ? rtClassName : rtClassName.substring(lastDot + 1, rtClassName.length());
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
}
