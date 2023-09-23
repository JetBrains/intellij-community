// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.navigation;

import com.intellij.ide.IdeBundle;
import com.intellij.lang.IdeLanguageCustomization;
import com.intellij.lang.Language;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Consider implementing {@link ChooseByNameContributorEx} additionally for better performance.
 */
public interface GotoClassContributor extends ChooseByNameContributor {
  
  @Nullable
  String getQualifiedName(@NotNull NavigationItem item);

  @Nullable
  String getQualifiedNameSeparator();

  /**
   * Override this method to change texts in 'Go to Class' popup and presentation of 'Navigate | Class' action.
   *
   * @return collective name of items provided by this contributor
   * @see #getElementLanguage()
   */
  default @NotNull @Nls String getElementKind() {
    return IdeBundle.message("go.to.class.kind.text");
  }

  /**
   * Pluralized {@link #getElementKind()}
   */
  default @NotNull @Nls List<String> getElementKindsPluralized() {
    return List.of(IdeBundle.message("go.to.class.kind.text.pluralized"));
  }

  default @NotNull String getTabTitlePluralized() {
    List<String> kinds = getElementKindsPluralized();
    return !kinds.isEmpty() ? kinds.get(0) : IdeBundle.message("go.to.class.kind.text.pluralized");
  }

  /**
   * If the language returned by this method is one of {@link IdeLanguageCustomization#getPrimaryIdeLanguages() the primary IDE languages} the result of
   * {@link #getElementKind()} will be used to name `Navigate | Class' action and in 'Go to Class' popup.
   *
   * @return the language to which items returned by this contributor belong
   */
  default @Nullable Language getElementLanguage() {
    return null;
  }
}
