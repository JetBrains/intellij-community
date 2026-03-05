// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.smartPointers;

import com.intellij.lang.Language;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.AbstractFileViewProvider;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.IFileElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.util.containers.Interner;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * Captures the identity of a PSI element — its class, {@link IElementType}, and file language —
 * so the element can be relocated after a PSI tree rebuild.
 * <p>
 * Two strategies exist:
 * <ul>
 *   <li>{@link ByType} — identifies an element by class name, element-type index, and file language ID (the common case).</li>
 *   <li>{@link ByAnchor} — delegates to a {@link SmartPointerAnchorProvider} for more resilient
 *       restoration during stub-to-AST switches.</li>
 * </ul>
 * Instances are interned via weak interners to save memory.
 */
public abstract class Identikit {
  private static final Interner<ByType> ourPlainInterner = Interner.createWeakInterner();
  private static final Interner<ByAnchor> ourAnchorInterner = Interner.createWeakInterner();

  /**
   * Restores a PSI element in the given {@code file} whose text range matches
   * [{@code startOffset}, {@code endOffset}).
   *
   * @return the matching element, or {@code null} if it cannot be found
   */
  public abstract @Nullable PsiElement findPsiElement(@NotNull PsiFile file, int startOffset, int endOffset);

  /**
   * Returns the language of the file this identikit targets,
   * or {@code null} if the language plugin has been unloaded.
   */
  public abstract @Nullable Language getFileLanguage();

  /** Returns {@code true} if this identikit represents a {@link PsiFile} (root) element. */
  public abstract boolean isForPsiFile();

  /** Creates a {@link ByType} identikit from a live PSI element and its file language. */
  public static @NotNull ByType fromPsi(@NotNull PsiElement element, @NotNull Language fileLanguage) {
    return fromTypes(element.getClass(), PsiUtilCore.getElementType(element), fileLanguage);
  }

  /**
   * Attempts to create a {@link ByAnchor} identikit by querying registered
   * {@link SmartPointerAnchorProvider}s for an anchor element.
   *
   * @return a pair of the interned {@code ByAnchor} and the anchor element,
   * or {@code null} if no provider applies
   */
  static @Nullable Pair<ByAnchor, PsiElement> withAnchor(@NotNull PsiElement element, @NotNull Language fileLanguage) {
    PsiUtilCore.ensureValid(element);
    if (element.isPhysical()) {
      for (SmartPointerAnchorProvider provider : SmartPointerAnchorProvider.EP_NAME.getExtensionList()) {
        PsiElement anchor = provider.getAnchor(element);
        if (anchor != null && anchor.isPhysical() && provider.restoreElement(anchor) == element) {
          ByAnchor anchorKit = new ByAnchor(fromPsi(element, fileLanguage), fromPsi(anchor, fileLanguage), provider);
          return Pair.create(ourAnchorInterner.intern(anchorKit), anchor);
        }
      }
    }
    return null;
  }

  /**
   * Creates a {@link ByType} identikit from raw class, element type, and file language.
   */
  static @NotNull ByType fromTypes(@NotNull Class<? extends PsiElement> elementClass,
                                   @Nullable IElementType elementType,
                                   @NotNull Language fileLanguage) {
    return ourPlainInterner.intern(new ByType(elementClass, elementType, fileLanguage));
  }

  /**
   * Identifies a PSI element by its class name, {@link IElementType} index, and file language ID.
   * This is the common identification strategy used by smart pointers.
   */
  public static final class ByType extends Identikit {
    private final String myElementClassName;
    private final short myElementTypeId;
    private final String myFileLanguageId;

    private ByType(@NotNull Class<? extends PsiElement> elementClass, @Nullable IElementType elementType, @NotNull Language fileLanguage) {
      myElementClassName = elementClass.getName();
      myElementTypeId = elementType != null ? elementType.getIndex() : -1;
      myFileLanguageId = fileLanguage.getID();
    }

    @Override
    public @Nullable PsiElement findPsiElement(@NotNull PsiFile file, int startOffset, int endOffset) {
      Language fileLanguage = Language.findLanguageByID(myFileLanguageId);
      if (fileLanguage == null) return null;   // plugin has been unloaded
      Language actualLanguage = fileLanguage != Language.ANY ? fileLanguage : file.getViewProvider().getBaseLanguage();
      PsiFile actualLanguagePsi = file.getViewProvider().getPsi(actualLanguage);
      if (actualLanguagePsi == null) {
        return null; // the file has changed its language or dialect, so we can't restore
      }
      return findInside(actualLanguagePsi, startOffset, endOffset);
    }

