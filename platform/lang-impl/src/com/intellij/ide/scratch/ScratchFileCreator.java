// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.scratch;

import com.intellij.ide.util.PsiNavigationSupport;
import com.intellij.lang.Language;
import com.intellij.lang.LanguageUtil;
import com.intellij.lang.StdLanguages;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.application.impl.LaterInvocator;
import com.intellij.openapi.command.UndoConfirmationPolicy;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Caret;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.ExtensionFileNameMatcher;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.FileTypes;
import com.intellij.openapi.fileTypes.InternalFileType;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.fileTypes.PlainTextLanguage;
import com.intellij.openapi.fileTypes.UnknownFileType;
import com.intellij.openapi.fileTypes.WildcardFileNameMatcher;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.util.text.StringUtilRt;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.Navigatable;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiErrorElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.SyntaxTraverser;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.ui.UIBundle;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.PathUtil;
import com.intellij.util.PathUtilRt;
import com.intellij.util.concurrency.annotations.RequiresWriteLock;
import com.intellij.util.containers.JBIterable;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Creates files under a {@link RootType} the way scratch files are created: runs the {@link ScratchFileCreationHelper}
 * of the language, picks a free file name, writes the text, stores the language mapping and opens the file in an editor.
 * {@link #createScratchOrTempFile} creates a temporary file instead when the scratch plugin is not loaded.
 * <p>
 * Stays in the platform because the welcome screen creates its files through it;
 * the scratch actions themselves live in the {@code intellij.platform.scratch} module.
 */
@ApiStatus.Internal
public final class ScratchFileCreator {
  private static final Logger LOG = Logger.getInstance(ScratchFileCreator.class);

  public static @Nullable PsiFile doCreateNewScratch(@NotNull Project project, @NotNull ScratchFileCreationHelper.Context context) {
    return doCreateNewScratch(project, context, DataContext.EMPTY_CONTEXT);
  }

  public static @Nullable PsiFile doCreateNewScratch(@NotNull Project project,
                                                     @NotNull ScratchFileCreationHelper.Context context,
                                                     @NotNull DataContext dataContext) {
    RootType targetRootType = context.defaultRootType != null ? context.defaultRootType : findScratchesRootTypeOrWarn();
    if (targetRootType == null) return null;
    context.defaultRootType = targetRootType;

    if (context.fileExtension == null && context.language != null) {
      LanguageFileType fileType = context.language.getAssociatedFileType();
      if (fileType != null) {
        context.fileExtension = getFileTypeExtensions(fileType, true, FileTypeManager.getInstance()).first();
      }
    }
    if (context.language != null && context.prepareText) {
      ScratchFileCreationHelper helper = ScratchFileCreationHelper.EXTENSION.forLanguage(context.language);
      if (StringUtil.isEmpty(context.text)) {
        helper.prepareText(project, context, dataContext);
      }
      helper.beforeCreate(project, context);
    }

    VirtualFile dir = context.ideView != null ? PsiUtilCore.getVirtualFile(ArrayUtil.getFirstElement(context.ideView.getDirectories())) : null;
    RootType rootType = dir == null ? null : ScratchFileService.findRootType(dir);
    String relativePath = rootType != targetRootType ? "" :
                          FileUtil.getRelativePath(ScratchFileService.getInstance().getRootPath(rootType), dir.getPath(), '/');

    String fileName = (StringUtil.isEmpty(relativePath) ? "" : relativePath + "/") +
                      PathUtil.makeFileName(ObjectUtils.notNull(context.filePrefix, "scratch") +
                                            (context.fileCounter != null ? context.fileCounter.create() : ""),
                                            context.fileExtension);
    VirtualFile file = createScratchFile(project, fileName, context.language, context.text, context.createOption, targetRootType);
    if (file == null) return null;

    Navigatable navigatable = PsiNavigationSupport.getInstance().createNavigatable(project, file, context.caretOffset);
    navigatable.navigate(!LaterInvocator.isInModalContextForProject(project));
    PsiFile psiFile = PsiManager.getInstance(project).findFile(file);

    if (context.language != null) {
      ScratchFileCreationHelper.EXTENSION.forLanguage(context.language)
        .afterCreate(project, context, psiFile);
    }

    if (context.ideView != null && psiFile != null) {
      context.ideView.selectElement(psiFile);
    }
    return psiFile;
  }

  /**
   * Builds the context for a scratch file from the selection in {@code editor}: the selected text,
   * the language at the caret and the IDE view of {@code dataContext}.
   */
  public static @NotNull ScratchFileCreationHelper.Context createContext(@NotNull Project project,
                                                                         @Nullable PsiFile file,
                                                                         @Nullable Editor editor,
                                                                         @NotNull DataContext dataContext) {
    ScratchFileCreationHelper.Context context = new ScratchFileCreationHelper.Context();
    context.text = StringUtil.notNullize(getSelectionText(editor));
    context.sourceFile = file;
    if (StringUtil.isNotEmpty(context.text)) {
      initLanguageFromCaret(project, editor, file, context, dataContext);
    }
    context.ideView = LangDataKeys.IDE_VIEW.getData(dataContext);
    return context;
  }

  private static @Nullable String getSelectionText(@Nullable Editor editor) {
    if (editor == null) return null;
    return editor.getSelectionModel().getSelectedText(true);
  }

  private static void initLanguageFromCaret(@NotNull Project project,
                                            @Nullable Editor editor,
                                            @Nullable PsiFile psiFile,
                                            @NotNull ScratchFileCreationHelper.Context context,
                                            @NotNull DataContext dataContext) {
    if (editor == null || psiFile == null) return;
    Caret caret = editor.getCaretModel().getPrimaryCaret();
    context.selectionRange = caret.getSelectionRange();
    int offset = caret.getOffset();
    PsiElement element = InjectedLanguageManager.getInstance(project).findInjectedElementAt(psiFile, offset);
    PsiFile file = element != null ? element.getContainingFile() : psiFile;
    FileTypeManager fileTypeManager = FileTypeManager.getInstance();
    FileType fileType = getFileTypeFromName(file.getName(), fileTypeManager);
    context.language = fileType instanceof LanguageFileType ? file.getLanguage() : null;
    if (fileType != null) {
      context.fileExtension = getFileTypeExtensions(fileType, true, fileTypeManager).first();
    }
    if (context.language == PlainTextLanguage.INSTANCE && file.getFileType() instanceof InternalFileType) {
      context.language = StdLanguages.XML;
    }
    checkLanguageAndTryToFixText(project, context, dataContext);
  }

  private static void checkLanguageAndTryToFixText(@NotNull Project project,
                                                   @NotNull ScratchFileCreationHelper.Context context,
                                                   @NotNull DataContext dataContext) {
    if (context.language == null) return;
    ScratchFileCreationHelper handler = ScratchFileCreationHelper.EXTENSION.forLanguage(context.language);
    if (handler.prepareText(project, context, dataContext)) return;

    PsiFile psiFile = ScratchFileCreationHelper.parseHeader(project, context.language, context.text);
    PsiErrorElement firstError = SyntaxTraverser.psiTraverser(psiFile).traverse().filter(PsiErrorElement.class).first();
    // heuristics: first error must not be right under the file PSI
    // otherwise let the user choose the language manually
    if (firstError != null && firstError.getParent() == psiFile) {
      context.language = null;
    }
  }

  /**
   * Creates a file under the "Scratches" root with the {@link ScratchFileService.Option#create_new_always} option.
   *
   * @return the created file, or null when the scratch plugin is not loaded or the file cannot be created
   */
  public static @Nullable VirtualFile createScratchFile(@Nullable Project project,
                                                        @NotNull String fileName,
                                                        @Nullable Language language,
                                                        @NotNull String text) {
    return createScratchFile(project, fileName, language, text, ScratchFileService.Option.create_new_always);
  }

  /**
   * Creates a file under the "Scratches" root.
   *
   * @return the created file, or null when the scratch plugin is not loaded or the file cannot be created
   */
  public static @Nullable VirtualFile createScratchFile(@Nullable Project project,
                                                        @NotNull String fileName,
                                                        @Nullable Language language,
                                                        @NotNull String text,
                                                        @NotNull ScratchFileService.Option option) {
    RootType rootType = findScratchesRootTypeOrWarn();
    return rootType == null ? null : createScratchFile(project, fileName, language, text, option, rootType);
  }

  /**
   * Creates a file under the "Scratches" root when the scratch plugin is loaded.
   * Otherwise it creates a file in a temporary directory, which the JVM deletes on exit.
   * The temporary file takes the last segment of {@code fileName} and the default extension of {@code language},
   * because only the extension selects the language outside the "Scratches" root.
   * The method does not open an editor.
   *
   * @return the created file, or null when the file cannot be created
   */
  public static @Nullable VirtualFile createScratchOrTempFile(@Nullable Project project,
                                                              @NotNull String fileName,
                                                              @Nullable Language language,
                                                              @NotNull String text) {
    RootType rootType = ScratchUtil.findScratchesRootType();
    if (rootType != null) {
      return createScratchFile(project, fileName, language, text, ScratchFileService.Option.create_new_always, rootType);
    }
    LOG.info("The Scratches root is absent, a temporary file replaces the scratch file '" + fileName + "'");
    String tempFileName = withLanguageExtension(PathUtil.getFileName(fileName), language);
    return createFile(project, fileName, () -> {
      VirtualFile dir = VfsUtil.findFile(TempDirHolder.get(), true);
      if (dir == null) throw new IOException("Cannot find the temporary directory for scratch files in the VFS");
      VirtualFile file = dir.createChildData(ScratchFileCreator.class, getNextAvailableName(dir, tempFileName));
      VfsUtil.saveText(file, text);
      return file;
    });
  }

  private static @Nullable RootType findScratchesRootTypeOrWarn() {
    RootType rootType = ScratchUtil.findScratchesRootType();
    if (rootType == null) {
      LOG.warn("Cannot create a scratch file: no root type with id '" + ScratchUtil.SCRATCHES_ROOT_TYPE_ID + "' is registered");
    }
    return rootType;
  }

  public static @Nullable VirtualFile createScratchFile(@Nullable Project project,
                                                        @NotNull String fileName,
                                                        @Nullable Language language,
                                                        @NotNull String text,
                                                        @NotNull ScratchFileService.Option option,
                                                        @NotNull RootType rootType) {
    return createFile(project, fileName, () -> {
      ScratchFileService fileService = ScratchFileService.getInstance();
      VirtualFile file = fileService.findFile(rootType, fileName, option);
      // save text should go before any other manipulations that load document,
      // otherwise undo will be broken
      VfsUtil.saveText(file, text);
      if (language != null) {
        Language fileLanguage = LanguageUtil.getFileLanguage(file);
        fileService.getScratchesMapping().setMapping(file, fileLanguage == null || language == fileLanguage ? null : language);
      }
      return file;
    });
  }

  /** Runs {@code creator} in a global undoable write command. Shows the error dialog and returns null on an I/O error. */
  private static @Nullable VirtualFile createFile(@Nullable Project project,
                                                  @NotNull String fileName,
                                                  @NotNull ThrowableComputable<VirtualFile, IOException> creator) {
    try {
      return
        WriteCommandAction.writeCommandAction(project).withName(UIBundle.message("file.chooser.create.new.scratch.file.command.name"))
          .withGlobalUndo().shouldRecordActionForActiveDocument(false)
          .withUndoConfirmationPolicy(UndoConfirmationPolicy.REQUEST_CONFIRMATION).compute(creator);
    }
    catch (IOException e) {
      Messages.showMessageDialog(UIBundle.message("create.new.file.could.not.create.file.error.message", fileName),
                                 UIBundle.message("error.dialog.title"), Messages.getErrorIcon());
      return null;
    }
  }

  /** Appends the default extension of {@code language} when {@code fileName} does not map to the file type of the language. */
  private static @NotNull String withLanguageExtension(@NotNull String fileName, @Nullable Language language) {
    LanguageFileType fileType = language == null ? null : language.getAssociatedFileType();
    if (fileType == null) return fileName;
    FileTypeManager fileTypeManager = FileTypeManager.getInstance();
    if (getFileTypeFromName(fileName, fileTypeManager) == fileType) return fileName;
    String extension = getFileTypeExtensions(fileType, true, fileTypeManager).first();
    return extension == null ? fileName : PathUtil.makeFileName(fileName, extension);
  }

  /** Holds the temporary directory of the session for {@link #createScratchOrTempFile}. */
  private static final class TempDirHolder {
    private static @Nullable Path ourDir;

    /** Creates the directory again when it is missing on the disk. The write lock serializes the callers. */
    @RequiresWriteLock
    static @NotNull Path get() throws IOException {
      Path dir = ourDir;
      if (dir == null || !Files.isDirectory(dir)) {
        dir = FileUtil.createTempDirectory("scratch", null, true).toPath();
        ourDir = dir;
      }
      return dir;
    }
  }

  public static @Nullable FileType getFileTypeFromName(@NotNull String fileName, @NotNull FileTypeManager fileTypeManager) {
    if (PathUtilRt.getFileExtension(fileName) == null) return null;
    FileType result = fileTypeManager.getFileTypeByFileName(fileName);
    if (result == UnknownFileType.INSTANCE || getFileTypeExtensions(result, false, fileTypeManager).isEmpty()) {
      return null;
    }
    return result;
  }

  public static @NotNull JBIterable<String> getFileTypeExtensions(@NotNull FileType fileType,
                                                                  boolean sort,
                                                                  @NotNull FileTypeManager fileTypeManager) {
    JBIterable<String> result = JBIterable.from(fileTypeManager.getAssociations(fileType))
      .filterMap(o -> {
        if (o instanceof ExtensionFileNameMatcher) {
          return StringUtil.nullize(((ExtensionFileNameMatcher)o).getExtension());
        }
        else if (o instanceof WildcardFileNameMatcher) {
          String pattern = ((WildcardFileNameMatcher)o).getPattern();
          if (pattern.startsWith("*.") && pattern.indexOf('*', 1) < 0 && pattern.indexOf('?', 1) < 0) {
            return StringUtil.nullize(pattern.substring(2));
          }
        }
        return null;
      });
    if (sort) {
      String def = fileType.getDefaultExtension();
      return result.sort((o1, o2) -> o1.equals(o2) ? 0 : o1.equals(def) ? -1 : o2.equals(def) ? 1 : Integer.compare(o1.length(), o2.length()));
    }
    return result;
  }

  public static @NotNull String getNameWithoutExtension(@NotNull VirtualFile file, @NotNull FileTypeManager fileTypeManager) {
    return getNameWithoutExtension(file.getName(), file.isCaseSensitive(), fileTypeManager);
  }

  public static @NotNull String getNameWithoutExtension(@NotNull String fileName,
                                                        boolean caseSensitive,
                                                        @NotNull FileTypeManager fileTypeManager) {
    // support multipart extensions like *.blade.php
    String extension = StringUtil.notNullize(PathUtilRt.getFileExtension(fileName));
    if (extension.isEmpty()) return fileName;
    FileType fileType = fileTypeManager.getFileTypeByFileName(fileName);
    if (fileType != FileTypes.UNKNOWN) {
      extension = getFileTypeExtensions(fileType, false, fileTypeManager)
        .reduce(extension, (val, o) -> val.length() < o.length() && hasExtension(fileName, o, caseSensitive) ? o : val);
    }
    return fileName.substring(0, fileName.length() - extension.length() - 1);
  }

  public static boolean hasExtension(@NotNull String fileName, @NotNull String extension, boolean caseSensitive) {
    if (fileName.length() < extension.length() + 1) return false;
    if (fileName.charAt(fileName.length() - extension.length() - 1) != '.') return false;
    return StringUtilRt.equal(fileName.subSequence(fileName.length() - extension.length(), fileName.length()), extension, caseSensitive);
  }

  /** @return {@code fileNameAndExt} or the first {@code name_N.ext} variant that does not exist in {@code dir} yet */
  public static @NotNull String getNextAvailableName(@NotNull VirtualFile dir, @NotNull String fileNameAndExt) {
    FileTypeManager fileTypeManager = FileTypeManager.getInstance();
    IntSet existing = new IntOpenHashSet();
    String fileName = getNameWithoutExtension(fileNameAndExt, dir.isCaseSensitive(), fileTypeManager);
    int fileNameLen = fileName.length();
    String fileExt = fileNameAndExt.length() > fileNameLen + 1 ? fileNameAndExt.substring(fileNameLen + 1) : "";
    for (VirtualFile child : dir.getChildren()) {
      if (child.isDirectory()) continue;
      String childName = getNameWithoutExtension(child, fileTypeManager);
      if (!StringUtil.startsWithIgnoreCase(childName, fileName)) continue;
      if (childName.length() == fileNameLen) {
        existing.add(0);
      }
      else if (childName.length() > fileNameLen + 1 &&
               childName.charAt(fileNameLen) == '_') {
        int val = StringUtil.parseInt(childName.substring(fileNameLen + 1), -1);
        if (val > -1) existing.add(val);
      }
    }
    int num = 0;
    while (existing.contains(num)) num++;

    return PathUtil.makeFileName(num == 0 ? fileName : fileName + "_" + num, fileExt);
  }
}
