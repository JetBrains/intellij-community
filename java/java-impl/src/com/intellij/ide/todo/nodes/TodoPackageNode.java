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
package com.intellij.ide.todo.nodes;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.projectView.impl.nodes.PackageElement;
import com.intellij.ide.projectView.impl.nodes.PackageElementNode;
import com.intellij.ide.todo.TodoFileDirAndModuleComparator;
import com.intellij.ide.todo.TodoTreeBuilder;
import com.intellij.ide.todo.TodoTreeStructure;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.psi.JavaDirectoryService;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiPackage;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public final class TodoPackageNode extends PackageElementNode {
  private final TodoTreeBuilder myBuilder;
  @Nullable private final String myPresentationName;

  public TodoPackageNode(@NotNull Project project,
                         @NotNull PackageElement element,
                         TodoTreeBuilder builder) {
    this(project, element, builder,null);
  }

  public TodoPackageNode(@NotNull Project project,
                         @NotNull PackageElement element,
                         TodoTreeBuilder builder,
                         @Nullable String name) {
    super(project, element, ViewSettings.DEFAULT);
    myBuilder = builder;
    if (name == null){
      final PsiPackage aPackage = element.getPackage();
      myPresentationName = aPackage.getName();
    }
    else {
      myPresentationName = name;
    }
  }

  @Override
  protected void update(@NotNull PresentationData data) {
    super.update(data);
    final PackageElement packageElement = getValue();

    try {
      if (packageElement == null || !packageElement.getPackage().isValid()) {
        setValue(null);
        return;
      }

      int fileCount = getFileCount(packageElement);
      if (fileCount == 0){
        setValue(null);
        return;
      }

      PsiPackage aPackage = packageElement.getPackage();
      String newName;
      if (getStructure().areFlattenPackages()) {
        newName = aPackage.getQualifiedName();
      }
      else {
        newName = myPresentationName != null ? myPresentationName : "";
      }

      int todoItemCount = getTodoItemCount(packageElement);

      data.setLocationString(IdeBundle.message("node.todo.group", todoItemCount));
      data.setPresentableText(newName);
    }
    catch (IndexNotReadyException e) {
      LOG.info(e);
      data.setPresentableText("N/A");
    }
  }

  @Override
  public void apply(@NotNull Map<String, String> info) {
    info.put("toDoFileCount", String.valueOf(getFileCount(getValue())));
    info.put("toDoItemCount", String.valueOf(getTodoItemCount(getValue())));
  }

  private int getFileCount(final PackageElement packageElement) {
    int count = 0;
    if (getSettings().isFlattenPackages()) {
      final PsiPackage aPackage = packageElement.getPackage();
      final Module module = packageElement.getModule();
      final GlobalSearchScope scope =
        module != null ? GlobalSearchScope.moduleScope(module) : GlobalSearchScope.projectScope(aPackage.getProject());
      final PsiDirectory[] directories = aPackage.getDirectories(scope);
      for (PsiDirectory directory : directories) {
        Iterator<PsiFile> iterator = myBuilder.getFilesUnderDirectory(directory);
        while (iterator.hasNext()) {
          PsiFile psiFile = iterator.next();
          if (getStructure().accept(psiFile)) count++;
        }
      }
    }
    else {
      Iterator<PsiFile> iterator = getFiles(packageElement);
      while (iterator.hasNext()) {
        PsiFile psiFile = iterator.next();
        if (getStructure().accept(psiFile)) {
          count++;
        }
      }
    }
    return count;
  }

  public int getTodoItemCount(PackageElement packageElement) {
    int count = 0;
    if (getSettings().isFlattenPackages()){
        final PsiPackage aPackage = packageElement.getPackage();
        final Module module = packageElement.getModule();
        GlobalSearchScope scope = module != null ? GlobalSearchScope.moduleScope(module) : GlobalSearchScope.projectScope(aPackage.getProject());
        final PsiDirectory[] directories = aPackage.getDirectories(scope);
        for (PsiDirectory directory : directories) {
          Iterator<PsiFile> iterator = myBuilder.getFilesUnderDirectory(directory);
          while(iterator.hasNext()){
            PsiFile psiFile = iterator.next();
            count+=getStructure().getTodoItemCount(psiFile);
          }
        }
      } else {
        Iterator<PsiFile> iterator = getFiles(packageElement);
        while(iterator.hasNext()){
          PsiFile psiFile = iterator.next();
          count+=getStructure().getTodoItemCount(psiFile);
        }
      }
    return count;
  }

  private TodoTreeStructure getStructure() {
    return myBuilder.getTodoTreeStructure();
  }

  @Override
  @NotNull
  public Collection<AbstractTreeNode> getChildren() {
    ArrayList<AbstractTreeNode> children = new ArrayList<>();
    final Project project = getProject();
    final ProjectFileIndex projectFileIndex = ProjectRootManager.getInstance(Objects.requireNonNull(project)).getFileIndex();
    PackageElement value = getValue();
    if (value == null) return children;
    final PsiPackage psiPackage = value.getPackage();
    final Module module = value.getModule();
    final Iterator<PsiFile> iterator = getFiles(value);
    if (!getStructure().getIsFlattenPackages()) {
      while (iterator.hasNext()) {
        final PsiFile psiFile = iterator.next();
        final Module psiFileModule = projectFileIndex.getModuleForFile(psiFile.getVirtualFile());
        //group by module
        if (module != null && psiFileModule != null && !module.equals(psiFileModule)){
          continue;
        }
        final GlobalSearchScope scope = module != null ? GlobalSearchScope.moduleScope(module) : GlobalSearchScope.projectScope(project);
        // Add files
        final PsiDirectory containingDirectory = psiFile.getContainingDirectory();
        TodoFileNode todoFileNode = new TodoFileNode(project, psiFile, myBuilder, false);
        if (ArrayUtil.find(psiPackage.getDirectories(scope), containingDirectory) > -1) {
          if (!children.contains(todoFileNode)) {
            children.add(todoFileNode);
          }
          continue;
        }
        // Add packages
        PsiDirectory _dir = psiFile.getContainingDirectory();
        while (_dir != null) {
          final PsiDirectory parentDirectory = _dir.getParentDirectory();
          if (parentDirectory != null){
            PsiPackage _package = JavaDirectoryService.getInstance().getPackage(_dir);
            if (_package != null && psiPackage.equals(_package.getParentPackage())) {
              _package = TodoJavaTreeHelper.findNonEmptyPackage(_package, module, project, myBuilder, scope); //compact empty middle packages
              final String name = psiPackage.equals(Objects.requireNonNull(_package).getParentPackage())
                                  ? null //non compacted
                                  : _package.getQualifiedName().substring(psiPackage.getQualifiedName().length() + 1);
              TodoPackageNode todoPackageNode = new TodoPackageNode(project, new PackageElement(module, _package, false), myBuilder, name);
              if (!children.contains(todoPackageNode)) {
                children.add(todoPackageNode);
                break;
              }
            }
          }
          _dir = parentDirectory;
        }
      }
    }
    else { // flatten packages
      while (iterator.hasNext()) {
        final PsiFile psiFile = iterator.next();
         //group by module
        final Module psiFileModule = projectFileIndex.getModuleForFile(psiFile.getVirtualFile());
        if (module != null && psiFileModule != null && !module.equals(psiFileModule)){
          continue;
        }
        final PsiDirectory _dir = psiFile.getContainingDirectory();
        // Add files
        TodoFileNode todoFileNode = new TodoFileNode(getProject(), psiFile, myBuilder, false);
        if (ArrayUtil.find(psiPackage.getDirectories(), _dir) > -1 && !children.contains(todoFileNode)) {
          children.add(todoFileNode);
        }
      }
    }
    Collections.sort(children, TodoFileDirAndModuleComparator.INSTANCE);
    return children;
  }

  /**
   * @return read-only iterator of all valid PSI files that can have T.O.D.O items
   *         and which are located under specified {@code psiDirctory}.
   */
  public Iterator<PsiFile> getFiles(PackageElement packageElement) {
    ArrayList<PsiFile> psiFileList = new ArrayList<>();
    GlobalSearchScope scope = packageElement.getModule() != null ? GlobalSearchScope.moduleScope(packageElement.getModule()) :
                              GlobalSearchScope.projectScope(myProject);
    final PsiDirectory[] directories = packageElement.getPackage().getDirectories(scope);
    for (PsiDirectory directory : directories) {
      Iterator<PsiFile> files = myBuilder.getFiles(directory, false);
      while (files.hasNext()) {
        psiFileList.add(files.next());
      }
    }
    return psiFileList.iterator();
  }

  @Override
  public int getWeight() {
    return 3;
  }
}

