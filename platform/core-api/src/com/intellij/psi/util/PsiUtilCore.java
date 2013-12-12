/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.psi.util;

import com.intellij.lang.ASTNode;
import com.intellij.lang.Language;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.meta.PsiMetaData;
import com.intellij.psi.meta.PsiMetaOwner;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.SearchScope;
import com.intellij.util.TimeoutUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Collection;

/**
 * @author yole
 */
public class PsiUtilCore {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.util.PsiUtilCore");
  public static final PsiElement NULL_PSI_ELEMENT = new PsiElement() {
    @Override
    @NotNull
    public Project getProject() {
      throw createException();
    }

    @Override
    @NotNull
    public Language getLanguage() {
      throw createException();
    }

    @Override
    public PsiManager getManager() {
      throw createException();
    }

    @Override
    @NotNull
    public PsiElement[] getChildren() {
      throw createException();
    }

    @Override
    public PsiElement getParent() {
      throw createException();
    }

    @Override
    @Nullable
    public PsiElement getFirstChild() {
      throw createException();
    }

    @Override
    @Nullable
    public PsiElement getLastChild() {
      throw createException();
    }

    @Override
    @Nullable
    public PsiElement getNextSibling() {
      throw createException();
    }

    @Override
    @Nullable
    public PsiElement getPrevSibling() {
      throw createException();
    }

    @Override
    public PsiFile getContainingFile() {
      throw createException();
    }

    @Override
    public TextRange getTextRange() {
      throw createException();
    }

    @Override
    public int getStartOffsetInParent() {
      throw createException();
    }

    @Override
    public int getTextLength() {
      throw createException();
    }

    @Override
    public PsiElement findElementAt(int offset) {
      throw createException();
    }

    @Override
    @Nullable
    public PsiReference findReferenceAt(int offset) {
      throw createException();
    }

    @Override
    public int getTextOffset() {
      throw createException();
    }

    @Override
    public String getText() {
      throw createException();
    }

    @Override
    @NotNull
    public char[] textToCharArray() {
      throw createException();
    }

    @Override
    public PsiElement getNavigationElement() {
      throw createException();
    }

    @Override
    public PsiElement getOriginalElement() {
      throw createException();
    }

    @Override
    public boolean textMatches(@NotNull CharSequence text) {
      throw createException();
    }

    @Override
    public boolean textMatches(@NotNull PsiElement element) {
      throw createException();
    }

    @Override
    public boolean textContains(char c) {
      throw createException();
    }

    @Override
    public void accept(@NotNull PsiElementVisitor visitor) {
      throw createException();
    }

    @Override
    public void acceptChildren(@NotNull PsiElementVisitor visitor) {
      throw createException();
    }

    @Override
    public PsiElement copy() {
      throw createException();
    }

    @Override
    public PsiElement add(@NotNull PsiElement element) {
      throw createException();
    }

    @Override
    public PsiElement addBefore(@NotNull PsiElement element, PsiElement anchor) {
      throw createException();
    }

    @Override
    public PsiElement addAfter(@NotNull PsiElement element, PsiElement anchor) {
      throw createException();
    }

    @Override
    public void checkAdd(@NotNull PsiElement element) {
      throw createException();
    }

    @Override
    public PsiElement addRange(PsiElement first, PsiElement last) {
      throw createException();
    }

    @Override
    public PsiElement addRangeBefore(@NotNull PsiElement first, @NotNull PsiElement last, PsiElement anchor) {
      throw createException();
    }

    @Override
    public PsiElement addRangeAfter(PsiElement first, PsiElement last, PsiElement anchor) {
      throw createException();
    }

    @Override
    public void delete() {
      throw createException();
    }

    @Override
    public void checkDelete() {
      throw createException();
    }

    @Override
    public void deleteChildRange(PsiElement first, PsiElement last) {
      throw createException();
    }

    @Override
    public PsiElement replace(@NotNull PsiElement newElement) {
      throw createException();
    }

    @Override
    public boolean isValid() {
      throw createException();
    }

    @Override
    public boolean isWritable() {
      throw createException();
    }

    private PsiInvalidElementAccessException createException() {
      return new PsiInvalidElementAccessException(this, "NULL_PSI_ELEMENT", null);
    }

    @Override
    @Nullable
    public PsiReference getReference() {
      throw createException();
    }

    @Override
    @NotNull
    public PsiReference[] getReferences() {
      throw createException();
    }

    @Override
    public <T> T getCopyableUserData(Key<T> key) {
      throw createException();
    }

    @Override
    public <T> void putCopyableUserData(Key<T> key, T value) {
      throw createException();
    }

    @Override
    public boolean processDeclarations(@NotNull PsiScopeProcessor processor,
                                       @NotNull ResolveState state,
                                       PsiElement lastParent,
                                       @NotNull PsiElement place) {
      throw createException();
    }

    @Override
    public PsiElement getContext() {
      throw createException();
    }

    @Override
    public boolean isPhysical() {
      throw createException();
    }

    @Override
    @NotNull
    public GlobalSearchScope getResolveScope() {
      throw createException();
    }

    @Override
    @NotNull
    public SearchScope getUseScope() {
      throw createException();
    }

    @Override
    public ASTNode getNode() {
      throw createException();
    }

    @Override
    public <T> T getUserData(@NotNull Key<T> key) {
      throw createException();
    }

    @Override
    public <T> void putUserData(@NotNull Key<T> key, T value) {
      throw createException();
    }

    @Override
    public Icon getIcon(int flags) {
      throw createException();
    }

    @Override
    public boolean isEquivalentTo(final PsiElement another) {
      return this == another;
    }

    @Override
    public String toString() {
      return "NULL_PSI_ELEMENT";
    }
  };

