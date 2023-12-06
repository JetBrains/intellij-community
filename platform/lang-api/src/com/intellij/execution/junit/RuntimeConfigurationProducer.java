// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.execution.junit;

import com.intellij.execution.*;
import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.execution.configurations.*;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.psi.PsiElement;
import com.intellij.psi.SmartPointerManager;
import com.intellij.psi.SmartPsiElementPointer;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;
import java.util.List;

/**
 * @deprecated please use {@link com.intellij.execution.actions.RunConfigurationProducer} instead
 */
@Deprecated
public abstract class RuntimeConfigurationProducer implements Comparable, Cloneable {
  public static final ExtensionPointName<RuntimeConfigurationProducer> RUNTIME_CONFIGURATION_PRODUCER = ExtensionPointName.create("com.intellij.configurationProducer");

  public static final Comparator<RuntimeConfigurationProducer> COMPARATOR = new ProducerComparator();
  protected static final int PREFERED = -1;
  private final ConfigurationFactory myConfigurationFactory;
  private RunnerAndConfigurationSettings myConfiguration;
  protected boolean isClone;
  private SmartPsiElementPointer<PsiElement> myPointer;

  public RuntimeConfigurationProducer(@NotNull ConfigurationType configurationType) {
    this(configurationType.getConfigurationFactories()[0]);
  }

  protected RuntimeConfigurationProducer(ConfigurationFactory configurationFactory) {
    myConfigurationFactory = configurationFactory;
  }

  public RuntimeConfigurationProducer createProducer(final Location location, final ConfigurationContext context) {
    final RuntimeConfigurationProducer result = clone();
    result.myConfiguration = location != null ? result.createConfigurationByElement(location, context) : null;

    if (result.myConfiguration != null) {
      final PsiElement psiElement = result.getSourceElement();
      final Location<PsiElement> _location = PsiLocation.fromPsiElement(psiElement, location.getModule());
      if (_location != null) {
        // replace with existing configuration if any
        final RunManager runManager = RunManager.getInstance(context.getProject());
        final ConfigurationType type = result.myConfiguration.getType();
        RunnerAndConfigurationSettings configuration = result.findExistingByElement(_location, runManager.getConfigurationSettingsList(type), context);
        if (configuration != null) {
          result.myConfiguration = configuration;
        }
        else {
          runManager.setUniqueNameIfNeeded(result.myConfiguration);
        }
      }
    }

    return result;
  }

  public @Nullable RunnerAndConfigurationSettings findExistingConfiguration(@NotNull Location location, ConfigurationContext context) {
    assert isClone;
    final RunManager runManager = RunManager.getInstance(location.getProject());
    final List<RunnerAndConfigurationSettings> configurations = runManager.getConfigurationSettingsList(getConfigurationType());
    return findExistingByElement(location, configurations, context);
  }

  public abstract PsiElement getSourceElement();

  protected void storeSourceElement(@NotNull PsiElement e) {
    myPointer = SmartPointerManager.createPointer(e);
  }

  protected @Nullable PsiElement restoreSourceElement() {
    return myPointer == null ? null : myPointer.getElement();
  }

  public RunnerAndConfigurationSettings getConfiguration() {
    assert isClone;
    return myConfiguration;
  }

  public void setConfiguration(RunnerAndConfigurationSettings configuration) {
    assert isClone;
    myConfiguration = configuration;
  }

  protected abstract @Nullable RunnerAndConfigurationSettings createConfigurationByElement(Location location, ConfigurationContext context);

  protected @Nullable RunnerAndConfigurationSettings findExistingByElement(final Location location,
                                                                           final @NotNull List<? extends RunnerAndConfigurationSettings> existingConfigurations,
                                                                           ConfigurationContext context) {
    assert isClone;
    return null;
  }

