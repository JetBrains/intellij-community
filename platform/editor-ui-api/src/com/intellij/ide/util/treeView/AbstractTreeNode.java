// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.util.treeView;

import com.intellij.ide.projectView.PresentationData;
import com.intellij.navigation.NavigationItem;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Queryable;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.FileStatusManager;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.presentation.FilePresentationService;
import com.intellij.psi.PsiElement;
import com.intellij.psi.SmartPsiElementPointer;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.ui.tree.LeafState;
import org.jetbrains.annotations.*;

import java.awt.*;
import java.util.Collection;
import java.util.Map;

public abstract class AbstractTreeNode<T> extends PresentableNodeDescriptor<AbstractTreeNode<T>>
  implements NavigationItem, Queryable.Contributor, LeafState.Supplier {

  private static final TextAttributesKey FILESTATUS_ERRORS = TextAttributesKey.createTextAttributesKey("FILESTATUS_ERRORS");
  private static final Logger LOG = Logger.getInstance(AbstractTreeNode.class);
  private AbstractTreeNode<?> myParent;
  private Object myValue;
  private boolean myNullValueSet;
  private final boolean myNodeWrapper;
  static final Object TREE_WRAPPER_VALUE = new Object();

  protected AbstractTreeNode(Project project, @NotNull T value) {
    super(project, null);
    myNodeWrapper = setInternalValue(value);
  }

  @NotNull
  public abstract Collection<? extends AbstractTreeNode<?>> getChildren();

  protected boolean hasProblemFileBeneath() {
    return false;
  }

  protected boolean valueIsCut() {
    return CopyPasteManager.getInstance().isCutElement(getValue());
  }

  @Override
  public PresentableNodeDescriptor getChildToHighlightAt(int index) {
    final Collection<? extends AbstractTreeNode<?>> kids = getChildren();
    int i = 0;
    for (final AbstractTreeNode<?> kid : kids) {
      if (i == index) return kid;
      i++;
    }

    return null;
  }

  @Override
  protected void postprocess(@NotNull PresentationData presentation) {
    if (hasProblemFileBeneath() ) {
      presentation.setAttributesKey(FILESTATUS_ERRORS);
    }

    setForcedForeground(presentation);
  }

  private void setForcedForeground(@NotNull PresentationData presentation) {
    final FileStatus status = getFileStatus();
    Color fgColor = getFileStatusColor(status);
    fgColor = fgColor == null ? status.getColor() : fgColor;

    if (valueIsCut()) {
      fgColor = CopyPasteManager.CUT_COLOR;
    }

    if (presentation.getForcedTextForeground() == null) {
      presentation.setForcedTextForeground(fgColor);
    }
  }

  @Override
  protected boolean shouldUpdateData() {
    return !myProject.isDisposed() && getEqualityObject() != null;
  }

  @NotNull
  @Override
  public LeafState getLeafState() {
    if (isAlwaysShowPlus()) return LeafState.NEVER;
    if (isAlwaysLeaf()) return LeafState.ALWAYS;
    return LeafState.DEFAULT;
  }

  public boolean isAlwaysShowPlus() {
    return false;
  }

  public boolean isAlwaysLeaf() {
    return false;
  }

  public boolean isAlwaysExpand() {
    return false;
  }

  @Override
  @Nullable
  public final AbstractTreeNode<T> getElement() {
    return getEqualityObject() != null ? this : null;
  }

  @Override
  public boolean equals(Object object) {
    if (object == this) return true;
    if (object == null || !object.getClass().equals(getClass())) return false;
    // we should not change this behaviour if value is set to null
    return Comparing.equal(myValue, ((AbstractTreeNode<?>)object).myValue);
  }

  @Override
  public int hashCode() {
    // we should not change hash code if value is set to null
    Object value = myValue;
    return value == null ? 0 : value.hashCode();
  }

  public final AbstractTreeNode getParent() {
    return myParent;
  }

  public final void setParent(AbstractTreeNode parent) {
    myParent = parent;
  }

  @Override
  public final NodeDescriptor getParentDescriptor() {
    return myParent;
  }

  public final T getValue() {
    Object value = getEqualityObject();
    return value == null ? null : (T)TreeAnchorizer.getService().retrieveElement(value);
  }

  public final void setValue(T value) {
    boolean debug = !myNodeWrapper && LOG.isDebugEnabled();
    int hash = !debug ? 0 : hashCode();
    myNullValueSet = value == null || setInternalValue(value);
    recordValueSetTrace(myNullValueSet);
    if (debug && hash != hashCode()) {
      LOG.warn("hash code changed: " + myValue);
    }
  }

  protected void recordValueSetTrace(boolean nullValue) {

  }

  /**
   * Stores the anchor to new value if it is not {@code null}
   *
   * @param value a new value to set
   * @return {@code true} if the specified value is {@code null} and the anchor is not changed
   */
  private boolean setInternalValue(@NotNull T value) {
    if (value == TREE_WRAPPER_VALUE) return true;
    myValue = TreeAnchorizer.getService().createAnchor(value);
    return false;
  }

  public final Object getEqualityObject() {
    return myNullValueSet ? null : myValue;
  }

  @Nullable
  @TestOnly
  public String toTestString(@Nullable Queryable.PrintInfo printInfo) {
    if (getValue() instanceof Queryable) {
      return Queryable.Util.print((Queryable)getValue(), printInfo, this);
    }

    return getTestPresentation();
  }

  @Override
  public void apply(@NotNull Map<String, String> info) {
  }

  /**
   * @deprecated use {@link #toTestString(Queryable.PrintInfo)} instead
   */
  @Deprecated
  @Nullable
  @NonNls
  @TestOnly
  public String getTestPresentation() {
    if (myName != null) {
      return myName;
    }
    if (getValue() != null){
      return getValue().toString();
    }
    return null;
  }

  @Nullable
  public Color getFileStatusColor(final FileStatus status) {
    if (FileStatus.NOT_CHANGED.equals(status) && myProject != null && !myProject.isDefault()) {
      final VirtualFile vf = getVirtualFile();
      if (vf != null && vf.isDirectory()) {
        return FileStatusManager.getInstance(myProject).getRecursiveStatus(vf).getColor();
      }
    }
    return status.getColor();
  }

  protected VirtualFile getVirtualFile() {
    return null;
  }

  public FileStatus getFileStatus() {
    return FileStatus.NOT_CHANGED;
  }

  @Override
  public String getName() {
    return myName;
  }

  @Override
  public void navigate(boolean requestFocus) {
  }

  @Override
  public boolean canNavigate() {
    return false;
  }

  @Override
  public boolean canNavigateToSource() {
    return false;
  }

  @Nullable
  protected final Object getParentValue() {
    AbstractTreeNode<?> parent = getParent();
    return parent == null ? null : parent.getValue();
  }


  public boolean canRepresent(final Object element) {
    return Comparing.equal(getValue(), element);
  }

  /**
   * @deprecated use {@link #getPresentation()} instead
   */
  @Deprecated(forRemoval = true)
  protected String getToolTip() {
    return getPresentation().getTooltip();
  }

  @Override
  protected @Nullable Color computeBackgroundColor() {
    Object value = getValue();
    if (!(value instanceof PsiElement)) {
      return null;
    }
    PsiElement element = (PsiElement)value;
    return FilePresentationService.getInstance(element.getProject()).getFileBackgroundColor(element);
  }

  private @Nullable VirtualFile extractFileFromValue() {
    Object value = getEqualityObject();
    if (value instanceof SmartPsiElementPointer) {
      // see #getValue && default implementation of TreeAnchorizer
      SmartPsiElementPointer<?> pointer = (SmartPsiElementPointer<?>)value;
      return pointer.getVirtualFile();
    }
    return null;
  }

  /**
   * This method is intended to optimize a search through a PSI-based nodes.
   * It can be used within a tree model with file hierarchy (i.e. Project View).
   */
  @ApiStatus.Internal
  public final boolean mayContain(@Nullable Object object) {
    if (object == null) return false;
    VirtualFile ancestor = extractFileFromValue();
    if (ancestor == null) return true; // always search in unknown nodes
    if (!ancestor.isValid()) return false; // do not search in invalid files
    if (object instanceof PsiElement) {
      object = PsiUtilCore.getVirtualFile((PsiElement)object);
    }
    if (object instanceof VirtualFile) {
      VirtualFile file = (VirtualFile)object;
      if (!file.isValid()) return false; // do not search for invalid files
      return VfsUtilCore.isAncestor(ancestor, file, false);
    }
    return true; // any custom object can be contained somewhere in a tree
  }
}