  @NotNull
  public static PsiElement[] toPsiElementArray(@NotNull Collection<? extends PsiElement> collection) {
    if (collection.isEmpty()) return PsiElement.EMPTY_ARRAY;
    //noinspection SSBasedInspection
    return collection.toArray(new PsiElement[collection.size()]);
  }

  public static Language getNotAnyLanguage(ASTNode node) {
    if (node == null) return Language.ANY;

    final Language lang = node.getElementType().getLanguage();
    return lang == Language.ANY ? getNotAnyLanguage(node.getTreeParent()) : lang;
  }

  @Nullable
  public static VirtualFile getVirtualFile(@Nullable PsiElement element) {
    if (element == null || !element.isValid()) {
      return null;
    }

    if (element instanceof PsiFileSystemItem) {
      return ((PsiFileSystemItem)element).getVirtualFile();
    }

    final PsiFile containingFile = element.getContainingFile();
    if (containingFile == null) {
      return null;
    }

    return containingFile.getVirtualFile();
  }

  public static int compareElementsByPosition(final PsiElement element1, final PsiElement element2) {
    if (element1 != null && element2 != null) {
      final PsiFile psiFile1 = element1.getContainingFile();
      final PsiFile psiFile2 = element2.getContainingFile();
      if (Comparing.equal(psiFile1, psiFile2)){
        final TextRange textRange1 = element1.getTextRange();
        final TextRange textRange2 = element2.getTextRange();
        if (textRange1 != null && textRange2 != null) {
          return textRange1.getStartOffset() - textRange2.getStartOffset();
        }
      } else if (psiFile1 != null && psiFile2 != null){
        final String name1 = psiFile1.getName();
        final String name2 = psiFile2.getName();
        return name1.compareToIgnoreCase(name2);
      }
    }
    return 0;
  }

  public static boolean hasErrorElementChild(@NotNull PsiElement element) {
    for (PsiElement child = element.getFirstChild(); child != null; child = child.getNextSibling()) {
      if (child instanceof PsiErrorElement) return true;
    }
    return false;
  }

  @NotNull
  public static PsiElement getElementAtOffset(@NotNull PsiFile file, int offset) {
    PsiElement elt = file.findElementAt(offset);
    if (elt == null && offset > 0) {
      elt = file.findElementAt(offset - 1);
    }
    if (elt == null) {
      return file;
    }
    return elt;
  }

