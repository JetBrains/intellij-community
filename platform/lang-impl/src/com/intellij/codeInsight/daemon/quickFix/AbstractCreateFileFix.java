// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.quickFix;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.codeInsight.hint.HintManager;
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo;
import com.intellij.codeInspection.LocalQuickFixAndIntentionActionOnPsiElement;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.JBPopupListener;
import com.intellij.openapi.ui.popup.LightweightWindowEvent;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.text.HtmlBuilder;
import com.intellij.openapi.util.text.HtmlChunk;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.ui.SimpleListCellRenderer;
import com.intellij.util.IconUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.*;

import javax.swing.*;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Supplier;

import static com.intellij.openapi.project.ProjectUtilCore.displayUrlRelativeToProject;
import static com.intellij.openapi.util.io.FileUtil.toSystemDependentName;
import static com.intellij.openapi.util.text.HtmlChunk.*;
import static com.intellij.openapi.vfs.VfsUtilCore.VFS_SEPARATOR;
import static com.intellij.openapi.vfs.VfsUtilCore.VFS_SEPARATOR_CHAR;

public abstract class AbstractCreateFileFix extends LocalQuickFixAndIntentionActionOnPsiElement {

  private static final int REFRESH_INTERVAL = 1000;

  protected static final String CURRENT_DIRECTORY_REF = ".";
  protected static final String PARENT_DIRECTORY_REF = "..";

  protected final @NlsSafe String myNewFileName;
  protected final List<TargetDirectory> myDirectories;
  protected final String[] mySubPath;
  protected final @PropertyKey(resourceBundle = CodeInsightBundle.BUNDLE) @NotNull String myKey;

  protected boolean myIsAvailable;
  protected long myIsAvailableTimeStamp;

  protected AbstractCreateFileFix(@Nullable PsiElement element,
                                  @NotNull NewFileLocation newFileLocation,
                                  @NotNull String fixLocaleKey) {
    super(element);

    myNewFileName = newFileLocation.getNewFileName();
    myDirectories = newFileLocation.getDirectories();
    mySubPath = newFileLocation.getSubPath();
    myKey = fixLocaleKey;
  }

  @Override
  public boolean startInWriteAction() {
    // required to open file not under Write lock
    return false;
  }

  /**
   * {@inheritDoc}
   * Must be implemented, as default implementation won't work anyway
   */
  @Override
  public abstract @NotNull IntentionPreviewInfo generatePreview(@NotNull Project project, @NotNull Editor editor, @NotNull PsiFile psiFile);

  @Override
  public boolean isAvailable(@NotNull Project project,
                             @NotNull PsiFile psiFile,
                             @NotNull PsiElement startElement,
                             @NotNull PsiElement endElement) {
    long current = System.currentTimeMillis();

    if (ApplicationManager.getApplication().isUnitTestMode() || current - myIsAvailableTimeStamp > REFRESH_INTERVAL) {
      if (myDirectories.size() == 1) {
        PsiDirectory myDirectory = myDirectories.get(0).getDirectory();
        myIsAvailable &= myDirectory != null && myDirectory.getVirtualFile().findChild(myNewFileName) == null;
        myIsAvailableTimeStamp = current;
      }
      else {
        // do not check availability for multiple roots
      }
    }

    return myIsAvailable;
  }

  @Override
  public void invoke(@NotNull Project project,
                     @NotNull PsiFile psiFile,
                     @Nullable Editor editor,
                     @NotNull PsiElement startElement,
                     @NotNull PsiElement endElement) {
    if (isAvailable(project, null, psiFile)) {
      if (myDirectories.size() == 1) {
        apply(myStartElement.getProject(), myDirectories.get(0), editor);
      }
      else {
        List<TargetDirectory> directories = ContainerUtil.filter(myDirectories, d -> d.getDirectory() != null);
        if (directories.isEmpty()) {
          // there are no valid PsiDirectory items
          return;
        }

        if (editor == null || ApplicationManager.getApplication().isUnitTestMode()) {
          // run on the first item of a sorted list in batch mode
          apply(myStartElement.getProject(), directories.get(0), editor);
        }
        else {
          showOptionsPopup(project, editor, directories);
        }
      }
    }
  }

