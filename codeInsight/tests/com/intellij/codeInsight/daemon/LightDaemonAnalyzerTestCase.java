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
import com.intellij.psi.impl.PsiManagerImpl;
import com.intellij.testFramework.ExpectedHighlightingData;
import com.intellij.testFramework.LightCodeInsightTestCase;
import org.jetbrains.annotations.NonNls;

import java.util.Collection;
import java.util.HashSet;

public abstract class LightDaemonAnalyzerTestCase extends LightCodeInsightTestCase {
  protected void doTest(@NonNls String filePath, boolean checkWarnings, boolean checkInfos) throws Exception {
    configureByFile(filePath);
    doTestConfiguredFile(checkWarnings, checkInfos);
  }

  protected void doTestConfiguredFile(boolean checkWarnings, boolean checkInfos) {
    ((PsiManagerImpl) getPsiManager()).setAssertOnFileLoadingFilter(VirtualFileFilter.NONE);

    ExpectedHighlightingData expectedData = new ExpectedHighlightingData(getEditor().getDocument(),checkWarnings, checkInfos);

    PsiDocumentManager.getInstance(getProject()).commitAllDocuments();
    getFile().getText(); //to load text
    VirtualFileFilter javaFilesFilter = new VirtualFileFilter() {
      public boolean accept(VirtualFile file) {
        FileType fileType = FileTypeManager.getInstance().getFileTypeByFile(file);
        return fileType == StdFileTypes.JAVA || fileType == StdFileTypes.CLASS;
      }
    };
    ((PsiManagerImpl) getPsiManager()).setAssertOnFileLoadingFilter(javaFilesFilter); // check repository work

    Collection<HighlightInfo> infos = doHighlighting();

    ((PsiManagerImpl) getPsiManager()).setAssertOnFileLoadingFilter(VirtualFileFilter.NONE);

    expectedData.checkResult(infos, getEditor().getDocument().getText());
  }


  protected Collection<HighlightInfo> doHighlighting() {
    PsiDocumentManager.getInstance(getProject()).commitAllDocuments();

    Document document = getEditor().getDocument();
    if (doFolding()) {
      final CodeFoldingPassFactory factory = getProject().getComponent(CodeFoldingPassFactory.class);
      final TextEditorHighlightingPass highlightingPass = factory.createHighlightingPass(myFile, myEditor);
      highlightingPass.collectInformation(new MockProgressIndicator());
      highlightingPass.doApplyInformationToEditor();
    }

    GeneralHighlightingPass action1 = new GeneralHighlightingPass(getProject(), getFile(), document, 0, getFile().getTextLength(), true);
    action1.collectInformation(new MockProgressIndicator());
    action1.applyInformationToEditor();
    Collection<HighlightInfo> highlights1 = action1.getHighlights();

    PostHighlightingPass action2 = new PostHighlightingPass(getProject(), getFile(), getEditor(), 0, getFile().getTextLength());
    action2.doCollectInformation(new MockProgressIndicator());
    Collection<HighlightInfo> highlights2 = action2.getHighlights();

    LocalInspectionsPass action3 = new LocalInspectionsPass(getFile(), document, 0, getFile().getTextLength());
    action3.doCollectInformation(new MockProgressIndicator());
    Collection<HighlightInfo> highlights3 = action3.getHighlights();

    HashSet<HighlightInfo> result = new HashSet<HighlightInfo>(highlights1);
    result.addAll(highlights2);
    result.addAll(highlights3);
    return result;
  }

  protected boolean doFolding() {
    return false;
  }
}