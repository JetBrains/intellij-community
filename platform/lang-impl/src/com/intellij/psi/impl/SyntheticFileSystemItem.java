/*
 * @author max
 */
package com.intellij.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.lang.Language;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.search.PsiElementProcessor;
import com.intellij.psi.search.PsiFileSystemItemProcessor;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class SyntheticFileSystemItem extends PsiElementBase implements PsiFileSystemItem {
  public static final Logger LOG = Logger.getInstance("#" + SyntheticFileSystemItem.class.getPackage().getName());

  protected final Project myProject;
  protected final PsiManager myManager;

  public SyntheticFileSystemItem(Project project) {
    myProject = project;
    myManager = PsiManager.getInstance(myProject);
  }

  protected static boolean processFileSystemItem(PsiElementProcessor<PsiFileSystemItem> processor, PsiFileSystemItem element) {
    if (processor instanceof PsiFileSystemItemProcessor && !((PsiFileSystemItemProcessor)processor).acceptItem(element.getName(), true)) {
      return true;
    }

    return processor.execute(element);
  }

  public boolean isDirectory() {
    return true;
  }

  public ASTNode getNode() {
    return null;
  }

  public boolean isPhysical() {
    return true;
  }

  public boolean isWritable() {
    return true;
  }

  public boolean isValid() {
    final VirtualFile virtualFile = getVirtualFile();
    return virtualFile != null && virtualFile.isValid();
  }

  public PsiElement replace(@NotNull final PsiElement newElement) throws IncorrectOperationException {
    throw new IncorrectOperationException("Frameworks cannot be changed");
  }

  public void checkDelete() throws IncorrectOperationException {
    throw new IncorrectOperationException("Frameworks cannot be deleted");
  }

  public void delete() throws IncorrectOperationException {
    throw new IncorrectOperationException("Frameworks cannot be deleted");
  }

  public void accept(@NotNull final PsiElementVisitor visitor) {
    // TODO
  }

  @NotNull
  public PsiElement[] getChildren() {
    final PsiElementProcessor.CollectElements<PsiFileSystemItem> collector = new PsiElementProcessor.CollectElements<PsiFileSystemItem>();
    processChildren(collector);
    return collector.toArray(new PsiFileSystemItem[0]);
  }

  public PsiManager getManager() {
    return myManager;
  }

  @NotNull
  public Language getLanguage() {
    return Language.ANY;
  }

  public void checkSetName(final String name) throws IncorrectOperationException {
    throw new IncorrectOperationException("Frameworks cannot be renamed");
  }

  public PsiElement setName(@NonNls @NotNull final String name) throws IncorrectOperationException {
    throw new IncorrectOperationException("Frameworks cannot be renamed");
  }

  @Nullable
  public PsiFile getContainingFile() {
    return null;
  }

  @Nullable
  public TextRange getTextRange() {
    return null;
  }

  public int getStartOffsetInParent() {
    return -1;
  }

  public int getTextLength() {
    return -1;
  }

  public PsiElement findElementAt(int offset) {
    return null;
  }

  public int getTextOffset() {
    return -1;
  }

  @Nullable
  public String getText() {
    return null;
  }

  @NotNull
  public char[] textToCharArray() {
    return ArrayUtil.EMPTY_CHAR_ARRAY; // TODO throw new InsupportedOperationException()
  }

  public boolean textMatches(@NotNull CharSequence text) {
    return false;
  }

  public boolean textMatches(@NotNull PsiElement element) {
    return false;
  }

  public PsiElement copy() {
    LOG.error("method not implemented");
    return null;
  }

  public PsiElement add(@NotNull PsiElement element) throws IncorrectOperationException {
    throw new IncorrectOperationException();
  }

  public PsiElement addBefore(@NotNull PsiElement element, PsiElement anchor) throws IncorrectOperationException {
    throw new IncorrectOperationException();
  }

  public PsiElement addAfter(@NotNull PsiElement element, PsiElement anchor) throws IncorrectOperationException {
    throw new IncorrectOperationException();
  }

  public void checkAdd(@NotNull PsiElement element) throws IncorrectOperationException {
    throw new IncorrectOperationException();
  }
}
