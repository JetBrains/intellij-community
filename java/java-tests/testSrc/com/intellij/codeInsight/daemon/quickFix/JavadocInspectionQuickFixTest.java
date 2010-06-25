/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

/*
 * Created by IntelliJ IDEA.
 * User: Anna.Kozlova
 * Date: 06-Sep-2006
 * Time: 13:56:11
 */
package com.intellij.codeInsight.daemon.quickFix;

import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.javaDoc.JavaDocLocalInspection;

public class JavadocInspectionQuickFixTest extends LightQuickFix15TestCase {

  protected LocalInspectionTool[] configureLocalInspectionTools() {
    final JavaDocLocalInspection javaDocLocalInspection = new JavaDocLocalInspection();
    javaDocLocalInspection.TOP_LEVEL_CLASS_OPTIONS.REQUIRED_TAGS = "param";
    return new LocalInspectionTool[]{javaDocLocalInspection};
  }

  protected void tearDown() throws Exception {
    super.tearDown();
  }

  public void test() throws Exception { doAllTests(); }

  protected String getBasePath() {
    return "/codeInsight/daemonCodeAnalyzer/quickFix/javadocTags";
  }

}