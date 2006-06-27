/*
 * Copyright 2000-2005 JetBrains s.r.o.
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
package com.intellij.usages;

import com.intellij.ide.SelectInEditorManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.fileEditor.*;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.usageView.UsageInfo;
import com.intellij.usages.rules.*;
import com.intellij.util.IncorrectOperationException;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Dec 17, 2004
 * Time: 2:42:29 PM
 * To change this template use File | Settings | File Templates.
 */
public class UsageInfo2UsageAdapter implements Usage, UsageInModule, UsageInLibrary, UsageInFile, PsiElementUsage, MergeableUsage, Comparable<UsageInfo2UsageAdapter>, RenameableUsage {
  private static final Logger LOG = Logger.getInstance("#com.intellij.usages.UsageInfo2UsageAdapter");

  private final UsageInfo myUsageInfo;
  private int myLineNumber;
  protected Icon myIcon;
  private String myTooltipText;
  private List<RangeMarker> myRangeMarkers = new ArrayList<RangeMarker>();
  private TextChunk[] myTextChunks;
  private UsageInfo2UsageAdapter.MyUsagePresentation myUsagePresentation;

  public UsageInfo2UsageAdapter(final UsageInfo usageInfo) {
    myUsageInfo = usageInfo;
    PsiElement element = getElement();
    PsiFile psiFile = element.getContainingFile();
    Document document = PsiDocumentManager.getInstance(element.getProject()).getDocument(psiFile);

    TextRange range = element.getTextRange();
    int startOffset = range.getStartOffset() + myUsageInfo.startOffset;
    int endOffset = range.getStartOffset() + myUsageInfo.endOffset;

    myLineNumber = document.getLineNumber(startOffset);

    if (endOffset > document.getTextLength()) {
      LOG.assertTrue(false,
        "Invalid usage info, psiElement:" + element + " end offset: " + endOffset + " psiFile: " + psiFile.getName()
      );
    }

    myRangeMarkers.add(document.createRangeMarker(startOffset, endOffset));

    if (element instanceof PsiFile) {
      myIcon = null;
    }
    else {
      myIcon = element.getIcon(0);
    }

    myTooltipText = myUsageInfo.getTooltipText();

    initChunks();
    myUsagePresentation = new MyUsagePresentation();
  }

  private void initChunks() {
    myTextChunks = new ChunkExtractor(getElement(), myRangeMarkers).extractChunks();
  }
  
  public UsagePresentation getPresentation() {
    return myUsagePresentation;
  }

  public boolean isValid() {
    if (getElement() == null) return false;
    for (RangeMarker rangeMarker : myRangeMarkers) {
      if (!rangeMarker.isValid()) return false;
    }
    return true;
  }

  public boolean isReadOnly() {
    return isValid() && !getElement().isWritable();
  }

  public FileEditorLocation getLocation() {
    VirtualFile virtualFile = getFile();
    if (virtualFile == null) return null;
    FileEditor editor = FileEditorManager.getInstance(getProject()).getSelectedEditor(virtualFile);
    if (editor == null) return null;

    return new TextEditorLocation(myUsageInfo.startOffset + getElement().getTextRange().getStartOffset(), (TextEditor)editor);
  }

  public void selectInEditor() {
    if (!isValid()) return;
    Editor editor = openTextEditor(false);
    RangeMarker marker = getRangeMarker();
    editor.getSelectionModel().setSelection(marker.getStartOffset(), marker.getEndOffset());
  }

  public void highlightInEditor() {
    if (!isValid()) return;

    RangeMarker marker = getRangeMarker();
    SelectInEditorManager.getInstance(getProject())
      .selectInEditor(getFile(), marker.getStartOffset(), marker.getEndOffset(), false, false);
  }

  public final RangeMarker getRangeMarker() {
    return myRangeMarkers.get(0);
  }

  public void navigate(boolean focus) {
    if (canNavigate()) {
      openTextEditor(focus);
    }
  }

  private Editor openTextEditor(boolean focus) {
    return FileEditorManager.getInstance(getProject()).openTextEditor(getDescriptor(), focus);
  }

  public boolean canNavigate() {
    return isValid();
  }

  public boolean canNavigateToSource() {
    return canNavigate();
  }

  private OpenFileDescriptor getDescriptor() {
    return isValid() ? new OpenFileDescriptor(getProject(), getFile(), getRangeMarker().getStartOffset()) : null;
  }

  private Project getProject() {
    return getElement().getProject();
  }