  @Override
  public RuntimeConfigurationProducer clone() {
    assert !isClone;
    try {
      RuntimeConfigurationProducer clone = (RuntimeConfigurationProducer)super.clone();
      clone.isClone = true;
      return clone;
    }
    catch (CloneNotSupportedException e) {
      throw new RuntimeException(e);
    }
  }

  protected RunnerAndConfigurationSettings cloneTemplateConfiguration(final Project project, final @Nullable ConfigurationContext context) {
    if (context != null) {
      final RunConfiguration original = context.getOriginalConfiguration(myConfigurationFactory.getType());
      if (original != null) {
        final RunConfiguration c = original instanceof DelegatingRuntimeConfiguration ? ((DelegatingRuntimeConfiguration<?>)original).getPeer() : original;
        return RunManager.getInstance(project).createConfiguration(c.clone(), myConfigurationFactory);
      }
    }
    return RunManager.getInstance(project).createConfiguration("", myConfigurationFactory);
  }

  protected ConfigurationFactory getConfigurationFactory() {
    return myConfigurationFactory;
  }

  public ConfigurationType getConfigurationType() {
    return myConfigurationFactory.getType();
  }

  public void perform(ConfigurationContext context, Runnable performRunnable){
    performRunnable.run();
  }

  public static <T extends RuntimeConfigurationProducer> T getInstance(final Class<T> aClass) {
    for (RuntimeConfigurationProducer configurationProducer : RUNTIME_CONFIGURATION_PRODUCER.getExtensionList()) {
      if (configurationProducer.getClass() == aClass) {
        //noinspection unchecked
        return (T) configurationProducer;
      }
    }
    return null;
  }

  private static class ProducerComparator implements Comparator<RuntimeConfigurationProducer> {
    @Override
    public int compare(final RuntimeConfigurationProducer producer1, final RuntimeConfigurationProducer producer2) {
      final PsiElement psiElement1 = producer1.getSourceElement();
      final PsiElement psiElement2 = producer2.getSourceElement();
      if (doesContain(psiElement1, psiElement2)) return -PREFERED;
      if (doesContain(psiElement2, psiElement1)) return PREFERED;
      return producer1.compareTo(producer2);
    }

    private static boolean doesContain(final PsiElement container, PsiElement element) {
      while ((element = element.getParent()) != null) {
        if (container.equals(element)) return true;
      }
      return false;
    }
  }

  /**
   * @deprecated feel free to pass your configuration to SMTRunnerConsoleProperties directly instead of wrapping in DelegatingRuntimeConfiguration
   */
  @Deprecated
  public static class DelegatingRuntimeConfiguration<T extends LocatableConfiguration>
    extends LocatableConfigurationBase implements ModuleRunConfiguration {
    private final T myConfig;

    public DelegatingRuntimeConfiguration(T config) {
      super(config.getProject(), config.getFactory(), config.getName());
      myConfig = config;
    }

    @Override
    public @NotNull SettingsEditor<? extends RunConfiguration> getConfigurationEditor() {
      return myConfig.getConfigurationEditor();
    }

    @SuppressWarnings({"CloneDoesntCallSuperClone"})
    @Override
    public DelegatingRuntimeConfiguration<T> clone() {
      return new DelegatingRuntimeConfiguration<>((T)myConfig.clone());
    }

    @Override
    public RunProfileState getState(@NotNull Executor executor, @NotNull ExecutionEnvironment env) throws ExecutionException {
      return myConfig.getState(executor, env);
    }

    @Override
    public void checkConfiguration() throws RuntimeConfigurationException {
      myConfig.checkConfiguration();
    }

    @Override
    public String suggestedName() {
      return myConfig.suggestedName();
    }

    @Override
    public void readExternal(@NotNull Element element) throws InvalidDataException {
      myConfig.readExternal(element);
    }

    @Override
    public void writeExternal(@NotNull Element element) throws WriteExternalException {
      myConfig.writeExternal(element);
    }

    public T getPeer() {
      return myConfig;
    }
  }
}