  private void apply(@NotNull Project project, @NotNull TargetDirectory directory, @Nullable Editor editor) {
    myIsAvailableTimeStamp = 0; // to revalidate applicability

    try {
      Supplier<PsiDirectory> toDirectory = () -> {
        PsiDirectory currentDirectory = directory.getDirectory();
        if (currentDirectory == null) {
          return null;
        }

        for (String pathPart : directory.getPathToCreate()) {
          currentDirectory = findOrCreateSubdirectory(currentDirectory, pathPart);
        }

        for (String pathPart : mySubPath) {
          currentDirectory = findOrCreateSubdirectory(currentDirectory, pathPart);
        }

        if (currentDirectory == null) {
          if (editor != null) {
            throw new IncorrectCreateFilePathException(CodeInsightBundle.message("create.file.incorrect.path.hint", myNewFileName));
          }
          return null;
        }
        return currentDirectory;
      };

      apply(project, toDirectory, editor);
    }
    catch (IncorrectOperationException e) {
      myIsAvailable = false;
    }
  }

  /**
   * @deprecated override {@link #apply(Project, Supplier, Editor)} instead
   */
  @SuppressWarnings("unused")
  @Deprecated(forRemoval = true)
  protected void apply(@NotNull Project project, @NotNull PsiDirectory targetDirectory, @Nullable Editor editor)
    throws IncorrectOperationException {
  }

  protected void apply(@NotNull Project project,
                       @NotNull Supplier<? extends @Nullable PsiDirectory> targetDirectory,
                       @Nullable Editor editor)
    throws IncorrectOperationException {

    // only for compatibility with 3-rd party plugins, not used
    @Nullable PsiDirectory directory;
    try {
      directory = WriteCommandAction.writeCommandAction(project)
        .withName(CodeInsightBundle.message(myKey, myNewFileName))
        .compute(() -> targetDirectory.get());
    }
    catch (IncorrectCreateFilePathException e) {
      if (editor != null) {
        HintManager.getInstance().showErrorHint(editor, e.getLocalizedMessage());
      }
      directory = null;
    }
    if (directory != null) {
      // for compatibility with plugins
      apply(project, directory, editor);
    }
  }

  protected @Nullable HtmlChunk getDescription(@NotNull Icon itemIcon) {
    Path filePath;
    String directoryPath = null;
    try {
      if (myDirectories.size() == 1) {
        TargetDirectory directory = myDirectories.get(0);
        PsiDirectory psiDirectory = directory.getDirectory();
        directoryPath = psiDirectory == null ? "" : psiDirectory.getVirtualFile().getPresentableUrl();
        filePath = Path.of("", directory.getPathToCreate());
        for (String component : mySubPath) {
          filePath = filePath.resolve(component);
        }
        filePath = filePath.resolve(myNewFileName);
      }
      else {
        filePath = Path.of("", mySubPath).resolve(myNewFileName);
      }
    }
    catch (InvalidPathException e) {
      return null;
    }
    HtmlChunk fileReference = fragment(icon("file", itemIcon), nbsp(), text(filePath.toString()));
    HtmlBuilder builder = new HtmlBuilder();
    builder.append(template(CodeInsightBundle.message(myKey, "$file$"), "file", fileReference));
    if (filePath.getNameCount() > 1) {
      builder.br().append(CodeInsightBundle.message("intention.description.including.intermediate.directories"));
    }
    if (directoryPath != null) {
      HtmlChunk dirReference = fragment(icon("dir", AllIcons.Nodes.Folder), nbsp(), text(directoryPath));
      builder.br()
        .append(template(CodeInsightBundle.message("intention.description.inside.directory", "$directory$"),
                         "directory", dirReference));
    }
    return builder.toFragment();
  }

  private static @Nullable PsiDirectory findOrCreateSubdirectory(@Nullable PsiDirectory directory, @NotNull String subDirectoryName) {
    if (directory == null) {
      return null;
    }
    if (CURRENT_DIRECTORY_REF.equals(subDirectoryName)) {
      return directory;
    }
    if (PARENT_DIRECTORY_REF.equals(subDirectoryName)) {
      return directory.getParentDirectory();
    }

    PsiDirectory existingDirectory = directory.findSubdirectory(subDirectoryName);
    if (existingDirectory == null) {
      return directory.createSubdirectory(subDirectoryName);
    }
    return existingDirectory;
  }

