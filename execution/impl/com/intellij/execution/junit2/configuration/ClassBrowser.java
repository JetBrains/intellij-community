package com.intellij.execution.junit2.configuration;

import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.ExecutionUtil;
import com.intellij.execution.configurations.ConfigurationUtil;
import com.intellij.ide.util.TreeClassChooser;
import com.intellij.ide.util.TreeClassChooserDialog;
import com.intellij.ide.util.TreeClassChooserFactory;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ex.MessagesEx;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiMethodUtil;

public abstract class ClassBrowser extends BrowseModuleValueActionListener {
  private final String myTitle;

  public ClassBrowser(final Project project, final String title) {
    super(project);
    myTitle = title;
  }

  protected String showDialog() {
    final TreeClassChooser.ClassFilterWithScope classFilter;
    try {
      classFilter = getFilter();
    }
    catch (NoFilterException e) {
      final MessagesEx.MessageInfo info = e.getMessageInfo();
      info.showNow();
      return null;
    }
    final TreeClassChooser dialog = TreeClassChooserFactory.getInstance(getProject()).createWithInnerClassesScopeChooser(myTitle, classFilter.getScope(), classFilter, null);
    configureDialog(dialog);
    dialog.showDialog();
    final PsiClass psiClass = dialog.getSelectedClass();
    if (psiClass == null) return null;
    onClassChoosen(psiClass);
    return ExecutionUtil.getRuntimeQualifiedName(psiClass);
  }

  protected abstract TreeClassChooser.ClassFilterWithScope getFilter() throws NoFilterException;

  protected void onClassChoosen(final PsiClass psiClass) { }

  private void configureDialog(final TreeClassChooser dialog) {
    final String className = getText();
    final PsiClass psiClass = findClass(className);
    if (psiClass == null) return;
    final PsiDirectory directory = psiClass.getContainingFile().getContainingDirectory();
    if (directory != null) dialog.selectDirectory(directory);
    dialog.selectClass(psiClass);
  }

  protected abstract PsiClass findClass(String className);

  public static ClassBrowser createApplicationClassBrowser(final Project project,
                                                           final ConfigurationModuleSelector moduleSelector) {
    final TreeClassChooser.ClassFilter applicationClass = new TreeClassChooser.ClassFilter() {
      public boolean isAccepted(final PsiClass aClass) {
        return ConfigurationUtil.MAIN_CLASS.value(aClass) && PsiMethodUtil.findMainMethod(aClass) != null;
      }
    };
    return new MainClassBrowser(project, moduleSelector, ExecutionBundle.message("choose.main.class.dialog.title")){
      protected TreeClassChooser.ClassFilter createFilter(final Module module) {
        return applicationClass;
      }
    };
  }

  public static ClassBrowser createAppletClassBrowser(final Project project,
                                                      final ConfigurationModuleSelector moduleSelector) {
    return new MainClassBrowser(project, moduleSelector, ExecutionBundle.message("choose.applet.class.dialog.title")) {
      protected TreeClassChooser.ClassFilter createFilter(final Module module) {
        final GlobalSearchScope scope = module != null ?
                                  GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(module) :
                                  GlobalSearchScope.allScope(myProject);
        final PsiClass appletClass = PsiManager.getInstance(project).findClass("java.applet.Applet", scope);
        return new TreeClassChooserDialog.InheritanceClassFilterImpl(appletClass, false, false,
                                                                     ConfigurationUtil.PUBLIC_INSTANTIATABLE_CLASS);
      }
    };
  }

  private static abstract class MainClassBrowser extends ClassBrowser {
    protected final Project myProject;
    private final ConfigurationModuleSelector myModuleSelector;

    public MainClassBrowser(final Project project,
                            final ConfigurationModuleSelector moduleSelector,
                            final String title) {
      super(project, title);
      myProject = project;
      myModuleSelector = moduleSelector;
    }

    protected PsiClass findClass(final String className) {
      return myModuleSelector.findClass(className);
    }

    protected TreeClassChooser.ClassFilterWithScope getFilter() throws NoFilterException {
      final Module module = myModuleSelector.getModule();
      final GlobalSearchScope scope;
      if (module == null) scope = GlobalSearchScope.projectScope(myProject);
      else scope = GlobalSearchScope.moduleWithDependenciesScope(module);
      final TreeClassChooser.ClassFilter filter = createFilter(module);
      return new TreeClassChooser.ClassFilterWithScope() {
        public GlobalSearchScope getScope() {
          return scope;
        }

        public boolean isAccepted(final PsiClass aClass) {
          return filter == null || filter.isAccepted(aClass);
        }
      };
    }

    protected TreeClassChooser.ClassFilter createFilter(final Module module) { return null; }
  }

  public static class NoFilterException extends Exception {
    private MessagesEx.MessageInfo myMessageInfo;

    public NoFilterException(final MessagesEx.MessageInfo messageInfo) {
      super(messageInfo.getMessage());
      myMessageInfo = messageInfo;
    }

    public MessagesEx.MessageInfo getMessageInfo() {
      return myMessageInfo;
    }

    public static NoFilterException noJUnitInModule(final Module module) {
      return new NoFilterException(new MessagesEx.MessageInfo(
        module.getProject(),
        ExecutionBundle.message("junit.not.found.in.module.error.message", module.getName()),
        ExecutionBundle.message("cannot.browse.test.inheritors.dialog.title")));
    }

    public static NoFilterException moduleDoesntExist(final ConfigurationModuleSelector moduleSelector) {
      final Project project = moduleSelector.getProject();
      return new NoFilterException(new MessagesEx.MessageInfo(
        project,
        ExecutionBundle.message("module.does.not.exists", moduleSelector.getModuleName(), project.getName()),
        ExecutionBundle.message("cannot.browse.test.inheritors.dialog.title")));
    }
  }
}
