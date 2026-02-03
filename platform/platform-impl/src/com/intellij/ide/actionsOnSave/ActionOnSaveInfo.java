// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actionsOnSave;

import com.intellij.ide.IdeBundle;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.KeyboardShortcut;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.options.ex.Settings;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.ui.components.ActionLink;
import com.intellij.ui.components.DropDownLink;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Collections;
import java.util.List;

/**
 * <code>ActionOnSave</code> object lifecycle is described in {@link ActionOnSaveInfoProvider#getActionOnSaveInfos(ActionOnSaveContext)}.
 * <br/><br/>
 * Some 'actions on save' can be configured in 2 places: on the 'Actions on Save' page and on some other technology-specific page in
 * Settings (Preferences). The state of the corresponding 'action enabled' check boxes (and maybe other UI components) must be
 * the same on both pages at any time. Consider extending {@link ActionOnSaveBackedByOwnConfigurable} in this case.
 *
 * @see ActionOnSaveBackedByOwnConfigurable
 * @see ActionOnSaveInfo#ActionOnSaveInfo(ActionOnSaveContext)
 */
public abstract class ActionOnSaveInfo {

  private final @NotNull ActionOnSaveContext myContext;

  /**
   * Implementations should get understanding about their current state based on the provided {@link ActionOnSaveContext}. The current state
   * may be already modified. See {@link ActionOnSaveContext} and {@link ActionOnSaveInfo} objects lifecycle.
   * {@link ActionOnSaveInfo#isModified()} and all getters (like {@link #isActionOnSaveEnabled()} should be implemented accordingly.
   * <br/><br/>
   * Setter implementations ({@link #setActionOnSaveEnabled(boolean)}), as well as handlers of {@link #getActivatedOnDropDownLink()},
   * {@link #getDropDownLinks()}, and {@link #getActivatedOnDropDownLink()} should store their state in {@link ActionOnSaveContext}.
   * This way, new instances of <code>ActionOnSaveInfo</code> will be able to restore their state when they are re-created next time.
   */
  protected ActionOnSaveInfo(@NotNull ActionOnSaveContext context) {
    myContext = context;
  }

  protected final @NotNull Project getProject() {
    return myContext.getProject();
  }

  protected final @NotNull Settings getSettings() {
    return myContext.getSettings();
  }

  protected final @NotNull ActionOnSaveContext getContext() {
    return myContext;
  }

  /**
   * Called when OK or Apply button is pressed in the Settings (Preferences) dialog.
   */
  protected abstract void apply();

  protected abstract boolean isModified();

  /**
   * Text for the corresponding checkbox (if {@link #isSaveActionApplicable()} is <code>true</code>) or label (if {@link #isSaveActionApplicable()} is <code>false</code>).
   */
  public abstract @NotNull @NlsContexts.Checkbox String getActionOnSaveName();

  /**
   * Implementations may return <code>false</code> if this 'action on save' can't be enabled in this project with the current configuration.
   * For example, a corresponding technology is not used in the current project. In this case the text returned by
   * {@link #getActionOnSaveName()} will appear as a label but not as an actionable checkbox.
   */
  public boolean isSaveActionApplicable() { return true; }

  /**
   * This comment accompanies the corresponding checkbox (if {@link #isSaveActionApplicable()} is <code>true</code>) or label (if {@link #isSaveActionApplicable()} is <code>false</code>).
   * The text usually depends on the current state of the corresponding 'action on save' configuration. This might be a short summary of
   * the configuration of this 'action on save', or a warning about some problems with the feature configuration.
   * <br/><br/>
   * If {@link #isActionOnSaveEnabled()} is false then the implementation should return either <code>null</code> or {@link ActionOnSaveComment#info(String)}.
   * The recommended style is to use {@link ActionOnSaveComment#warning(String)} only for enabled 'actions on save' that are not configured properly.
   */
  public @Nullable ActionOnSaveComment getComment() { return null; }

  /**
   * If {@link #isSaveActionApplicable()} returns <code>true</code> then the value that this method returns is used to call
   * <code>setSelected()</code> for the corresponding checkbox.
   */
  public abstract boolean isActionOnSaveEnabled();

  /**
   * Called when a user selects/deselects the checkbox.
   */
  public abstract void setActionOnSaveEnabled(boolean enabled);

