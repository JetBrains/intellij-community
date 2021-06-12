// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.daemon.impl;

import com.intellij.CommonBundle;
import com.intellij.codeEditor.JavaEditorFileSwapper;
import com.intellij.codeInsight.AttachSourcesProvider;
import com.intellij.ide.JavaUiBundle;
import com.intellij.ide.highlighter.JavaClassFileType;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.projectRoots.JavaSdkVersion;
import com.intellij.openapi.roots.LibraryOrderEntry;
import com.intellij.openapi.roots.OrderEntry;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.ui.configuration.LibrarySourceRootDetectorUtil;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.ListSeparator;
import com.intellij.openapi.ui.popup.PopupStep;
import com.intellij.openapi.ui.popup.util.BaseListPopupStep;
import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.impl.compiled.ClsParsingUtil;
import com.intellij.ui.EditorNotificationPanel;
import com.intellij.ui.EditorNotifications;
import com.intellij.ui.GuiUtils;
import com.intellij.util.SmartList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author Dmitry Avdeev
 */
public class AttachSourcesNotificationProvider extends EditorNotifications.Provider<EditorNotificationPanel> {
  private static final ExtensionPointName<AttachSourcesProvider> EXTENSION_POINT_NAME =
    new ExtensionPointName<>("com.intellij.attachSourcesProvider");

  private static final Key<EditorNotificationPanel> KEY = Key.create("add sources to class");

  public AttachSourcesNotificationProvider() {
    EXTENSION_POINT_NAME.addChangeListener(() -> {
      for (Project project : ProjectManager.getInstance().getOpenProjects()) {
        EditorNotifications.getInstance(project).updateNotifications(this);
      }
    }, null);
  }

  @NotNull
  @Override
  public Key<EditorNotificationPanel> getKey() {
    return KEY;
  }

  @Override
  public EditorNotificationPanel createNotificationPanel(@NotNull final VirtualFile file, @NotNull FileEditor fileEditor, @NotNull Project project) {
    if (file.getFileType() != JavaClassFileType.INSTANCE) return null;

    final EditorNotificationPanel panel = new EditorNotificationPanel(fileEditor);

    String text = JavaUiBundle.message("class.file.decompiled.text");
    String classInfo = getClassFileInfo(file);
    if (classInfo != null) text += ", " + classInfo;
    panel.setText(text);

    final VirtualFile sourceFile = JavaEditorFileSwapper.findSourceFile(project, file);
    if (sourceFile == null) {
      final List<LibraryOrderEntry> libraries = findLibraryEntriesForFile(file, project);
      if (libraries != null) {
        List<AttachSourcesProvider.AttachSourcesAction> actions = new ArrayList<>();

        PsiFile clsFile = PsiManager.getInstance(project).findFile(file);
        boolean hasNonLightAction = false;
        for (AttachSourcesProvider each : EXTENSION_POINT_NAME.getExtensionList()) {
          for (AttachSourcesProvider.AttachSourcesAction action : each.getActions(libraries, clsFile)) {
            if (hasNonLightAction) {
              if (action instanceof AttachSourcesProvider.LightAttachSourcesAction) {
                continue; // Don't add LightAttachSourcesAction if non light action exists.
              }
            }
            else {
              if (!(action instanceof AttachSourcesProvider.LightAttachSourcesAction)) {
                actions.clear(); // All previous actions is LightAttachSourcesAction and should be removed.
                hasNonLightAction = true;
              }
            }
            actions.add(action);
          }
        }

        actions.sort((o1, o2) -> o1.getName().compareToIgnoreCase(o2.getName()));

        AttachSourcesProvider.AttachSourcesAction defaultAction;
        if (findSourceFileInSameJar(file) != null) {
          defaultAction = new AttachJarAsSourcesAction(file);
        }
        else {
          defaultAction = new ChooseAndAttachSourcesAction(project, panel);
        }
        actions.add(defaultAction);

        String originalText = text;
        for (final AttachSourcesProvider.AttachSourcesAction action : actions) {
          String escapedName = GuiUtils.getTextWithoutMnemonicEscaping(action.getName());
          panel.createActionLabel(escapedName, () -> {
            List<LibraryOrderEntry> entries = findLibraryEntriesForFile(file, project);
            if (!Comparing.equal(libraries, entries)) {
              Messages.showErrorDialog(project, JavaUiBundle.message("can.t.find.library.for.0", file.getName()), CommonBundle.message("title.error"));
              return;
            }

            panel.setText(action.getBusyText());

            action.perform(entries).doWhenProcessed(() -> panel.setText(originalText));
          });
        }
      }
    }
    else {
      panel.createActionLabel(JavaUiBundle.message("class.file.open.source.action"), () -> {
        if (sourceFile.isValid()) {
          OpenFileDescriptor descriptor = new OpenFileDescriptor(project, sourceFile);
          FileEditorManager.getInstance(project).openTextEditor(descriptor, true);
        }
      });
    }

    return panel;
  }

