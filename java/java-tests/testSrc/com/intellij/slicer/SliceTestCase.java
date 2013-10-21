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
package com.intellij.slicer;

import com.intellij.codeInsight.daemon.DaemonAnalyzerTestCase;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkModificator;
import com.intellij.openapi.roots.AnnotationOrderRootType;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.PlatformTestUtil;

public abstract class SliceTestCase extends DaemonAnalyzerTestCase {
  @Override
  protected Sdk getTestProjectJdk() {
    Sdk sdk = IdeaTestUtil.getMockJdk17();
    // add JDK annotations
    SdkModificator sdkModificator = sdk.getSdkModificator();
    VirtualFile root = LocalFileSystem.getInstance().findFileByPath(
      FileUtil.toSystemIndependentName(PlatformTestUtil.getCommunityPath()) + "/java/jdkAnnotations");
    assertNotNull(root);
    sdkModificator.addRoot(root, AnnotationOrderRootType.getInstance());
    sdkModificator.commitChanges();

    return sdk;
  }
}