  /**
   * {@link ActionLink}s which are visible only when the corresponding table row is hovered. One of the standard use cases is to return a
   * <code>Configure...</code> link that leads to the corresponding page in Settings (Preferences). See {@link #createGoToPageInSettingsLink(String)}.
   * <br/><br/>
   * <b>Note:</b> do not return {@link DropDownLink}s. The problem with them is that they show a popup on click, and the popup is higher
   * than the current table row. When user clicks something in this popup - the original {@link DropDownLink} is not visible anymore
   * because the mouse pointer hovers a different table row at this moment. Implement {@link #getDropDownLinks()} if needed - it is visible
   * always, not ony on hover.
   *
   * @see #createGoToPageInSettingsLink(String)
   * @see #createGoToPageInSettingsLink(String, String)
   * @see #getDropDownLinks()
   */
  public @NotNull List<? extends ActionLink> getActionLinks() { return Collections.emptyList(); }

  protected final @NotNull ActionLink createGoToPageInSettingsLink(@NotNull @NonNls String configurableId) {
    return createGoToPageInSettingsLink(IdeBundle.message("actions.on.save.link.configure"), configurableId);
  }

  protected final @NotNull ActionLink createGoToPageInSettingsLink(@NotNull @NlsContexts.LinkLabel String linkText,
                                                                   @NotNull @NonNls String configurableId) {
    return new ActionLink(linkText, new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        getSettings().select(getSettings().find(configurableId));
      }
    });
  }

  /**
   * Implementations may return a list of {@link DropDownLink}s for quick in-place configuration of the corresponding 'action on save'.
   * Unlike {@link #getActionLinks()} all {@link DropDownLink}s here are always visible for all rows of the 'actions on save' table.
   */
  public @NotNull List<? extends DropDownLink<?>> getDropDownLinks() {
    return Collections.emptyList();
  }

  /**
   * This component is shown in the 'Activated on' column. This is either a label (if there is no choice) or a {@link DropDownLink} with
   * options. By default, it is a label with 'Any save' text. If needed, implementations may override either
   * {@link #getActivatedOnDefaultText()} or {@link #getActivatedOnDropDownLink()}.
   */
  final @NotNull JComponent getActivatedOnComponent() {
    DropDownLink<?> dropDownLink = getActivatedOnDropDownLink();
    if (dropDownLink != null) {
      dropDownLink.setForeground(UIUtil.getLabelForeground());
      return dropDownLink;
    }

    JBLabel label = new JBLabel(getActivatedOnDefaultText());
    label.setForeground(UIUtil.getLabelDisabledForeground());
    return label;
  }

  /**
   * If this 'action on save' doesn't allow changing 'Activated on' mode (i.e., this implementation doesn't override
   * {@link #getActivatedOnDropDownLink()}, then the returned text appears as a label in the 'Activated on' column.
   * The default value is <code>"Any save"</code>.
   * <br/><br/>
   * If the implementation overrides {@link #getActivatedOnDropDownLink()}) then this method is not used.
   *
   * @see #getActivatedOnDropDownLink()
   * @see #getActivatedOnComponent()
   */
  protected @NotNull @NlsContexts.Label String getActivatedOnDefaultText() {
    return getAnySaveText();
  }

  /**
   * The returned {@link DropDownLink} is shown in the 'Activated on' column. It allows to choose on what kind of 'save' this 'actions on save' should work.
   * Typical use case is to give options like 'Any save (including autosave)' and 'Explicit save (Ctrl+S)'.
   *
   * @see #getActivatedOnDefaultText()
   * @see #getActivatedOnComponent()
   */
  public @Nullable DropDownLink<?> getActivatedOnDropDownLink() { return null; }

  public static @NotNull @NlsContexts.Label String getAnySaveText() {
    return IdeBundle.message("actions.on.save.label.activated.on.any.save");
  }

  public static @NotNull @NlsContexts.Label String getAnySaveTextForDropDownOption() {
    return IdeBundle.message("actions.on.save.option.activated.on.any.save.including.autosave");
  }

  public static @NotNull @NlsContexts.Label String getAnySaveAndExternalChangeText() {
    return IdeBundle.message("actions.on.save.label.activated.on.any.save.and.external.change");
  }

  public static @NotNull @NlsContexts.Label String getAnySaveAndExternalChangeTextForDropDownOption() {
    return IdeBundle.message("actions.on.save.option.activated.on.any.save.and.external.change");
  }

  public static @NotNull @NlsContexts.Label String getExplicitSaveText() {
    KeyboardShortcut shortcut = ActionManager.getInstance().getKeyboardShortcut("SaveAll");
    if (shortcut != null) {
      return IdeBundle.message("actions.on.save.label.activated.on.explicit.save.with.0.shortcut", KeymapUtil.getShortcutText(shortcut));
    }
    else {
      return IdeBundle.message("actions.on.save.label.activated.on.explicit.save");
    }
  }
}
