// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.Nullability;
import com.intellij.codeInsight.NullableNotNullManager;
import com.intellij.codeInsight.annoPackages.AnnotationPackageSupport;
import com.intellij.codeInsight.daemon.quickFix.LightQuickFixParameterizedTestCase;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.nullable.NullableStuffInspection;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Disposer;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.testFramework.ServiceContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class AnnotateMethodTest extends LightQuickFixParameterizedTestCase {
  @Override
  protected String getBasePath() {
    return "/codeInsight/daemonCodeAnalyzer/quickFix/annotateMethod";
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    if (getTestName(false).contains("TypeUse")) {
      NullableNotNullManager nnnManager = NullableNotNullManager.getInstance(getProject());
      AnnotationPackageSupport mySupport = new AnnotationPackageSupport() {
        @Override
        public @NotNull List<String> getNullabilityAnnotations(@NotNull Nullability nullability) {
          return switch (nullability) {
            case NOT_NULL -> List.of("typeUse.NotNull");
            case NULLABLE -> List.of("typeUse.Nullable");
            case UNKNOWN -> List.of();
          };
        }

        @Override
        public boolean isTypeUseAnnotationLocationRestricted() {
          return true;
        }
      }; 
      ServiceContainerUtil.registerExtension(ApplicationManager.getApplication(), AnnotationPackageSupport.EP_NAME, mySupport, getTestRootDisposable());
      String prevNullable = nnnManager.getDefaultNullable();
      String prevNotNull = nnnManager.getDefaultNotNull();
      nnnManager.setDefaultNotNull("typeUse.NotNull");
      nnnManager.setDefaultNullable("typeUse.Nullable");
      Disposer.register(getTestRootDisposable(), () -> {
        nnnManager.setDefaultNotNull(prevNotNull);
        nnnManager.setDefaultNullable(prevNullable);
      });
    }
  }

  @Override
  protected LocalInspectionTool @NotNull [] configureLocalInspectionTools() {
    return new LocalInspectionTool[]{new NullableStuffInspection()};
  }

  @Override
  protected LanguageLevel getLanguageLevel() {
    return LanguageLevel.JDK_1_8;
  }
}