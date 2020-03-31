// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.usages;

import com.intellij.find.FindManager;
import com.intellij.find.FindModel;
import com.intellij.find.impl.FindResultImpl;
import com.intellij.find.impl.FindResultUsageInfo;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.HeavyPlatformTestCase;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.usageView.UsageInfo;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertNotEquals;

public class UsageInfoCompareTest extends HeavyPlatformTestCase {
  public void testFindResultHasCorrectCompare() throws IOException {
    VirtualFile file = createTempFile("txt", null, "xxxx", StandardCharsets.UTF_8);
    PsiFile psiFile = PlatformTestUtil.notNull(getPsiManager().findFile(file));
    FindResultUsageInfo info1 = new FindResultUsageInfo(FindManager.getInstance(myProject), psiFile, 1, new FindModel(), new FindResultImpl(1, 2));
    FindResultUsageInfo info2 = new FindResultUsageInfo(FindManager.getInstance(myProject), psiFile, 2, new FindModel(), new FindResultImpl(2, 3));
    assertTrue("result: "+info1.compareToByStartOffset(info2),info1.compareToByStartOffset(info2) < 0);
  }

  public void testUsageInfoCompareMustNotReturnZeroForDifferentFiles() throws IOException {
    PsiFile psiFile0 = PlatformTestUtil.notNull(getPsiManager().findFile(createTempFile("txt", null, "xxxx", StandardCharsets.UTF_8)));
    PsiFile psiFile1 = PlatformTestUtil.notNull(getPsiManager().findFile(createTempFile("txt", null, "xxxx", StandardCharsets.UTF_8)));

    assertNotEquals(psiFile0, psiFile1);
    assertNotEquals(psiFile0.getVirtualFile(), psiFile1.getVirtualFile());
    UsageInfo info0 = new UsageInfo(psiFile0);
    UsageInfo info1 = new UsageInfo(psiFile1);
    int cmp = info0.compareToByStartOffset(info1);
    assertNotEquals("result: "+cmp,0, cmp);
  }

  public void testUsageInfoCompareMustNotReturnSomethingSensibleForDifferentPositionsInTheSameFile() throws IOException {
    PsiFile psiFile = PlatformTestUtil.notNull(getPsiManager().findFile(createTempFile("txt", null, "xxxx", StandardCharsets.UTF_8)));

    UsageInfo info0 = new UsageInfo(psiFile, 1, 2);
    UsageInfo info1 = new UsageInfo(psiFile, 3, 3);

    assertTrue("result: " + info0.compareToByStartOffset(info1), info0.compareToByStartOffset(info1) < 0);
    assertTrue("result: " + info1.compareToByStartOffset(info0), info1.compareToByStartOffset(info0) > 0);
    assertTrue("result: " + info0.compareToByStartOffset(info0), info0.compareToByStartOffset(info0) == 0);
  }
}
