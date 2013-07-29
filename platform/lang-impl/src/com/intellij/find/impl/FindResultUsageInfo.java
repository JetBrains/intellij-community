package com.intellij.find.impl;

import com.intellij.find.FindManager;
import com.intellij.find.FindModel;
import com.intellij.find.FindResult;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.util.Segment;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.SmartPointerManager;
import com.intellij.psi.SmartPsiFileRange;
import com.intellij.usageView.UsageInfo;
import org.jetbrains.annotations.NotNull;

public class FindResultUsageInfo extends UsageInfo {
  private FindManager myFindManager;
  private FindModel myFindModel;
  private SmartPsiFileRange myAnchor;

  @Override
  public boolean isValid() {
    if (!super.isValid()) return false;
    Segment segment = super.getSegment();
    if (segment == null) return false;

    VirtualFile file = getPsiFile().getVirtualFile();
    Document document = PsiDocumentManager.getInstance(getProject()).getDocument(getPsiFile());
    if (document == null) return false;

    Segment searchOffset;
    if (myAnchor != null) {
      searchOffset = myAnchor.getRange();
      if (searchOffset == null) return false;
    } else {
      searchOffset = segment;
    }

    int offset = searchOffset.getStartOffset();
    FindResult result;
    do {
      result = myFindManager.findString(document.getCharsSequence(), offset, myFindModel, file);
      offset = result.getEndOffset() == offset ? offset + 1 : result.getEndOffset();
      if (!result.isStringFound()) return false;
    } while (result.getStartOffset() < segment.getStartOffset());

    return segment.getStartOffset() == result.getStartOffset() && segment.getEndOffset() == result.getEndOffset();
  }

  private PsiFile getPsiFile() {
    return (PsiFile)getElement();
  }

  public FindResultUsageInfo(@NotNull FindManager finder, @NotNull PsiFile file, int offset, @NotNull FindModel findModel, @NotNull FindResult result) {
    super(file, result.getStartOffset(), result.getEndOffset());

    myFindManager = finder;
    myFindModel = findModel;

    assert result.isStringFound();

    if (myFindModel.isRegularExpressions()) {
      myAnchor = SmartPointerManager.getInstance(getProject()).createSmartPsiFileRangePointer(file, TextRange.from(offset, 0));
    }

  }
}
