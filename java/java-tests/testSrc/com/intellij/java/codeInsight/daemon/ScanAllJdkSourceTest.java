/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.java.codeInsight.daemon;

import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.codeInsight.daemon.DaemonAnalyzerTestCase;
import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInspection.ex.InspectionProfileImpl;
import com.intellij.codeInspection.ex.InspectionProfileModifiableModel;
import com.intellij.codeInspection.javaDoc.JavaDocLocalInspectionBase;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkModificator;
import com.intellij.openapi.projectRoots.impl.ProjectRootUtil;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.profile.codeInspection.ProjectInspectionProfileManager;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.InspectionsKt;
import com.intellij.util.Consumer;

public class ScanAllJdkSourceTest extends DaemonAnalyzerTestCase {
  public void testAll() throws Exception {
    if (System.getProperty("JDK_HIGHLIGHTING_TEST") == null) {
      return;
    }

    Sdk jdk = ModuleRootManager.getInstance(myModule).getSdk();
    String toolsPath = jdk.getHomeDirectory().getPath() + "/lib/tools.jar";
    VirtualFile toolsJar = JarFileSystem.getInstance().findFileByPath(toolsPath + JarFileSystem.JAR_SEPARATOR);
    assertNotNull(toolsJar);
    SdkModificator sdkModificator = jdk.getSdkModificator();
    sdkModificator.addRoot(toolsJar, OrderRootType.CLASSES);
    sdkModificator.commitChanges();

    ProjectInspectionProfileManager profileManager = ProjectInspectionProfileManager.getInstance(myProject);
    InspectionProfileImpl inspectionProfile = profileManager.getCurrentProfile();
    InspectionsKt.runInInitMode(() -> {
      inspectionProfile.initInspectionTools(myProject);
      return null;
    });

    Consumer<InspectionProfileModifiableModel> consumer = it -> it.setErrorLevel(HighlightDisplayKey.find(JavaDocLocalInspectionBase.SHORT_NAME), HighlightDisplayLevel.WARNING, getProject());
    inspectionProfile.modifyProfile(consumer);
    try {
      for (PsiDirectory root : ProjectRootUtil.getSourceRootDirectories(myProject)) {
        doScan(root);
      }
    }
    finally {
      inspectionProfile.modifyProfile(consumer);
      profileManager.deleteProfile(inspectionProfile.getName());
    }
  }

  static final int MAX_FILES = Integer.parseInt(
    System.getProperty("JDK_HIGHLIGHTING_MAX_FILES") == null ? "0" : System.getProperty("JDK_HIGHLIGHTING_MAX_FILES"));
  static int nFiles = 0;
  static final String FIRST_FILE = System.getProperty("JDK_HIGHLIGHTING_FIRST_FILE");
  static boolean doTest = FIRST_FILE == null;

  private void doScan(PsiDirectory dir) throws Exception {
    for (PsiElement child : dir.getChildren()) {
      if (child instanceof PsiDirectory) {
        doScan((PsiDirectory)child);
      }
      else {
        if (++nFiles == MAX_FILES) return;
        PsiFile file = (PsiFile)child;
        final String url = file.getVirtualFile().getPresentableUrl();
        if (url.equals(FIRST_FILE)) doTest = true;
        if (doTest) {
          System.out.println("analyzing file:" + url);
          doTest(file.getVirtualFile(), false, false);
        }
      }
    }
  }
}
