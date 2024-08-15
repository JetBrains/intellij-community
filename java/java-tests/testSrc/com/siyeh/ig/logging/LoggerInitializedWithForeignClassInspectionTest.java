// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.logging;

import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.codeInspection.ex.InspectionProfileImpl;
import com.intellij.openapi.project.Project;
import com.intellij.profile.codeInspection.ProjectInspectionProfileManager;
import com.siyeh.ig.LightJavaInspectionTestCase;
import org.jetbrains.annotations.Nullable;

/**
 * @author Bas Leijdekkers
 */
public class LoggerInitializedWithForeignClassInspectionTest extends LightJavaInspectionTestCase {

  @Override
  public void setUp() throws Exception {
    super.setUp();
    changeToWarning();
  }

  private void changeToWarning() {
    HighlightDisplayKey displayKey = HighlightDisplayKey.find(getInspection().getShortName());
    Project project = getProject();
    InspectionProfileImpl currentProfile = ProjectInspectionProfileManager.getInstance(project).getCurrentProfile();
    currentProfile.setErrorLevel(displayKey, HighlightDisplayLevel.WARNING, project);
  }

  public void testLoggerInitializedWithForeignClass() {
    doTest();
  }

  public void testLog4J2() {
    doTest();
    checkQuickFixAll();
  }

  public void testIgnoreNonPublicClasses() {
    final LoggerInitializedWithForeignClassInspection inspection = new LoggerInitializedWithForeignClassInspection();
    inspection.ignoreNonPublicClasses = true;
    inspection.warnOnlyFinalFieldAssignment = false;
    myFixture.enableInspections(inspection);
    changeToWarning();
    doTest();
  }

  public void testWarnOnlyFinalFieldAssignment() {
    changeToWarning();
    doTest();
  }

  @Nullable
  @Override
  protected InspectionProfileEntry getInspection() {
    final LoggerInitializedWithForeignClassInspection inspection = new LoggerInitializedWithForeignClassInspection();
    inspection.ignoreSuperClass = true;
    String name = getTestName(false);
    if (name.endsWith("WarnOnlyFinalFieldAssignment")) {
      inspection.warnOnlyFinalFieldAssignment = true;
    }
    else {
      inspection.warnOnlyFinalFieldAssignment = false;
    }
    return inspection;
  }

  @Override
  protected String[] getEnvironmentClasses() {
    return new String[] {
      "package java.util.logging;" +
      "public class Logger {" +
      "  public static Logger getLogger(String name) {" +
      "    return null;" +
      "  }" +
      "  public void info(String s) {}" +
      "}",

      "package org.apache.logging.log4j;" +
      "public class LogManager {" +
      "  public static Logger getLogger(Class<?> clazz) {" +
      "    return null;" +
      "  }" +
      "  public static Logger getLogger(String name) {" +
      "    return null;" +
      "  }" +
      "}" +
      "public interface Logger {}"
    };
  }
}
