// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs;

import com.intellij.openapi.editor.colors.ColorKey;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public final class FileStatusFactory {
  private static final FileStatusFactory ourInstance = new FileStatusFactory();
  public static final String FILESTATUS_COLOR_KEY_PREFIX = "FILESTATUS_";
  private final List<FileStatus> myStatuses = new ArrayList<>();

  private FileStatusFactory() {
  }

  public FileStatus createFileStatus(@NonNls @NotNull String id,
                                     @Nls(capitalization = Nls.Capitalization.Sentence) @NotNull String description) {
    return createFileStatus(id, () -> description, null);
  }

  public FileStatus createFileStatus(@NonNls @NotNull String id,
                                     @Nls(capitalization = Nls.Capitalization.Sentence) @NotNull String description,
                                     @Nullable Color color) {
    return createFileStatus(id, () -> description, color);
  }

  public FileStatus createFileStatus(@NonNls @NotNull String id,
                                     @NotNull Supplier<@Nls(capitalization = Nls.Capitalization.Sentence) @NotNull String> description) {
    return createFileStatus(id, description, null);
  }

  public synchronized FileStatus createFileStatus(@NonNls @NotNull String id,
                                                  @NotNull Supplier<@Nls(capitalization = Nls.Capitalization.Sentence) @NotNull String> description,
                                                  @Nullable Color color) {
    FileStatusImpl result = new FileStatusImpl(id, ColorKey.createColorKey(FILESTATUS_COLOR_KEY_PREFIX + id, color), description);
    myStatuses.add(result);
    return result;
  }

  public synchronized FileStatus[] getAllFileStatuses() {
    return myStatuses.toArray(new FileStatus[0]);
  }

  public static FileStatusFactory getInstance() {
    return ourInstance;
  }

  /**
   * author: lesya
   */
  private static class FileStatusImpl implements FileStatus {
    private final String myStatus;
    private final ColorKey myColorKey;
    private final Supplier<@Nls(capitalization = Nls.Capitalization.Sentence) String> myTextSupplier;

    FileStatusImpl(@NotNull String status,
                   @NotNull ColorKey key,
                   @NotNull Supplier<@Nls(capitalization = Nls.Capitalization.Sentence) String> textSupplier) {
      myStatus = status;
      myColorKey = key;
      myTextSupplier = textSupplier;
    }

    @NonNls
    public String toString() {
      return myStatus;
    }

    @Override
    public String getText() {
      return myTextSupplier.get();
    }

    @Override
    public Color getColor() {
      return EditorColorsManager.getInstance().getSchemeForCurrentUITheme().getColor(getColorKey());
    }

    @NotNull
    @Override
    public ColorKey getColorKey() {
      return myColorKey;
    }

    @NotNull
    @Override
    public String getId() {
      return myStatus;
    }
  }
}
