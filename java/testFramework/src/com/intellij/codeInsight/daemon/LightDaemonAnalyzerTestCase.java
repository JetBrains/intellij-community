/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.codeInsight.daemon;

import com.intellij.codeHighlighting.Pass;
import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerImpl;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.HighlightInfoType;
import com.intellij.injected.editor.EditorWindow;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.vfs.VirtualFileFilter;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.ExpectedHighlightingData;
import com.intellij.testFramework.FileTreeAccessFilter;
import com.intellij.testFramework.HighlightTestInfo;
import com.intellij.testFramework.LightCodeInsightTestCase;
import com.intellij.testFramework.fixtures.impl.CodeInsightTestFixtureImpl;
import com.intellij.util.ArrayUtil;
import gnu.trove.TIntArrayList;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;

public abstract class LightDaemonAnalyzerTestCase extends LightCodeInsightTestCase {
  private final FileTreeAccessFilter myJavaFilesFilter = new FileTreeAccessFilter();

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    ((DaemonCodeAnalyzerImpl)DaemonCodeAnalyzer.getInstance(getProject())).prepareForTest();
    DaemonCodeAnalyzerSettings.getInstance().setImportHintEnabled(false);
  }

  @Override
  protected void tearDown() throws Exception {
    DaemonCodeAnalyzerSettings.getInstance().setImportHintEnabled(true); // return default value to avoid unnecessary save
    ((DaemonCodeAnalyzerImpl)DaemonCodeAnalyzer.getInstance(getProject())).cleanupAfterTest();
    super.tearDown();
  }

  @Override
  protected void runTest() throws Throwable {
    final Throwable[] throwable = {null};
    CommandProcessor.getInstance().executeCommand(getProject(), new Runnable() {
      @Override
      public void run() {
        try {
          doRunTest();
        }
        catch (Throwable t) {
          throwable[0] = t;
        }
      }
    }, "", null);
    if (throwable[0] != null) {
      throw throwable[0];
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
    getJavaFacade().setAssertOnFileLoadingFilter(VirtualFileFilter.NONE, myTestRootDisposable);

    ExpectedHighlightingData data = new ExpectedHighlightingData(getEditor().getDocument(), checkWarnings, checkWeakWarnings, checkInfos);
    checkHighlighting(data, composeLocalPath(filePath));
  }

  @Nullable
  private String composeLocalPath(@Nullable String filePath) {
    return filePath != null ? getTestDataPath() + "/" + filePath : null;
  }

  private void checkHighlighting(ExpectedHighlightingData data, String filePath) {
    data.init();

    PsiDocumentManager.getInstance(getProject()).commitAllDocuments();
    getFile().getText(); //to load text
    myJavaFilesFilter.allowTreeAccessForFile(getVFile());
    getJavaFacade().setAssertOnFileLoadingFilter(myJavaFilesFilter, myTestRootDisposable); // check repository work

    try {
      Collection<HighlightInfo> infos = doHighlighting();

      data.checkResult(infos, getEditor().getDocument().getText(), filePath);
    }
    finally {
      getJavaFacade().setAssertOnFileLoadingFilter(VirtualFileFilter.NONE, myTestRootDisposable);
    }
  }

  protected HighlightTestInfo doTestFile(@NonNls @NotNull String filePath) {
    return new HighlightTestInfo(getTestRootDisposable(), filePath) {
      @Override
      public HighlightTestInfo doTest() {
        String path = assertOneElement(filePaths);
        configureByFile(path);
        ExpectedHighlightingData data = new JavaExpectedHighlightingData(myEditor.getDocument(), checkWarnings, checkWeakWarnings, checkInfos, myFile);
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

    TIntArrayList toIgnoreList = new TIntArrayList();
    if (!doFolding()) {
      toIgnoreList.add(Pass.UPDATE_FOLDING);
    }
    if (!doInspections()) {
      toIgnoreList.add(Pass.LOCAL_INSPECTIONS);
      toIgnoreList.add(Pass.WHOLE_FILE_LOCAL_INSPECTIONS);
    }
    int[] toIgnore = toIgnoreList.isEmpty() ? ArrayUtil.EMPTY_INT_ARRAY : toIgnoreList.toNativeArray();
    Editor editor = getEditor();
    PsiFile file = getFile();
    if (editor instanceof EditorWindow) {
      editor = ((EditorWindow)editor).getDelegate();
      file = InjectedLanguageManager.getInstance(file.getProject()).getTopLevelFile(file);
    }

    return CodeInsightTestFixtureImpl.instantiateAndRun(file, editor, toIgnore, false);
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
