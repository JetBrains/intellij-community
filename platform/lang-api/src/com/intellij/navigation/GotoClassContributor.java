// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.navigation;

import com.intellij.ide.IdeBundle;
import com.intellij.lang.IdeLanguageCustomization;
import com.intellij.lang.Language;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Consider implementing {@link ChooseByNameContributorEx} additionally for better performance.
 */
public interface GotoClassContributor extends ChooseByNameContributor {
  
  @Nullable
  String getQualifiedName(NavigationItem item);

  @Nullable
  String getQualifiedNameSeparator();

  /**
   * Override this method to change texts in 'Go to Class' popup and presentation of 'Navigate | Class' action.
   *
   * @return collective name of items provided by this contributor
   * @see #getElementLanguage()
   */
  @NotNull
  @Nls
  default String getElementKind() {
    return IdeBundle.message("go.to.class.kind.text");
  }

  /**
   * Pluralized {@link #getElementKind()}
   */
  @NotNull
  @Nls
  default List<String> getElementKindsPluralized() {
    return ContainerUtil.newArrayList(IdeBundle.message("go.to.class.kind.text.pluralized"));
  }

  @NotNull
  default String getTabTitlePluralized() {
    List<String> kinds = getElementKindsPluralized();
    return !kinds.isEmpty() ? kinds.get(0) : IdeBundle.message("go.to.class.kind.text.pluralized");
  }

  /**
   * If the language returned by this method is one of {@link IdeLanguageCustomization#getPrimaryIdeLanguages() the primary IDE languages} the result of
   * {@link #getElementKind()} will be used to name `Navigate | Class' action and in 'Go to Class' popup.
   *
   * @return the language to which items returned by this contributor belong
   */
  @Nullable
  default Language getElementLanguage() {
    return null;
  }
}
