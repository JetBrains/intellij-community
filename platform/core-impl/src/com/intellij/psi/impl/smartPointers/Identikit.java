/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.psi.impl.smartPointers;

import com.google.common.base.MoreObjects;
import com.intellij.lang.Language;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.AbstractFileViewProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.IFileElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.util.containers.WeakInterner;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author peter
 */
public abstract class Identikit {
  private static final WeakInterner<ByType> ourPlainInterner = new WeakInterner<>();
  private static final WeakInterner<ByAnchor> ourAnchorInterner = new WeakInterner<>();

  @Nullable
  public abstract PsiElement findPsiElement(@NotNull PsiFile file, int startOffset, int endOffset);

  @NotNull
  public abstract Language getFileLanguage();

  public abstract boolean isForPsiFile();

  public static ByType fromPsi(@NotNull PsiElement element, @NotNull Language fileLanguage) {
    return fromTypes(element.getClass(), PsiUtilCore.getElementType(element), fileLanguage);
  }

  @Nullable
  static Pair<ByAnchor, PsiElement> withAnchor(@NotNull PsiElement element, @NotNull Language fileLanguage) {
    PsiUtilCore.ensureValid(element);
    if (element.isPhysical()) {
      for (SmartPointerAnchorProvider provider : SmartPointerAnchorProvider.EP_NAME.getExtensions()) {
        PsiElement anchor = provider.getAnchor(element);
        if (anchor != null && anchor.isPhysical() && provider.restoreElement(anchor) == element) {
          ByAnchor anchorKit = new ByAnchor(fromPsi(element, fileLanguage), fromPsi(anchor, fileLanguage), provider);
          return Pair.create(ourAnchorInterner.intern(anchorKit), anchor);
        }
      }
    }
    return null;
  }

  @NotNull
  static ByType fromTypes(@NotNull Class elementClass, @Nullable IElementType elementType, @NotNull Language fileLanguage) {
    return ourPlainInterner.intern(new ByType(elementClass, elementType, fileLanguage));
  }

  public static class ByType extends Identikit {
    private final Class myElementClass;
    private final IElementType myElementType;
    private final Language myFileLanguage;

    private ByType(@NotNull Class elementClass, @Nullable IElementType elementType, @NotNull Language fileLanguage) {
      myElementClass = elementClass;
      myElementType = elementType;
      myFileLanguage = fileLanguage;
    }

    @Nullable
    @Override
    public PsiElement findPsiElement(@NotNull PsiFile file, int startOffset, int endOffset) {
      Language actualLanguage = myFileLanguage != Language.ANY ? myFileLanguage : file.getViewProvider().getBaseLanguage();
      PsiFile actualLanguagePsi = file.getViewProvider().getPsi(actualLanguage);
      return findInside(actualLanguagePsi, startOffset, endOffset);
    }

    public PsiElement findInside(@NotNull PsiElement element, int startOffset, int endOffset) {
      PsiElement anchor = AbstractFileViewProvider.findElementAt(element, startOffset); // finds child in this tree only, unlike PsiElement.findElementAt()
      if (anchor == null && startOffset == element.getTextLength()) {
        PsiElement lastChild = element.getLastChild();
        if (lastChild != null) {
          anchor = PsiTreeUtil.getDeepestLast(lastChild);
        }
      }
      if (anchor == null) return null;

      PsiElement result = findParent(startOffset, endOffset, anchor);
      if (endOffset == startOffset) {
        while ((result == null || result.getTextRange().getStartOffset() != startOffset) && anchor.getTextRange().getStartOffset() == endOffset) {
          anchor = PsiTreeUtil.prevLeaf(anchor, false);
          if (anchor == null) break;

          result = findParent(startOffset, endOffset, anchor);
        }
      }
      return result;

    }

    @Nullable
    private PsiElement findParent(int startOffset, int endOffset, PsiElement anchor) {
      TextRange range = anchor.getTextRange();

      if (range.getStartOffset() != startOffset) return null;
      while (range.getEndOffset() < endOffset) {
        anchor = anchor.getParent();
        if (anchor == null || anchor.getTextRange() == null) {
          return null;
        }
        range = anchor.getTextRange();
      }

      while (range.getEndOffset() == endOffset) {
        if (isAcceptable(anchor)) {
          return anchor;
        }
        anchor = anchor.getParent();
        if (anchor == null || anchor.getTextRange() == null) break;
        range = anchor.getTextRange();
      }

      return null;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (!(o instanceof ByType)) return false;

      ByType info = (ByType)o;
      return myElementType == info.myElementType && myElementClass == info.myElementClass && myFileLanguage == info.myFileLanguage;
    }

    @Override
    public int hashCode() {
      return (myElementType == null ? 0 : myElementType.hashCode() * 31 * 31) +
             31 * myElementClass.getName().hashCode() +
             myFileLanguage.hashCode();
    }

    @Override
    public String toString() {
      return MoreObjects.toStringHelper(this)
        .add("class", myElementClass)
        .add("elementType", myElementType)
        .add("fileLanguage", myFileLanguage)
        .toString();
    }

    @Override
    @NotNull
    public Language getFileLanguage() {
      return myFileLanguage;
    }

    @Override
    public boolean isForPsiFile() {
      return myElementType instanceof IFileElementType;
    }

    private boolean isAcceptable(@NotNull PsiElement element) {
      return myElementClass == element.getClass() && myElementType == PsiUtilCore.getElementType(element);
    }
  }

  static class ByAnchor extends Identikit {
    private final ByType myElementInfo;
    private final ByType myAnchorInfo;
    private final SmartPointerAnchorProvider myAnchorProvider;

    ByAnchor(@NotNull ByType elementInfo, @NotNull ByType anchorInfo, @NotNull SmartPointerAnchorProvider anchorProvider) {
      myElementInfo = elementInfo;
      myAnchorInfo = anchorInfo;
      myAnchorProvider = anchorProvider;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (!(o instanceof ByAnchor)) return false;

      ByAnchor anchor = (ByAnchor)o;

      if (!myElementInfo.equals(anchor.myElementInfo)) return false;
      if (!myAnchorInfo.equals(anchor.myAnchorInfo)) return false;
      if (!myAnchorProvider.equals(anchor.myAnchorProvider)) return false;

      return true;
    }

    @Override
    public int hashCode() {
      return myElementInfo.hashCode();
    }

    @Nullable
    @Override
    public PsiElement findPsiElement(@NotNull PsiFile file, int startOffset, int endOffset) {
      PsiElement anchor = myAnchorInfo.findPsiElement(file, startOffset, endOffset);
      PsiElement element = anchor == null ? null : myAnchorProvider.restoreElement(anchor);
      return element != null && myElementInfo.isAcceptable(element) ? element : null;
    }

    @NotNull
    @Override
    public Language getFileLanguage() {
      return myAnchorInfo.getFileLanguage();
    }

    @Override
    public boolean isForPsiFile() {
      return myAnchorInfo.isForPsiFile();
    }
  }

}
