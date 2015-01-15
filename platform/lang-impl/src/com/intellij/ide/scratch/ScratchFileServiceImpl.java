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
package com.intellij.ide.scratch;

import com.intellij.icons.AllIcons;
import com.intellij.ide.FileIconProvider;
import com.intellij.ide.navigationToolbar.AbstractNavBarModelExtension;
import com.intellij.lang.Language;
import com.intellij.lang.LanguageUtil;
import com.intellij.lang.PerFileMappings;
import com.intellij.lang.PerFileMappingsBase;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.application.RunResult;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.components.StoragePathMacros;
import com.intellij.openapi.fileEditor.impl.NonProjectFileWritingAccessExtension;
import com.intellij.openapi.fileTypes.*;
import com.intellij.openapi.fileTypes.ex.FileTypeIdentifiableByVirtualFile;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Iconable;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.IdeFrame;
import com.intellij.openapi.wm.StatusBar;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.openapi.wm.WindowManagerListener;
import com.intellij.psi.LanguageSubstitutor;
import com.intellij.psi.LanguageSubstitutors;
import com.intellij.ui.LayeredIcon;
import com.intellij.ui.UIBundle;
import com.intellij.util.ObjectUtils;
import com.intellij.util.PathUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Collection;
import java.util.List;


public abstract class ScratchFileServiceImpl extends ScratchFileService {

  public static final LanguageFileType SCRATCH_FILE_TYPE = new MyFileType();

  @State(
    name = "ScratchFileService",
    storages = {
      @Storage(file = StoragePathMacros.APP_CONFIG + "/scratches.xml")
    })
  public static class App extends ScratchFileServiceImpl implements PersistentStateComponent<Element> {

    private final MyLanguages myScratchMapping = new MyLanguages();

    @NotNull
    @Override
    public String getRootPath(@NotNull RootType rootType) {
      return FileUtil.toSystemIndependentName(PathManager.getConfigPath()) + "/" + rootType.getId();
    }

    public App(WindowManager windowManager) {
      WindowManagerListener listener = new WindowManagerListener() {
        @Override
        public void frameCreated(IdeFrame frame) {
          Project project = frame.getProject();
          StatusBar statusBar = frame.getStatusBar();
          if (project == null || statusBar == null || statusBar.getWidget(ScratchWidget.WIDGET_ID) != null) return;
          ScratchWidget widget = new ScratchWidget(project);
          statusBar.addWidget(widget, "before Encoding", project);
          statusBar.updateWidget(ScratchWidget.WIDGET_ID);
        }

        @Override
        public void beforeFrameReleased(IdeFrame frame) {
        }
      };
      for (IdeFrame frame : windowManager.getAllProjectFrames()) {
        listener.frameCreated(frame);
      }
      windowManager.addListener(listener);
    }

    @NotNull
    @Override
    public PerFileMappings<Language> getScratchesMapping() {
      return myScratchMapping;
    }

    @Nullable
    @Override
    public Element getState() {
      return myScratchMapping.getState();
    }

    @Override
    public void loadState(Element state) {
      myScratchMapping.loadState(state);
    }
  }

  private static class MyLanguages extends PerFileMappingsBase<Language> {
    @Override
    protected List<Language> getAvailableValues() {
      return LanguageUtil.getFileLanguages();
    }

    @Nullable
    @Override
    protected String serialize(Language language) {
      return language.getID();
    }

    @Nullable
    @Override
    protected Language handleUnknownMapping(VirtualFile file, String value) {
      return PlainTextLanguage.INSTANCE;
    }

    @Nullable
    @Override
    public Language getMapping(@Nullable VirtualFile file) {
      Language language = super.getMapping(file);
      if (language == null && file != null && file.getFileType() == SCRATCH_FILE_TYPE) {
        String extension = file.getExtension();
        FileType fileType = extension == null ? null : FileTypeManager.getInstance().getFileTypeByExtension(extension);
        language = fileType instanceof LanguageFileType ? ((LanguageFileType)fileType).getLanguage() : null;
      }
      return language;
    }
  }


  public static class Prj extends ScratchFileServiceImpl {

    private final Project myProject;

    public Prj(@NotNull Project project) {
      myProject = project;
    }

    @NotNull
    protected Project getProject() {
      return myProject;
    }

    @NotNull
    @Override
    public String getRootPath(@NotNull RootType rootType) {
      if (rootType == SCRATCHES) return ScratchFileService.getInstance().getRootPath(rootType);
      return FileUtil.toSystemIndependentName(StringUtil.notNullize(PathUtil.getParentPath(myProject.getProjectFilePath()))) + "/" + rootType.getId();
    }

    @Nullable
    @Override
    public VirtualFile createScratchFile(@NotNull Project project, @NotNull Language language, @NotNull String initialContent) {
      return ScratchFileService.getInstance().createScratchFile(project, language, initialContent);
    }

    @NotNull
    @Override
    public PerFileMappings<Language> getScratchesMapping() {
      return ScratchFileService.getInstance().getScratchesMapping();
    }
  }

