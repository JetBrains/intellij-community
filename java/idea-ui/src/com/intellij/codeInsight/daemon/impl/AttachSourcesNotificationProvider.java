// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl;

import com.intellij.CommonBundle;
import com.intellij.codeEditor.JavaEditorFileSwapper;
import com.intellij.codeInsight.AttachSourcesProvider;
import com.intellij.codeInsight.AttachSourcesProviderFilter;
import com.intellij.ide.JavaUiBundle;
import com.intellij.ide.highlighter.JavaClassFileType;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.java.JavaPluginDisposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.externalSystem.statistics.ExternalSystemSourceAttachCollector;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.fileTypes.FileTypeRegistry;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.JavaSdkVersion;
import com.intellij.openapi.roots.LibraryOrderEntry;
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
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.platform.backend.workspace.WorkspaceModel;
import com.intellij.platform.workspace.jps.entities.LibraryEntity;
import com.intellij.platform.workspace.jps.entities.ModuleEntity;
import com.intellij.platform.workspace.storage.ImmutableEntityStorage;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.impl.compiled.ClsParsingUtil;
import com.intellij.psi.util.JavaMultiReleaseUtil;
import com.intellij.ui.EditorNotificationPanel;
import com.intellij.ui.EditorNotificationProvider;
import com.intellij.ui.GuiUtils;
import com.intellij.util.concurrency.NonUrgentExecutor;
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread;
import com.intellij.util.concurrency.annotations.RequiresEdt;
import com.intellij.util.concurrency.annotations.RequiresReadLock;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.workspaceModel.ide.legacyBridge.LibraryBridgesKt;
import com.intellij.workspaceModel.ide.legacyBridge.ModuleBridges;
import kotlin.sequences.SequencesKt;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.VisibleForTesting;

import javax.swing.JComponent;
import javax.swing.SwingUtilities;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * @author Dmitry Avdeev
 */
@VisibleForTesting
public final class AttachSourcesNotificationProvider implements EditorNotificationProvider {

  private static final ExtensionPointName<AttachSourcesProvider> EXTENSION_POINT_NAME =
    new ExtensionPointName<>("com.intellij.attachSourcesProvider");

  @Override
  public @Nullable Function<? super @NotNull FileEditor, ? extends @Nullable JComponent> collectNotificationData(@NotNull Project project,
                                                                                                                @NotNull VirtualFile file) {
    if (!FileTypeRegistry.getInstance().isFileOfType(file, JavaClassFileType.INSTANCE)) {
      return null;
    }

    String classFileInfo = getTextWithClassFileInfo(file);
    Function<? super FileEditor, ? extends EditorNotificationPanel> notificationPanelCreator = fileEditor ->
      new EditorNotificationPanel(fileEditor, EditorNotificationPanel.Status.Info)
        .text(classFileInfo);

    VirtualFile sourceFile = JavaEditorFileSwapper.findSourceFile(project, file);
    if (sourceFile != null) {
      return notificationPanelCreator.andThen(panel -> {
        appendOpenFileAction(project, panel, sourceFile, JavaUiBundle.message("class.file.open.source.action"));
        return panel;
      });
    }

    Collection<LibraryEntity> libraries = findLibraryEntitiesForFile(file, project);
    if (libraries.isEmpty()) {
      return notificationPanelCreator;
    }

    PsiFile psiFile = PsiManager.getInstance(project).findFile(file);

    VirtualFile baseFile = JavaMultiReleaseUtil.findBaseFile(file);
    if (baseFile != null) {
      VirtualFile baseSource = JavaEditorFileSwapper.findSourceFile(project, baseFile);
      if (baseSource != null) {
        return notificationPanelCreator.andThen(panel -> {
          appendOpenFileAction(project, panel, baseSource, JavaUiBundle.message("class.file.open.source.version.specific.action"));
          return panel;
        });
      }
    }

    List<? extends AttachSourcesProvider.AttachSourcesAction> actionsByFile = psiFile != null ?
                                                                              collectActions(libraries, psiFile) :
                                                                              List.of();

    boolean sourceFileIsInSameJar = sourceFileIsInSameJar(file);
    return notificationPanelCreator.andThen(panel -> {
      List<AttachSourcesProvider.AttachSourcesAction> actions = new ArrayList<>(actionsByFile);
      AttachSourcesProvider.AttachSourcesAction defaultAction = sourceFileIsInSameJar ?
                                                                new AttachJarAsSourcesAction(file) :
                                                                new ChooseAndAttachSourcesAction(project, panel);
      actions.add(defaultAction);

      for (AttachSourcesProvider.AttachSourcesAction action : actions) {
        panel.createActionLabel(GuiUtils.getTextWithoutMnemonicEscaping(action.getName()), () -> {
          findLibraryEntitiesForFile(project, file, libraries, entries -> {
            String originalText = panel.getText();
            panel.setText(action.getBusyText());

            final long started = System.currentTimeMillis();
            final ActionCallback callback = action.perform(entries, project);
            callback.doWhenProcessed(() -> {
              panel.setText(originalText);
              if (psiFile != null) {
                ExternalSystemSourceAttachCollector.onSourcesAttached(project, action.getClass(), psiFile.getLanguage(), callback.isDone(),
                                                                      System.currentTimeMillis() - started);
              }
            });
          });
        });
      }

      return panel;
    });
  }

