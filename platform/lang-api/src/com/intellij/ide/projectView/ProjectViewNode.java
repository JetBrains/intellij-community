/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.ide.projectView;

import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.problems.WolfTheProblemSolver;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileSystemItem;
import com.intellij.psi.util.PsiUtilCore;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * A node in the project view tree.
 *
 * @see TreeStructureProvider#modify(com.intellij.ide.util.treeView.AbstractTreeNode, java.util.Collection, com.intellij.ide.projectView.ViewSettings)
 */

public abstract class ProjectViewNode <Value> extends AbstractTreeNode<Value> implements RootsProvider, SettingsProvider {

  protected static final Logger LOG = Logger.getInstance("#com.intellij.ide.projectView.ProjectViewNode");

  private final ViewSettings mySettings;
  private boolean myValidating;

  /**
   * Creates an instance of the project view node.
   *
   * @param project      the project containing the node.
   * @param value        the object (for example, a PSI element) represented by the project view node
   * @param viewSettings the settings of the project view.
   */
  protected ProjectViewNode(Project project, Value value, ViewSettings viewSettings) {
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

  public static List<AbstractTreeNode> wrap(Collection objects,
                                            Project project,
                                            Class<? extends AbstractTreeNode> nodeClass,
                                            ViewSettings settings) {
    try {
      ArrayList<AbstractTreeNode> result = new ArrayList<>();
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

  public static AbstractTreeNode createTreeNode(Class<? extends AbstractTreeNode> nodeClass,
                                                Project project,
                                                Object value,
                                                ViewSettings settings) throws
                                                                       InstantiationException {
    Object[] parameters = {project, value, settings};
    for (Constructor<? extends AbstractTreeNode> constructor : (Constructor<? extends AbstractTreeNode>[])nodeClass.getConstructors()) {
      if (constructor.getParameterTypes().length != 3) continue;
      try {
        return constructor.newInstance(parameters);
      }
      catch (InstantiationException ignored) {
      }
      catch (IllegalAccessException ignored) {
      }
      catch (IllegalArgumentException ignored) {
      }
      catch (InvocationTargetException ignored) {
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

    Collection<? extends AbstractTreeNode> kids = getChildren();
    for (final AbstractTreeNode kid : kids) {
      ProjectViewNode node = (ProjectViewNode)kid;
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
    } else if (value instanceof PsiFile) {
      PsiFile vFile = ((PsiFile)value).getContainingFile();
      if (vFile != null && vFile.getVirtualFile() != null) {
        return Collections.singleton(vFile.getVirtualFile());
      }
    } else if (value instanceof VirtualFile) {
      return Collections.singleton(((VirtualFile)value));
    } else if (value instanceof PsiFileSystemItem) {
      return Collections.singleton(((PsiFileSystemItem)value).getVirtualFile());
    }

    return EMPTY_ROOTS;
  }


  @Override
  protected boolean hasProblemFileBeneath() {
    if (!Registry.is("projectView.showHierarchyErrors")) return false;

    return WolfTheProblemSolver.getInstance(getProject()).hasProblemFilesBeneath(virtualFile -> {
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
  public boolean canHaveChildrenMatching(Condition<PsiFile> condition) {
    return true;
  }

  @Nullable
  public String getTitle() {
    return null;
  }

  public boolean isSortByFirstChild() {
    return false;
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
}
