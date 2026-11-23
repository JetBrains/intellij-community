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

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.daemon.DaemonAnalyzerFixture;
import com.intellij.codeInspection.ex.InspectionProfileImpl;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.project.Project;
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
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.codeInsight.daemon.DaemonAnalyzerFixture.daemonAnalyzerFixture;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@TestApplication
public class ScanAllJdkSourceTest {
  private final TestFixture<DaemonAnalyzerFixture> daemonFixture = daemonAnalyzerFixture(JavaTestUtil.getJavaTestDataPath());

  @Test
  public void testAll() {
    if (System.getProperty("JDK_HIGHLIGHTING_TEST") == null) {
      return;
    }

    DaemonAnalyzerFixture fixture = daemonFixture.get();
    Project myProject = fixture.getProject();
    Sdk jdk = ModuleRootManager.getInstance(fixture.getModule()).getSdk();
    String toolsPath = jdk.getHomeDirectory().getPath() + "/lib/tools.jar";
    VirtualFile toolsJar = JarFileSystem.getInstance().findFileByPath(toolsPath + JarFileSystem.JAR_SEPARATOR);
    assertNotNull(toolsJar);
    SdkModificator sdkModificator = jdk.getSdkModificator();
    sdkModificator.addRoot(toolsJar, OrderRootType.CLASSES);
    WriteAction.runAndWait(() -> sdkModificator.commitChanges());

    ProjectInspectionProfileManager profileManager = ProjectInspectionProfileManager.getInstance(myProject);
    InspectionProfileImpl inspectionProfile = profileManager.getCurrentProfile();
    InspectionsKt.runInInitMode(() -> {
      inspectionProfile.initInspectionTools(myProject);
      return null;
    });

    try {
      for (PsiDirectory root : ReadAction.computeBlocking(() -> ProjectRootUtil.getSourceRootDirectories(myProject))) {
        doScan(fixture, root);
      }
    }
    finally {
      profileManager.deleteProfile(inspectionProfile.getName());
    }
  }

  static final int MAX_FILES = Integer.parseInt(
    System.getProperty("JDK_HIGHLIGHTING_MAX_FILES") == null ? "0" : System.getProperty("JDK_HIGHLIGHTING_MAX_FILES"));
  static int nFiles = 0;
  static final String FIRST_FILE = System.getProperty("JDK_HIGHLIGHTING_FIRST_FILE");
  static boolean doTest = FIRST_FILE == null;

  private static void doScan(DaemonAnalyzerFixture fixture, PsiDirectory dir) {
    for (PsiElement child : ReadAction.computeBlocking(() -> dir.getChildren())) {
      if (child instanceof PsiDirectory) {
        doScan(fixture, (PsiDirectory)child);
      }
      else {
        if (++nFiles == MAX_FILES) return;
        PsiFile file = (PsiFile)child;
        final String url = file.getVirtualFile().getPresentableUrl();
        if (url.equals(FIRST_FILE)) doTest = true;
        if (doTest) {
          System.out.println("analyzing file:" + url);
          fixture.doTest(file.getVirtualFile(), false, false);
        }
      }
    }
  }
}
