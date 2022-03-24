// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.scratch;

import com.intellij.icons.AllIcons;
import com.intellij.ide.FileIconProvider;
import com.intellij.ide.navigationToolbar.AbstractNavBarModelExtension;
import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.projectView.ProjectViewNode;
import com.intellij.ide.projectView.ProjectViewNodeDecorator;
import com.intellij.ide.util.DeleteHandler;
import com.intellij.lang.*;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.RoamingType;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.extensions.ExtensionPointListener;
import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.FileEditorManagerListener;
import com.intellij.openapi.fileEditor.impl.EditorTabTitleProvider;
import com.intellij.openapi.fileEditor.impl.FileEditorManagerImpl;
import com.intellij.openapi.fileEditor.impl.NonProjectFileWritingAccessExtension;
import com.intellij.openapi.fileTypes.*;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ProjectManagerListener;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Iconable;
import com.intellij.openapi.util.io.ByteSequence;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.packageDependencies.ui.PackageDependenciesNode;
import com.intellij.psi.*;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.search.UseScopeEnlarger;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.testFramework.LightVirtualFile;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.usages.impl.rules.UsageType;
import com.intellij.usages.impl.rules.UsageTypeProvider;
import com.intellij.util.FileContentUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.PathUtil;
import com.intellij.util.containers.ConcurrentFactoryMap;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.indexing.LightDirectoryIndex;
import com.intellij.util.messages.MessageBusConnection;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.SystemIndependent;

import javax.swing.*;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentMap;
import java.util.function.BiConsumer;

@State(name = "ScratchFileService", storages = @Storage(value = "scratches.xml", roamingType = RoamingType.DISABLED))
public final class ScratchFileServiceImpl extends ScratchFileService implements PersistentStateComponent<Element>, Disposable {
  private static final RootType NO_ROOT_TYPE = new RootType("", "NO_ROOT_TYPE") {};

  private final LightDirectoryIndex<RootType> myIndex;
  private final MyLanguages myScratchMapping = new MyLanguages();
  private final ConcurrentMap<String, String> myRootPaths = ConcurrentFactoryMap.createMap(ScratchFileServiceImpl::calcRootPath);

  private ScratchFileServiceImpl() {
    Disposer.register(this, myScratchMapping);
    myIndex = new LightDirectoryIndex<>(ApplicationManager.getApplication(), NO_ROOT_TYPE, index -> {
      LocalFileSystem fileSystem = LocalFileSystem.getInstance();
      for (RootType r : RootType.getAllRootTypes()) {
        index.putInfo(fileSystem.findFileByPath(getRootPath(r)), r);
      }
    });
    initFileOpenedListener();
  }

  @Override
  public @SystemIndependent @NotNull String getRootPath(@NotNull RootType rootType) {
    return myRootPaths.get(rootType.getId());
  }
  
  private static @SystemIndependent @NotNull String calcRootPath(@NotNull String rootId) {
    String path = System.getProperty(PathManager.PROPERTY_SCRATCH_PATH + "/" + rootId);
    if (path != null && path.length() > 2 && path.charAt(0) == '\"') {
      path = StringUtil.unquoteString(path);
    }
    return path != null ? FileUtil.toSystemIndependentName(path) :
           FileUtil.toSystemIndependentName(PathManager.getScratchPath()) + "/" + rootId;
  }

  @Override
  public @Nullable RootType getRootType(@Nullable VirtualFile file) {
    if (file == null || !file.isInLocalFileSystem()) {
      return null;
    }

    VirtualFile directory = file.isDirectory() ? file : file.getParent();
    RootType result = myIndex.getInfoForFile(directory);
    return result == NO_ROOT_TYPE ? null : result;
  }

