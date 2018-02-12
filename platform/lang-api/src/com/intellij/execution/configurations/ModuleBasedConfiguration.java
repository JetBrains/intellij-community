/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.execution.configurations;

import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ArrayUtil;
import gnu.trove.THashSet;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * Base class for a configuration that is associated with a specific module. For example, Java run configurations use the selected module
 * to determine the run classpath.
 */
public abstract class ModuleBasedConfiguration<ConfigurationModule extends RunConfigurationModule> extends LocatableConfigurationBase implements Cloneable, ModuleRunConfiguration {
  private static final Logger LOG = Logger.getInstance("#com.intellij.execution.configurations.ModuleBasedConfiguration");

  protected static final String TO_CLONE_ELEMENT_NAME = "toClone";

  private final ConfigurationModule myModule;

  public ModuleBasedConfiguration(String name, @NotNull ConfigurationModule configurationModule, @NotNull ConfigurationFactory factory) {
    super(configurationModule.getProject(), factory, name);

    myModule = configurationModule;
    setInitialModuleName();
  }

  public ModuleBasedConfiguration(@NotNull ConfigurationModule configurationModule, @NotNull ConfigurationFactory factory) {
    super(configurationModule.getProject(), factory, "");

    myModule = configurationModule;
    setInitialModuleName();
  }

  private void setInitialModuleName() {
    // to ensure that newly created RC will have modification counter 0
    ModuleBasedConfigurationOptions options = getOptions();
    options.setModule(myModule.getModuleName());
    options.resetModificationCount();
  }

  @Override
  protected ModuleBasedConfigurationOptions getOptions() {
    //noinspection unchecked
    return (ModuleBasedConfigurationOptions)super.getOptions();
  }

  @Override
  protected Class<? extends ModuleBasedConfigurationOptions> getOptionsClass() {
    return ModuleBasedConfigurationOptions.class;
  }

  public abstract Collection<Module> getValidModules();

  public ConfigurationModule getConfigurationModule() {
    return myModule;
  }

  public void setModule(final Module module) {
    getConfigurationModule().setModule(module);
  }

  public void setModuleName(@Nullable String moduleName) {
    getConfigurationModule().setModuleName(moduleName);
  }

  protected void readModule(@NotNull Element element) {
    getConfigurationModule().readExternal(element);
  }

  /**
   * @deprecated Not required to be called anymore.
   */
  @Deprecated
  protected void writeModule(@NotNull Element element) {
    //if (myModule.getModule() != null) {
    getConfigurationModule().writeExternal(element);
    //}
  }

  public Collection<Module> getAllModules() {
    return Arrays.asList(ModuleManager.getInstance(getProject()).getModules());
  }

  /**
   * @deprecated  method {@link ConfigurationFactory#createTemplateConfiguration(com.intellij.openapi.project.Project)}
   * would be used instead to avoid wrong custom 'cloning'
   */
  protected ModuleBasedConfiguration createInstance() {
    @SuppressWarnings("unchecked")
    ModuleBasedConfiguration<ConfigurationModule> configuration =
      (ModuleBasedConfiguration<ConfigurationModule>)getFactory().createTemplateConfiguration(getProject());
    configuration.setName(getName());
    return configuration;
  }

  @Override
  public void readExternal(@NotNull Element element) throws InvalidDataException {
    super.readExternal(element);

    myModule.setModuleName(getOptions().getModule());
  }

  @Override
  public void writeExternal(@NotNull Element element) throws WriteExternalException {
    getOptions().setModule(myModule.getModuleName());

    super.writeExternal(element);
  }

  @SuppressWarnings("MethodDoesntCallSuperMethod")
  @Override
  public ModuleBasedConfiguration clone() {
    final Element element = new Element(TO_CLONE_ELEMENT_NAME);
    try {
      writeExternal(element);
      ModuleBasedConfiguration configuration = (ModuleBasedConfiguration)getFactory().createTemplateConfiguration(getProject());
      configuration.setName(getName());

      // AbstractPythonRunConfiguration calls in the constructor and so, there is a chance that newly created configuration will have module, but old haven't
      // and so, on readExternal module will be lost
      RunConfigurationModule configurationModule = configuration.getConfigurationModule();
      String moduleName = StringUtil.nullize(configurationModule.getModuleName());
      configuration.readExternal(element);
      if (moduleName != null && StringUtil.nullize(configurationModule.getModuleName()) == null) {
        configurationModule.setModuleName(moduleName);
      }
      return configuration;
    }
    catch (InvalidDataException | WriteExternalException e) {
      LOG.error(e);
      return null;
    }
  }

  @Override
  @NotNull
  public Module[] getModules() {
    Module module = ReadAction.compute(() -> getConfigurationModule().getModule());
    return module == null ? Module.EMPTY_ARRAY : new Module[]{module};
  }

  public void restoreOriginalModule(final Module originalModule) {
    if (canRestoreOriginalModule(originalModule, getModules())) {
      setModule(originalModule);
    }
  }

  public static boolean canRestoreOriginalModule(Module originalModule, Module[] configModules) {
    if (originalModule == null || configModules.length == 0) {
      return false;
    }

    Deque<Module> queue = new ArrayDeque<>();
    queue.addLast(originalModule);
    Set<Module> modules = new THashSet<>();
    while (!queue.isEmpty()) {
      Module module = queue.removeFirst();
      //configModules contains 1 element
      if (ArrayUtil.contains(module, configModules)) {
        return true;
      }

      for (Module next : ModuleRootManager.getInstance(module).getModuleDependencies(true)) {
        if (!modules.add(next)) continue;
        queue.addLast(next);
      }
    }
    return false;
  }

  public void onNewConfigurationCreated() {
    final RunConfigurationModule configurationModule = getConfigurationModule();
    if (configurationModule.getModule() == null) {
      final Module[] modules = ModuleManager.getInstance(getProject()).getModules();
      configurationModule.setModule(modules.length == 1 ? modules[0] : null);
    }
  }

  public boolean isModuleDirMacroSupported() {
    return false;
  }
}
