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
package com.intellij.usageView;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import org.jetbrains.annotations.Nullable;

public class UsageInfo {
  public static final UsageInfo[] EMPTY_ARRAY = new UsageInfo[0];
  private static final Logger LOG = Logger.getInstance("#com.intellij.usageView.UsageInfo");
  private SmartPsiElementPointer mySmartPointer;
  public final int startOffset; // in navigation element
  public final int endOffset; // in navigation element

  public final boolean isNonCodeUsage;

  public UsageInfo(PsiElement element, int startOffset, int endOffset, boolean isNonCodeUsage) {
    LOG.assertTrue(element.isValid());
    LOG.assertTrue(element == element.getNavigationElement());
    mySmartPointer = SmartPointerManager.getInstance(element.getProject()).createSmartPsiElementPointer(element);
    this.startOffset = startOffset;
    this.endOffset = endOffset;
    this.isNonCodeUsage = isNonCodeUsage;
  }

  public UsageInfo(PsiElement element, boolean isNonCodeUsage) {
    LOG.assertTrue(element.isValid());
    element = element.getNavigationElement();
    mySmartPointer = SmartPointerManager.getInstance(element.getProject()).createSmartPsiElementPointer(element);

    TextRange range = element.getTextRange();
    if (range == null) {
      LOG.assertTrue(false, "text range null for " + element);
    }
    this.startOffset = element.getTextOffset() - range.getStartOffset();
    this.endOffset = range.getEndOffset() - range.getStartOffset();

    this.isNonCodeUsage = isNonCodeUsage;
  }

  public UsageInfo(PsiElement element, int startOffset, int endOffset) {
    this(element, startOffset, endOffset, false);
  }

  public UsageInfo(PsiReference reference) {
    this(reference.getElement(), reference.getRangeInElement().getStartOffset(), reference.getRangeInElement().getEndOffset());
  }

  public UsageInfo(PsiJavaCodeReferenceElement reference) {
    this((PsiElement)reference);
  }

  public UsageInfo(PsiElement element) {
    this(element, false);
  }

  @Nullable
  public PsiElement getElement() { // SmartPointer is used to fix SCR #4572, hotya eto krivo i nado vse perepisat'
    return mySmartPointer.getElement();
  }

  @Nullable
  public PsiReference getReference() {
    return getElement().getReference();
  }

  public TextRange getRange() {
    return new TextRange(startOffset, endOffset);
  }

  /**
   * Override this method if you want a tooltip to be displayed for this usage
   */
  public String getTooltipText () {
    return null;
  }

  public final void navigateTo(boolean requestFocus) {
    VirtualFile file = getElement().getContainingFile().getVirtualFile();
    TextRange range = getElement().getTextRange();
    int offset = range.getStartOffset() + startOffset;
    Project project = getElement().getProject();
    FileEditorManager.getInstance(project).openTextEditor(new OpenFileDescriptor(project, file, offset),
                                                          requestFocus);
  }

  public final boolean isWritable() {
    PsiElement element = getElement();
    return element == null || element.isWritable();
  }

  public boolean equals(Object o) {
    if (this == o) return true;
    if (!getClass().equals(o.getClass())) return false;

    final UsageInfo usageInfo = (UsageInfo)o;

    if (endOffset != usageInfo.endOffset) return false;
    if (isNonCodeUsage != usageInfo.isNonCodeUsage) return false;
    if (startOffset != usageInfo.startOffset) return false;
    PsiElement thisElement = mySmartPointer.getElement();
    PsiElement thatElement = usageInfo.mySmartPointer.getElement();
    return Comparing.equal(thisElement, thatElement);
  }

  public int hashCode() {
    int result;
    result = (mySmartPointer != null ? mySmartPointer.hashCode() : 0);
    result = 29 * result + startOffset;
    result = 29 * result + endOffset;
    result = 29 * result + (isNonCodeUsage ? 1 : 0);
    return result;
  }
}