  private static void appendOpenFileAction(@NotNull Project project, EditorNotificationPanel panel, VirtualFile sourceFile,
                                           @NotNull @Nls String title) {
    panel.createActionLabel(title, () -> {
      if (sourceFile.isValid()) {
        OpenFileDescriptor descriptor = new OpenFileDescriptor(project, sourceFile);
        FileEditorManager.getInstance(project).openTextEditor(descriptor, true);
      }
    });
  }

  @SuppressWarnings("IncorrectParentDisposable")
  @RequiresEdt
  private static void findLibraryEntitiesForFile(@NotNull Project project,
                                                 @NotNull VirtualFile file,
                                                 @NotNull Collection<LibraryEntity> originalLibraries,
                                                 @NotNull Consumer<? super Collection<LibraryEntity>> uiThreadAction) {
    ReadAction.nonBlocking(() -> {
        Collection<LibraryEntity> libraries = findLibraryEntitiesForFile(file, project);
        if (Comparing.equal(originalLibraries, libraries)) {
          return libraries;
        }

        throw new RuntimeException(JavaUiBundle.message("can.t.find.library.for.0", file.getName()));
      })
      .expireWith(JavaPluginDisposable.getInstance(project))
      .expireWhen(() -> !file.isValid())
      .coalesceBy(file, project)
      .finishOnUiThread(ModalityState.current(), uiThreadAction)
      .submit(NonUrgentExecutor.getInstance())
      .onError(rejected -> {
        if (rejected instanceof CancellationException) {
          return;
        }

        SwingUtilities.invokeLater(() -> {
          Messages.showErrorDialog(project,
                                   rejected.getLocalizedMessage(),
                                   CommonBundle.message("title.error"));
        });
      });
  }

