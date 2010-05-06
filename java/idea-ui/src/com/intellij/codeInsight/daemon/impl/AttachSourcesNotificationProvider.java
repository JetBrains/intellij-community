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
import com.intellij.codeEditor.JavaEditorFileSwapper;
import com.intellij.codeInsight.AttachSourcesProvider;
import com.intellij.ide.highlighter.JavaClassFileType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.ui.configuration.PathUIUtils;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.ListSeparator;
import com.intellij.openapi.ui.popup.PopupStep;
import com.intellij.openapi.ui.popup.util.BaseListPopupStep;
import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.ui.EditorNotificationPanel;
import com.intellij.ui.EditorNotifications;
import com.intellij.ui.GuiUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.*;

/**
 * @author Dmitry Avdeev
 */
public class AttachSourcesNotificationProvider implements EditorNotifications.Provider<EditorNotificationPanel> {
  private static final ExtensionPointName<AttachSourcesProvider> EXTENSION_POINT_NAME
    = new ExtensionPointName<AttachSourcesProvider>("com.intellij.attachSourcesProvider");

  private static final Key<EditorNotificationPanel> KEY = Key.create("add sources to class");

  private final Project myProject;

  public AttachSourcesNotificationProvider(Project project, final EditorNotifications notifications) {
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
    final List<LibraryOrderEntry> libraries = findOrderEntriesContainingFile(file);
    if (libraries == null) return null;

    PsiFile psiFile = PsiManager.getInstance(myProject).findFile(file);
    final String fqn = JavaEditorFileSwapper.getFQN(psiFile);
    if (fqn == null) return null;

    if (JavaEditorFileSwapper.findSourceFile(myProject, file) != null) return null;

    final EditorNotificationPanel panel = new EditorNotificationPanel();
    panel.setText(ProjectBundle.message("library.sources.not.found"));

    final AttachSourcesProvider.AttachSourcesAction defaultAction = createDefaultAction();

    TreeSet<AttachSourcesProvider.AttachSourcesAction> actions = new TreeSet<AttachSourcesProvider.AttachSourcesAction>(
      new Comparator<AttachSourcesProvider.AttachSourcesAction>() {
        public int compare(AttachSourcesProvider.AttachSourcesAction o1, AttachSourcesProvider.AttachSourcesAction o2) {
          if (o1 == defaultAction) return 1;
          if (o2 == defaultAction) return -1;
          return o1.getName().compareToIgnoreCase(o2.getName());
        }
      }
    );

    actions.add(defaultAction);
    for (AttachSourcesProvider each : Extensions.getExtensions(EXTENSION_POINT_NAME)) {
      actions.addAll(each.getActions(libraries, psiFile));
    }

    for (final AttachSourcesProvider.AttachSourcesAction each : actions) {
      panel.createActionLabel(GuiUtils.getTextWithoutMnemonicEscaping(each.getName()), new Runnable() {
        public void run() {
          if (!Comparing.equal(libraries, findOrderEntriesContainingFile(file))) {
            Messages.showErrorDialog(myProject, "Cannot find library for " + StringUtil.getShortName(fqn), "Error");
            return;
          }

          panel.setText(each.getBusyText());

          Runnable onFinish = new Runnable() {
            public void run() {
              SwingUtilities.invokeLater(new Runnable() {
                public void run() {
                  panel.setText(ProjectBundle.message("library.sources.not.found"));
                }
              });
            }
          };
          ActionCallback callback = each.perform(findOrderEntriesContainingFile(file));
          callback.doWhenRejected(onFinish);
          callback.doWhenDone(onFinish);
        }
      });
    }

    return panel;
  }

  private AttachSourcesProvider.AttachSourcesAction createDefaultAction() {
    return new AttachSourcesProvider.AttachSourcesAction() {
      public String getName() {
        return ProjectBundle.message("module.libraries.attach.sources.button");
      }

      public String getBusyText() {
        return ProjectBundle.message("library.attach.sources.action.busy.text");
      }

      public ActionCallback perform(final List<LibraryOrderEntry> libraries) {
        FileChooserDescriptor descriptor = new FileChooserDescriptor(false, true, true, false, true, true);
        descriptor.setTitle(ProjectBundle.message("library.attach.sources.action"));
        descriptor.setDescription(ProjectBundle.message("library.attach.sources.description"));
        final Library firstLibrary = libraries.get(0).getLibrary();
        VirtualFile[] roots = firstLibrary != null ? firstLibrary.getFiles(OrderRootType.CLASSES) : VirtualFile.EMPTY_ARRAY;
        VirtualFile[] candidates = FileChooser.chooseFiles(myProject, descriptor, roots.length == 0 ? null : roots[0]);
        final VirtualFile[] files = PathUIUtils.scanAndSelectDetectedJavaSourceRoots(myProject, candidates);
        if (files.length == 0) {
          return new ActionCallback.Rejected();
        }
        if (libraries.size() == 1) {
          appendSources(firstLibrary, files);
        } else {
          final List<LibraryOrderEntry> librariesToAppendSourcesTo = new ArrayList<LibraryOrderEntry>(libraries);
          librariesToAppendSourcesTo.add(null);
          JBPopupFactory.getInstance().createListPopup(new BaseListPopupStep<LibraryOrderEntry>("<html><body>Multiple libraries contain file.<br> Choose libraries to attach sources to</body></html>", librariesToAppendSourcesTo){
            @Override
            public ListSeparator getSeparatorAbove(LibraryOrderEntry value) {
              return value == null ? new ListSeparator() : null;
            }

            @NotNull
            @Override
            public String getTextFor(LibraryOrderEntry value) {
              if (value != null) {
                return value.getPresentableName() + " (" + value.getOwnerModule().getName() + ")";
              }
              else {
                return "All";
              }
            }

            @Override
            public PopupStep onChosen(LibraryOrderEntry libraryOrderEntry, boolean finalChoice) {
              if (libraryOrderEntry != null) {
                appendSources(libraryOrderEntry.getLibrary(), files);
              } else {
                for (LibraryOrderEntry libOrderEntry : libraries) {
                  appendSources(libOrderEntry.getLibrary(), files);
                }
              }
              return FINAL_CHOICE;
            }
          }).showCenteredInCurrentWindow(myProject);
        }

        return new ActionCallback.Done();
      }
    };
  }

  private static void appendSources(final Library library, final VirtualFile[] files) {
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        Library.ModifiableModel model = library.getModifiableModel();
        for (VirtualFile virtualFile : files) {
          model.addRoot(virtualFile, OrderRootType.SOURCES);
        }
        model.commit();
      }
    });
  }

  @Nullable
  private List<LibraryOrderEntry> findOrderEntriesContainingFile(VirtualFile file) {
    final List<LibraryOrderEntry> libs = new ArrayList<LibraryOrderEntry>();
    List<OrderEntry> entries = ProjectRootManager.getInstance(myProject).getFileIndex().getOrderEntriesForFile(file);
    for (OrderEntry entry : entries) {
      if (entry instanceof LibraryOrderEntry) {
        libs.add ((LibraryOrderEntry)entry);
      }
    }
    return libs.isEmpty() ? null : libs;
  }
}
