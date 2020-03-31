// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.propertyBased;


import com.intellij.openapi.application.PathManager;

import java.io.File;

public abstract class BaseUnivocityTest extends AbstractApplyAndRevertTestCase {
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