  private static @NotNull List<? extends AttachSourcesProvider.AttachSourcesAction> collectActions(@NotNull Collection<LibraryEntity> libraries,
                                                                                                   @NotNull PsiFile classFile) {
    List<AttachSourcesProvider.AttachSourcesAction> actions = new ArrayList<>();

    boolean hasNonLightAction = false;
    for (AttachSourcesProvider provider : EXTENSION_POINT_NAME.getExtensionList()) {
      if (!AttachSourcesProviderFilter.isProviderApplicable(provider, libraries, classFile)) {
        continue;
      }
      for (AttachSourcesProvider.AttachSourcesAction action : provider.getLibrariesActions(libraries, classFile)) {
        if (hasNonLightAction) {
          if (action instanceof AttachSourcesProvider.LightAttachSourcesAction) {
            continue; // Don't add LightAttachSourcesAction if non-light action exists.
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
    return Collections.unmodifiableList(actions);
  }

  @RequiresBackgroundThread
  private static @NotNull @NlsContexts.Label String getTextWithClassFileInfo(@NotNull VirtualFile file) {
    LanguageLevel level = JavaMultiReleaseUtil.getVersion(file);
    @Nls StringBuilder info = new StringBuilder();
    if (level != null) {
      info.append(JavaUiBundle.message("class.file.multi.release.decompiled.text", level.feature()));
    } else {
      info.append(JavaUiBundle.message("class.file.decompiled.text"));
    }

    try {
      byte[] data = file.contentsToByteArray(false);
      if (data.length > 8) {
        try (DataInputStream stream = new DataInputStream(new ByteArrayInputStream(data))) {
          if (stream.readInt() == 0xCAFEBABE) {
            int minor = stream.readUnsignedShort();
            int major = stream.readUnsignedShort();
            info.append(", ")
              .append(JavaUiBundle.message("class.file.decompiled.bytecode.version.text", major, minor));

            JavaSdkVersion sdkVersion = ClsParsingUtil.getJdkVersionByBytecode(major);
            if (sdkVersion != null) {
              info.append(" ")
                .append(JavaUiBundle.message("class.file.decompiled.sdk.version.text",
                                             getSdkDescription(sdkVersion, ClsParsingUtil.isPreviewLevel(minor))));
            }
          }
        }
      }
    }
    catch (IOException ignored) { }

    return info.toString();
  }

  private static @NlsSafe @NotNull String getSdkDescription(@NotNull JavaSdkVersion sdkVersion,
                                                            boolean isPreview) {
    return sdkVersion.getDescription() +
           (sdkVersion.isAtLeast(JavaSdkVersion.JDK_11) && isPreview ? "-preview" : "");
  }

  @RequiresReadLock
  private static @NotNull Collection<LibraryEntity> findLibraryEntitiesForFile(@NotNull VirtualFile file,
                                                                               @NotNull Project project) {

    return ProjectFileIndex.getInstance(project).findContainingLibraries(file);
  }

  private static boolean sourceFileIsInSameJar(@NotNull VirtualFile classFile) {
    String name = classFile.getName();
    int i = name.indexOf('$');
    if (i != -1) name = name.substring(0, i);
    i = name.indexOf('.');
    if (i != -1) name = name.substring(0, i);
    return classFile.getParent().findChild(name + JavaFileType.DOT_DEFAULT_EXTENSION) != null;
  }

  private static class AttachJarAsSourcesAction implements AttachSourcesProvider.AttachSourcesAction {

    private final @NotNull VirtualFile myClassFile;

    AttachJarAsSourcesAction(@NotNull VirtualFile classFile) {
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
    public @NotNull ActionCallback perform(@NotNull List<? extends LibraryOrderEntry> orderEntriesContainingFile) {
      List<Library> libraries = ContainerUtil.mapNotNull(orderEntriesContainingFile, orderEntry -> orderEntry.getLibrary());
      return performInternal(libraries);
    }

    @Override
    public @NotNull ActionCallback perform(@NotNull Collection<LibraryEntity> libraryEntities, @NotNull Project myProject) {
      ImmutableEntityStorage currentSnapshot = WorkspaceModel.getInstance(myProject).getCurrentSnapshot();
      List<Library> libraries = ContainerUtil.mapNotNull(libraryEntities, entity -> LibraryBridgesKt.findLibraryBridge(entity, currentSnapshot));
      return performInternal(libraries);
    }

    private @NotNull ActionCallback performInternal(@NotNull List<Library> libraries) {
      final List<Library.ModifiableModel> modelsToCommit = new ArrayList<>();
      for (Library library : libraries) {
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

    private @Nullable VirtualFile findRoot(Library library) {
      for (VirtualFile classesRoot : library.getFiles(OrderRootType.CLASSES)) {
        if (VfsUtilCore.isAncestor(classesRoot, myClassFile, true)) {
          return classesRoot;
        }
      }
      return null;
    }
  }

  private static class ChooseAndAttachSourcesAction implements AttachSourcesProvider.AttachSourcesAction {

    private final @NotNull Project myProject;
    private final @NotNull JComponent myParentComponent;

    ChooseAndAttachSourcesAction(@NotNull Project project,
                                 @NotNull JComponent parentComponent) {
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
    public @NotNull ActionCallback perform(@NotNull Collection<LibraryEntity> libraryEntities, @NotNull Project project) {
      ImmutableEntityStorage currentSnapshot = WorkspaceModel.getInstance(project).getCurrentSnapshot();
      List<Library> libraries = ContainerUtil.mapNotNull(libraryEntities, library -> LibraryBridgesKt.findLibraryBridge(library, currentSnapshot));
      Library firstLibrary = ContainerUtil.getFirstItem(libraries);

      Map<Library, LibraryAndModule> librariesToAppendSourcesTo = new HashMap<>();
      for (LibraryEntity library : libraryEntities) {
        Library lib = LibraryBridgesKt.findLibraryBridge(library, currentSnapshot);
        ModuleEntity moduleEntity = SequencesKt.firstOrNull(currentSnapshot.referrers(library.getSymbolicId(), ModuleEntity.class));
        if (moduleEntity != null) {
          Module module = ModuleBridges.findModule(moduleEntity, currentSnapshot);
          if (module != null) {
            librariesToAppendSourcesTo.put(lib, new LibraryAndModule(lib, module));
          }
        }
      }
      return chooseFilesAndPerform(librariesToAppendSourcesTo, firstLibrary);
    }

    @Override
    public @NotNull ActionCallback perform(@NotNull List<? extends LibraryOrderEntry> libraries) {
      Library firstLibrary = libraries.get(0).getLibrary();
      Map<Library, LibraryAndModule> librariesToAppendSourcesTo = new HashMap<>();
      for (LibraryOrderEntry library : libraries) {
        librariesToAppendSourcesTo.put(library.getLibrary(), new LibraryAndModule(library.getLibrary(), library.getOwnerModule()));
      }
      return chooseFilesAndPerform(librariesToAppendSourcesTo, firstLibrary);
    }

    private @NotNull ActionCallback chooseFilesAndPerform(@NotNull Map<Library, LibraryAndModule> librariesToAppendSourcesTo,
                                                           Library firstLibrary) {
      VirtualFile[] files = chooseSourceFiles(firstLibrary);
      if (files == null) return ActionCallback.REJECTED;
      return performInternal(librariesToAppendSourcesTo, firstLibrary, files);
    }

    private @Nullable VirtualFile[] chooseSourceFiles(Library firstLibrary) {
      FileChooserDescriptor descriptor = FileChooserDescriptorFactory.createMultipleJavaPathDescriptor();
      descriptor.setTitle(JavaUiBundle.message("library.attach.sources.action"));
      descriptor.setDescription(JavaUiBundle.message("library.attach.sources.description"));

      VirtualFile[] roots = firstLibrary != null ? firstLibrary.getFiles(OrderRootType.CLASSES) : VirtualFile.EMPTY_ARRAY;
      VirtualFile[] candidates = FileChooser.chooseFiles(descriptor, myProject, roots.length == 0 ? null : VfsUtil.getLocalFile(roots[0]));
      if (candidates.length == 0) return null;
      VirtualFile[] files = LibrarySourceRootDetectorUtil.scanAndSelectDetectedJavaSourceRoots(myParentComponent, candidates);
      return files.length == 0 ? null : files;
    }

    private @NotNull ActionCallback performInternal(Map<Library, LibraryAndModule> librariesToAppendSourcesTo,
                                                    Library firstLibrary,
                                                    VirtualFile[] files) {
      if (librariesToAppendSourcesTo.size() == 1) {
        appendSources(firstLibrary, files);
      }
      else {
        librariesToAppendSourcesTo.put(null, null);
        String title = JavaUiBundle.message("library.choose.one.to.attach");
        List<LibraryAndModule> entries = new ArrayList<>(librariesToAppendSourcesTo.values());
        JBPopupFactory.getInstance().createListPopup(new BaseListPopupStep<>(title, entries) {
          @Override
          public ListSeparator getSeparatorAbove(LibraryAndModule value) {
            return value == null ? new ListSeparator() : null;
          }

          @Override
          public @NotNull String getTextFor(LibraryAndModule value) {
            return value == null ? CommonBundle.message("action.text.all")
                                 : value.library.getPresentableName() + " (" + value.ownerModule.getName() + ")";
          }

          @Override
          public PopupStep<?> onChosen(LibraryAndModule libraryOrderEntry, boolean finalChoice) {
            if (libraryOrderEntry != null) {
              appendSources(libraryOrderEntry.library, files);
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

    private record LibraryAndModule(
      Library library,
      Module ownerModule
    ) { }
  }
}
