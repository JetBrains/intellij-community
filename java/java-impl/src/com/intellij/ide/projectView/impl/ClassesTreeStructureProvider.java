// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.projectView.impl;

import com.intellij.codeInsight.template.Template;
import com.intellij.codeInsight.template.TemplateManager;
import com.intellij.ide.projectView.ProjectViewNode;
import com.intellij.ide.projectView.SelectableTreeStructureProvider;
import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.projectView.impl.nodes.ClassTreeNode;
import com.intellij.ide.projectView.impl.nodes.PsiFileNode;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.ide.util.treeView.AbstractTreeUi;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.util.ClassUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.java.JavaModuleSourceRootTypes;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class ClassesTreeStructureProvider implements SelectableTreeStructureProvider, DumbAware {
  private final Project myProject;

  public ClassesTreeStructureProvider(@NotNull Project project) {
    myProject = project;
  }

  @NotNull
  @Override
  public Collection<AbstractTreeNode> modify(@NotNull AbstractTreeNode parent,
                                             @NotNull Collection<AbstractTreeNode> children,
                                             ViewSettings settings) {
    return AbstractTreeUi.calculateYieldingToWriteAction(() -> doModify(parent, children));
  }

  @NotNull
  private Collection<AbstractTreeNode> doModify(@NotNull AbstractTreeNode parent, @NotNull Collection<AbstractTreeNode> children) {
    List<AbstractTreeNode> result = new ArrayList<>();
    for (AbstractTreeNode<?> child : children) {
      ProgressManager.checkCanceled();

      Object o = child.getValue();
      if (o instanceof PsiClassOwner && !(o instanceof ServerPageFile)) {
        ViewSettings settings1 = ((ProjectViewNode)parent).getSettings();
        PsiClassOwner classOwner = (PsiClassOwner)o;
        VirtualFile file = classOwner.getVirtualFile();

        if (!(classOwner instanceof PsiCompiledElement)) {
          //do not show duplicated items if jar file contains classes and sources
          ProjectFileIndex fileIndex = ProjectRootManager.getInstance(myProject).getFileIndex();
          if (file != null && fileIndex.isInLibrarySource(file)) {
            PsiElement originalElement = classOwner.getOriginalElement();
            if (originalElement instanceof PsiFile) {
              PsiFile classFile = (PsiFile)originalElement;
              VirtualFile virtualClassFile = classFile.getVirtualFile();
              if (virtualClassFile != null && fileIndex.isInLibraryClasses(virtualClassFile)
                  && !classOwner.getManager().areElementsEquivalent(classOwner, classFile)
                  && classOwner.getManager().areElementsEquivalent(classOwner.getContainingDirectory(), classFile.getContainingDirectory())) {
                continue;
              }
            }
          }
        }

        if (fileInRoots(file)) {
          PsiClass[] classes = ReadAction.compute(classOwner::getClasses);
          if (classes.length == 1 && isClassForTreeNode(file, classes[0])) {
            result.add(new ClassTreeNode(myProject, classes[0], settings1, child.getChildren()));
          } else {
            result.add(new PsiClassOwnerTreeNode(classOwner, settings1, child.getChildren()));
          }
          continue;
        }
      }
      result.add(child);
    }
    return result;
  }

  private static boolean isClassForTreeNode(VirtualFile file, PsiClass psiClass) {
    if (psiClass == null || psiClass instanceof SyntheticElement) return false;
    if (file == null || file.getNameWithoutExtension().equals(psiClass.getName())) return true;
    Project project = psiClass.getProject();
    for (FileEditor fileEditor : FileEditorManager.getInstance(project).getAllEditors(file)) {
      if (fileEditor instanceof TextEditor) {
        TextEditor textEditor = (TextEditor)fileEditor;
        Template template = TemplateManager.getInstance(project).getActiveTemplate(textEditor.getEditor());
        if (template != null) return true; // only if refactoring is in progress
      }
    }
    return false;
  }

  private boolean fileInRoots(VirtualFile file) {
    ProjectFileIndex index = ProjectRootManager.getInstance(myProject).getFileIndex();
    return file != null && (index.isUnderSourceRootOfType(file, JavaModuleSourceRootTypes.SOURCES) || index.isInLibrary(file));
  }

  @Override
  public PsiElement getTopLevelElement(PsiElement element) {
    PsiFile baseRootFile = getBaseRootFile(element);
    if (baseRootFile == null) return null;

    if (!fileInRoots(baseRootFile.getVirtualFile())) return baseRootFile;

    PsiElement current = element;
    while (current != null) {
      if (isSelectable(current) || isTopLevelClass(current)) {
        break;
      }
      current = current.getParent();
    }

    if (current instanceof PsiClassOwner) {
      PsiClass[] classes = ((PsiClassOwner)current).getClasses();
      if (classes.length == 1 && !(classes[0] instanceof SyntheticElement) && isTopLevelClass(classes[0])) {
        current = classes[0];
      }
    }

    return current != null ? current : baseRootFile;
  }

  private static boolean isSelectable(PsiElement element) {
    if (element instanceof PsiFileSystemItem) return true;

    if (element instanceof PsiJavaModule) return true;

    if (element instanceof PsiField || element instanceof PsiClass || element instanceof PsiMethod) {
      return !(element instanceof PsiAnonymousClass || element.getParent() instanceof PsiAnonymousClass);
    }

    return false;
  }

  @Nullable
  private static PsiFile getBaseRootFile(PsiElement element) {
    PsiFile containingFile = element.getContainingFile();
    if (containingFile == null) return null;

    FileViewProvider viewProvider = containingFile.getViewProvider();
    return viewProvider.getPsi(viewProvider.getBaseLanguage());
  }

  private static boolean isTopLevelClass(PsiElement element) {
    return element instanceof PsiClass && ClassUtil.isTopLevelClass((PsiClass)element);
  }

  private static class PsiClassOwnerTreeNode extends PsiFileNode {
    @NotNull private final Collection<? extends AbstractTreeNode> myMandatoryChildren;

    PsiClassOwnerTreeNode(@NotNull PsiClassOwner classOwner,
                          ViewSettings settings,
                          @NotNull Collection<? extends AbstractTreeNode> mandatoryChildren) {
      super(classOwner.getProject(), classOwner, settings);
      myMandatoryChildren = mandatoryChildren;
    }

    @Override
    public Collection<AbstractTreeNode> getChildrenImpl() {
      List<AbstractTreeNode> result = new ArrayList<>(myMandatoryChildren);
      PsiFile value = getValue();
      if (value instanceof PsiClassOwner) {
        ViewSettings settings = getSettings();
        for (PsiClass aClass : ((PsiClassOwner)value).getClasses()) {
          if (!(aClass instanceof SyntheticElement)) {
            result.add(new ClassTreeNode(myProject, aClass, settings));
          }
        }
      }
      return result;
    }
  }
}