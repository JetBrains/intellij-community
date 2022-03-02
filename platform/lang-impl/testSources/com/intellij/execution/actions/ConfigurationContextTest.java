// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.actions;

import com.intellij.execution.Location;
import com.intellij.execution.PsiLocation;
import com.intellij.execution.RunManager;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.execution.impl.FakeConfigurationFactory;
import com.intellij.execution.impl.FakeRunConfiguration;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.extensions.ExtensionPoint;
import com.intellij.openapi.fileTypes.FileTypes;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.MapDataContext;
import com.intellij.testFramework.TestActionEvent;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.junit.Assert;

import java.util.List;
import java.util.Objects;

public class ConfigurationContextTest extends BasePlatformTestCase {

  public void testBasicExistingConfigurations() {
    myFixture.configureByText(FileTypes.PLAIN_TEXT, "qq<caret>q");

    ConfigurationContext context = ConfigurationContext.getFromContext(createDataContext(), ActionPlaces.UNKNOWN);
    Assert.assertNull(context.findExisting());

    Disposable disposable = Disposer.newDisposable();
    try {
      RunConfigurationProducer.EP_NAME.getPoint().registerExtension(new FakeRunConfigurationProducer(""), disposable);
      List<RunnerAndConfigurationSettings> configs = getConfigurationsFromContext();
      Assert.assertEquals(1, configs.size());
      for (RunnerAndConfigurationSettings config : configs) {
        addConfiguration(config);
      }

      context = ConfigurationContext.getFromContext(createDataContext(), ActionPlaces.UNKNOWN);
      RunnerAndConfigurationSettings existing = context.findExisting();
      Assert.assertNotNull(existing);
      Assert.assertTrue(existing.getConfiguration() instanceof FakeRunConfiguration);
    }
    finally {
      Disposer.dispose(disposable);
    }

    context = ConfigurationContext.getFromContext(createDataContext(), ActionPlaces.UNKNOWN);
    Assert.assertNull(context.findExisting());
  }

  public void testPreferredExistingConfiguration() {
    myFixture.configureByText(FileTypes.PLAIN_TEXT, "hello,<caret>world");
    @SuppressWarnings("rawtypes")
    ExtensionPoint<RunConfigurationProducer> ep = RunConfigurationProducer.EP_NAME.getPoint();
    ep.registerExtension(new FakeRunConfigurationProducer("hello_"), getTestRootDisposable());
    ep.registerExtension(new FakeRunConfigurationProducer("world_"), getTestRootDisposable());

    FakeRunConfigurationProducer.SORTING = SortingMode.NONE;
    List<RunnerAndConfigurationSettings> configs = getConfigurationsFromContext();
    Assert.assertEquals(2, configs.size());
    for (RunnerAndConfigurationSettings config : configs) {
      addConfiguration(config);
    }

    FakeRunConfigurationProducer.SORTING = SortingMode.NAME_ASC;
    ConfigurationContext context = ConfigurationContext.getFromContext(createDataContext(), ActionPlaces.UNKNOWN);
    RunnerAndConfigurationSettings existing = context.findExisting();
    Assert.assertNotNull(existing);
    Assert.assertTrue(existing.getConfiguration().getName().startsWith("hello_"));

    FakeRunConfigurationProducer.SORTING = SortingMode.NAME_DESC;
    context = ConfigurationContext.getFromContext(createDataContext(), ActionPlaces.UNKNOWN);
    existing = context.findExisting();
    Assert.assertNotNull(existing);
    Assert.assertTrue(existing.getConfiguration().getName().startsWith("world_"));
  }

  public void test2ApplicableConfigurationsInContext() {
    myFixture.configureByText(FileTypes.PLAIN_TEXT, "hello,<caret>world");
    @SuppressWarnings("rawtypes") 
    ExtensionPoint<RunConfigurationProducer> ep = RunConfigurationProducer.EP_NAME.getPoint();
    ep.registerExtension(new FakeRunConfigurationProducer("hello_") {
      @Override
      public boolean isPreferredConfiguration(ConfigurationFromContext self, ConfigurationFromContext other) {
        return true;
      }
    }, getTestRootDisposable());
    ep.registerExtension(new FakeRunConfigurationProducer("world_") {
      @Override
      public boolean isPreferredConfiguration(ConfigurationFromContext self, ConfigurationFromContext other) {
        return true;
      }
    }, getTestRootDisposable());

    List<RunnerAndConfigurationSettings> configs = getConfigurationsFromContext();
    Assert.assertEquals(2, configs.size());
    RunnerAndConfigurationSettings configuration = configs.get(0);
    assertEquals("hello_", configuration.getName());
    addConfiguration(configuration);
    DataContext context = createDataContext();
    TestActionEvent event = new TestActionEvent(context);
    new RunNewConfigurationContextAction(DefaultRunExecutor.getRunExecutorInstance()).fullUpdate(event);
    assertTrue(event.getPresentation().isEnabledAndVisible());
    assertEquals("Run 'world_'", event.getPresentation().getText());

    RunnerAndConfigurationSettings configuration1 = configs.get(1);
    assertEquals("world_", configuration1.getName());
    addConfiguration(configuration1);

    DataContext context1 = createDataContext();
    TestActionEvent event1 = new TestActionEvent(context1);
    new RunNewConfigurationContextAction(DefaultRunExecutor.getRunExecutorInstance()).fullUpdate(event1);
    assertTrue(event1.getPresentation().isEnabledAndVisible());
    assertEquals("Run 'world_'", event1.getPresentation().getText());
  }

  public static void main(String[] args) {
    
  }
  
