// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.injection;

import com.intellij.lang.Language;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.util.ui.EmptyIcon;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * @author Dmitry Avdeev
 */
public abstract class Injectable implements Comparable<Injectable> {

  /** Unique ID among injected language and reference injector IDs */
  public abstract @NotNull String getId();

  public abstract @NotNull @Nls(capitalization = Nls.Capitalization.Title) String getDisplayName();

  public @Nullable @Nls(capitalization = Nls.Capitalization.Sentence) String getAdditionalDescription() {
    return null;
  }

  public @NotNull Icon getIcon() {
    return EmptyIcon.ICON_16;
  }

  @Override
  public int compareTo(@NotNull Injectable o) {
    return getDisplayName().compareTo(o.getDisplayName());
  }

  /**
   * @return null for reference injections
   */
  public abstract @Nullable Language getLanguage();

  public Language toLanguage() {
    Language language = getLanguage();
    return language == null ? new Language(getId(), false) {
      @Override
      public @NotNull String getDisplayName() {
        return Injectable.this.getDisplayName();
      }
    } : language;
  }

  public static Injectable fromLanguage(final Language language) {
    return new Injectable() {
      @Override
      public @NotNull String getId() {
        return language.getID();
      }

      @Override
      public @NotNull String getDisplayName() {
        return language.getDisplayName();
      }

      @Override
      public @Nullable String getAdditionalDescription() {
        final FileType ft = language.getAssociatedFileType();
        return ft != null ? " (" + ft.getDescription() + ")" : null;
      }

      @Override
      public @NotNull Icon getIcon() {
        final FileType ft = language.getAssociatedFileType();
        return ft != null && ft.getIcon() != null ? ft.getIcon() : EmptyIcon.ICON_16;
      }

      @Override
      public Language getLanguage() {
        return language;
      }
    };
  }
}
