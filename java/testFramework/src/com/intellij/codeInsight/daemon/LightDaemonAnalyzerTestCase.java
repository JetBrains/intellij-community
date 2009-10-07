package com.intellij.codeInsight.daemon;

import com.intellij.codeHighlighting.Pass;
import com.intellij.codeHighlighting.TextEditorHighlightingPass;
import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerImpl;
import com.intellij.codeInsight.daemon.impl.DaemonProgressIndicator;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.TextEditorHighlightingPassRegistrarEx;
import com.intellij.injected.editor.EditorWindow;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.vfs.VirtualFileFilter;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil;
import com.intellij.testFramework.ExpectedHighlightingData;
import com.intellij.testFramework.FileTreeAccessFilter;
import com.intellij.testFramework.LightCodeInsightTestCase;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public abstract class LightDaemonAnalyzerTestCase extends LightCodeInsightTestCase {
  private final FileTreeAccessFilter myJavaFilesFilter = new FileTreeAccessFilter();

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    DaemonCodeAnalyzer.getInstance(getProject()).projectOpened();
  }

  @Override
  protected void tearDown() throws Exception {
    DaemonCodeAnalyzer.getInstance(getProject()).projectClosed();

    super.tearDown();
  }

  protected void doTest(@NonNls String filePath, boolean checkWarnings, boolean checkInfos) throws Exception {
    configureByFile(filePath);
    doTestConfiguredFile(checkWarnings, checkInfos);
  }

  protected void doTestConfiguredFile(boolean checkWarnings, boolean checkInfos) {
    getJavaFacade().setAssertOnFileLoadingFilter(VirtualFileFilter.NONE);

    ExpectedHighlightingData expectedData = new ExpectedHighlightingData(getEditor().getDocument(),checkWarnings, checkInfos);

    PsiDocumentManager.getInstance(getProject()).commitAllDocuments();
    getFile().getText(); //to load text
    myJavaFilesFilter.allowTreeAccessForFile(getVFile());
    getJavaFacade().setAssertOnFileLoadingFilter(myJavaFilesFilter); // check repository work

    Collection<HighlightInfo> infos = doHighlighting();

    getJavaFacade().setAssertOnFileLoadingFilter(VirtualFileFilter.NONE);

    expectedData.checkResult(infos, getEditor().getDocument().getText());
  }

  @NotNull
  protected List<HighlightInfo> doHighlighting() {
    PsiDocumentManager.getInstance(getProject()).commitAllDocuments();

    ProgressIndicator progress = new DaemonProgressIndicator();

    int[] toIgnore = doFolding() ? ArrayUtil.EMPTY_INT_ARRAY : new int[]{Pass.UPDATE_FOLDING};
    Editor editor = getEditor();
    PsiFile file = getFile();
    if (editor instanceof EditorWindow) {
      editor = ((EditorWindow)editor).getDelegate();
      file = InjectedLanguageUtil.getTopLevelFile(file);
    }
    TextEditorHighlightingPassRegistrarEx registrar = TextEditorHighlightingPassRegistrarEx.getInstanceEx(getProject());
    List<TextEditorHighlightingPass> passes = registrar.instantiatePasses(file, editor, toIgnore);

    for (TextEditorHighlightingPass pass : passes) {
      pass.collectInformation(progress);
    }
    for (TextEditorHighlightingPass pass : passes) {
      pass.applyInformationToEditor();
    }

    List<HighlightInfo> infos = DaemonCodeAnalyzerImpl.getHighlights(editor.getDocument(), getProject());
    return infos == null ? Collections.<HighlightInfo>emptyList() : new ArrayList<HighlightInfo>(infos);
  }

  protected boolean doFolding() {
    return false;
  }
}