  public void test2ApplicableConfigurationsInContextNestedScopes() {
    PsiFile file = myFixture.configureByText(FileTypes.PLAIN_TEXT, "hello,<caret>world");
    @SuppressWarnings("rawtypes") 
    ExtensionPoint<RunConfigurationProducer> ep = RunConfigurationProducer.EP_NAME.getPoint();
    ep.registerExtension(new FakeRunConfigurationProducer("hello_") {
      @Override
      public boolean isPreferredConfiguration(ConfigurationFromContext self, ConfigurationFromContext other) {
        return true;
      }
      @Override
      protected boolean setupConfigurationFromContext(@NotNull FakeRunConfiguration configuration,
                                                      @NotNull ConfigurationContext context,
                                                      @NotNull Ref<PsiElement> sourceElement) {
        super.setupConfigurationFromContext(configuration, context, sourceElement);
        sourceElement.set(file);
        return true;
      }
    }, getTestRootDisposable());
    ep.registerExtension(new FakeRunConfigurationProducer("world_") {
      @Override
      public boolean isPreferredConfiguration(ConfigurationFromContext self, ConfigurationFromContext other) {
        return true;
      }
    }, getTestRootDisposable());

    List<RunnerAndConfigurationSettings> configs = getConfigurationsFromContext();
    Assert.assertEquals(2, configs.size());
    RunnerAndConfigurationSettings configuration = configs.get(1);
    assertEquals("hello_", configuration.getName());
    addConfiguration(configuration);
    
    DataContext context = createDataContext();
    assertNull(ConfigurationContext.getFromContext(context, ActionPlaces.UNKNOWN).findExisting());
    TestActionEvent event = new TestActionEvent(context);
    new RunNewConfigurationContextAction(DefaultRunExecutor.getRunExecutorInstance()).fullUpdate(event);
    assertFalse(event.getPresentation().isEnabledAndVisible());
    
    DataContext context1 = createDataContext();
    TestActionEvent event1 = new TestActionEvent(context1);
    new RunContextAction(DefaultRunExecutor.getRunExecutorInstance()).fullUpdate(event1);
    assertTrue(event1.getPresentation().isEnabledAndVisible());
    assertEquals("Run 'world_'", event1.getPresentation().getText());
  }

  private @NotNull DataContext createDataContext() {
    MapDataContext dataContext = new MapDataContext();
    dataContext.put(CommonDataKeys.PROJECT, getProject());
    int offset = myFixture.getEditor().getCaretModel().getOffset();
    PsiElement element = Objects.requireNonNull(myFixture.getFile().findElementAt(offset));
    dataContext.put(Location.DATA_KEY, PsiLocation.fromPsiElement(element));
    return dataContext;
  }

  private void addConfiguration(@NotNull RunnerAndConfigurationSettings configuration) {
    Assert.assertTrue(configuration.getConfiguration() instanceof FakeRunConfiguration);
    final RunManager runManager = RunManager.getInstance(getProject());
    runManager.addConfiguration(configuration);
    Disposer.register(getTestRootDisposable(), new Disposable() {
      @Override
      public void dispose() {
        runManager.removeConfiguration(configuration);
      }
    });
  }

  @NotNull
  private List<RunnerAndConfigurationSettings> getConfigurationsFromContext() {
    DataContext dataContext = createDataContext();
    List<ConfigurationFromContext> list = PreferredProducerFind.getConfigurationsFromContext(
      dataContext.getData(Location.DATA_KEY),
      ConfigurationContext.getFromContext(dataContext, ActionPlaces.UNKNOWN),
      false,
      true
    );
    return ContainerUtil.map(list, ConfigurationFromContext::getConfigurationSettings);
  }

  private static class FakeRunConfigurationProducer extends LazyRunConfigurationProducer<FakeRunConfiguration> {
    private static SortingMode SORTING = SortingMode.NONE;

    private final String myNamePrefix;

    FakeRunConfigurationProducer(@NotNull String namePrefix) {
      myNamePrefix = namePrefix;
    }

    @Override
    protected boolean setupConfigurationFromContext(@NotNull FakeRunConfiguration configuration,
                                                    @NotNull ConfigurationContext context,
                                                    @NotNull Ref<PsiElement> sourceElement) {
      configuration.setName(myNamePrefix + configuration.getName());
      sourceElement.set(context.getPsiLocation());
      return true;
    }

    @Override
    public boolean isConfigurationFromContext(@NotNull FakeRunConfiguration configuration, @NotNull ConfigurationContext context) {
      return configuration.getName().startsWith(myNamePrefix);
    }

    @Override
    public boolean isPreferredConfiguration(ConfigurationFromContext self, ConfigurationFromContext other) {
      FakeRunConfiguration selfConfig = ObjectUtils.tryCast(self.getConfiguration(), FakeRunConfiguration.class);
      FakeRunConfiguration otherConfig = ObjectUtils.tryCast(other.getConfiguration(), FakeRunConfiguration.class);
      if (selfConfig == null || otherConfig == null) {
        return false;
      }
      if (SORTING == SortingMode.NAME_ASC) {
        return selfConfig.getName().compareTo(otherConfig.getName()) < 0;
      }
      if (SORTING == SortingMode.NAME_DESC) {
        return selfConfig.getName().compareTo(otherConfig.getName()) > 0;
      }
      return false;
    }

    @NotNull
    @Override
    public ConfigurationFactory getConfigurationFactory() {
      return FakeConfigurationFactory.INSTANCE;
    }
  }

  private enum SortingMode { NAME_ASC, NAME_DESC, NONE }
}
