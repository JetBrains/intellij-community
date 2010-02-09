/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.codeInsight.daemon.impl;

import com.intellij.ProjectTopics;
import com.intellij.ide.highlighter.JavaClassFileType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx;
import com.intellij.openapi.fileEditor.impl.EditorWindow;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.ui.configuration.PathUIUtils;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.impl.compiled.ClsClassImpl;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.ui.EditorNotificationPanel;
import com.intellij.ui.EditorNotifications;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @author Dmitry Avdeev
 */
public class AddSourcesNotificationProvider implements EditorNotifications.Provider<EditorNotificationPanel> {

  private static final Key<EditorNotificationPanel> KEY = Key.create("add sources to class");

  private final Project myProject;

  public AddSourcesNotificationProvider(Project project, final EditorNotifications notifications) {
    myProject = project;
    myProject.getMessageBus().connect(project).subscribe(ProjectTopics.PROJECT_ROOTS, new ModuleRootListener() {

      public void beforeRootsChange(ModuleRootEvent event) {
      }

      public void rootsChanged(ModuleRootEvent event) {
        notifications.updateAllNotifications();
      }
    });
  }

  public Key<EditorNotificationPanel> getKey() {
    return KEY;
  }

  public EditorNotificationPanel createNotificationPanel(final VirtualFile file) {

    if (file.getFileType() != JavaClassFileType.INSTANCE) return null;
    final Library library = findLibrary(file);
    if (library == null) return null;

    PsiFile psiFile = PsiManager.getInstance(myProject).findFile(file);
    if (!(psiFile instanceof PsiJavaFile)) return null;
    PsiClass[] classes = ((PsiJavaFile)psiFile).getClasses();
    if (classes.length == 0) return null;
    final String fqn = classes[0].getQualifiedName();
    if (fqn == null) return null;

    final EditorNotificationPanel panel = new EditorNotificationPanel();
    panel.setText("Class sources not found");
    panel.createActionLabel("Attach sources", new Runnable() {
      public void run() {
        final Library library = findLibrary(file);
        if (library == null) {
          Messages.showErrorDialog(myProject, "Cannot find library for " + StringUtil.getShortName(fqn), "Error");
          return;
        }
        FileChooserDescriptor descriptor = new FileChooserDescriptor(false, true, true, false, true, true);
        descriptor.setTitle(ProjectBundle.message("library.attach.sources.action"));
        descriptor.setDescription(ProjectBundle.message("library.attach.sources.description"));
        VirtualFile[] roots = library.getFiles(OrderRootType.CLASSES);
        VirtualFile[] candidates = FileChooser.chooseFiles(myProject, descriptor, roots.length == 0 ? null : roots[0]);
        final VirtualFile[] files = PathUIUtils.scanAndSelectDetectedJavaSourceRoots(myProject, candidates);
        if (files.length == 0) {
          return;
        }
        ApplicationManager.getApplication().runWriteAction(new Runnable() {
          public void run() {
            final Library library = findLibrary(file);
            assert library != null;
            Library.ModifiableModel model = library.getModifiableModel();
            for (VirtualFile virtualFile : files) {
              model.addRoot(virtualFile, OrderRootType.SOURCES);
            }
            model.commit();
          }
        });

        PsiClass clsClass = JavaPsiFacade.getInstance(myProject).findClass(fqn, GlobalSearchScope.allScope(myProject));
        if (!(clsClass instanceof ClsClassImpl)) {
          return;
        }
        PsiClass sourceClass = ((ClsClassImpl)clsClass).getSourceMirrorClass();
        if (sourceClass == null) {
          return;
        }
        VirtualFile newFile = sourceClass.getContainingFile().getVirtualFile();
        assert newFile != null;

        FileEditorManagerEx manager = FileEditorManagerEx.getInstanceEx(myProject);
        EditorWindow[] windows = manager.getWindows();
        for (EditorWindow window : windows) {
          int index = window.findFileIndex(file);
          if (index != -1) {
            manager.closeFile(file, window);
            try {
              newFile.putUserData(EditorWindow.INITIAL_INDEX_KEY, index);
              manager.openFile(newFile, true);
            }
            finally {
              newFile.putUserData(EditorWindow.INITIAL_INDEX_KEY, null);
            }
          }
        }
      }
    });
    return panel;
  }

  @Nullable
  private Library findLibrary(VirtualFile file) {
    List<OrderEntry> entries = ProjectRootManager.getInstance(myProject).getFileIndex().getOrderEntriesForFile(file);
    for (OrderEntry entry : entries) {
      if (entry instanceof LibraryOrderEntry) {
        return ((LibraryOrderEntry)entry).getLibrary();
      }
    }
    return null;
  }
}