  public static class TypeFactory extends FileTypeFactory {

    @Override
    public void createFileTypes(@NotNull FileTypeConsumer consumer) {
      consumer.consume(SCRATCH_FILE_TYPE);
    }
  }


  public static class Substitutor extends LanguageSubstitutor {
    @Nullable
    @Override
    public Language getLanguage(@NotNull VirtualFile file, @NotNull Project project) {
      if (file.getFileType() != SCRATCH_FILE_TYPE) return null;
      PerFileMappings<Language> mapping = ScratchFileService.getInstance().getScratchesMapping();
      Language language = mapping.getMapping(file);
      return language != null && language != SCRATCH_FILE_TYPE.getLanguage() ?
             LanguageSubstitutors.INSTANCE.substituteLanguage(language, file, project) : language;
    }
  }

  public static class Highlighter implements SyntaxHighlighterProvider {
    @Override
    @Nullable
    public SyntaxHighlighter create(@NotNull FileType fileType, @Nullable Project project, @Nullable VirtualFile file) {
      if (fileType == SCRATCH_FILE_TYPE && project != null && file != null) {
        PerFileMappings<Language> mapping = ScratchFileService.getInstance().getScratchesMapping();
        Language language = mapping.getMapping(file);
        return language != null ? SyntaxHighlighterFactory.getSyntaxHighlighter(language, project, file) : null;
      }
      return null;
    }
  }

  public static class IconProvider implements FileIconProvider {

    @Nullable
    @Override
    public Icon getIcon(@NotNull VirtualFile file, @Iconable.IconFlags int flags, @Nullable Project project) {
      if (project == null || file.getFileType() != SCRATCH_FILE_TYPE) return null;
      PerFileMappings<Language> mapping = ScratchFileService.getInstance().getScratchesMapping();
      Language language = ObjectUtils.notNull(mapping.getMapping(file), SCRATCH_FILE_TYPE.getLanguage());
      LanguageFileType fileType = language.getAssociatedFileType();
      return fileType == null ? null : LayeredIcon.create(fileType.getIcon(), AllIcons.Actions.Scratch);
    }
  }

  public static class AccessExtension implements NonProjectFileWritingAccessExtension {

    @Override
    public boolean isWritable(@NotNull VirtualFile file) {
      return file.getFileType() == SCRATCH_FILE_TYPE; // todo ensure project is OK
    }
  }

  public static class NavBarExtension extends AbstractNavBarModelExtension {

    @Nullable
    @Override
    public String getPresentableText(Object object) {
      return null;
    }

    @NotNull
    @Override
    public Collection<VirtualFile> additionalRoots(Project project) {
      String path = ScratchFileService.getInstance().getRootPath(SCRATCHES);
      VirtualFile root = LocalFileSystem.getInstance().findFileByPath(path);
      return ContainerUtil.createMaybeSingletonList(root);
    }
  }

  @Nullable
  @Override
  public VirtualFile createScratchFile(@NotNull Project project, @NotNull final Language language, @NotNull final String initialContent) {
    RunResult<VirtualFile> result =
      new WriteCommandAction<VirtualFile>(project, UIBundle.message("file.chooser.create.new.file.command.name")) {
        @Override
        protected void run(@NotNull Result<VirtualFile> result) throws Throwable {
          VirtualFile dir = VfsUtil.createDirectories(getRootPath(SCRATCHES));
          VirtualFile file = VfsUtil.createChildSequent(LocalFileSystem.getInstance(), dir, "scratch", "");
          getScratchesMapping().setMapping(file, language);
          VfsUtil.saveText(file, initialContent);
          result.setResult(file);
        }
      }.execute();
    if (result.hasException()) {
      Messages.showMessageDialog(UIBundle.message("create.new.file.could.not.create.file.error.message", "scratch"),
                                 UIBundle.message("error.dialog.title"), Messages.getErrorIcon());
      return null;
    }
    return result.getResultObject();
  }

  @Override
  public boolean isScratchFile(@NotNull VirtualFile file) {
    return file.getFileType() == SCRATCH_FILE_TYPE;
  }

  private static class MyFileType extends LanguageFileType implements FileTypeIdentifiableByVirtualFile, InternalFileType {

    MyFileType() {
      super(PlainTextLanguage.INSTANCE);
    }

    @Override
    public boolean isMyFileType(@NotNull VirtualFile file) {
      String rootPath = ScratchFileService.getInstance().getRootPath(SCRATCHES);
      return file.getPath().startsWith(rootPath);
    }

    @NotNull
    @Override
    public String getName() {
      return "Scratch";
    }

    @NotNull
    @Override
    public String getDescription() {
      return "Scratch";
    }

    @NotNull
    @Override
    public String getDefaultExtension() {
      return "";
    }

    @Nullable
    @Override
    public Icon getIcon() {
      return PlainTextFileType.INSTANCE.getIcon();
    }

    @Override
    public boolean isReadOnly() {
      return true;
    }

    @Nullable
    @Override
    public String getCharset(@NotNull VirtualFile file, @NotNull byte[] content) {
      return null;
    }
  }
}