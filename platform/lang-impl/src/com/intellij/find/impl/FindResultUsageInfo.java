package com.intellij.find.impl;

import com.intellij.find.FindManager;
import com.intellij.find.FindModel;
import com.intellij.find.FindResult;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.util.Key;
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

  private Boolean myCachedResult;
  private long myTimestamp = 0;

  private static Key<Long> ourDocumentTimestampKey = Key.create("com.intellij.find.impl.FindResultUsageInfo.documentTimestamp");

  @Override
  public boolean isValid() {
    if (!super.isValid()) return false;

    Document document = PsiDocumentManager.getInstance(getProject()).getDocument(getPsiFile());
    if (document == null) {
      myCachedResult = null;
      return false;
    }

    if (document.getModificationStamp() == myTimestamp && myCachedResult != null) {
      return myCachedResult;
    } else {
      myTimestamp = document.getModificationStamp();
    }

    Segment segment = super.getSegment();
    if (segment == null) return myCachedResult = false;

    VirtualFile file = getPsiFile().getVirtualFile();

    Segment searchOffset;
    if (myAnchor != null) {
      searchOffset = myAnchor.getRange();
      if (searchOffset == null) return myCachedResult = false;
    } else {
      searchOffset = segment;
    }

    int offset = searchOffset.getStartOffset();
    FindResult result;
    Long data = myFindModel.getUserData(ourDocumentTimestampKey);
    if (data == null || data != myTimestamp) {
      data = myTimestamp;
      myFindModel.putUserData(FindManagerImpl.ourCommentsLiteralsSearchDataKey, null);
    }
    myFindModel.putUserData(ourDocumentTimestampKey, data);
    do {
      result = myFindManager.findString(document.getCharsSequence(), offset, myFindModel, file);
      offset = result.getEndOffset() == offset ? offset + 1 : result.getEndOffset();
      if (!result.isStringFound()) return myCachedResult = false;
    } while (result.getStartOffset() < segment.getStartOffset());

    return myCachedResult = (segment.getStartOffset() == result.getStartOffset() && segment.getEndOffset() == result.getEndOffset());
  }

  private PsiFile getPsiFile() {
    return (PsiFile)getElement();
  }

  public FindResultUsageInfo(@NotNull FindManager finder, @NotNull PsiFile file, int offset, @NotNull FindModel findModel, @NotNull FindResult result) {
    super(file, result.getStartOffset(), result.getEndOffset());

    myFindManager = finder;
    myFindModel = findModel;

    assert result.isStringFound();

    if (myFindModel.isRegularExpressions() || myFindModel.isInCommentsOnly() || myFindModel.isInStringLiteralsOnly()) {
      myAnchor = SmartPointerManager.getInstance(getProject()).createSmartPsiFileRangePointer(file, TextRange.from(offset, 0));
    }

  }
}
