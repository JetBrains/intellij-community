// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.library;

import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.DefaultLightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.testFramework.fixtures.MavenDependencyUtil;
import org.jetbrains.annotations.NotNull;

public class JavaLibraryUtilTest extends LightJavaCodeInsightFixtureTestCase {
  private static final LightProjectDescriptor JUNIT_4_PROJECT = new DefaultLightProjectDescriptor() {
    @Override
    public void configureModule(@NotNull Module module, @NotNull ModifiableRootModel model, @NotNull ContentEntry contentEntry) {
      super.configureModule(module, model, contentEntry);
      MavenDependencyUtil.addFromMaven(model, "junit:junit:4.13.2");
    }
  };

  @Override
  protected @NotNull LightProjectDescriptor getProjectDescriptor() {
    return JUNIT_4_PROJECT;
  }

  public void testJpsLibraryVersionFromJarManifest() {
    assertEquals("4.13.2", ReadAction.compute(() -> JavaLibraryUtil.getLibraryVersion(getModule(), "junit:junit")));
  }

  public void testJpsLibraryVersionOfMissingLibrary() {
    assertNull(ReadAction.compute(() -> JavaLibraryUtil.getLibraryVersion(getModule(), "org.junit.jupiter:junit-jupiter-api")));
  }
}
