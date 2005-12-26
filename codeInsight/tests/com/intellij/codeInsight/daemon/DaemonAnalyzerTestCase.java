package com.intellij.codeInsight.daemon;

import com.intellij.codeInsight.CodeInsightTestCase;
import com.intellij.codeInsight.daemon.impl.*;
import com.intellij.mock.MockProgressIndicator;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileFilter;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.UsageSearchContext;
import com.intellij.lang.annotation.HighlightSeverity;

import java.util.ArrayList;
import java.util.Collection;

public abstract class DaemonAnalyzerTestCase extends CodeInsightTestCase {
  protected void doTest(String filePath, boolean checkWarnings, boolean checkInfos) throws Exception {
    configureByFile(filePath);
    doDoTest(checkWarnings, checkInfos);
  }
  protected void doTest(String filePath, String projectRoot, boolean checkWarnings, boolean checkInfos) throws Exception {
    configureByFile(filePath, projectRoot);
    doDoTest(checkWarnings, checkInfos);
  }

  protected void doTest(VirtualFile vFile, boolean checkWarnings, boolean checkInfos) throws Exception {
    doTest(new VirtualFile[] { vFile }, checkWarnings, checkInfos );
  }

  protected void doTest(VirtualFile[] vFile, boolean checkWarnings, boolean checkInfos) throws Exception {
    configureByFiles(vFile,null);
    doDoTest(checkWarnings, checkInfos);
  }

  protected Collection<HighlightInfo> doDoTest(boolean checkWarnings, boolean checkInfos) {
    ExpectedHighlightingData data = new ExpectedHighlightingData(myEditor.getDocument(),checkWarnings, checkInfos);

    PsiDocumentManager.getInstance(myProject).commitAllDocuments();
    myFile.getText(); //to load text
    //to initialize caches
    myPsiManager.getCacheManager().getFilesWithWord("XXX", UsageSearchContext.IN_COMMENTS, GlobalSearchScope.allScope(myProject), true);
    VirtualFileFilter javaFilesFilter = new VirtualFileFilter() {
      public boolean accept(VirtualFile file) {
        FileType fileType = FileTypeManager.getInstance().getFileTypeByFile(file);
        return fileType == StdFileTypes.JAVA || fileType == StdFileTypes.CLASS;
      }
    };
    myPsiManager.setAssertOnFileLoadingFilter(javaFilesFilter); // check repository work

    Collection<HighlightInfo> infos = doHighlighting();

    myPsiManager.setAssertOnFileLoadingFilter(VirtualFileFilter.NONE);

    data.checkResult(infos, myEditor.getDocument().getText());
    
    return infos;
  }

  protected Collection<HighlightInfo> doHighlighting() {
    PsiDocumentManager.getInstance(myProject).commitAllDocuments();

    Document document = myEditor.getDocument();
    GeneralHighlightingPass action1 = new GeneralHighlightingPass(myProject, myFile, document, 0, myFile.getTextLength(), false, true);
    action1.doCollectInformation(new MockProgressIndicator());
    Collection<HighlightInfo> highlights1 = action1.getHighlights();

    PostHighlightingPass action2 = new PostHighlightingPass(myProject, myFile, myEditor, 0, myFile.getTextLength(), false);
    action2.doCollectInformation(new MockProgressIndicator());
    Collection<HighlightInfo> highlights2 = action2.getHighlights();
    
    Collection<HighlightInfo> highlights3 = null;
    
    if (doInspections()) {
      LocalInspectionsPass inspectionsPass = new LocalInspectionsPass(myProject, myFile, myEditor.getDocument(), 0, myFile.getTextLength());
      inspectionsPass.doCollectInformation(new MockProgressIndicator());
      highlights3 = inspectionsPass.getHighlights();
    }

    ArrayList<HighlightInfo> list = new ArrayList<HighlightInfo>();
    for (HighlightInfo info : highlights1) {
      list.add(info);
    }

    for (HighlightInfo info : highlights2) {
      list.add(info);
    }
    
    if (highlights3 != null) {
      for (HighlightInfo info : highlights3) {
        list.add(info);
      }
    }

    boolean isToLaunchExternal = true;
    for (HighlightInfo info : list) {
      if (info.getSeverity() == HighlightSeverity.ERROR) {
        isToLaunchExternal = false;
        break;
      }
    }

    if (isToLaunchExternal && doExternalValidation()) {
      ExternalToolPass action3 = new ExternalToolPass(myFile, myEditor, 0, myEditor.getDocument().getTextLength());
      action3.doCollectInformation(new MockProgressIndicator());

      highlights3 = action3.getHighlights();
      for (HighlightInfo info : highlights3) {
        list.add(info);
      }
    }


    return list;
  }

  protected boolean doInspections() {
    return false;
  }

  protected boolean doExternalValidation() {
    return true;
  }
}