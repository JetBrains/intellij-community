// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.projectView;

import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.presentation.FilePresentationService;
import com.intellij.problems.WolfTheProblemSolver;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileSystemItem;
import com.intellij.psi.util.PsiUtilCore;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * A node in the project view tree.
 *
 * @see TreeStructureProvider#modify(AbstractTreeNode, Collection, ViewSettings)
 */

public abstract class ProjectViewNode <Value> extends AbstractTreeNode<Value> implements RootsProvider, SettingsProvider {

  protected static final Logger LOG = Logger.getInstance(ProjectViewNode.class);

  private final ViewSettings mySettings;
  private boolean myValidating;

  /**
   * Creates an instance of the project view node.
   *
   * @param project      the project containing the node.
   * @param value        the object (for example, a PSI element) represented by the project view node
   * @param viewSettings the settings of the project view.
   */
  protected ProjectViewNode(Project project, @NotNull Value value, ViewSettings viewSettings) {
    super(project, value);

    mySettings = viewSettings;
  }

  /**
   * Checks if this node or one of its children represents the specified virtual file.
   *
   * @param file the file to check for.
   * @return true if the file is found in the subtree, false otherwise.
   */
  public abstract boolean contains(@NotNull VirtualFile file);

  /**
   * Returns the virtual file represented by this node or one of its children.
   *
   * @return the virtual file instance, or null if the project view node doesn't represent a virtual file.
   */
  @Override
  @Nullable
  public VirtualFile getVirtualFile() {
    return null;
  }

  @Override
  public final ViewSettings getSettings() {
    return mySettings;
  }

  public static List<AbstractTreeNode<?>> wrap(Collection<?> objects,
                                            Project project,
                                            Class<? extends AbstractTreeNode<?>> nodeClass,
                                            ViewSettings settings) {
    try {
      ArrayList<AbstractTreeNode<?>> result = new ArrayList<>();
      for (Object object : objects) {
        result.add(createTreeNode(nodeClass, project, object, settings));
      }
      return result;
    }
    catch (Exception e) {
      LOG.error(e);
      return new ArrayList<>();
    }
  }

  @NotNull
  public static AbstractTreeNode<?> createTreeNode(Class<? extends AbstractTreeNode<?>> nodeClass,
                                                   Project project,
                                                   Object value,
                                                   ViewSettings settings) throws InstantiationException {
    Object[] parameters = {project, value, settings};
    for (Constructor<? extends AbstractTreeNode<?>> constructor : (Constructor<? extends AbstractTreeNode<?>>[])nodeClass.getConstructors()) {
      if (constructor.getParameterCount() != 3) continue;
      try {
        return constructor.newInstance(parameters);
      }
      catch (InstantiationException | InvocationTargetException | IllegalArgumentException | IllegalAccessException ignored) {
      }
    }
    throw new InstantiationException("no constructor found in " + nodeClass);
  }

  public boolean someChildContainsFile(final VirtualFile file) {
    return someChildContainsFile(file, true);
  }

  public boolean someChildContainsFile(final VirtualFile file, boolean optimizeByCheckingFileRootsFirst) {
    VirtualFile parent = file.getParent();

    boolean mayContain = false;

    if (optimizeByCheckingFileRootsFirst && parent != null) {
      Collection<VirtualFile> roots = getRoots();
      for (VirtualFile eachRoot : roots) {
        if (parent.equals(eachRoot.getParent())) {
          mayContain = true;
          break;
        }

        if (VfsUtilCore.isAncestor(eachRoot, file, true)) {
          mayContain = true;
          break;
        }
      }
    } else {
      mayContain = true;
    }

    if (!mayContain) {
      return false;
    }

    Collection<? extends AbstractTreeNode<?>> kids = getChildren();
    for (final AbstractTreeNode<?> kid : kids) {
      ProjectViewNode<?> node = (ProjectViewNode<?>)kid;
      if (node.contains(file)) return true;
    }
    return false;
  }

