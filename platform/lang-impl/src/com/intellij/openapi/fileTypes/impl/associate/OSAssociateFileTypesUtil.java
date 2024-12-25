// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileTypes.impl.associate;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.ExtensionFileNameMatcher;
import com.intellij.openapi.fileTypes.FileNameMatcher;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.impl.associate.ui.FileTypeAssociationDialog;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.Strings;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;

public final class OSAssociateFileTypesUtil {
  public static final String ENABLE_REG_KEY = "system.file.type.associations.enabled";
  public static final String EXTENSION_SEPARATOR = "-x-";

  private static final Logger LOG = Logger.getInstance(OSAssociateFileTypesUtil.class);

  private OSAssociateFileTypesUtil() { }

  public static void chooseAndAssociate(@NotNull Callback callback) {
    SystemFileTypeAssociator associator = SystemAssociatorFactory.getAssociator();
    if (associator != null) {
      FileTypeAssociationDialog dialog = new FileTypeAssociationDialog();
      if (dialog.showAndGet()) {
        List<FileType> fileTypes = dialog.getSelectedFileTypes();
        doAssociate(callback, associator, fileTypes);
        OSFileAssociationPreferences.getInstance().updateFileTypes(fileTypes);
      }
    }
  }

  public static void restoreAssociations(@NotNull Callback callback) {
    SystemFileTypeAssociator associator = SystemAssociatorFactory.getAssociator();
    if (associator != null) {
      List<FileType> savedFileTypes = restoreFileTypes();
      if (!savedFileTypes.isEmpty()) {
        doAssociate(callback, associator, savedFileTypes);
      }
    }
  }

  private static List<FileType> restoreFileTypes() {
    List<FileType> fileTypes = new ArrayList<>();
    for (String name : OSFileAssociationPreferences.getInstance().fileTypeNames) {
      if (name.contains(EXTENSION_SEPARATOR)) {
        int extPos = name.indexOf(EXTENSION_SEPARATOR);
        String originalName = name.substring(0, extPos);
        String extension = name.substring(extPos + EXTENSION_SEPARATOR.length());
        FileType originalType = getFileTypeByName(originalName);
        if (originalType != null) {
          for (FileNameMatcher matcher : FileTypeManager.getInstance().getAssociations(originalType)) {
            if (matcher instanceof ExtensionFileNameMatcher) {
              if (extension.equals(((ExtensionFileNameMatcher)matcher).getExtension())) {
                fileTypes.add(new MyFileSubtype(originalType, matcher, name, matcher.getPresentableString()));
              }
            }
          }
        }
      }
      else {
        FileType type = getFileTypeByName(name);
        if (type != null) {
          fileTypes.add(type);
        }
      }
    }
    return fileTypes;
  }

  private static @Nullable FileType getFileTypeByName(@NotNull String name) {
    for (FileType fileType : FileTypeManager.getInstance().getRegisteredFileTypes()) {
      if (name.equals(fileType.getName())) return fileType;
    }
    LOG.warn(name + " file type not found.");
    return null;
  }

  private static void doAssociate(Callback callback, SystemFileTypeAssociator associator, List<FileType> fileTypes) {
    if (!fileTypes.isEmpty()) {
      ApplicationManager.getApplication().executeOnPooledThread(
        () -> {
          try {
            callback.beforeStart();
            associator.associateFileTypes(fileTypes);
            OSFileAssociationFeatureUsagesUtil.logFilesAssociated(fileTypes);
            callback.onSuccess(associator.isOsRestartRequired());
          }
          catch (OSFileAssociationException exception) {
            callback.onFailure(exception.getMessage());
            LOG.info(exception);
          }
        }
      );
    }
  }

  public static boolean isAvailable() {
    return Registry.get(ENABLE_REG_KEY).asBoolean() && SystemAssociatorFactory.getAssociator() != null;
  }

  public interface Callback {
    void beforeStart();

    void onSuccess(boolean isOsRestartRequired);

