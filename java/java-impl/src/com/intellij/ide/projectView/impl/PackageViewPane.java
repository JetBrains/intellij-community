// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.ide.projectView.impl;

import com.intellij.history.LocalHistory;
import com.intellij.history.LocalHistoryAction;
import com.intellij.icons.AllIcons;
import com.intellij.ide.DeleteProvider;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.SelectInTarget;
import com.intellij.ide.impl.PackagesPaneSelectInTarget;
import com.intellij.ide.projectView.ProjectView;
import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.projectView.impl.nodes.PackageElement;
import com.intellij.ide.projectView.impl.nodes.PackageElementNode;
import com.intellij.ide.projectView.impl.nodes.PackageViewProjectNode;
import com.intellij.ide.util.DeleteHandler;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.java.JavaBundle;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiPackage;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.util.ObjectUtils;
import com.intellij.util.PlatformUtils;
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.tree.DefaultTreeModel;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static com.intellij.openapi.module.ModuleGrouperKt.isQualifiedModuleNamesEnabled;

public class PackageViewPane extends AbstractProjectViewPaneWithAsyncSupport {
  @NonNls public static final String ID = "PackagesPane";
  private final MyDeletePSIElementProvider myDeletePSIElementProvider = new MyDeletePSIElementProvider();

  public PackageViewPane(Project project) {
    super(project);
  }

  @NotNull
  @Override
  public String getTitle() {
    return JavaBundle.message("title.packages");
  }

  @NotNull
  @Override
  public Icon getIcon() {
    return AllIcons.Nodes.CopyOfFolder;
  }

  @Override
  @NotNull
  public String getId() {
    return ID;
  }

  @NotNull
  @Override
  public List<PsiElement> getElementsFromNode(@Nullable Object node) {
    Object o = getValueFromNode(node);
    if (o instanceof PackageElement) {
      PsiPackage aPackage = ((PackageElement)o).getPackage();
      return ContainerUtil.createMaybeSingletonList(aPackage.isValid() ? aPackage : null);
    }
    return super.getElementsFromNode(node);
  }

  @Override
  protected Module getNodeModule(@Nullable Object element) {
    if (element instanceof PackageElement) {
      return ((PackageElement)element).getModule();
    }
    return super.getNodeModule(element);
  }

  @Override
  protected @Nullable Object getSlowDataFromSelection(@Nullable Object @NotNull [] selectedUserObjects,
                                                      @Nullable Object @Nullable [] singleSelectedPathUserObjects,
                                                      @NotNull String dataId) {
    if (PlatformDataKeys.DELETE_ELEMENT_PROVIDER.is(dataId)) {
      Object o = selectedUserObjects.length != 1 ? null : getValueFromNode(selectedUserObjects[0]);
      if (o instanceof PackageElement) {
        return myDeletePSIElementProvider;
      }
    }
    if (PackageElement.DATA_KEY.is(dataId)) {
      Object o = selectedUserObjects.length != 1 ? null : getValueFromNode(selectedUserObjects[0]);
      return o instanceof PackageElement ? o : null;
    }
    if (PlatformCoreDataKeys.MODULE.is(dataId)) {
      Object o = selectedUserObjects.length != 1 ? null : getValueFromNode(selectedUserObjects[0]);
      if (o instanceof PackageElement) {
        return ((PackageElement)o).getModule();
      }
    }
    return super.getSlowDataFromSelection(selectedUserObjects, singleSelectedPathUserObjects, dataId);
  }

