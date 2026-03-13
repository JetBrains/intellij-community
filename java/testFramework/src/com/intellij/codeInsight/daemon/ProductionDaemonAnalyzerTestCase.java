// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon;

import com.intellij.codeInsight.daemon.impl.EditorTracker;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.fixtures.impl.CodeInsightTestFixtureImpl;
import com.intellij.util.ThrowableRunnable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.List;

/**
 * A {@link DaemonAnalyzerTestCase} which uses only production methods of the daemon and waits for its completion via e.g.
 * {@link com.intellij.codeInsight.daemon.impl.TestDaemonCodeAnalyzerImpl#waitForDaemonToFinish} methods
 * and prohibits explicitly manipulating daemon state via e.g. {@link CodeInsightTestFixtureImpl#instantiateAndRun}
 */
public abstract class ProductionDaemonAnalyzerTestCase extends DaemonAnalyzerTestCase {
  @Override
  protected void runTestRunnable(@NotNull ThrowableRunnable<Throwable> testRunnable) throws Throwable {
    ProductionLightDaemonAnalyzerTestCase.runTestInProduction(myDaemonCodeAnalyzer, () -> super.runTestRunnable(testRunnable));
  }
  @Override
  protected final void configureByExistingFile(@NotNull VirtualFile virtualFile) {
    super.configureByExistingFile(virtualFile);
    EditorTracker.getInstance(myProject).setActiveEditorsInTests(Collections.singletonList(getEditor()));
  }

  @Override
  protected final VirtualFile configureByFiles(@Nullable File rawProjectRoot, VirtualFile @NotNull ... vFiles) throws IOException {
    VirtualFile virtualFile = super.configureByFiles(rawProjectRoot, vFiles);
    EditorTracker.getInstance(myProject).setActiveEditorsInTests(List.of(EditorFactory.getInstance().getAllEditors()));
    return virtualFile;
  }
}