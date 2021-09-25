// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actionsOnSave.api;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.actionsOnSave.ActionsOnSaveConfigurable;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.ui.components.ActionLink;
import com.intellij.ui.components.DropDownLink;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
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
   * because the mouse pointer is over a different table row at this moment. Implement {@link #getInPlaceConfigDropDownLink()} if needed - it is visible
   * always, not ony on hover.
   *
   * @see ActionsOnSaveConfigurable#createGoToPageInSettingsLink(String)
   * @see #getInPlaceConfigDropDownLink()
   */
  public @NotNull List<? extends ActionLink> getActionLinks() { return Collections.emptyList(); }

  /**
   * Implementations may return a {@link DropDownLink} for quick in-place configuration of the corresponding 'action on save'.
   * Unlike {@link #getActionLinks()} all {@link DropDownLink}s are always visible for all rows of the 'actions on save' table.
   */
  public @Nullable DropDownLink<?> getInPlaceConfigDropDownLink() { return null; }

  /**
   * This component is shown in the 'Activated on' column. This is either a label (if there is no choice) or a {@link DropDownLink} with
   * options. By default it is a label with 'Any save' text. If needed, implementations may override either
   * {@link #getActivatedOnDefaultText()} or {@link #getActivatedOnDropDownLink()}.
   */
  public final @NotNull JComponent getActivatedOnComponent() {
    DropDownLink<?> dropDownLink = getActivatedOnDropDownLink();
    if (dropDownLink != null) {
      dropDownLink.setForeground(UIUtil.getLabelForeground());
      return dropDownLink;
    }

    JBLabel label = new JBLabel(getActivatedOnDefaultText());
    label.setEnabled(false);
    return label;
  }

  /**
   * If this 'action on save' doesn't allow changing 'Activated on' mode (i.e. this implementation doesn't override
   * {@link #getActivatedOnDropDownLink()}), then the returned text is shown as label in the 'Activated on' column.
   * The default value is <code>"Any save"</code>.
   * <br/><br/>
   * If the implementation overrides {@link #getActivatedOnDropDownLink()}) then this method is not used.
   *
   * @see #getActivatedOnDropDownLink()
   */
  protected @NotNull @NlsContexts.Label String getActivatedOnDefaultText() {
    return IdeBundle.message("actions.on.save.label.activated.on.any.save");
  }

  /**
   * The returned {@link DropDownLink} is shown in the 'Activated on' column. It allows to choose on what kind of 'save' this 'actions on save' should work.
   * Typical use case is to give options like 'Any save' and 'Explicit save (Ctrl+S)'.
   *
   * @see #getActivatedOnDefaultText()
   */
  public @Nullable DropDownLink<?> getActivatedOnDropDownLink() { return null; }
}