  @RequiresBackgroundThread(generateAssertion = false)
  @Override
  protected PsiDirectory @NotNull [] getSelectedDirectories(Object @NotNull[] objects) {
    List<PsiDirectory> directories = new ArrayList<>();
    for (Object obj : objects) {
      PackageElementNode node = ObjectUtils.tryCast(obj, PackageElementNode.class);
      if (node != null) {
        PackageElement packageElement = node.getValue();
        PsiPackage aPackage = packageElement != null ? packageElement.getPackage() : null;
        final Module module = packageElement != null ? packageElement.getModule() : null;
        if (aPackage != null && module != null) {
          GlobalSearchScope scope = GlobalSearchScope.moduleScope(module);
          Collections.addAll(directories, aPackage.getDirectories(scope));
          if (Registry.is("projectView.choose.directory.on.compacted.middle.packages")) {
            Object parentValue = node.getParent().getValue();
            PsiPackage parentNodePackage = parentValue instanceof PackageElement ? ((PackageElement)parentValue).getPackage() : null;
            while (true) {
              aPackage = aPackage.getParentPackage();
              if (aPackage == null || aPackage.getQualifiedName().isEmpty() || aPackage.equals(parentNodePackage)) {
                break;
              }
              Collections.addAll(directories, aPackage.getDirectories(scope));
            }
          }
        }
      }
    }
    if (!directories.isEmpty()) {
      return directories.toArray(PsiDirectory.EMPTY_ARRAY);
    }
    return super.getSelectedDirectories(objects);
  }

  @NotNull
  @Override
  public SelectInTarget createSelectInTarget() {
    return new PackagesPaneSelectInTarget(myProject);
  }

  @NotNull
  @Override
  protected ProjectAbstractTreeStructureBase createStructure() {
    return new ProjectTreeStructure(myProject, ID){
      @Override
      protected AbstractTreeNode createRoot(@NotNull final Project project, @NotNull ViewSettings settings) {
        return new PackageViewProjectNode(project, settings);
      }

      @Override
      public boolean isToBuildChildrenInBackground(@NotNull Object element) {
        return Registry.is("ide.projectView.PackageViewTreeStructure.BuildChildrenInBackground");
      }
    };
  }

  @NotNull
  @Override
  protected ProjectViewTree createTree(@NotNull DefaultTreeModel treeModel) {
    return new ProjectViewTree(treeModel) {
      public String toString() {
        return getTitle() + " " + super.toString();
      }
    };
  }

  @NotNull
  public String getComponentName() {
    return "PackagesPane";
  }

  @Override
  public int getWeight() {
    return 1;
  }

  private Project getProject() {
    return myProject;
  }

  private final class MyDeletePSIElementProvider implements DeleteProvider {
    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
      return ActionUpdateThread.BGT;
    }

    @Override
    public boolean canDeleteElement(@NotNull DataContext dataContext) {
      Object[] objs = PlatformCoreDataKeys.SELECTED_ITEMS.getData(dataContext);
      if (objs != null && objs.length > 0) {
        for (PsiDirectory directory : getSelectedDirectories(objs)) {
          if (!directory.getManager().isInProject(directory)) return false;
        }
      }
      return true;
    }

    @Override
    public void deleteElement(@NotNull DataContext dataContext) {
      Object[] objs = PlatformCoreDataKeys.SELECTED_ITEMS.getData(dataContext);
      PsiDirectory[] allElements = objs != null ? getSelectedDirectories(objs) : PsiDirectory.EMPTY_ARRAY;
      List<PsiElement> validElements = new ArrayList<>();
      for (PsiElement psiElement : allElements) {
        if (psiElement != null && psiElement.isValid()) validElements.add(psiElement);
      }
      final PsiElement[] elements = PsiUtilCore.toPsiElementArray(validElements);

      LocalHistoryAction a = LocalHistory.getInstance().startAction(IdeBundle.message("progress.deleting"));
      try {
        DeleteHandler.deletePsiElement(elements, getProject());
      }
      finally {
        a.finish();
      }
    }
  }

  @Override
  public boolean supportsFlattenModules() {
    return PlatformUtils.isIntelliJ() && isQualifiedModuleNamesEnabled(myProject) && ProjectView.getInstance(myProject).isShowModules(ID);
  }

  @Override
  public boolean supportsShowLibraryContents() {
    return true;
  }

  @Override
  public boolean supportsShowModules() {
    return PlatformUtils.isIntelliJ();
  }
}