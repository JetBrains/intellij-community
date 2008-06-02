package com.intellij.codeInsight.daemon;

import com.intellij.codeHighlighting.TextEditorHighlightingPass;
import com.intellij.codeInsight.daemon.impl.*;
import com.intellij.mock.MockProgressIndicator;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileFilter;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.testFramework.ExpectedHighlightingData;
import com.intellij.testFramework.LightCodeInsightTestCase;
import org.jetbrains.annotations.NonNls;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

public abstract class LightDaemonAnalyzerTestCase extends LightCodeInsightTestCase {
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


  protected Collection<HighlightInfo> doHighlighting() {
    PsiDocumentManager.getInstance(getProject()).commitAllDocuments();

    Document document = getEditor().getDocument();
    MockProgressIndicator progress = new MockProgressIndicator();
    if (doFolding()) {
      final CodeFoldingPassFactory factory = getProject().getComponent(CodeFoldingPassFactory.class);
      final TextEditorHighlightingPass highlightingPass = factory.createHighlightingPass(myFile, myEditor);
      highlightingPass.collectInformation(progress);
      highlightingPass.doApplyInformationToEditor();
    }

    GeneralHighlightingPass action1 = new GeneralHighlightingPass(getProject(), getFile(), document, 0, getFile().getTextLength(), true);
    action1.collectInformation(progress);
    action1.applyInformationToEditor();
    Set<HighlightInfo> result = new HashSet<HighlightInfo>(action1.getHighlights());

    PostHighlightingPassFactory phpFactory = getProject().getComponent(PostHighlightingPassFactory.class);
    if (phpFactory != null) {
      PostHighlightingPass action2 = new PostHighlightingPass(getProject(), getFile(), getEditor(), 0, getFile().getTextLength());
      action2.doCollectInformation(progress);
      result.addAll(action2.getHighlights());
    }

    LocalInspectionsPass action3 = new LocalInspectionsPass(getFile(), document, 0, getFile().getTextLength());
    action3.doCollectInformation(progress);
    result.addAll(action3.getHighlights());

    return result;
  }

  protected boolean doFolding() {
    return false;
  }
}