  private void initFileOpenedListener() {
    // handle all previously opened projects (as we are service, lazily created)
    processOpenFiles((file, manager) -> {
      RootType rootType = getRootType(file);
      if (rootType != null) {
        rootType.fileOpened(file, manager);
      }
    });

    MessageBusConnection messageBusConnection = ApplicationManager.getApplication().getMessageBus().connect(this);
    messageBusConnection.subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, new FileEditorManagerListener() {
      @Override
      public void fileOpened(@NotNull FileEditorManager source, @NotNull VirtualFile file) {
        RootType rootType = getRootType(file);
        Document document = FileDocumentManager.getInstance().getDocument(file);
        if (document == null || rootType == null || rootType.isHidden()) return;
        rootType.fileOpened(file, source);
      }

      @Override
      public void fileClosed(@NotNull FileEditorManager source, @NotNull VirtualFile file) {
        if (Boolean.TRUE.equals(file.getUserData(FileEditorManagerImpl.CLOSING_TO_REOPEN))) return;
        if (ApplicationManager.getApplication().isUnitTestMode()) return;
        if (!isToBeDeletedOnClose(file)) return;
        Project project = source.getProject();
        ApplicationManager.getApplication().invokeLater(() -> {
          if (!file.isValid() || source.isFileOpen(file)) return;
          PsiFile psiFile = PsiManager.getInstance(project).findFile(file);
          if (psiFile == null) return;
          DeleteHandler.deletePsiElement(new PsiElement[]{psiFile}, project, false);
        }, project.getDisposed());
      }
    });
    messageBusConnection.subscribe(ProjectManager.TOPIC, new ProjectManagerListener() {
      @Override
      public void projectClosing(@NotNull Project project) {
        if (ApplicationManager.getApplication().isUnitTestMode()) return;
        List<PsiFile> psiFiles = new ArrayList<>();
        for (VirtualFile file : FileEditorManager.getInstance(project).getOpenFiles()) {
          if (!isToBeDeletedOnClose(file)) continue;
          ContainerUtil.addIfNotNull(psiFiles, PsiManager.getInstance(project).findFile(file));
        }
        if (!psiFiles.isEmpty()) {
          DeleteHandler.deletePsiElement(PsiUtilCore.toPsiElementArray(psiFiles), project, false);
        }
      }
    });