    /**
     * Finds a matching element within the subtree rooted at {@code element}
     * whose text range is [{@code startOffset}, {@code endOffset}).
     */
    public PsiElement findInside(@NotNull PsiElement element, int startOffset, int endOffset) {
      PsiElement anchor =
        AbstractFileViewProvider.findElementAt(element, startOffset); // finds child in this tree only, unlike PsiElement.findElementAt()
      if (anchor == null && startOffset == element.getTextLength()) {
        anchor = PsiTreeUtil.getDeepestLast(element);
      }
      if (anchor == null) return null;

      PsiElement result = findParent(startOffset, endOffset, anchor);
      if (endOffset == startOffset) {
        while ((result == null || result.getTextRange().getStartOffset() != startOffset) &&
               anchor.getTextRange().getStartOffset() == endOffset) {
          anchor = PsiTreeUtil.prevLeaf(anchor, false);
          if (anchor == null) break;

          result = findParent(startOffset, endOffset, anchor);
        }
      }
      return result;
    }

    private @Nullable PsiElement findParent(int startOffset, int endOffset, @NotNull PsiElement anchor) {
      TextRange range = anchor.getTextRange();

      if (range.getStartOffset() != startOffset) return null;
      while (range.getEndOffset() < endOffset) {
        anchor = anchor.getParent();
        if (anchor == null || anchor instanceof PsiDirectory) {
          return null;
        }
        range = anchor.getTextRange();
      }

      while (range.getEndOffset() == endOffset) {
        if (isAcceptable(anchor)) {
          return anchor;
        }
        anchor = anchor.getParent();
        if (anchor == null || anchor instanceof PsiDirectory) break;
        range = anchor.getTextRange();
      }

      return null;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      ByType type = (ByType)o;
      return myElementTypeId == type.myElementTypeId &&
             Objects.equals(myElementClassName, type.myElementClassName) &&
             Objects.equals(myFileLanguageId, type.myFileLanguageId);
    }

    @Override
    public int hashCode() {
      return Objects.hash(myElementClassName, myElementTypeId, myFileLanguageId);
    }

    @Override
    public String toString() {
      return "Identikit(" +
             "class='" + myElementClassName + '\'' +
             ", elementType=" + (myElementTypeId == -1 ? "-1" : IElementType.find(myElementTypeId)) +
             ", fileLanguage='" + myFileLanguageId + '\'' +
             ')';
    }

    @Override
    public @Nullable Language getFileLanguage() {
      return Language.findLanguageByID(myFileLanguageId);
    }

    @Override
    public boolean isForPsiFile() {
      if (myElementTypeId < 0) return false;
      IElementType elementType = IElementType.find(myElementTypeId);
      return elementType instanceof IFileElementType;
    }

    /** Returns {@code true} if {@code element} matches the stored class name and element-type index. */
    private boolean isAcceptable(@NotNull PsiElement element) {
      IElementType type = PsiUtilCore.getElementType(element);
      return myElementClassName.equals(element.getClass().getName()) &&
             type != null &&
             myElementTypeId == type.getIndex();
    }
  }

  /**
   * Delegates element restoration to a {@link SmartPointerAnchorProvider}, providing more
   * resilient identification during stub-to-AST switches than {@link ByType} alone.
   */
  static final class ByAnchor extends Identikit {
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

    @Override
    public @Nullable PsiElement findPsiElement(@NotNull PsiFile file, int startOffset, int endOffset) {
      PsiElement anchor = myAnchorInfo.findPsiElement(file, startOffset, endOffset);
      PsiElement element = anchor == null ? null : myAnchorProvider.restoreElement(anchor);
      return element != null && myElementInfo.isAcceptable(element) ? element : null;
    }

    @Override
    public @Nullable Language getFileLanguage() {
      return myAnchorInfo.getFileLanguage();
    }

    @Override
    public boolean isForPsiFile() {
      return myAnchorInfo.isForPsiFile();
    }
  }
}