  @Nullable
  private static String getClassFileInfo(VirtualFile file) {
    try {
      byte[] data = file.contentsToByteArray(false);
      if (data.length > 8) {
        try (DataInputStream stream = new DataInputStream(new ByteArrayInputStream(data))) {
          if (stream.readInt() == 0xCAFEBABE) {
            int minor = stream.readUnsignedShort();
            int major = stream.readUnsignedShort();
            StringBuilder info = new StringBuilder().append("bytecode version: ").append(major).append('.').append(minor);
            JavaSdkVersion sdkVersion = ClsParsingUtil.getJdkVersionByBytecode(major);
            if (sdkVersion != null) {
              info.append(" (Java ");
              info.append(sdkVersion.getDescription());
              if (sdkVersion.isAtLeast(JavaSdkVersion.JDK_11) && ClsParsingUtil.isPreviewLevel(minor)) info.append("-preview");
              info.append(')');
            }
            return info.toString();
          }
        }
      }
    }
    catch (IOException ignored) { }
    return null;
  }

  @Nullable
  private static List<LibraryOrderEntry> findLibraryEntriesForFile(VirtualFile file, @NotNull Project project) {
    List<LibraryOrderEntry> entries = null;

    ProjectFileIndex index = ProjectFileIndex.SERVICE.getInstance(project);
    for (OrderEntry entry : index.getOrderEntriesForFile(file)) {
      if (entry instanceof LibraryOrderEntry) {
        if (entries == null) entries = new SmartList<>();
        entries.add((LibraryOrderEntry)entry);
      }
    }

    return entries;
  }

  @Nullable
  private static VirtualFile findSourceFileInSameJar(VirtualFile classFile) {
    String name = classFile.getName();
    int i = name.indexOf('$');
    if (i != -1) name = name.substring(0, i);
    i = name.indexOf('.');
    if (i != -1) name = name.substring(0, i);
    return classFile.getParent().findChild(name + JavaFileType.DOT_DEFAULT_EXTENSION);
  }

  private static class AttachJarAsSourcesAction implements AttachSourcesProvider.AttachSourcesAction {
    private final VirtualFile myClassFile;

    AttachJarAsSourcesAction(VirtualFile classFile) {
      myClassFile = classFile;
    }

    @Override
    public String getName() {
      return JavaUiBundle.message("module.libraries.attach.sources.button");
    }

    @Override
    public String getBusyText() {
      return JavaUiBundle.message("library.attach.sources.action.busy.text");
    }

    @Override
    public ActionCallback perform(List<LibraryOrderEntry> orderEntriesContainingFile) {
      final List<Library.ModifiableModel> modelsToCommit = new ArrayList<>();
      for (LibraryOrderEntry orderEntry : orderEntriesContainingFile) {
        final Library library = orderEntry.getLibrary();
        if (library == null) continue;
        final VirtualFile root = findRoot(library);
        if (root == null) continue;
        final Library.ModifiableModel model = library.getModifiableModel();
        model.addRoot(root, OrderRootType.SOURCES);
        modelsToCommit.add(model);
      }
      if (modelsToCommit.isEmpty()) return ActionCallback.REJECTED;
      WriteAction.runAndWait(() -> {
        for (Library.ModifiableModel model : modelsToCommit) {
          model.commit();
        }
      });

      return ActionCallback.DONE;
    }

