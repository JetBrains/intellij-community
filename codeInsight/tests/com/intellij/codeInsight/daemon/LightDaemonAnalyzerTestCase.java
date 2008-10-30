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


    //if (doFolding()) {
    //  final CodeFoldingPassFactory factory = getProject().getComponent(CodeFoldingPassFactory.class);
    //  final TextEditorHighlightingPass highlightingPass = factory.createHighlightingPass(myFile, myEditor);
    //  highlightingPass.collectInformation(progress);
    //  highlightingPass.applyInformationToEditor();
    //}

    //GeneralHighlightingPassFactory gpf = getProject().getComponent(GeneralHighlightingPassFactory.class);
    //TextEditorHighlightingPass ghp = gpf.createHighlightingPass(getFile(), getEditor());
    //ghp.collectInformation(progress);
    //Set<HighlightInfo> result = new HashSet<HighlightInfo>(ghp.getHighlights());
    //
    //PostHighlightingPassFactory phpFactory = getProject().getComponent(PostHighlightingPassFactory.class);
    //TextEditorHighlightingPass php = phpFactory.createHighlightingPass(getFile(), getEditor());
    //if (php != null) { //possible in CreateFileFromUsageTest
    //  php.collectInformation(progress);
    //  result.addAll(php.getHighlights());
    //}
    //
    //LocalInspectionsPassFactory lipf = getProject().getComponent(LocalInspectionsPassFactory.class);
    //TextEditorHighlightingPass lip = lipf.createHighlightingPass(getFile(), getEditor());
    //lip.collectInformation(progress);
    //result.addAll(lip.getHighlights());
    //
    //LineMarkersPassFactory lmf = getProject().getComponent(LineMarkersPassFactory.class);
    //TextEditorHighlightingPass markersPass = lmf.createHighlightingPass(getFile(), getEditor());
    //markersPass.collectInformation(progress);
    //
    //SlowLineMarkersPassFactory smf = getProject().getComponent(SlowLineMarkersPassFactory.class);
    //TextEditorHighlightingPass slowPass = smf.createHighlightingPass(getFile(), getEditor());
    //slowPass.collectInformation(progress);
    //
    //ghp.applyInformationToEditor();
    //if (php != null) {
    //  php.applyInformationToEditor();
    //}
    //lip.applyInformationToEditor();
    //markersPass.applyInformationToEditor();
    //slowPass.applyInformationToEditor();

    List<HighlightInfo> infos = DaemonCodeAnalyzerImpl.getHighlights(getEditor().getDocument(), getProject());
    return infos == null ? Collections.<HighlightInfo>emptyList() : infos;
  }

  protected boolean doFolding() {
    return false;
  }
}
