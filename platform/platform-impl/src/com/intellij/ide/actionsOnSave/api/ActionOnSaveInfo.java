// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actionsOnSave.api;

import com.intellij.ide.actionsOnSave.ActionsOnSaveConfigurable;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.ui.components.ActionLink;
import com.intellij.ui.components.DropDownLink;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

public abstract class ActionOnSaveInfo {

  /**
   * Implementations may decide not to show the check box if the corresponding 'action on save' is not properly configured or not applicable
   * for the current project. In this case the text returned by {@link #getActionOnSaveName()} will appear as a label but not as a check box.
   */
  public boolean isShowCheckbox() { return true; }

  /**
   * Text for the corresponding check box (if {@link #isShowCheckbox()} is <code>true</code>) or label (if {@link #isShowCheckbox()} is <code>false</code>).
   */
  public abstract @NotNull @NlsContexts.Checkbox String getActionOnSaveName();

  /**
   * This comment accompanies the corresponding check box (if {@link #isShowCheckbox()} is <code>true</code>) or label (if {@link #isShowCheckbox()} is <code>false</code>).
   * The text usually depends on the current state of the corresponding 'action on save' configuration. This might be a short summary of
   * the configuration of this 'action on save', or a warning about some problems with the feature configuration.
   */
  public @Nullable @NlsContexts.Label String getComment() { return null; }

  /**
   * If <code>true</code> then the text returned by {@link #getComment()} will be accompanied by a warning icon.
   * The returned value should depend on the current state of the corresponding 'action on save' configuration.
   */
  public boolean isWarningComment() { return false; }

  /**
   * {@link ActionLink}s which are visible only when the corresponding table row is hovered. One on the standard use cases is to return a
   * <code>Configure...</code> link that leads to the corresponding page in Settings (Preferences).
   * <br/><br/>
   * <b>Note:</b> do not return {@link DropDownLink}s. The problem with them is that they show a popup on click, and the popup is bigger
   * in height than the current table row. When user clicks something this popup - the original {@link DropDownLink} is not visible anymore
   * because the mouse pointer is over a different table row at this moment. Implement {@link #getDropDownLink()} if needed - it is visible
   * always, not ony on hover.
   *
   * @see ActionsOnSaveConfigurable#createGoToPageInSettingsLink(String)
   * @see #getDropDownLink()
   */
  public @NotNull List<? extends ActionLink> getActionLinks() { return Collections.emptyList(); }

  /**
   * Implementations my return some {@link DropDownLink} for quick in-place configuration of the corresponding 'action on save'.
   * Unlike {@link #getActionLinks()} all {@link DropDownLink}s are always visible for all rows of the 'actions on save' table.
   */
  public @Nullable DropDownLink<?> getDropDownLink() { return null; }
}
