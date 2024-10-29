// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon;

import com.intellij.codeHighlighting.Pass;
import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerImpl;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.injected.editor.EditorWindow;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.vfs.VirtualFileFilter;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.PsiManagerEx;
import com.intellij.testFramework.ExpectedHighlightingData;
import com.intellij.testFramework.FileTreeAccessFilter;
import com.intellij.testFramework.HighlightTestInfo;
import com.intellij.testFramework.LightJavaCodeInsightTestCase;
import com.intellij.testFramework.fixtures.impl.CodeInsightTestFixtureImpl;
import com.intellij.util.ArrayUtilRt;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;

public abstract class LightDaemonAnalyzerTestCase extends LightJavaCodeInsightTestCase {
  private final FileTreeAccessFilter myJavaFilesFilter = new FileTreeAccessFilter();

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    ((DaemonCodeAnalyzerImpl)DaemonCodeAnalyzer.getInstance(getProject())).prepareForTest();
    DaemonCodeAnalyzerSettings.getInstance().setImportHintEnabled(false);
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      // return default value to avoid unnecessary save
      DaemonCodeAnalyzerSettings.getInstance().setImportHintEnabled(true);
      DaemonCodeAnalyzerImpl daemonCodeAnalyzer = (DaemonCodeAnalyzerImpl)DaemonCodeAnalyzer.getInstance(getProject());
      if (daemonCodeAnalyzer != null) {
        daemonCodeAnalyzer.cleanupAfterTest();
      }
    }
    catch (Throwable e) {
      addSuppressedException(e);
    }
    finally {
      super.tearDown();
    }
  }

  protected void doTest(@NonNls String filePath, boolean checkWarnings, boolean checkInfos) {
    configureByFile(filePath);
    doTestConfiguredFile(checkWarnings, checkInfos, filePath);
  }

  protected void doTest(@NonNls String filePath, boolean checkWarnings, boolean checkWeakWarnings, boolean checkInfos) {
    configureByFile(filePath);
    doTestConfiguredFile(checkWarnings, checkWeakWarnings, checkInfos, filePath);
  }

  protected void doTestConfiguredFile(boolean checkWarnings, boolean checkInfos, @Nullable String filePath) {
    doTestConfiguredFile(checkWarnings, false, checkInfos, filePath);
  }

  protected void doTestConfiguredFile(boolean checkWarnings, boolean checkWeakWarnings, boolean checkInfos, @Nullable String filePath) {
    PsiManagerEx.getInstanceEx(getProject()).setAssertOnFileLoadingFilter(VirtualFileFilter.NONE, getTestRootDisposable());

    ExpectedHighlightingData data = getExpectedHighlightingData(checkWarnings, checkWeakWarnings, checkInfos);
    checkHighlighting(data, composeLocalPath(filePath));
  }

  protected ExpectedHighlightingData getExpectedHighlightingData(boolean checkWarnings, boolean checkWeakWarnings, boolean checkInfos) {
    return new ExpectedHighlightingData(getEditor().getDocument(), checkWarnings, checkWeakWarnings, checkInfos);
  }

  @Nullable
  private String composeLocalPath(@Nullable String filePath) {
    return filePath != null ? getTestDataPath() + "/" + filePath : null;
  }

  private void checkHighlighting(@NotNull ExpectedHighlightingData data, String filePath) {
    data.init();

    PsiDocumentManager.getInstance(getProject()).commitAllDocuments();
    //noinspection ResultOfMethodCallIgnored
    getFile().getText(); //to load text
    myJavaFilesFilter.allowTreeAccessForFile(getVFile());
    PsiManagerEx.getInstanceEx(getProject()).setAssertOnFileLoadingFilter(myJavaFilesFilter, getTestRootDisposable());

    try {
      Collection<HighlightInfo> infos = doHighlighting();

      data.checkResult(getFile(), infos, getEditor().getDocument().getText(), filePath);
    }
    finally {
      PsiManagerEx.getInstanceEx(getProject()).setAssertOnFileLoadingFilter(VirtualFileFilter.NONE, getTestRootDisposable());
    }
  }

  protected HighlightTestInfo doTestFile(@NonNls @NotNull String filePath) {
    return new HighlightTestInfo(getTestRootDisposable(), filePath) {
      @Override
      public HighlightTestInfo doTest() {
        String path = assertOneElement(filePaths);
        configureByFile(path);
        ExpectedHighlightingData data = new JavaExpectedHighlightingData(getEditor().getDocument(), checkWarnings, checkWeakWarnings, checkInfos);
        if (checkSymbolNames) data.checkSymbolNames();

        checkHighlighting(data, composeLocalPath(path));
        return this;
      }
    };
  }

  @NotNull
  protected List<HighlightInfo> highlightErrors() {
    return doHighlighting(HighlightSeverity.ERROR);
  }

  @NotNull
  protected List<HighlightInfo> doHighlighting() {
    PsiDocumentManager.getInstance(getProject()).commitAllDocuments();

    IntList toIgnoreList = new IntArrayList();
    if (!doFolding()) {
      toIgnoreList.add(Pass.UPDATE_FOLDING);
    }
    if (!doInspections()) {
      toIgnoreList.add(Pass.LOCAL_INSPECTIONS);
    }
    int[] toIgnore = toIgnoreList.isEmpty() ? ArrayUtilRt.EMPTY_INT_ARRAY : toIgnoreList.toIntArray();
    Editor editor = getEditor();
    PsiFile file = getFile();
    if (editor instanceof EditorWindow) {
      editor = ((EditorWindow)editor).getDelegate();
      file = InjectedLanguageManager.getInstance(file.getProject()).getTopLevelFile(file);
    }

    return CodeInsightTestFixtureImpl.instantiateAndRun(file, editor, toIgnore, canChangeDocumentDuringHighlighting());
  }

  private boolean canChangeDocumentDuringHighlighting() {
    return annotatedWith(DaemonAnalyzerTestCase.CanChangeDocumentDuringHighlighting.class);
  }

  protected List<HighlightInfo> doHighlighting(HighlightSeverity minSeverity) {
    return DaemonAnalyzerTestCase.filter(doHighlighting(), minSeverity);
  }

  protected boolean doFolding() {
    return false;
  }

  protected boolean doInspections() {
    return true;
  }
}
