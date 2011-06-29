/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.psi.impl.file;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.ui.Queryable;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.impl.DebugUtil;
import com.intellij.psi.impl.PsiElementBase;
import com.intellij.psi.impl.PsiManagerEx;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.refactoring.rename.RenameUtil;
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

  final PsiManagerEx myManager;
  private final String myQualifiedName;

  protected abstract Collection<PsiDirectory> getAllDirectories();

  protected abstract PsiElement findPackage(String qName);

  protected abstract PsiPackageBase createInstance(PsiManagerEx manager, String qName);

  public PsiPackageBase(PsiManagerEx manager, String qualifiedName) {
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

  @NotNull
  public PsiDirectory[] getDirectories() {
    final Collection<PsiDirectory> collection = getAllDirectories();
    return ContainerUtil.toArray(collection, new PsiDirectory[collection.size()]);
  }

  @NotNull
  public PsiDirectory[] getDirectories(@NotNull GlobalSearchScope scope) {
    List<PsiDirectory> result = null;
    final Collection<PsiDirectory> directories = getAllDirectories();
    for (final PsiDirectory directory : directories) {
      if (scope.contains(directory.getVirtualFile())) {
        if (result == null) result = new ArrayList<PsiDirectory>();
        result.add(directory);
      }
    }
    return result == null ? PsiDirectory.EMPTY_ARRAY : result.toArray(new PsiDirectory[result.size()]);
  }

  public RowIcon getElementIcon(final int elementFlags) {
    return createLayeredIcon(PlatformIcons.PACKAGE_ICON, elementFlags);
  }

  public String getName() {
    if (DebugUtil.CHECK_INSIDE_ATOMIC_ACTION_ENABLED) {
      ApplicationManager.getApplication().assertReadAccessAllowed();
    }
    if (myQualifiedName.length() == 0) return null;
    int index = myQualifiedName.lastIndexOf('.');
    if (index < 0) {
      return myQualifiedName;
    }
    else {
      return myQualifiedName.substring(index + 1);
    }
  }

  @Nullable
  public PsiElement setName(@NotNull String name) throws IncorrectOperationException {
    checkSetName(name);
    PsiDirectory[] dirs = getDirectories();
    for (PsiDirectory dir : dirs) {
      dir.setName(name);
    }
    String nameAfterRename = RenameUtil.getQualifiedNameAfterRename(getQualifiedName(), name);
    return findPackage(nameAfterRename);
  }

  public void checkSetName(@NotNull String name) throws IncorrectOperationException {
    PsiDirectory[] dirs = getDirectories();
    for (PsiDirectory dir : dirs) {
      dir.checkSetName(name);
    }
  }

  public PsiPackageBase getParentPackage() {
    if (myQualifiedName.length() == 0) return null;
    int lastDot = myQualifiedName.lastIndexOf('.');
    if (lastDot < 0) {
      return createInstance(myManager, "");
    }
    else {
      return createInstance(myManager, myQualifiedName.substring(0, lastDot));
    }
  }

  public PsiManager getManager() {
    return myManager;
  }

  @NotNull
  public PsiElement[] getChildren() {
    LOG.error("method not implemented");
    return PsiElement.EMPTY_ARRAY;
  }

  @Nullable
  public PsiElement getParent() {
    return getParentPackage();
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

  public void delete() throws IncorrectOperationException {
    checkDelete();
    PsiDirectory[] dirs = getDirectories();
    for (PsiDirectory dir : dirs) {
      dir.delete();
    }
  }

  public void checkDelete() throws IncorrectOperationException {
    for (PsiDirectory dir : getDirectories()) {
      dir.checkDelete();
    }
  }

  public PsiElement replace(@NotNull PsiElement newElement) throws IncorrectOperationException {
    throw new IncorrectOperationException();
  }

  public boolean isWritable() {
    PsiDirectory[] dirs = getDirectories();
    for (PsiDirectory dir : dirs) {
      if (!dir.isWritable()) return false;
    }
    return true;
  }

  public void accept(@NotNull PsiElementVisitor visitor) {
    visitor.visitElement(this);
  }

  public String toString() {
    return "PsiPackageBase:" + getQualifiedName();
  }

  public boolean canNavigate() {
    return isValid();
  }

  public boolean canNavigateToSource() {
    return false;
  }

  public boolean isPhysical() {
    return true;
  }

  public ASTNode getNode() {
    return null;
  }

  public void putInfo(Map<String, String> info) {
    info.put("packageName", getName());
    info.put("packageQualifiedName", getQualifiedName());
  }
}