  public String toString() {
    TextChunk[] textChunks = getPresentation().getText();
    StringBuffer result = new StringBuffer();
    for (int j = 0; j < textChunks.length; j++) {
      if (j > 0) result.append("|");
      TextChunk textChunk = textChunks[j];
      result.append(textChunk);
    }

    return result.toString();
  }

  public Module getModule() {
    if (!isValid()) return null;
    PsiElement element = getElement();
    VirtualFile virtualFile = getFile();
    if (virtualFile == null) return null;

    ProjectRootManager projectRootManager = ProjectRootManager.getInstance(element.getProject());
    ProjectFileIndex fileIndex = projectRootManager.getFileIndex();
    return fileIndex.getModuleForFile(virtualFile);
  }

  public OrderEntry getLibraryEntry() {
    if (!isValid()) return null;
    PsiElement element = getElement();
    PsiFile psiFile = element.getContainingFile();
    VirtualFile virtualFile = getFile();
    if (virtualFile == null) return null;

    ProjectRootManager projectRootManager = ProjectRootManager.getInstance(element.getProject());
    ProjectFileIndex fileIndex = projectRootManager.getFileIndex();

    if (psiFile instanceof PsiCompiledElement || fileIndex.isInLibrarySource(virtualFile)) {
      List<OrderEntry> orders = fileIndex.getOrderEntriesForFile(psiFile.getVirtualFile());
      for (OrderEntry order : orders) {
        if (order instanceof LibraryOrderEntry || order instanceof JdkOrderEntry) {
          return order;
        }
      }
    }

    return null;
  }

  public VirtualFile getFile() {
    return isValid() ? getElement().getContainingFile().getVirtualFile() : null;
  }

  public int getLine() {
    return myLineNumber;
  }

  public boolean merge(MergeableUsage other) {
    if (!(other instanceof UsageInfo2UsageAdapter)) return false;
    UsageInfo2UsageAdapter u2 = (UsageInfo2UsageAdapter)other;
    if (myLineNumber != u2.myLineNumber || getFile() != u2.getFile()) return false;
    myRangeMarkers.addAll(u2.myRangeMarkers);
    initChunks();
    return true;
  }

  public void reset() {
    if (myRangeMarkers.size() > 1) {
      RangeMarker marker = getRangeMarker();
      myRangeMarkers = new ArrayList<RangeMarker>();
      myRangeMarkers.add(marker);
      initChunks();
    }
  }

  public final PsiElement getElement() {
    return myUsageInfo.getElement();
  }

  public PsiReference getReference() {
    return getElement().getReference();
  }

  public boolean isNonCodeUsage() {
    return myUsageInfo.isNonCodeUsage;
  }

  public UsageInfo getUsageInfo() {
    return myUsageInfo;
  }

  public int compareTo(final UsageInfo2UsageAdapter o) {
    final PsiElement element = getElement();
    final PsiFile containingFile = element == null ? null : element.getContainingFile();
    final PsiElement oElement = o.getElement();
    final PsiFile oContainingFile = oElement == null ? null : oElement.getContainingFile();
    if ((containingFile == null && oContainingFile == null) ||
        !Comparing.equal(containingFile, oContainingFile)) {
      return 0;
    }
    return getRangeMarker().getStartOffset() - o.getRangeMarker().getStartOffset();
  }

  public void rename(String newName) throws IncorrectOperationException {
    final PsiReference reference = myUsageInfo.getReference();
    assert reference != null : this;
    reference.handleElementRename(newName);
  }

  private class MyUsagePresentation implements UsagePresentation {
    private long myModificationStamp;

    public MyUsagePresentation() {
      myModificationStamp = getCurrentModificationStamp();
    }

    private long getCurrentModificationStamp() {
      final PsiFile containingFile = getElement().getContainingFile();
      return containingFile == null? -1L : containingFile.getModificationStamp();
    }

    public TextChunk[] getText() {
      if (isValid()) {
        // the check below makes sence only for valid PsiElement
        final long currentModificationStamp = getCurrentModificationStamp();
        if (currentModificationStamp != myModificationStamp) {
          initChunks();
          myModificationStamp = currentModificationStamp;
        }
      }
      return myTextChunks;
    }

    public Icon getIcon() {
      return myIcon;
    }

    public String getTooltipText() {
      return myTooltipText;
    }
  }

  public static UsageInfo2UsageAdapter[] convert(UsageInfo[] usageInfos) {
    UsageInfo2UsageAdapter[] result = new UsageInfo2UsageAdapter[usageInfos.length];
    for (int i = 0; i < result.length; i++) {
      result[i] = new UsageInfo2UsageAdapter(usageInfos[i]);
    }

    return result;
  }
}
