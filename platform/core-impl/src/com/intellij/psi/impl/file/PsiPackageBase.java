/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.psi.impl.file;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.ui.Queryable;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.impl.DebugUtil;
import com.intellij.psi.impl.PsiElementBase;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.ui.RowIcon;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.PlatformIcons;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

public abstract class PsiPackageBase extends PsiElementBase implements PsiDirectoryContainer, Queryable {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.file.PsiPackageBase");

  private final PsiManager myManager;
  private final String myQualifiedName;

  protected Collection<PsiDirectory> getAllDirectories() {
    return getAllDirectories(false);
  }

  protected abstract Collection<PsiDirectory> getAllDirectories(boolean includeLibrarySources);

  protected abstract PsiElement findPackage(String qName);

  protected abstract PsiPackageBase createInstance(PsiManager manager, String qName);

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

  @NotNull
  public String getQualifiedName() {
    return myQualifiedName;
  }

  @Override
  @NotNull
  public PsiDirectory[] getDirectories() {
    final Collection<PsiDirectory> collection = getAllDirectories();
    return ContainerUtil.toArray(collection, new PsiDirectory[collection.size()]);
  }

  @Override
  @NotNull
  public PsiDirectory[] getDirectories(@NotNull GlobalSearchScope scope) {
    List<PsiDirectory> result = null;
    final boolean includeLibrarySources = scope.isForceSearchingInLibrarySources();
    final Collection<PsiDirectory> directories = getAllDirectories(includeLibrarySources);
    for (final PsiDirectory directory : directories) {
      if (scope.contains(directory.getVirtualFile())) {
        if (result == null) result = new ArrayList<>();
        result.add(directory);
      }
    }
    return result == null ? PsiDirectory.EMPTY_ARRAY : result.toArray(new PsiDirectory[result.size()]);
  }

  @Override
  public RowIcon getElementIcon(final int elementFlags) {
    return createLayeredIcon(this, PlatformIcons.PACKAGE_ICON, elementFlags);
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
  @Nullable
  public PsiElement setName(@NotNull String name) throws IncorrectOperationException {
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
    int lastDot = myQualifiedName.lastIndexOf('.');
    if (lastDot < 0) {
      return createInstance(myManager, "");
    }
    else {
      return createInstance(myManager, myQualifiedName.substring(0, lastDot));
    }
  }

  @Override
  public PsiManager getManager() {
    return myManager;
  }

  @Override
  @NotNull
  public PsiElement[] getChildren() {
    LOG.error("method not implemented in " + getClass());
    return PsiElement.EMPTY_ARRAY;
  }

  @Override
  @Nullable
  public PsiElement getParent() {
    return getParentPackage();
  }

  @Override
  @Nullable
  public PsiFile getContainingFile() {
    return null;
  }

  @Override
  @Nullable
  public TextRange getTextRange() {
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
  @Nullable
  public String getText() {
    return null;
  }

  @Override
  @NotNull
  public char[] textToCharArray() {
    return ArrayUtil.EMPTY_CHAR_ARRAY; // TODO throw new InsupportedOperationException()
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
  public void accept(@NotNull PsiElementVisitor visitor) {
    visitor.visitElement(this);
  }

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
  public void putInfo(@NotNull Map<String, String> info) {
    info.put("packageName", getName());
    info.put("packageQualifiedName", getQualifiedName());
  }
}
