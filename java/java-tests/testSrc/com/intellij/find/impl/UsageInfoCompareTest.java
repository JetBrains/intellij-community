// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.find.impl;

import com.intellij.find.FindManager;
import com.intellij.find.FindModel;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.HeavyPlatformTestCase;
import com.intellij.usageView.UsageInfo;
import com.intellij.usages.UsageInfo2UsageAdapter;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;
import java.util.function.Function;

import static org.junit.Assert.assertNotEquals;

public class UsageInfoCompareTest extends HeavyPlatformTestCase {
  public void testFindResultHasCorrectCompare() {
    VirtualFile file = getTempDir().createVirtualFile(".txt", "xxxx");
    PsiFile psiFile = Objects.requireNonNull(getPsiManager().findFile(file));
    FindResultUsageInfo info1 = new FindResultUsageInfo(FindManager.getInstance(myProject), psiFile, 1, new FindModel(), new FindResultImpl(1, 2));
    FindResultUsageInfo info2 = new FindResultUsageInfo(FindManager.getInstance(myProject), psiFile, 2, new FindModel(), new FindResultImpl(2, 3));

    assertValue(info1.compareToByStartOffset(info2), v -> v < 0);
    assertValue(ua(info1).compareTo(ua(info2)), v -> v < 0);
  }

  public void testUsageInfoCompareMustNotReturnZeroForDifferentFiles() {
    PsiFile psiFile0 = Objects.requireNonNull(getPsiManager().findFile(getTempDir().createVirtualFile(".txt", "xxxx")));
    PsiFile psiFile1 = Objects.requireNonNull(getPsiManager().findFile(getTempDir().createVirtualFile(".txt", "xxxx")));

    assertNotEquals(psiFile0, psiFile1);
    assertNotEquals(psiFile0.getVirtualFile(), psiFile1.getVirtualFile());
    UsageInfo info0 = new UsageInfo(psiFile0);
    UsageInfo info1 = new UsageInfo(psiFile1);

    assertValue(info0.compareToByStartOffset(info1), v -> v != 0);
    assertValue(ua(info0).compareTo(ua(info1)), v -> v != 0);
  }

  public void testUsageInfoCompareMustNotReturnSomethingSensibleForDifferentPositionsInTheSameFile() {
    PsiFile psiFile = Objects.requireNonNull(getPsiManager().findFile(getTempDir().createVirtualFile(".txt", "xxxx")));

    UsageInfo info0 = new UsageInfo(psiFile, 1, 2);
    UsageInfo info1 = new UsageInfo(psiFile, 3, 3);

    // UsageInfo
    assertValue(info0.compareToByStartOffset(info1), v -> v < 0);
    assertValue(info1.compareToByStartOffset(info0), v -> v > 0);
    assertValue(info0.compareToByStartOffset(info0), v -> v == 0);

    // Adapter
    assertValue(ua(info0).compareTo(ua(info1)), v -> v < 0);
    assertValue(ua(info1).compareTo(ua(info0)), v -> v > 0);
    assertValue(ua(info0).compareTo(ua(info0)), v -> v == 0);
  }

  private static void assertValue(int value, Function<Integer, Boolean> condition) {
    assertTrue("result: " + value, condition.apply(value));
  }

  private static UsageInfo2UsageAdapter ua(@NotNull UsageInfo usageInfo) {
    return new UsageInfo2UsageAdapter(usageInfo);
  }
}