  @NotNull
  @Override
  public Collection<VirtualFile> getRoots() {
    Value value = getValue();
    if (value instanceof RootsProvider) {
      return ((RootsProvider)value).getRoots();
    }
    if (value instanceof VirtualFile) {
      return Collections.singleton((VirtualFile)value);
    }
    if (value instanceof PsiFileSystemItem item) {
      return getDefaultRootsFor(item.getVirtualFile());
    }
    return Collections.emptySet();
  }

  protected static Collection<VirtualFile> getDefaultRootsFor(@Nullable VirtualFile file) {
    return file != null ? Collections.singleton(file) : Collections.emptySet();
  }

  @Override
  protected boolean hasProblemFileBeneath() {
    if (!Registry.is("projectView.showHierarchyErrors")) return false;

    Project project = getProject();
    WolfTheProblemSolver wolf = project == null ? null : WolfTheProblemSolver.getInstance(project);
    return wolf != null && wolf.hasProblemFilesBeneath(virtualFile -> {
      Value value;
      return contains(virtualFile)
             // in case of flattened packages, when package node a.b.c contains error file, node a.b might not.
             && ((value = getValue()) instanceof PsiElement && Comparing.equal(PsiUtilCore.getVirtualFile((PsiElement)value), virtualFile) ||
                 someChildContainsFile(virtualFile));
    });
  }

  /**
   * Efficiently checks if there are nodes under the project view node which match the specified condition. Should
   * return true if it's not possible to perform the check efficiently (for example, if recursive traversal of
   * all child nodes is required to check the condition).
   *
   * @param condition the condition to check the nodes.
   */
  public boolean canHaveChildrenMatching(Condition<? super PsiFile> condition) {
    return true;
  }

  @Nullable
  @NlsContexts.PopupTitle
  public String getTitle() {
    return null;
  }

  public boolean isSortByFirstChild() {
    return false;
  }

  /**
   * This method is intended to separate the sorting of folders and files and
   * to simplify implementing the {@link #getSortKey} and {@link #getTypeSortKey} methods.
   *
   * @return the top-level groups for sorting the tree nodes
   */
  public @NotNull NodeSortOrder getSortOrder(@NotNull NodeSortSettings settings) {
    return settings.isManualOrder() ? NodeSortOrder.MANUAL : NodeSortOrder.UNSPECIFIED;
  }

  public int getTypeSortWeight(boolean sortByType) {
    return 0;
  }

  /**
   * When nodes are sorted by type all objects with same weigh will be sorted using
   * some common algorithm (e.g alpha comparator). This method allows to perform custom
   * sorting for such objects. And default comparison will be applied only if nodes are equal
   * from custom comparator's point of view. Also comparison will be applied only if both objects
   * have not null comparable keys.
   * @return Comparable object.
   */
  @Nullable
  public Comparable getTypeSortKey() {
    return null;
  }

  /**
   * When nodes aren't sorted by type all objects with same weigh will be sorted using
   * some common algorithm (e.g alpha comparator). This method allows to perform custom
   * sorting for such objects. And default comparison will be applied only if nodes are equal
   * from custom comparator's point of view. Also comparison will be applied only if both objects
   * have not null comparable keys.
   * @return Comparable object.
   */
  @Nullable
  public Comparable getSortKey() {
    return null;
  }

  @Nullable
  public Comparable getManualOrderKey() {
    return null;
  }

  @Nullable
  public String getQualifiedNameSortKey() {
    return null;
  }

  public boolean shouldDrillDownOnEmptyElement() {
    return false;
  }

  public boolean validate() {
    setValidating(true);
    try {
      update();
    }
    finally {
      setValidating(false);
    }
    return getValue() != null;
  }

  @Override
  protected boolean shouldPostprocess() {
    return !isValidating();
  }

  @Override
  protected boolean shouldApply() {
    return !isValidating();
  }

  private void setValidating(boolean validating) {
    myValidating = validating;
  }

  public boolean isValidating() {
    return myValidating;
  }

  @Override
  protected @Nullable Color computeBackgroundColor() {
    Color elementBackgroundColor = super.computeBackgroundColor();
    if (elementBackgroundColor != null) {
      return elementBackgroundColor;
    }
    return FilePresentationService.getFileBackgroundColor(getProject(), getVirtualFile());
  }
}