  @Nullable
  public static PsiFile getTemplateLanguageFile(final PsiElement element) {
    if (element == null) return null;
    final PsiFile containingFile = element.getContainingFile();
    if (containingFile == null) return null;

    final FileViewProvider viewProvider = containingFile.getViewProvider();
    return viewProvider.getPsi(viewProvider.getBaseLanguage());
  }

  @NotNull
  public static PsiFile[] toPsiFileArray(@NotNull Collection<? extends PsiFile> collection) {
    if (collection.isEmpty()) return PsiFile.EMPTY_ARRAY;
    //noinspection SSBasedInspection
    return collection.toArray(new PsiFile[collection.size()]);
  }

  /**
   * @return name for element using element structure info
   */
  @Nullable
  public static String getName(PsiElement element) {
    String name = null;
    if (element instanceof PsiMetaOwner) {
      final PsiMetaData data = ((PsiMetaOwner) element).getMetaData();
      if (data != null) {
        name = data.getName(element);
      }
    }
    if (name == null && element instanceof PsiNamedElement) {
      name = ((PsiNamedElement) element).getName();
    }
    return name;
  }

  public static String getQualifiedNameAfterRename(String qName, String newName) {
    if (qName == null) return newName;
    int index = qName.lastIndexOf('.');
    return index < 0 ? newName : qName.substring(0, index + 1) + newName;
  }

  public static Language getDialect(@NotNull PsiElement element) {
    return narrowLanguage(element.getLanguage(), element.getContainingFile().getLanguage());
  }

  protected static Language narrowLanguage(final Language language, final Language candidate) {
    if (candidate.isKindOf(language)) return candidate;
    return language;
  }

  public static void ensureValid(@NotNull PsiElement element) {
    if (!element.isValid()) {
      TimeoutUtil.sleep(1); // to see if processing in another thread suddenly makes the element valid again (which is a bug)
      if (element.isValid()) {
        LOG.error("PSI resurrected: " + element + " of " + element.getClass());
        return;
      }
      throw new PsiInvalidElementAccessException(element);
    }
  }

  /**
   * @deprecated use CompletionUtil#getOriginalElement where appropriate instead
   */
  @Nullable
  public static <T extends PsiElement> T getOriginalElement(@NotNull T psiElement, final Class<? extends T> elementClass) {
    final PsiFile psiFile = psiElement.getContainingFile();
    final PsiFile originalFile = psiFile.getOriginalFile();
    if (originalFile == psiFile) return psiElement;
    final TextRange range = psiElement.getTextRange();
    final PsiElement element = originalFile.findElementAt(range.getStartOffset());
    final int maxLength = range.getLength();
    T parent = PsiTreeUtil.getParentOfType(element, elementClass, false);
    T next = parent ;
    while (next != null && next.getTextLength() <= maxLength) {
      parent = next;
      next = PsiTreeUtil.getParentOfType(next, elementClass, true);
    }
    return parent;
  }

  @NotNull
  public static Language findLanguageFromElement(final PsiElement elt) {
    if (!(elt instanceof PsiFile) && elt.getFirstChild() == null) { //is leaf
      final PsiElement parent = elt.getParent();
      if (parent != null) {
        return parent.getLanguage();
      }
    }

    return elt.getLanguage();
  }

  @NotNull
  public static Language getLanguageAtOffset (@NotNull PsiFile file, int offset) {
    final PsiElement elt = file.findElementAt(offset);
    if (elt == null) return file.getLanguage();
    if (elt instanceof PsiWhiteSpace) {
      TextRange textRange = elt.getTextRange();
      if (!textRange.contains(offset)) {
        LOG.error("PSI corrupted: in file "+file+" ("+file.getViewProvider().getVirtualFile()+") offset="+offset+" returned element "+elt+" with text range "+textRange);
      }
      final int decremented = textRange.getStartOffset() - 1;
      if (decremented >= 0) {
        return getLanguageAtOffset(file, decremented);
      }
    }
    return findLanguageFromElement(elt);
  }
}
