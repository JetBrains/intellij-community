// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.find.impl;

import com.intellij.find.FindManager;
import com.intellij.find.FindModel;
import com.intellij.find.impl.FindResultImpl;
import com.intellij.find.impl.FindResultUsageInfo;
import com.intellij.ide.impl.OpenProjectTask;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.usageView.UsageInfo;
import com.intellij.usages.UsageInfo2UsageAdapter;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.function.Function;

import static com.intellij.testFramework.junit5.fixture.FixturesKt.projectFixture;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.tempPathFixture;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;


@TestApplication
public class UsageInfoCompareTest {
  @SuppressWarnings("deprecation")
  private final TestFixture<Project> projectFixture = projectFixture(tempPathFixture(), OpenProjectTask.build(), true);

  @TempDir
  Path myTempDir;
  private int myFileCounter;

  @Test
  public void testFindResultHasCorrectCompare() throws IOException {
    Project project = projectFixture.get();
    VirtualFile file = createVirtualFile();
    ReadAction.runBlocking(() -> {
      PsiFile psiFile = Objects.requireNonNull(PsiManager.getInstance(project).findFile(file));
      FindResultUsageInfo info1 = new FindResultUsageInfo(FindManager.getInstance(project), psiFile, 1, new FindModel(), new FindResultImpl(1, 2));
      FindResultUsageInfo info2 = new FindResultUsageInfo(FindManager.getInstance(project), psiFile, 2, new FindModel(), new FindResultImpl(2, 3));

      assertValue(info1.compareToByStartOffset(info2), v -> v < 0);
      assertValue(ua(info1).compareTo(ua(info2)), v -> v < 0);
    });
  }

  @Test
  public void testUsageInfoCompareMustNotReturnZeroForDifferentFiles() throws IOException {
    PsiManager psiManager = PsiManager.getInstance(projectFixture.get());
    VirtualFile file0 = createVirtualFile();
    VirtualFile file1 = createVirtualFile();
    ReadAction.runBlocking(() -> {
      PsiFile psiFile0 = Objects.requireNonNull(psiManager.findFile(file0));
      PsiFile psiFile1 = Objects.requireNonNull(psiManager.findFile(file1));

      assertNotEquals(psiFile0, psiFile1);
      assertNotEquals(psiFile0.getVirtualFile(), psiFile1.getVirtualFile());
      UsageInfo info0 = new UsageInfo(psiFile0);
      UsageInfo info1 = new UsageInfo(psiFile1);

      assertValue(info0.compareToByStartOffset(info1), v -> v != 0);
      assertValue(ua(info0).compareTo(ua(info1)), v -> v != 0);
    });
  }

  @Test
  public void testUsageInfoCompareMustNotReturnSomethingSensibleForDifferentPositionsInTheSameFile() throws IOException {
    PsiManager psiManager = PsiManager.getInstance(projectFixture.get());
    VirtualFile file = createVirtualFile();
    ReadAction.runBlocking(() -> {
      PsiFile psiFile = Objects.requireNonNull(psiManager.findFile(file));

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
    });
  }

  /**
   * Creates a new file with the text "xxxx" in the temporary directory of the test.
   */
  private VirtualFile createVirtualFile() throws IOException {
    Path file = Files.writeString(myTempDir.resolve("file" + myFileCounter++ + ".txt"), "xxxx");
    return Objects.requireNonNull(LocalFileSystem.getInstance().refreshAndFindFileByNioFile(file));
  }

  private static void assertValue(int value, Function<Integer, Boolean> condition) {
    assertTrue(condition.apply(value), "result: " + value);
  }

  private static UsageInfo2UsageAdapter ua(@NotNull UsageInfo usageInfo) {
    return new UsageInfo2UsageAdapter(usageInfo);
  }
}