  private void showOptionsPopup(@NotNull Project project,
                                @NotNull Editor editor,
                                List<? extends TargetDirectory> directories) {
    List<TargetDirectoryListItem> items = getTargetDirectoryListItems(directories);

    String filePath = myNewFileName;
    if (mySubPath.length > 0) {
      filePath = toSystemDependentName(
        StringUtil.join(mySubPath, VFS_SEPARATOR)
        + VFS_SEPARATOR_CHAR + myNewFileName
      );
    }

    SimpleListCellRenderer<TargetDirectoryListItem> renderer = SimpleListCellRenderer.create((label, value, index) -> {
      label.setIcon(value.getIcon());
      label.setText(value.getPresentablePath());
    });

    JBPopupFactory.getInstance()
      .createPopupChooserBuilder(items)
      .setSelectionMode(ListSelectionModel.SINGLE_SELECTION)
      .setTitle(CodeInsightBundle.message(myKey, filePath))
      .setMovable(false)
      .setResizable(false)
      .setRequestFocus(true)
      .setRenderer(renderer)
      .setNamerForFiltering(item -> item.getPresentablePath())
      .setItemChosenCallback(chosenValue -> apply(project, chosenValue.getTarget(), editor))
      .addListener(new JBPopupListener() {
        @Override
        public void onClosed(@NotNull LightweightWindowEvent event) {
          // rerun code-insight after popup close
          PsiFile file = PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument());
          if (file != null) {
            DaemonCodeAnalyzer.getInstance(project).restart(file);
          }
        }
      })
      .createPopup()
      .showInBestPositionFor(editor);
  }

  private static @Unmodifiable @NotNull List<TargetDirectoryListItem> getTargetDirectoryListItems(List<? extends TargetDirectory> directories) {
    return ContainerUtil.map(directories, targetDirectory -> {
      PsiDirectory d = targetDirectory.getDirectory();
      assert d != null : "Invalid PsiDirectory instances found";

      String presentablePath = getPresentableContentRootPath(d, targetDirectory.getPathToCreate());
      Icon icon = getContentRootIcon(d);

      return new TargetDirectoryListItem(targetDirectory, icon, presentablePath);
    });
  }

  private static @NotNull Icon getContentRootIcon(@NotNull PsiDirectory directory) {
    VirtualFile file = directory.getVirtualFile();

    Project project = directory.getProject();
    ProjectFileIndex projectFileIndex = ProjectFileIndex.getInstance(project);
    VirtualFile sourceRoot = projectFileIndex.getSourceRootForFile(file);
    if (sourceRoot != null) {
      return IconUtil.getIcon(sourceRoot, 0, project);
    }

    return IconUtil.getIcon(file, 0, project);
  }

  private static @NotNull @NlsSafe String getPresentableContentRootPath(@NotNull PsiDirectory directory,
                                                                        String @NotNull [] pathToCreate) {
    VirtualFile f = directory.getVirtualFile();
    Project project = directory.getProject();

    String path = f.getPath();
    if (pathToCreate.length > 0) {
      path += VFS_SEPARATOR_CHAR + StringUtil.join(pathToCreate, VFS_SEPARATOR);
    }
    String presentablePath = f.getFileSystem().extractPresentableUrl(path);

    return displayUrlRelativeToProject(f, presentablePath, project, true, true);
  }

  protected static final class TargetDirectoryListItem {
    private final TargetDirectory myTargetDirectory;
    private final Icon myIcon;
    private final @Nls String myPresentablePath;

    public TargetDirectoryListItem(@NotNull TargetDirectory targetDirectory,
                                   Icon icon, @NotNull @Nls String presentablePath) {
      myTargetDirectory = targetDirectory;
      myIcon = icon;
      myPresentablePath = presentablePath;
    }

    public Icon getIcon() {
      return myIcon;
    }

    private @Nls String getPresentablePath() {
      return myPresentablePath;
    }

    private TargetDirectory getTarget() {
      return myTargetDirectory;
    }
  }
}
