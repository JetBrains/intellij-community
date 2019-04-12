// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.find.impl;

import com.intellij.find.FindManager;
import com.intellij.find.FindModel;
import com.intellij.find.impl.FindResultImpl;
import com.intellij.find.impl.FindResultUsageInfo;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.PlatformTestCase;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class FindResultTest extends PlatformTestCase {
  public void testFindResultHasCorrectCompare() throws IOException {
    VirtualFile file = createTempFile("txt", null, "xxxx", StandardCharsets.UTF_8);
    PsiFile psiFile = getPsiManager().findFile(file);
    FindResultUsageInfo
      info1 = new FindResultUsageInfo(FindManager.getInstance(myProject), psiFile, 1, new FindModel(), new FindResultImpl(1, 2));
    FindResultUsageInfo info2 = new FindResultUsageInfo(FindManager.getInstance(myProject), psiFile, 2, new FindModel(), new FindResultImpl(2, 3));
    assertTrue("result: "+info1.compareToByStartOffset(info2),info1.compareToByStartOffset(info2) < 0);
  }
}