    @Nullable
    private VirtualFile findRoot(Library library) {
      for (VirtualFile classesRoot : library.getFiles(OrderRootType.CLASSES)) {
        if (VfsUtilCore.isAncestor(classesRoot, myClassFile, true)) {
          return classesRoot;
        }
      }
      return null;
    }
  }

  private static class ChooseAndAttachSourcesAction implements AttachSourcesProvider.AttachSourcesAction {
    private final Project myProject;
    private final JComponent myParentComponent;

    ChooseAndAttachSourcesAction(Project project, JComponent parentComponent) {
      myProject = project;
      myParentComponent = parentComponent;
    }

    @Override
    public String getName() {
      return JavaUiBundle.message("module.libraries.choose.sources.button");
    }

    @Override
    public String getBusyText() {
      return JavaUiBundle.message("library.attach.sources.action.busy.text");
    }

    @Override
    public ActionCallback perform(final List<LibraryOrderEntry> libraries) {
      FileChooserDescriptor descriptor = FileChooserDescriptorFactory.createMultipleJavaPathDescriptor();
      descriptor.setTitle(JavaUiBundle.message("library.attach.sources.action"));
      descriptor.setDescription(JavaUiBundle.message("library.attach.sources.description"));
      Library firstLibrary = libraries.get(0).getLibrary();
      VirtualFile[] roots = firstLibrary != null ? firstLibrary.getFiles(OrderRootType.CLASSES) : VirtualFile.EMPTY_ARRAY;
      VirtualFile[] candidates = FileChooser.chooseFiles(descriptor, myProject, roots.length == 0 ? null : VfsUtil.getLocalFile(roots[0]));
      if (candidates.length == 0) return ActionCallback.REJECTED;
      VirtualFile[] files = LibrarySourceRootDetectorUtil.scanAndSelectDetectedJavaSourceRoots(myParentComponent, candidates);
      if (files.length == 0) return ActionCallback.REJECTED;

      final Map<Library, LibraryOrderEntry> librariesToAppendSourcesTo = new HashMap<>();
      for (LibraryOrderEntry library : libraries) {
        librariesToAppendSourcesTo.put(library.getLibrary(), library);
      }
      if (librariesToAppendSourcesTo.size() == 1) {
        appendSources(firstLibrary, files);
      }
      else {
        librariesToAppendSourcesTo.put(null, null);
        String title = JavaUiBundle.message("library.choose.one.to.attach");
        List<LibraryOrderEntry> entries = new ArrayList<>(librariesToAppendSourcesTo.values());
        JBPopupFactory.getInstance().createListPopup(new BaseListPopupStep<>(title, entries) {
          @Override
          public ListSeparator getSeparatorAbove(LibraryOrderEntry value) {
            return value == null ? new ListSeparator() : null;
          }

          @NotNull
          @Override
          public String getTextFor(LibraryOrderEntry value) {
            return value == null ? CommonBundle.message("action.text.all")
                                 : value.getPresentableName() + " (" + value.getOwnerModule().getName() + ")";
          }

          @Override
          public PopupStep<?> onChosen(LibraryOrderEntry libraryOrderEntry, boolean finalChoice) {
            if (libraryOrderEntry != null) {
              appendSources(libraryOrderEntry.getLibrary(), files);
            }
            else {
              for (Library libOrderEntry : librariesToAppendSourcesTo.keySet()) {
                if (libOrderEntry != null) {
                  appendSources(libOrderEntry, files);
                }
              }
            }
            return FINAL_CHOICE;
          }
        }).showCenteredInCurrentWindow(myProject);
      }

      return ActionCallback.DONE;
    }

    private static void appendSources(final Library library, final VirtualFile[] files) {
      ApplicationManager.getApplication().runWriteAction(() -> {
        Library.ModifiableModel model = library.getModifiableModel();
        for (VirtualFile virtualFile : files) {
          model.addRoot(virtualFile, OrderRootType.SOURCES);
        }
        model.commit();
      });
    }
  }
}
