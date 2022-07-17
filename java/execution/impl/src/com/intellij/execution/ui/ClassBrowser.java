// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.ui;

import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.configuration.BrowseModuleValueActionListener;
import com.intellij.execution.configurations.JavaRunConfigurationModule;
import com.intellij.ide.util.ClassFilter;
import com.intellij.ide.util.TreeClassChooser;
import com.intellij.ide.util.TreeClassChooserFactory;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ex.MessagesEx;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiMethodUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public abstract class ClassBrowser<T extends JComponent> extends BrowseModuleValueActionListener<T> {
  private final @NlsContexts.DialogTitle String myTitle;

  public ClassBrowser(@NotNull Project project, @NlsContexts.DialogTitle String title) {
    super(project);
    myTitle = title;
  }

  @Override
  @Nullable
  protected String showDialog() {
    ClassFilter.ClassFilterWithScope classFilter;
    try {
      classFilter = getFilter();
    }
    catch (NoFilterException e) {
      e.getMessageInfo().showNow();
      return null;
    }

    TreeClassChooser dialog = createClassChooser(classFilter);
    configureDialog(dialog);
    dialog.showDialog();
    PsiClass psiClass = dialog.getSelected();
    if (psiClass == null) return null;
    onClassChosen(psiClass);
    return psiClass.getQualifiedName();
  }

  protected abstract ClassFilter.ClassFilterWithScope getFilter() throws NoFilterException;

  protected TreeClassChooser createClassChooser(ClassFilter.ClassFilterWithScope classFilter) {
    TreeClassChooserFactory factory = TreeClassChooserFactory.getInstance(getProject());
    return factory.createWithInnerClassesScopeChooser(myTitle, classFilter.getScope(), classFilter, null);
  }

  protected void onClassChosen(@NotNull PsiClass psiClass) {
  }

  private void configureDialog(TreeClassChooser dialog) {
    PsiClass psiClass = findClass(getText());
    if (psiClass == null) return;
    PsiDirectory directory = psiClass.getContainingFile().getContainingDirectory();
    if (directory != null) dialog.selectDirectory(directory);
    dialog.select(psiClass);
  }

  protected abstract PsiClass findClass(String className);

  /** @deprecated use {@link AppClassBrowser#AppClassBrowser(Project, ConfigurationModuleSelector)} instead. */
  @Deprecated(forRemoval = true)
  @SuppressWarnings("rawtypes")
  public static ClassBrowser createApplicationClassBrowser(@NotNull Project project, @NotNull ConfigurationModuleSelector moduleSelector) {
    return new AppClassBrowser(project, moduleSelector);
  }

  public abstract static class MainClassBrowser<T extends JComponent> extends ClassBrowser<T> {
    private final Computable<? extends Module> myModuleSelector;

    public MainClassBrowser(@NotNull Project project, @NotNull ConfigurationModuleSelector moduleSelector, @NlsContexts.DialogTitle String title) {
      this(project, () -> moduleSelector.getModule(), title);
    }

    public MainClassBrowser(@NotNull Project project, @NotNull Computable<? extends Module> moduleSelector, @NlsContexts.DialogTitle String title) {
      super(project, title);
      myModuleSelector = moduleSelector;
    }

    @Override
    protected PsiClass findClass(String className) {
      final JavaRunConfigurationModule configurationModule = new JavaRunConfigurationModule(getProject(), false);
      configurationModule.setModule(myModuleSelector.get());
      return configurationModule.findClass(className);
    }

    @Override
    protected ClassFilter.ClassFilterWithScope getFilter() {
      Module module = myModuleSelector.get();
      GlobalSearchScope scope =
        module != null ? GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(module) : GlobalSearchScope.allScope(getProject());
      ClassFilter filter = createFilter(module);
      return new ClassFilter.ClassFilterWithScope() {
        @Override
        public GlobalSearchScope getScope() {
          return scope;
        }

        @Override
        public boolean isAccepted(PsiClass aClass) {
          return filter == null || filter.isAccepted(aClass);
        }
      };
    }

    @Nullable
    protected ClassFilter createFilter(@Nullable Module module) {
      return null;
    }
  }

  public static class AppClassBrowser<T extends JComponent> extends MainClassBrowser<T> {
    private static final ClassFilter CLASS_FILTER =
      aClass -> PsiMethodUtil.MAIN_CLASS.value(aClass) && ReadAction.compute(() -> PsiMethodUtil.findMainMethod(aClass)) != null;

    public AppClassBrowser(@NotNull Project project, @NotNull ConfigurationModuleSelector moduleSelector) {
      super(project, moduleSelector, ExecutionBundle.message("choose.main.class.dialog.title"));
    }

    public AppClassBrowser(@NotNull Project project,
                           @NotNull Computable<? extends Module> moduleSelector) {
      super(project, moduleSelector, ExecutionBundle.message("choose.main.class.dialog.title"));
    }

    @Override
    protected ClassFilter createFilter(Module module) {
      return CLASS_FILTER;
    }
  }

  public static class NoFilterException extends Exception {
    private final MessagesEx.MessageInfo myMessageInfo;

    public NoFilterException(@NotNull MessagesEx.MessageInfo messageInfo) {
      super(messageInfo.getMessage());
      myMessageInfo = messageInfo;
    }

    public MessagesEx.MessageInfo getMessageInfo() {
      return myMessageInfo;
    }
  }
}