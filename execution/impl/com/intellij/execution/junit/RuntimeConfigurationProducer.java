package com.intellij.execution.junit;

import com.intellij.execution.Location;
import com.intellij.execution.RunManagerEx;
import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.configurations.ConfigurationType;
import com.intellij.execution.impl.RunnerAndConfigurationSettingsImpl;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;

import java.util.Comparator;

public abstract class RuntimeConfigurationProducer implements Comparable {
  public static final Comparator<RuntimeConfigurationProducer> COMPARATOR = new ProducerComparator();
  protected static final int PREFERED = -1;
  private final ConfigurationFactory myConfigurationFactory;
  private RunnerAndConfigurationSettingsImpl myConfiguration;

  public RuntimeConfigurationProducer(final ConfigurationType configurationType) {
    myConfigurationFactory = configurationType.getConfigurationFactories()[0];
  }

  public RuntimeConfigurationProducer createProducer(final Location location, final ConfigurationContext context) {
    final RuntimeConfigurationProducer result = clone();
    result.myConfiguration = location != null ? result.createConfigurationByElement(location, context) : null;
    return result;
  }

  public abstract PsiElement getSourceElement();

  public RunnerAndConfigurationSettingsImpl getConfiguration() {
    return myConfiguration;
  }

  protected abstract RunnerAndConfigurationSettingsImpl createConfigurationByElement(Location location, ConfigurationContext context);

  public RuntimeConfigurationProducer clone() {
    try {
      return (RuntimeConfigurationProducer)super.clone();
    }
    catch (CloneNotSupportedException e) {
      throw new RuntimeException(e);
    }
  }

  public RunnerAndConfigurationSettingsImpl cloneTemplateConfiguration(final Project project, final ConfigurationContext context) {
    if (context != null) {
      final RunnerAndConfigurationSettingsImpl original = context.getOriginalConfiguration(myConfigurationFactory.getType());
      if (original != null) return original.clone();
    }
    return RunManagerEx.getInstanceEx(project).createConfiguration("", myConfigurationFactory);
  }

  public static PsiMethod getContainingMethod(PsiElement element) {
    while (element != null)
      if (element instanceof PsiMethod) break;
      else element = element.getParent();
    return (PsiMethod) element;
  }

  public ConfigurationFactory getConfigurationFactory() {
    return myConfigurationFactory;
  }

  private static class ProducerComparator implements Comparator<RuntimeConfigurationProducer> {
    public int compare(final RuntimeConfigurationProducer producer1, final RuntimeConfigurationProducer producer2) {
      final PsiElement psiElement1 = producer1.getSourceElement();
      final PsiElement psiElement2 = producer2.getSourceElement();
      if (doesContains(psiElement1, psiElement2)) return -PREFERED;
      if (doesContains(psiElement2, psiElement1)) return PREFERED;
      return producer1.compareTo(producer2);
    }

    private static boolean doesContains(final PsiElement container, PsiElement element) {
      while ((element = element.getParent()) != null)
        if (container.equals(element)) return true;
      return false;
    }
  }
}
