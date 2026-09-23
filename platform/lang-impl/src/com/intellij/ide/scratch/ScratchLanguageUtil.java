// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.scratch;

import com.intellij.icons.AllIcons;
import com.intellij.lang.Language;
import com.intellij.lang.LanguageUtil;
import com.intellij.lang.PerFileMappings;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.command.undo.BasicUndoableAction;
import com.intellij.openapi.command.undo.UndoManager;
import com.intellij.openapi.command.undo.UnexpectedUndoException;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.fileTypes.impl.AbstractFileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.ReadonlyStatusHandler;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ObjectUtils;
import com.intellij.util.PathUtil;
import com.intellij.util.PathUtilRt;
import com.intellij.util.containers.JBIterable;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.Icon;
import java.io.IOException;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Changes the language mapping of files under a {@link RootType} (see {@link ScratchFileService#getScratchesMapping()})
 * and builds the language chooser for it. Shared by the scratch plugin and the SQL dialect switcher.
 */
@ApiStatus.Internal
public final class ScratchLanguageUtil {
  private ScratchLanguageUtil() {
  }

  public static void updateFileExtension(@NotNull Project project,
                                         @Nullable VirtualFile file,
                                         @NotNull LanguageItem item) throws IOException {
    ApplicationManager.getApplication().assertWriteAccessAllowed();
    if (!CommandProcessor.getInstance().isCommandInProgress()) {
      throw new AssertionError("command required");
    }

    if (file == null) return;
    FileTypeManager fileTypeManager = FileTypeManager.getInstance();
    FileType prevType = ScratchFileCreator.getFileTypeFromName(file.getName(), fileTypeManager);
    Language currLanguage = LanguageUtil.getLanguageForPsi(project, file, item.fileType);
    FileType currType = currLanguage == null ? item.fileType : ObjectUtils.notNull(currLanguage.getAssociatedFileType(), item.fileType);
    if (prevType == currType) return;

    String nameWithoutExtension = ScratchFileCreator.getNameWithoutExtension(file, fileTypeManager);

    VirtualFile parent = file.getParent();
    String newName = parent != null ? VfsUtil.getNextAvailableName(parent, nameWithoutExtension, item.fileExtension) :
                     PathUtil.makeFileName(nameWithoutExtension, item.fileExtension);
    file.rename(ScratchLanguageUtil.class, newName);
  }

  public static boolean hasMatchingExtension(@NotNull Project project, @NotNull VirtualFile file) {
    String fileName = file.getName();
    if (PathUtilRt.getFileExtension(fileName) == null) return false;
    boolean caseSensitive = file.isCaseSensitive();
    FileTypeManager fileTypeManager = FileTypeManager.getInstance();
    FileType expected = ScratchFileCreator.getFileTypeFromName(fileName, fileTypeManager);
    Language language = LanguageUtil.getLanguageForPsi(project, file);
    FileType actual = language == null ? null : language.getAssociatedFileType();
    if (actual == null) return expected != null;
    if (expected == actual) return true;
    return ScratchFileCreator.getFileTypeExtensions(actual, false, fileTypeManager)
      .filter(o -> ScratchFileCreator.hasExtension(fileName, o, caseSensitive))
      .isNotEmpty();
  }

  public static void changeLanguageWithUndo(@NotNull Project project,
                                            @NotNull LanguageItem item,
                                            VirtualFile @NotNull [] sortedFiles,
                                            @NotNull PerFileMappings<Language> mappings) throws UnexpectedUndoException {
    ReadonlyStatusHandler.OperationStatus status = ReadonlyStatusHandler.getInstance(project).ensureFilesWritable(Arrays.asList(sortedFiles));
    if (status.hasReadonlyFiles()) return;

    final Set<VirtualFile> matchedExtensions = new LinkedHashSet<>();
    final Map<VirtualFile, Language> oldMapping = new HashMap<>();
    for (VirtualFile file : sortedFiles) {
      oldMapping.put(file, mappings.getMapping(file));
      if (hasMatchingExtension(project, file)) {
        matchedExtensions.add(file);
      }
    }

    BasicUndoableAction action = new BasicUndoableAction(sortedFiles) {
      @Override
      public void undo() {
        for (VirtualFile file : sortedFiles) {
          mappings.setMapping(file, oldMapping.get(file));
        }
      }

      @Override
      public void redo() {
        for (VirtualFile file : sortedFiles) {
          mappings.setMapping(file, item.language);
        }
      }
    };
    action.redo();
    UndoManager.getInstance(project).undoableActionPerformed(action);

    for (VirtualFile file : matchedExtensions) {
      try {
        updateFileExtension(project, file, item);
      }
      catch (IOException ignored) {
      }
    }

    Language language = item.language;
    if (language == null) {
      language = LanguageUtil.getFileTypeLanguage(item.fileType);
    }
    if (language != null) {
      ScratchFileCreationHelper.EXTENSION.forLanguage(language)
        .afterLanguageChange(project, matchedExtensions);
    }
  }

  public static LRUPopupBuilder<LanguageItem> buildLanguagesPopup(@NotNull Project project,
                                                                  @NotNull @NlsContexts.PopupTitle String title) {
    FileTypeManager fileTypeManager = FileTypeManager.getInstance();
    List<LanguageItem> items = JBIterable.from(LanguageUtil.getFileLanguages())
      .map(LanguageItem::fromLanguage)
      .append(JBIterable.of(fileTypeManager.getRegisteredFileTypes())
                .filter(AbstractFileType.class)
                .map(o -> LanguageItem.fromAbstractFileType(o, ScratchFileCreator.getFileTypeExtensions(o, true, fileTypeManager).first())))
      .filter(item -> item != null && ScratchFileTypeFilter.isEnabled(item.fileType()))
      .toList();

    Comparator<LanguageItem> comparator = (o1, o2) -> StringUtil.naturalCompare(o1.getDisplayName(), o2.getDisplayName());
    return new LRUPopupBuilder<LanguageItem>(project, title) {
      @Override
      public String getDisplayName(LanguageItem o) {
        return o.getDisplayName();
      }

      @Override
      public Icon getIcon(LanguageItem o) {
        return ObjectUtils.notNull(o.fileType.getIcon(), AllIcons.FileTypes.Any_type);
      }

      @Override
      public String getStorageId(LanguageItem o) {
        return o.language != null ? o.language.getID() : o.fileType.getName();
      }
    }
      .forValues(items)
      .withComparator(comparator)
      .withExtraSpeedSearchNamer(o -> o.fileExtension)
      .withSelection(null);
  }

  public record LanguageItem(@Nullable Language language, @NotNull FileType fileType, @NotNull String fileExtension) {
    public @NotNull @NlsSafe String getDisplayName() {
      return language != null ? language.getDisplayName() : fileType.getDescription() + " (*." + fileExtension + ")";
    }

    public static @Nullable LanguageItem fromLanguage(@NotNull Language language) {
      LanguageFileType fileType = language.getAssociatedFileType();
      String defaultExtension = fileType == null ? null : fileType.getDefaultExtension();
      if (StringUtil.isEmpty(defaultExtension)) return null;
      return new LanguageItem(language, fileType, defaultExtension);
    }

    public static @Nullable LanguageItem fromAbstractFileType(@NotNull AbstractFileType fileType, @Nullable String fileExtension) {
      if (fileExtension == null) return null;
      return new LanguageItem(null, fileType, fileExtension);
    }
  }
}
