/*
 * Copyright 2000-2013 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.psi;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ex.PathManagerEx;
import com.intellij.openapi.roots.LanguageLevelProjectExtension;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.testFramework.PsiTestCase;
import com.intellij.testFramework.PsiTestUtil;

import java.io.File;

/**
 * @author dsl
 */
public abstract class GenericsTestCase extends PsiTestCase {
  @Override
  protected void setUp() throws Exception {
    super.setUp();
    LanguageLevelProjectExtension.getInstance(myJavaFacade.getProject()).setLanguageLevel(LanguageLevel.JDK_1_5);
  }

  protected void setupGenericSampleClasses() {
    final String commonPath = PathManagerEx.getTestDataPath().replace(File.separatorChar, '/') + "/psi/types/src";
    final VirtualFile[] commonRoot = { null };
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        commonRoot[0] = LocalFileSystem.getInstance().refreshAndFindFileByPath(commonPath);
      }
    });

    PsiTestUtil.addSourceRoot(myModule, commonRoot[0]);
  }
}
