// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.impl.statistics;

import com.intellij.execution.configurations.ConfigurationType;
import com.intellij.execution.impl.statistics.RunConfigurationTypeUsagesCollector.RunConfigurationUtilValidator;
import com.intellij.internal.statistic.eventLog.FeatureUsageData;
import com.jetbrains.fus.reporting.api.ValidationResultType;
import com.intellij.internal.statistic.eventLog.validator.rules.EventContext;
import com.intellij.internal.statistic.eventLog.validator.rules.impl.CustomValidationRule;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.extensions.ExtensionPoint;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.util.Disposer;
import com.intellij.testFramework.junit5.TestApplication;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestApplication
public class RunConfigurationValidatorTest {

  private static void doTest(@NotNull ValidationResultType expected, @NotNull CustomValidationRule validator,
                             @NotNull String data, @NotNull EventContext context) {
    final Disposable disposable = Disposer.newDisposable();
    try {
      final ExtensionPoint<ConfigurationType> ep = Extensions.getRootArea().getExtensionPoint(ConfigurationType.CONFIGURATION_TYPE_EP);
      assertNotNull(ep);

      ep.registerExtension(new BaseTestConfigurationType.FirstTestRunConfigurationType(), disposable);
      ep.registerExtension(new BaseTestConfigurationType.SecondTestRunConfigurationType(), disposable);
      ep.registerExtension(new BaseTestConfigurationType.MultiFactoryTestRunConfigurationType(), disposable);

      assertEquals(expected, validator.validate(data, context));
    }
    finally {
      Disposer.dispose(disposable);
    }
  }

  private static void doValidateFactory(@NotNull FeatureUsageData eventData) {
    doValidateData(eventData, "factory");
  }

  private static void doValidateConfigId(@NotNull FeatureUsageData eventData) {
    doValidateData(eventData, "id");
  }

  private static void doValidateData(@NotNull FeatureUsageData eventData, @NotNull String field) {
    final RunConfigurationUtilValidator validator = new RunConfigurationUtilValidator();
    final EventContext context = EventContext.create("configured.in.project", eventData.build());
    final Object data = eventData.build().get(field);
    assertTrue(data instanceof String);
    doTest(ValidationResultType.ACCEPTED, validator, (String)data, context);
  }

  private static void doRejectEventId(@NotNull String eventId, @NotNull FeatureUsageData eventData) {
    final RunConfigurationUtilValidator validator = new RunConfigurationUtilValidator();
    final EventContext context = EventContext.create(eventId, eventData.build());
    doTest(ValidationResultType.REJECTED, validator, eventId, context);
  }

  private static void doRejectConfigId(@NotNull FeatureUsageData eventData) {
    doRejectData(eventData, "id");
  }

  private static void doRejectFactory(@NotNull FeatureUsageData eventData) {
    doRejectData(eventData, "factory");
  }

  private static void doRejectData(@NotNull FeatureUsageData eventData, @NotNull String field) {
    final RunConfigurationUtilValidator validator = new RunConfigurationUtilValidator();
    final EventContext context = EventContext.create("configured.in.project", eventData.build());
    final Object data = eventData.build().get(field);
    assertTrue(data instanceof String);
    doTest(ValidationResultType.REJECTED, validator, (String)data, context);
  }

  @Test
  public void testRunConfigurationWithOneFactory() {
    doValidateConfigId(newFeatureUsageData().addData("id", "FirstTestRunConfigurationType"));
  }

  @Test
  public void testAnotherRunConfigurationWithOneFactory() {
    doValidateConfigId(newFeatureUsageData().addData("id", "SecondTestRunConfigurationType"));
  }

  @Test
  public void testAnotherRunConfigurationWithEmptyName() {
    doValidateConfigId(newFeatureUsageData().addData("id", "SecondTestRunConfigurationType"));
  }

  @Test
  public void testThirdPartyRunConfigurationWithOneFactory() {
    doValidateConfigId(newFeatureUsageData().addData("id", "third.party"));
  }

  @Test
  public void testRejectUnknownRunConfiguration() {
    doRejectConfigId(newFeatureUsageData().addData("id", "UnknownTestRunConfigurationType"));
  }

  @Test
  public void testRejectEmptyRunConfiguration() {
    doRejectConfigId(newFeatureUsageData().addData("id", ""));
  }

  @Test
  public void testRejectNoEmptyRunConfiguration() {
    doRejectFactory(newFeatureUsageData().addData("factory", "Local"));
  }

  @Test
  public void testRunConfigurationWithIncorrectFactory() {
    doRejectConfigId(newFeatureUsageData().addData("id", "FirstTestRunConfigurationType/Local"));
  }

  @Test
  public void testRunConfigurationWithLocalFactory() {
    final FeatureUsageData data = newFeatureUsageData().
      addData("factory", "Local").
      addData("id", "MultiFactoryTestRunConfigurationType");
    doValidateConfigId(data);
    doValidateFactory(data);
  }

  @Test
  public void testRunConfigurationWithRemoteFactory() {
    final FeatureUsageData data = newFeatureUsageData().
      addData("factory", "Remote").
      addData("id", "MultiFactoryTestRunConfigurationType");
    doValidateConfigId(data);
    doValidateFactory(data);
  }

  @Test
  public void testRunConfigurationWithEmptyFactory() {
    doRejectEventId("MultiFactoryTestRunConfigurationType/", newFeatureUsageData());
  }

  @Test
  public void testRejectRunConfigurationWithUnknownFactory() {
    final FeatureUsageData data = newFeatureUsageData().
      addData("factory", "Unknown").
      addData("id", "MultiFactoryTestRunConfigurationType");
    doRejectConfigId(data);
    doRejectFactory(data);
  }

  @Test
  public void testRejectRunConfigurationWithEmptyName() {
    doRejectConfigId(newFeatureUsageData().addData("id", "/Remote"));
  }

  @Test
  public void testRejectRunConfigurationWithEmptyNameAndFactory() {
    doRejectEventId("/", newFeatureUsageData().addData("id", "/"));
  }

  @Test
  public void testRejectRunConfigurationWithTwoMuchValues() {
    doRejectEventId("RunConfigName/Factory/AnotherValue", newFeatureUsageData().addData("id", "RunConfigName/Factory/AnotherValue"));
  }

  @NotNull
  private static FeatureUsageData newFeatureUsageData() {
    return new FeatureUsageData().
      addData("plugin_type", "PLATFORM").
      addData("edit_before_run", true).
      addData("activate_before_run", false).
      addData("shared", false).
      addData("parallel", true);
  }
}
