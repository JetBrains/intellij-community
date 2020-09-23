// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.source.resolve.reference.impl.providers;

import com.intellij.lang.ASTNode;
import com.intellij.lang.Language;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.NonPhysicalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiElementBase;
import com.intellij.psi.search.PsiElementProcessor;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;

/**
 * @author Gregory.Shrago
*/
final class PackagePrefixFileSystemItemImpl extends PsiElementBase implements PsiFileSystemItem, PackagePrefixFileSystemItem {
  @NotNull private final PsiDirectory myDirectory;
  private final int myIndex;
  private final PsiPackage[] myPackages;

  public static PackagePrefixFileSystemItemImpl create(@NotNull PsiDirectory directory) {
    final ArrayList<PsiPackage> packages = new ArrayList<>();
    for (PsiPackage cur = JavaDirectoryService.getInstance().getPackage(directory); cur != null; cur = cur.getParentPackage()) {
      packages.add(0, cur);
    }
    return new PackagePrefixFileSystemItemImpl(directory, 0, packages.toArray(PsiPackage.EMPTY_ARRAY));
  }

  private PackagePrefixFileSystemItemImpl(@NotNull PsiDirectory directory, int index, final PsiPackage[] packages) {
    myDirectory = directory;
    myIndex = index;
    myPackages = packages;
  }

  @Override
  @NotNull
  public String getName() {
    return StringUtil.notNullize(myPackages[myIndex].getName());
  }

  @Override
  public PsiElement setName(@NonNls @NotNull final String name) throws IncorrectOperationException {
    throw new IncorrectOperationException();
  }

  @Override
  public void checkSetName(final String name) throws IncorrectOperationException {
    throw new IncorrectOperationException();
  }

  @Override
  public boolean isDirectory() {
    return true;
  }

  @Override
  public PsiFileSystemItem getParent() {
    return myIndex > 0 ? new PackagePrefixFileSystemItemImpl(myDirectory, myIndex - 1, myPackages) : myDirectory.getParent();
  }

  @Override
  public PsiFile getContainingFile() throws PsiInvalidElementAccessException {
    return null;
  }

  @Override
  public TextRange getTextRange() {
    return null;
  }

  @Override
  public int getStartOffsetInParent() {
    return 0;
  }

  @Override
  public int getTextLength() {
    return 0;
  }

  @Override
  @Nullable
  public PsiElement findElementAt(final int offset) {
    return null;
  }

  @Override
  public int getTextOffset() {
    return 0;
  }

  @Override
  @NonNls
  public String getText() {
    return "";
  }

  @Override
  public char @NotNull [] textToCharArray() {
    return ArrayUtilRt.EMPTY_CHAR_ARRAY;
  }

  @Override
  public boolean textMatches(@NotNull @NonNls final CharSequence text) {
    return false;
  }

  @Override
  public boolean textMatches(@NotNull final PsiElement element) {
    return false;
  }

  @Override
  public void accept(@NotNull final PsiElementVisitor visitor) {
  }

  @Override
  public PsiElement copy() {
    return null;
  }

  @Override
  public PsiElement add(@NotNull final PsiElement element) throws IncorrectOperationException {
    throw new IncorrectOperationException();
  }

  @Override
  public PsiElement addBefore(@NotNull final PsiElement element, final PsiElement anchor) throws IncorrectOperationException {
    throw new IncorrectOperationException();
  }

  @Override
  public PsiElement addAfter(@NotNull final PsiElement element, final PsiElement anchor) throws IncorrectOperationException {
    throw new IncorrectOperationException();
  }

  @Override
  public void checkAdd(@NotNull final PsiElement element) throws IncorrectOperationException {
    throw new IncorrectOperationException();
  }

  @Override
  public void delete() throws IncorrectOperationException {
    throw new IncorrectOperationException();
  }

  @Override
  public void checkDelete() throws IncorrectOperationException {
    throw new IncorrectOperationException();
  }

  @Override
  public PsiElement replace(@NotNull final PsiElement newElement) throws IncorrectOperationException {
    throw new IncorrectOperationException();
  }

  @Override
  public boolean isValid() {
    return myDirectory.isValid();
  }

  @Override
  public boolean isWritable() {
    final VirtualFile file = getVirtualFile();
    return file != null && file.isWritable();
  }

  @Override
  public boolean isPhysical() {
    final VirtualFile file = getVirtualFile();
    return file != null && !(file.getFileSystem() instanceof NonPhysicalFileSystem);
  }

  @Override
  @Nullable
  public ASTNode getNode() {
    return null;
  }

  @Override
  public boolean processChildren(final @NotNull PsiElementProcessor<? super PsiFileSystemItem> processor) {
    if (myIndex == myPackages.length - 1) {
      return myDirectory.processChildren(processor);
    }
    else {
      return processor.execute(new PackagePrefixFileSystemItemImpl(myDirectory, myIndex+1, myPackages));
    }
  }

  @Override
  @NotNull
  public Language getLanguage() {
    return Language.ANY;
  }

  @Override
  public PsiManager getManager() {
    return myDirectory.getManager();
  }

  @Override
  public PsiElement @NotNull [] getChildren() {
    return myIndex == myPackages.length -1? myDirectory.getChildren() : new PsiElement[] {new PackagePrefixFileSystemItemImpl(myDirectory, myIndex + 1, myPackages)};
  }

  @Override
  public boolean canNavigate() {
    return getVirtualFile() != null;
  }

  @Override
  public VirtualFile getVirtualFile() {
    if (myIndex == myPackages.length - 1) {
      return myDirectory.getVirtualFile();
    }
    else {
      return null;
    }
  }

  @Override
  @Nullable
  public Icon getIcon(final int flags) {
    return myDirectory.getIcon(flags);
  }

  @NotNull
  @Override
  public PsiDirectory getDirectory() {
    return myDirectory;
  }
}
