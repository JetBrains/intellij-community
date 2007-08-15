package com.intellij.execution.application;

import com.intellij.execution.ExecutionUtil;
import com.intellij.execution.Location;
import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.execution.configurations.ConfigurationUtil;
import com.intellij.execution.impl.RunnerAndConfigurationSettingsImpl;
import com.intellij.execution.junit.RuntimeConfigurationProducer;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.util.PsiMethodUtil;

public class ApplicationConfigurationProducer extends RuntimeConfigurationProducer implements Cloneable {
  private PsiElement myPsiElement = null;
  public static final RuntimeConfigurationProducer PROTOTYPE = new ApplicationConfigurationProducer();

  public ApplicationConfigurationProducer() {
    super(ApplicationConfigurationType.getInstance());
  }

  public PsiElement getSourceElement() {
    return myPsiElement;
  }

  protected RunnerAndConfigurationSettingsImpl createConfigurationByElement(Location location, final ConfigurationContext context) {
    location = ExecutionUtil.stepIntoSingleClass(location);
    final PsiElement element = location.getPsiElement();

    PsiElement currentElement = element;
    PsiMethod method;
    while ((method = findMain(currentElement)) != null) {
      final PsiClass aClass = method.getContainingClass();
      if (ConfigurationUtil.MAIN_CLASS.value(aClass)) {
        myPsiElement = method;
        return createConfiguration(aClass, context);
      }
      currentElement = method.getParent();
    }
    final PsiClass aClass = ApplicationConfigurationType.getMainClass(element);
    if (aClass == null) return null;
    myPsiElement = aClass;
    return createConfiguration(aClass, context);
  }

  private RunnerAndConfigurationSettingsImpl createConfiguration(final PsiClass aClass, final ConfigurationContext context) {
    final Project project = aClass.getProject();
    RunnerAndConfigurationSettingsImpl settings = cloneTemplateConfiguration(project, context);
    final ApplicationConfiguration configuration = (ApplicationConfiguration)settings.getConfiguration();
    configuration.MAIN_CLASS_NAME = ExecutionUtil.getRuntimeQualifiedName(aClass);
    configuration.setName(configuration.getGeneratedName());
    configuration.setModule(ExecutionUtil.findModule(aClass));
    return settings;
  }

  private static PsiMethod findMain(PsiElement element) {
    PsiMethod method;
    while ((method = getContainingMethod(element)) != null)
      if (PsiMethodUtil.isMainMethod(method)) return method;
      else element = method.getParent();
    return null;
  }

  public int compareTo(final Object o) {
    return PREFERED;
  }
}