    void onFailure(@NotNull @Nls String errorMessage);
  }

  public static @Unmodifiable @NotNull List<String> getExtensions(@NotNull FileType fileType) {
    return ContainerUtil.map(getExtensionMatchers(fileType), matcher -> matcher.getExtension());
  }

  public static @NotNull List<ExtensionFileNameMatcher> getExtensionMatchers(@NotNull FileType fileType) {
    return getMatchers(fileType).stream()
      .filter(matcher -> matcher instanceof ExtensionFileNameMatcher)
      .map(matcher -> (ExtensionFileNameMatcher)matcher)
      .toList();
  }

  public static @NotNull List<FileNameMatcher> getMatchers(@NotNull FileType fileType) {
    if (fileType instanceof MyFileSubtype) {
      return List.of(((MyFileSubtype)fileType).getMatcher());
    }
    else {
      List<FileNameMatcher> matchers = FileTypeManager.getInstance().getAssociations(fileType);
      if (!matchers.isEmpty()) return matchers;
      String defaultExt = fileType.getDefaultExtension();
      return !defaultExt.isBlank() ? List.of(new ExtensionFileNameMatcher(defaultExt)) : List.of();
    }
  }

  public static @NotNull List<FileType> createSubtypes(@NotNull FileType originalType) {
    List<FileType> subtypes = new ArrayList<>();
    for (FileNameMatcher matcher : FileTypeManager.getInstance().getAssociations(originalType)) {
      if (matcher instanceof ExtensionFileNameMatcher) {
        String name =
          ((ExtensionFileNameMatcher)matcher).getExtension().equals(originalType.getDefaultExtension())
          ? originalType.getName()
          : getSubtypeName(originalType.getName(), (ExtensionFileNameMatcher)matcher);
        subtypes.add(new MyFileSubtype(originalType, matcher, name, matcher.getPresentableString()));
      }
    }
    return subtypes;
  }

  private static String getSubtypeName(@NotNull String baseName, @NotNull ExtensionFileNameMatcher matcher) {
    String ext = Strings.trimStart(matcher.getExtension(), ".");
    return baseName + EXTENSION_SEPARATOR + ext;
  }

  public static @Nullable FileNameMatcher getSubtypeMatcher(@NotNull FileType fileType) {
    return fileType instanceof MyFileSubtype ? ((MyFileSubtype)fileType).getMatcher() : null;
  }

  static @NotNull FileType getOriginalType(@NotNull FileType fileType) {
    return fileType instanceof MyFileSubtype ? ((MyFileSubtype)fileType).myOriginalType : fileType;
  }

  private static class MyFileSubtype implements FileType {
    private final FileType myOriginalType;
    private final FileNameMatcher myMatcher;
    private final String myName;
    private final @NlsSafe String myDescription;

    private MyFileSubtype(FileType originalType, FileNameMatcher matcher, String name, String description) {
      myOriginalType = originalType;
      myMatcher = matcher;
      myName = name;
      myDescription = description;
    }

    @Override
    public @NotNull String getName() {
      return myName;
    }

    @Override
    public @NlsContexts.Label @NotNull String getDescription() {
      return myDescription;
    }

    @Override
    public @NlsSafe @NotNull String getDefaultExtension() {
      return myMatcher instanceof ExtensionFileNameMatcher ? ((ExtensionFileNameMatcher)myMatcher).getExtension() :
             myOriginalType.getDefaultExtension();
    }

    @Override
    public Icon getIcon() {
      return myOriginalType.getIcon();
    }

    @Override
    public boolean isBinary() {
      return myOriginalType.isBinary();
    }

    @Override
    public boolean isReadOnly() {
      return myOriginalType.isReadOnly();
    }

    @Override
    public @Nullable String getCharset(@NotNull VirtualFile file, byte @NotNull [] content) {
      return myOriginalType.getCharset(file, content);
    }

    private @NotNull FileNameMatcher getMatcher() {
      return myMatcher;
    }
  }
}
