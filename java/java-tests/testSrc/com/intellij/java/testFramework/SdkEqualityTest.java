// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.testFramework;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.EmptyModuleType;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.AnnotationOrderRootType;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.StandardFileSystems;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.LightPlatformTestCase;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.testFramework.common.BazelTestUtil;

public class SdkEqualityTest extends LightPlatformTestCase {
  public void testSdkEquality() {
    Sdk jdk8 = IdeaTestUtil.getMockJdk(LanguageLevel.JDK_1_8.toJavaVersion());
    Sdk jdk8_copy = IdeaTestUtil.getMockJdk(LanguageLevel.JDK_1_8.toJavaVersion());

    String someRandomStrangePath = FileUtil.toSystemIndependentName(
      BazelTestUtil.isUnderBazelTest()
      ? BazelTestUtil.findRunfilesDirectoryUnderCommunityOrUltimate("java/jdkAnnotations/resources/javax").toString()
      : PlatformTestUtil.getCommunityPath() + "/java/jdkAnnotations/resources/javax");
    VirtualFile root = StandardFileSystems.local().findFileByPath(someRandomStrangePath);
    Sdk jdk8_annotated = PsiTestUtil.addRootsToJdk(jdk8, AnnotationOrderRootType.getInstance(), root);

    Sdk jdk11 = IdeaTestUtil.getMockJdk(LanguageLevel.JDK_11.toJavaVersion());
    assertTrue(areSdkEqual(jdk8, jdk8));
    assertTrue(areSdkEqual(jdk8, jdk8_copy));
    assertFalse(areSdkEqual(jdk8, jdk11));
    assertFalse(areSdkEqual(jdk8, jdk8_annotated));

    // We need to remove JDK from the table  to dispose created VFP, otherwise we need to add JDK with disposable using
    // ProjectJdkTable.addJdk(Sdk, Disposable) which makes it under the hood
    ApplicationManager.getApplication().runWriteAction(() -> {
      ProjectJdkTable.getInstance().removeJdk(jdk8);
      ProjectJdkTable.getInstance().removeJdk(jdk8_copy);
      ProjectJdkTable.getInstance().removeJdk(jdk8_annotated);
      ProjectJdkTable.getInstance().removeJdk(jdk11);
    });
  }
  
  boolean areSdkEqual(Sdk sdk1, Sdk sdk2) {
    LightProjectDescriptor desc1 = createSimpleLightProjectDescriptor(EmptyModuleType.EMPTY_MODULE, sdk1);
    LightProjectDescriptor desc2 = createSimpleLightProjectDescriptor(EmptyModuleType.EMPTY_MODULE, sdk2);
    return desc1.equals(desc2);
  }
}
