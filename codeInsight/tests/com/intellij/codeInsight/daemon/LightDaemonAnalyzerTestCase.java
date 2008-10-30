package com.intellij.codeInsight.daemon;

import com.intellij.codeHighlighting.Pass;
import com.intellij.codeHighlighting.TextEditorHighlightingPass;
import com.intellij.codeInsight.daemon.impl.*;
import com.intellij.mock.MockProgressIndicator;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileFilter;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.testFramework.ExpectedHighlightingData;
import com.intellij.testFramework.LightCodeInsightTestCase;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public abstract class LightDaemonAnalyzerTestCase extends LightCodeInsightTestCase {
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
    VirtualFileFilter javaFilesFilter = new VirtualFileFilter() {
      public boolean accept(VirtualFile file) {
        FileType fileType = FileTypeManager.getInstance().getFileTypeByFile(file);
        return fileType == StdFileTypes.JAVA || fileType == StdFileTypes.CLASS;
      }
    };
    getJavaFacade().setAssertOnFileLoadingFilter(javaFilesFilter); // check repository work

    Collection<HighlightInfo> infos = doHighlighting();

    getJavaFacade().setAssertOnFileLoadingFilter(VirtualFileFilter.NONE);

    expectedData.checkResult(infos, getEditor().getDocument().getText());
  }

  @NotNull
  protected Collection<HighlightInfo> doHighlighting() {
    PsiDocumentManager.getInstance(getProject()).commitAllDocuments();

    MockProgressIndicator progress = new MockProgressIndicator();

    int[] toIgnore = doFolding() ? new int[0] : new int[]{Pass.UPDATE_FOLDING};
    List<TextEditorHighlightingPass> passes = TextEditorHighlightingPassRegistrarEx.getInstanceEx(getProject()).instantiatePasses(getFile(), getEditor(), toIgnore);

    for (TextEditorHighlightingPass pass : passes) {
      pass.collectInformation(progress);
    }
    for (TextEditorHighlightingPass pass : passes) {
      pass.applyInformationToEditor();
    }

    List<HighlightInfo> infos = DaemonCodeAnalyzerImpl.getHighlights(getEditor().getDocument(), getProject());
    return infos == null ? Collections.<HighlightInfo>emptyList() : infos;
  }

  protected boolean doFolding() {
    return false;
  }
}
