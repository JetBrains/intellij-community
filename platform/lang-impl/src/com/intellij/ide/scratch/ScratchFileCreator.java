// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.scratch;

import com.intellij.ide.util.PsiNavigationSupport;
import com.intellij.lang.Language;
import com.intellij.lang.LanguageUtil;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.impl.LaterInvocator;
import com.intellij.openapi.command.UndoConfirmationPolicy;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.ExtensionFileNameMatcher;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.FileTypes;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.fileTypes.UnknownFileType;
import com.intellij.openapi.fileTypes.WildcardFileNameMatcher;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.util.text.StringUtilRt;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.Navigatable;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.ui.UIBundle;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.PathUtil;
import com.intellij.util.PathUtilRt;
import com.intellij.util.containers.JBIterable;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;

/**
 * Creates files under a {@link RootType} the way scratch files are created: runs the {@link ScratchFileCreationHelper}
 * of the language, picks a free file name, writes the text, stores the language mapping and opens the file in an editor.
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
    RootType targetRootType = context.defaultRootType != null ? context.defaultRootType : ScratchUtil.findScratchesRootType();
    if (targetRootType == null) {
      LOG.warn("Cannot create a scratch file: no root type with id '" + ScratchUtil.SCRATCHES_ROOT_TYPE_ID + "' is registered");
      return null;
    }
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

  public static @Nullable VirtualFile createScratchFile(@Nullable Project project,
                                                        @NotNull String fileName,
                                                        @Nullable Language language,
                                                        @NotNull String text,
                                                        @NotNull ScratchFileService.Option option,
                                                        @NotNull RootType rootType) {
    try {
      return
        WriteCommandAction.writeCommandAction(project).withName(UIBundle.message("file.chooser.create.new.scratch.file.command.name"))
          .withGlobalUndo().shouldRecordActionForActiveDocument(false)
          .withUndoConfirmationPolicy(UndoConfirmationPolicy.REQUEST_CONFIRMATION).compute(() -> {
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
    catch (IOException e) {
      Messages.showMessageDialog(UIBundle.message("create.new.file.could.not.create.file.error.message", fileName),
                                 UIBundle.message("error.dialog.title"), Messages.getErrorIcon());
      return null;
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
