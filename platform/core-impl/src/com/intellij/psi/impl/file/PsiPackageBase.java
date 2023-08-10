// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.file;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.ui.Queryable;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.impl.DebugUtil;
import com.intellij.psi.impl.PsiElementBase;
import com.intellij.psi.search.EverythingGlobalScope;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.ui.IconManager;
import com.intellij.ui.PlatformIcons;
import com.intellij.ui.icons.RowIcon;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Map;

public abstract class PsiPackageBase extends PsiElementBase implements PsiDirectoryContainer, Queryable {
  private static final Logger LOG = Logger.getInstance(PsiPackageBase.class);

  private final PsiManager myManager;
  private final String myQualifiedName;

  protected abstract Collection<PsiDirectory> getAllDirectories(GlobalSearchScope scope);

  protected abstract PsiPackageBase findPackage(String qName);

  public PsiPackageBase(PsiManager manager, String qualifiedName) {
    myManager = manager;
    myQualifiedName = qualifiedName;
  }

  public boolean equals(Object o) {
    return o != null && getClass() == o.getClass()
           && myManager == ((PsiPackageBase)o).myManager
           && myQualifiedName.equals(((PsiPackageBase)o).myQualifiedName);
  }

  public int hashCode() {
    return myQualifiedName.hashCode();
  }

  public @NotNull String getQualifiedName() {
    return myQualifiedName;
  }

  @Override
  public PsiDirectory @NotNull [] getDirectories() {
    return getDirectories(new EverythingGlobalScope());
  }

  @Override
  public PsiDirectory @NotNull [] getDirectories(@NotNull GlobalSearchScope scope) {
    Collection<PsiDirectory> directories = getAllDirectories(scope);
    return directories.isEmpty() ? PsiDirectory.EMPTY_ARRAY : directories.toArray(PsiDirectory.EMPTY_ARRAY);
  }

  @Override
  public RowIcon getElementIcon(int elementFlags) {
    return IconManager.getInstance().createLayeredIcon(this, IconManager.getInstance().getPlatformIcon(PlatformIcons.Package), elementFlags);
  }

  @Override
  public String getName() {
    if (DebugUtil.CHECK_INSIDE_ATOMIC_ACTION_ENABLED) {
      ApplicationManager.getApplication().assertReadAccessAllowed();
    }
    if (myQualifiedName.isEmpty()) return null;
    int index = myQualifiedName.lastIndexOf('.');
    if (index <= 0) {
      return myQualifiedName;
    }
    else {
      return myQualifiedName.substring(index + 1);
    }
  }

  @Override
  public @Nullable PsiElement setName(@NotNull String name) throws IncorrectOperationException {
    checkSetName(name);
    PsiDirectory[] dirs = getDirectories();
    for (PsiDirectory dir : dirs) {
      dir.setName(name);
    }
    String nameAfterRename = PsiUtilCore.getQualifiedNameAfterRename(getQualifiedName(), name);
    return findPackage(nameAfterRename);
  }

  public void checkSetName(@NotNull String name) throws IncorrectOperationException {
    PsiDirectory[] dirs = getDirectories();
    for (PsiDirectory dir : dirs) {
      dir.checkSetName(name);
    }
  }

  public PsiPackageBase getParentPackage() {
    if (myQualifiedName.isEmpty()) return null;
    return findPackage(StringUtil.getPackageName(myQualifiedName));
  }

  @Override
  public PsiManager getManager() {
    return myManager;
  }

  @Override
  public PsiElement @NotNull [] getChildren() {
    LOG.error("method not implemented in " + getClass());
    return PsiElement.EMPTY_ARRAY;
  }

  @Override
  public @Nullable PsiElement getParent() {
    return getParentPackage();
  }

  @Override
  public @Nullable PsiFile getContainingFile() {
    return null;
  }

  @Override
  public @Nullable TextRange getTextRange() {
    return null;
  }

  @Override
  public int getStartOffsetInParent() {
    return -1;
  }

  @Override
  public int getTextLength() {
    return -1;
  }

  @Override
  public PsiElement findElementAt(int offset) {
    return null;
  }

  @Override
  public int getTextOffset() {
    return -1;
  }

  @Override
  public @Nullable String getText() {
    return null;
  }

  @Override
  public char @NotNull [] textToCharArray() {
    return ArrayUtilRt.EMPTY_CHAR_ARRAY; // TODO throw new InsupportedOperationException()
  }

  @Override
  public boolean textMatches(@NotNull CharSequence text) {
    return false;
  }

  @Override
  public boolean textMatches(@NotNull PsiElement element) {
    return false;
  }

  @Override
  public PsiElement copy() {
    LOG.error("method not implemented in " + getClass());
    return null;
  }

  @Override
  public PsiElement add(@NotNull PsiElement element) throws IncorrectOperationException {
    throw new IncorrectOperationException();
  }

  @Override
  public PsiElement addBefore(@NotNull PsiElement element, PsiElement anchor) throws IncorrectOperationException {
    throw new IncorrectOperationException();
  }

  @Override
  public PsiElement addAfter(@NotNull PsiElement element, PsiElement anchor) throws IncorrectOperationException {
    throw new IncorrectOperationException();
  }

  @Override
  public void checkAdd(@NotNull PsiElement element) throws IncorrectOperationException {
    throw new IncorrectOperationException();
  }

  @Override
  public void delete() throws IncorrectOperationException {
    checkDelete();
    PsiDirectory[] dirs = getDirectories();
    for (PsiDirectory dir : dirs) {
      dir.delete();
    }
  }

  @Override
  public void checkDelete() throws IncorrectOperationException {
    for (PsiDirectory dir : getDirectories()) {
      dir.checkDelete();
    }
  }

  @Override
  public PsiElement replace(@NotNull PsiElement newElement) throws IncorrectOperationException {
    throw new IncorrectOperationException();
  }

  @Override
  public boolean isWritable() {
    PsiDirectory[] dirs = getDirectories();
    for (PsiDirectory dir : dirs) {
      if (!dir.isWritable()) return false;
    }
    return true;
  }

  @Override
  public String toString() {
    return "PsiPackageBase:" + getQualifiedName();
  }

  @Override
  public boolean canNavigate() {
    return isValid();
  }

  @Override
  public boolean canNavigateToSource() {
    return false;
  }

  @Override
  public boolean isPhysical() {
    return true;
  }

  @Override
  public ASTNode getNode() {
    return null;
  }

  @Override
  public void putInfo(@NotNull Map<? super String, ? super String> info) {
    info.put("packageName", getName());
    info.put("packageQualifiedName", getQualifiedName());
  }
}