    RootType.ROOT_EP.addExtensionPointListener(new ExtensionPointListener<>() {
      @Override
      public void extensionAdded(@NotNull RootType rootType, @NotNull PluginDescriptor pluginDescriptor) {
        myIndex.resetIndex();
        processOpenFiles((file, manager) -> {
          if (getRootType(file) == rootType) {
            rootType.fileOpened(file, manager);
          }
        });
        for (Project project : ProjectManager.getInstance().getOpenProjects()) {
          FileContentUtil.reparseFiles(project, Collections.emptyList(), true);
        }
      }

      @Override
      public void extensionRemoved(@NotNull RootType rootType, @NotNull PluginDescriptor pluginDescriptor) {
        myIndex.resetIndex();
        for (Project project : ProjectManager.getInstance().getOpenProjects()) {
          FileContentUtil.reparseFiles(project, Collections.emptyList(), true);
        }
      }
    }, null);
  }

  private boolean isToBeDeletedOnClose(@NotNull VirtualFile file) {
    RootType rootType = getRootType(file);
    if (rootType == null || rootType.isHidden()) return false;
    Document document = FileDocumentManager.getInstance().getDocument(file);
    return document != null && document.getTextLength() < 10240 && StringUtil.isEmptyOrSpaces(document.getText());
  }

  private static void processOpenFiles(@NotNull BiConsumer<? super VirtualFile, ? super FileEditorManager> consumer) {
    for (Project project : ProjectManager.getInstance().getOpenProjects()) {
      FileEditorManager editorManager = FileEditorManager.getInstance(project);
      for (VirtualFile file : editorManager.getOpenFiles()) {
        if (file.isValid() && !file.isDirectory()) {
          consumer.accept(file, editorManager);
        }
      }
    }
  }

  @Override
  public @NotNull PerFileMappings<Language> getScratchesMapping() {
    return new PerFileMappings<>() {
      @Override
      public void setMapping(@Nullable VirtualFile file, @Nullable Language value) {
        myScratchMapping.setMapping(file, value == null ? null : value.getID());
      }

      @Override
      public @Nullable Language getMapping(@Nullable VirtualFile file) {
        return Language.findLanguageByID(myScratchMapping.getMapping(file));
      }
    };
  }

  @Override
  public @Nullable Element getState() {
    return myScratchMapping.getState();
  }

  @Override
  public void loadState(@NotNull Element state) {
    myScratchMapping.loadState(state);
  }

  @Override
  public void dispose() {
  }

  private static final class MyLanguages extends PerFileMappingsBase<String> {
    @Override
    public @NotNull List<String> getAvailableValues() {
      return ContainerUtil.map(LanguageUtil.getFileLanguages(), Language::getID);
    }

    @Override
    protected @NotNull String serialize(@NotNull String languageID) {
      return languageID;
    }

    @Override
    protected @NotNull String handleUnknownMapping(VirtualFile file, String value) {
      return PlainTextLanguage.INSTANCE.getID();
    }

    @Override
    protected boolean isDefaultMapping(@NotNull VirtualFile file, @NotNull String mapping) {
      if (PlainTextLanguage.INSTANCE.getID().equals(mapping)) return true;
      FileType byName = FileTypeManager.getInstance().getFileTypeByFileName(file.getName());
      Language language = LanguageUtil.getFileTypeLanguage(byName);
      return language != null && language.getID().equals(mapping);
    }
  }

  public static final class Detector implements FileTypeRegistry.FileTypeDetector {
    @Override
    public @Nullable FileType detect(@NotNull VirtualFile file, @NotNull ByteSequence firstBytes, @Nullable CharSequence firstCharsIfText) {
      if (firstCharsIfText == null) return null;
      return ProgressManager.getInstance().computeInNonCancelableSection(() -> {
        FileType byName = FileTypeManager.getInstance().getFileTypeByFileName(file.getName());
        RootType rootType = byName != UnknownFileType.INSTANCE ? null : findRootType(file);
        return rootType != null ? PlainTextFileType.INSTANCE : null;
      });
    }
  }

  public static final class Substitutor extends LanguageSubstitutor {
    @Override
    public @Nullable Language getLanguage(@NotNull VirtualFile file, @NotNull Project project) {
      return substituteLanguage(project, file);
    }

    public static @Nullable Language substituteLanguage(@NotNull Project project, @NotNull VirtualFile file) {
      RootType rootType = findRootType(file instanceof LightVirtualFile? ((LightVirtualFile)file).getOriginalFile() : file);
      if (rootType == null) return null;
      return rootType.substituteLanguage(project, file);
    }
  }

  static final class Highlighter implements SyntaxHighlighterProvider {
    @Override
    public @Nullable SyntaxHighlighter create(@NotNull FileType fileType, @Nullable Project project, @Nullable VirtualFile file) {
      if (project == null || file == null) return null;
      if (!ScratchUtil.isScratch(file)) return null;

      Language language = LanguageUtil.getLanguageForPsi(project, file, fileType);
      return language == null ? null : SyntaxHighlighterFactory.getSyntaxHighlighter(language, project, file);
    }
  }

  static final class FilePresentation implements FileIconProvider, EditorTabTitleProvider, ProjectViewNodeDecorator, DumbAware {
    @Override
    public void decorate(ProjectViewNode<?> node, PresentationData data) {
      Object value = node.getValue();
      RootType rootType;
      VirtualFile virtualFile = null;
      if (value instanceof RootType) {
        rootType = (RootType)value;
      }
      else {
        virtualFile = node.getVirtualFile();
        if (virtualFile == null || !virtualFile.isValid()) return;
        rootType = ScratchFileService.getInstance().getRootType(virtualFile);
        if (rootType == null) return;
      }
      ScratchFileService scratchFileService = ScratchFileService.getInstance();
      VirtualFile rootFile = LocalFileSystem.getInstance().findFileByPath(scratchFileService.getRootPath(rootType));
      String text;
      Icon icon = null;
      if (virtualFile == null || virtualFile.isDirectory() && virtualFile.equals(rootFile)) {
        text = rootType.getDisplayName();
      }
      else {
        Project project = Objects.requireNonNull(node.getProject());
        text = rootType.substituteName(project, virtualFile);
        icon = rootType.substituteIcon(project, virtualFile);
      }
      if (text != null) {
        data.clearText();
        data.addText(text, SimpleTextAttributes.REGULAR_ATTRIBUTES);
        data.setPresentableText(text);
      }
      if (icon != null) {
        data.setIcon(icon);
      }
    }

    @Override
    public void decorate(PackageDependenciesNode node, ColoredTreeCellRenderer cellRenderer) {
    }

    @Override
    public @Nullable Icon getIcon(@NotNull VirtualFile file, @Iconable.IconFlags int flags, @Nullable Project project) {
      if (project == null || file.isDirectory()) return null;
      RootType rootType = ScratchFileService.getInstance().getRootType(file);
      if (rootType == null) return null;
      return ObjectUtils.notNull(rootType.substituteIcon(project, file), AllIcons.FileTypes.Text);
    }

    @Override
    public @Nullable String getEditorTabTitle(@NotNull Project project, @NotNull VirtualFile file) {
      RootType rootType = ScratchFileService.getInstance().getRootType(file);
      if (rootType == null) return null;
      return rootType.substituteName(project, file);
    }
  }

  static final class AccessExtension implements NonProjectFileWritingAccessExtension {
    @Override
    public boolean isWritable(@NotNull VirtualFile file) {
      return ScratchUtil.isScratch(file);
    }
  }

  static final class NavBarExtension extends AbstractNavBarModelExtension {
    @Override
    public @Nullable Icon getIcon(Object object) {
      VirtualFile file = object instanceof PsiFileSystemItem ? ((PsiFileSystemItem)object).getVirtualFile() : null;
      if (file == null) return null;
      RootType rootType = ScratchFileService.getInstance().getRootType(file);
      if (rootType == null) return null;
      Icon icon = rootType.substituteIcon(((PsiFileSystemItem)object).getProject(), file);
      return icon == null && file.isDirectory() ? AllIcons.Nodes.Folder : icon;
    }

    @Override
    public @Nullable String getPresentableText(Object object) {
      PsiElement psi = object instanceof PsiElement ? (PsiElement)object : null;
      if (psi == null || !psi.isValid()) return null;
      VirtualFile virtualFile = PsiUtilCore.getVirtualFile(psi);
      if (virtualFile == null || !virtualFile.isValid()) return null;
      RootType rootType = ScratchFileService.getInstance().getRootType(virtualFile);
      if (rootType == null) return null;
      Project project = psi.getProject();
      if (virtualFile.isDirectory() && additionalRoots(project).contains(virtualFile)) {
        return rootType.getDisplayName();
      }
      return rootType.substituteName(project, virtualFile);
    }

    @Override
    public @NotNull Collection<VirtualFile> additionalRoots(Project project) {
      Set<VirtualFile> result = new LinkedHashSet<>();
      LocalFileSystem fileSystem = LocalFileSystem.getInstance();
      ScratchFileService app = ScratchFileService.getInstance();
      for (RootType r : RootType.getAllRootTypes()) {
        ContainerUtil.addIfNotNull(result, fileSystem.findFileByPath(app.getRootPath(r)));
      }
      return result;
    }
  }

  @Override
  public VirtualFile findFile(@NotNull RootType rootType, @NotNull String pathName, @NotNull Option option) throws IOException {
    ApplicationManager.getApplication().assertReadAccessAllowed();

    LocalFileSystem fileSystem = LocalFileSystem.getInstance();
    String fullPath = getRootPath(rootType) + "/" + pathName;
    if (option != Option.create_new_always) {
      VirtualFile file = fileSystem.findFileByPath(fullPath);
      if (file != null && !file.isDirectory()) {
        return file;
      }
      if (option == Option.existing_only) {
        return null;
      }
    }
    String fileName = PathUtil.getFileName(pathName);
    return WriteAction.compute(() -> {
      VirtualFile dir = VfsUtil.createDirectories(PathUtil.getParentPath(fullPath));
      if (option == Option.create_new_always) {
        return dir.createChildData(fileSystem, ScratchImplUtil.getNextAvailableName(dir, fileName));
      }
      else if (option == Option.create_if_missing && rootType instanceof ScratchRootType && fileName.startsWith("buffer")) {
        VirtualFile file = ScratchImplUtil.findFileIgnoreExtension(dir, fileName);
        if (file != null && !file.getName().equals(fileName)) {
          file.rename(this, fileName);
        }
        return file != null ? file : dir.findOrCreateChildData(fileSystem, fileName);
      }
      else {
        return dir.findOrCreateChildData(fileSystem, fileName);
      }
    });
  }

  static final class UseScopeExtension extends UseScopeEnlarger {
    @Override
    public @Nullable SearchScope getAdditionalUseScope(@NotNull PsiElement element) {
      SearchScope useScope = element.getUseScope();
      if (useScope instanceof LocalSearchScope) return null;
      return ScratchesSearchScope.getScratchesScope(element.getProject());
    }
  }

  static final class UsageTypeExtension implements UsageTypeProvider {
    private static final ConcurrentMap<RootType, UsageType> ourUsageTypes =
      ConcurrentFactoryMap.createMap(key -> new UsageType(LangBundle.messagePointer("usage.type.usage.in.0", key.getDisplayName())));

    @Override
    public @Nullable UsageType getUsageType(@NotNull PsiElement element) {
      VirtualFile file = PsiUtilCore.getVirtualFile(element);
      RootType rootType = ScratchFileService.getInstance().getRootType(file);
      return rootType == null ? null : ourUsageTypes.get(rootType);
    }
  }
}