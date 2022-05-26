// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInsight.daemon.quickFix;

import com.intellij.codeInsight.daemon.quickFix.LightQuickFixParameterizedTestCase;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.javaDoc.JavaDocLocalInspection;
import com.intellij.codeInspection.javaDoc.JavaDocReferenceInspection;
import com.intellij.codeInspection.javaDoc.JavadocDeclarationInspection;
import com.intellij.codeInspection.javaDoc.MissingJavadocInspection;
import org.jetbrains.annotations.NotNull;

public class JavadocInspectionQuickFixTest extends LightQuickFixParameterizedTestCase {
  @Override
  protected LocalInspectionTool @NotNull [] configureLocalInspectionTools() {
    JavaDocLocalInspection inspection = new JavaDocLocalInspection();
    inspection.TOP_LEVEL_CLASS_OPTIONS.REQUIRED_TAGS = "param";
    inspection.TOP_LEVEL_CLASS_OPTIONS.ACCESS_JAVADOC_REQUIRED_FOR = "package";
    inspection.METHOD_OPTIONS.ACCESS_JAVADOC_REQUIRED_FOR = "package";
    return new LocalInspectionTool[]{new MissingJavadocInspection(), new JavadocDeclarationInspection(), new JavaDocReferenceInspection()};
  }

  @Override
  protected String getBasePath() {
    return "/codeInsight/daemonCodeAnalyzer/quickFix/javadocTags";
  }
}
