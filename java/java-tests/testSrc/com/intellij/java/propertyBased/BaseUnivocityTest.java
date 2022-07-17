// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.propertyBased;


import com.intellij.compiler.CompilerTestUtil;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.impl.JavaAwareProjectJdkTableImpl;
import com.intellij.openapi.roots.LanguageLevelProjectExtension;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.pom.java.LanguageLevel;
import org.jetbrains.annotations.NotNull;

import java.io.File;

public abstract class BaseUnivocityTest extends AbstractApplyAndRevertTestCase {

  @Override
  public void setUp() throws Exception {
    super.setUp();
    Sdk jdk = JavaAwareProjectJdkTableImpl.getInstanceEx().getInternalJdk();
    WriteAction.run(() -> {
      ProjectJdkTable.getInstance().addJdk(jdk, getTestRootDisposable());
      ProjectRootManager.getInstance(myProject).setProjectSdk(jdk);
      LanguageLevelProjectExtension.getInstance(myProject).setLanguageLevel(projectLanguageLevel());
    });
    CompilerTestUtil.saveApplicationSettings();
  }

  @NotNull
  protected LanguageLevel projectLanguageLevel() {
    return LanguageLevel.JDK_1_8;
  }

  @Override
  protected String getTestDataPath() {
    File file = new File(PathManager.getHomePath(), "univocity-parsers");
    if (!file.exists()) {
      fail("Cannot find univocity project:\n" +
           "  execute this in project home: git clone https://github.com/JetBrains/univocity-parsers.git\n" +
           "  open the just cloned univocity-parsers project in IntelliJ IDEA, let it download all the libraries, close the IDE\n" +
           "  execute this in univocity-parsers directory: git reset HEAD --hard");
    }
    return file.getAbsolutePath();
  }
}
