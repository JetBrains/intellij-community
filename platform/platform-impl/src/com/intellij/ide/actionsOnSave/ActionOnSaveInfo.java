// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actionsOnSave;

import com.intellij.ide.IdeBundle;
import com.intellij.openapi.options.UnnamedConfigurable;
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

/**
 * Some 'actions on save' can be configured in 2 places: on the 'Actions on Save' page and on some other technology-specific page in
 * Settings (Preferences). The state of the corresponding 'action enabled' check boxes (and maybe other UI components) must be
 * the same on both pages at any time. Consider extending {@link ActionOnSaveBackedByOwnConfigurable}.
 *
 * @see #ActionOnSaveInfo(String)
 * @see ActionOnSaveBackedByOwnConfigurable
 */
public abstract class ActionOnSaveInfo {

  final @Nullable String myConfigurableId;

  /**
   * If this field is not-null, it means that {@link UnnamedConfigurable#createComponent()} and {@link UnnamedConfigurable#reset()} have
   * already been run for this configurable.
   */
  private @Nullable UnnamedConfigurable myConfigurableWithInitializedUiComponent;

  /**
   * Some 'actions on save' can be configured in 2 places: on the 'Actions on Save' page and on some other technology-specific page in
   * Settings (Preferences). The state of the corresponding 'action enabled' check boxes (and maybe other UI components) must be
   * the same on both pages at any time. In such cases {@link ActionOnSaveInfo} implementations should pass not-null <code>configurableId</code> to this constructor.
   * Consider extending {@link ActionOnSaveBackedByOwnConfigurable}.
   * <br/><br/>
   * If <code>configurableId</code> is not null then {@link #getConfigurableIfItsUiComponentInitialized()} is guaranteed to return not-null at
   * the moment when the Platform calls setters like {@link #setActionOnSaveEnabled(boolean)}.
   * <br/><br/>
   * When Platform calls getters (like {@link #isActionOnSaveEnabled()} or {@link #getActionOnSaveName()},
   * {@link #getConfigurableIfItsUiComponentInitialized()} may return both null and not-null (it depends on whether the corresponding page
   * in Settings has already been initialized or not). If it's null then the getter implementation should
   * use the currently stored state of the corresponding 'action on save'. If it's not-null then the getter implementation should use the
   * current state of the corresponding UI component on the corresponding page.
   *
   * @see ActionOnSaveBackedByOwnConfigurable
   */
  public ActionOnSaveInfo(@Nullable String configurableId) {
    myConfigurableId = configurableId;
  }

  final void setConfigurableWithInitializedUiComponent(@NotNull UnnamedConfigurable configurable) {
    myConfigurableWithInitializedUiComponent = configurable;
  }

  /**
   * If this {@link ActionOnSaveInfo} implementation passes null <code>configurableId</code> to the constructor then this method always
   * returns null and shouldn't be used.
   * <br/><br/>
   * If this {@link ActionOnSaveInfo} implementation passes not-null <code>configurableId</code> to the constructor then it should use this 
   * method in all the implemented getters and setters.
   * <br/><br/>
   * This method is guaranteed to return not-null at the moment when the Platform calls setters like {@link #setActionOnSaveEnabled(boolean)}.
   * <br/><br/>
   * When Platform calls getters (like {@link #isActionOnSaveEnabled()} or {@link #getActionOnSaveName()}, 
   * this method may return both null and not-null (it depends on whether the corresponding page 
   * in Settings has already been initialized or not). If it's null then the getter implementation should
   * use the currently stored state of the corresponding 'action on save'. If it's not-null then the getter implementation should use the
   * current state of the corresponding UI component on the corresponding page.-
   */
  protected final @Nullable UnnamedConfigurable getConfigurableIfItsUiComponentInitialized() {
    return myConfigurableWithInitializedUiComponent;
  }

  /**
   * Text for the corresponding check box (if {@link #isSaveActionApplicable()} is <code>true</code>) or label (if {@link #isSaveActionApplicable()} is <code>false</code>).
   */
  public abstract @NotNull @NlsContexts.Checkbox String getActionOnSaveName();

  /**
   * Implementations may return <code>false</code> if this 'action on save' can't be enabled in this project with the current configuration.
   * For example, a corresponding technology is not used in the current project. In this case the text returned by
   * {@link #getActionOnSaveName()} will appear as a label but not as an actionable check box.
   * <br/><br/>
   * Implementation may use {@link #getConfigurableIfItsUiComponentInitialized()} to decide whether its logic should depend on the stored
   * state of this 'action on save' or on the current UI components state on the corresponding page in Settings.
   */
  public boolean isSaveActionApplicable() { return true; }

  /**
   * This comment accompanies the corresponding check box (if {@link #isSaveActionApplicable()} is <code>true</code>) or label (if {@link #isSaveActionApplicable()} is <code>false</code>).
   * The text usually depends on the current state of the corresponding 'action on save' configuration. This might be a short summary of
   * the configuration of this 'action on save', or a warning about some problems with the feature configuration.
   * <br/><br/>
   * If {@link #isActionOnSaveEnabled()} is false then the implementation should return either <code>null</code> or {@link ActionOnSaveComment#info(String)}.
   * The recommended style is to use {@link ActionOnSaveComment#warning(String)} only for enabled 'actions on save' that are not configured properly.
   * <br/><br/>
   * Implementation may use {@link #getConfigurableIfItsUiComponentInitialized()} to decide whether its logic should depend on the stored
   * state of this 'action on save' or on the current UI components state on the corresponding page in Settings.
   */
  public @Nullable ActionOnSaveComment getComment() { return null; }

  /**
   * If {@link #isSaveActionApplicable()} returns <code>true</code> then the returned value is used to call <code>setSelected()</code> for the
   * corresponding check box.
   * <br/><br/>
   * Implementation may use {@link #getConfigurableIfItsUiComponentInitialized()} to decide whether its logic should depend on the persisted
   * state of this 'action on save' or on the current UI components state on the corresponding page in Settings.
   */
  public abstract boolean isActionOnSaveEnabled();

  /**
   * Called when a user selects/deselects the check box.
   * <br/><br/>
   * If this {@link ActionOnSaveInfo} implementation passes not-null <code>configurableId</code> to the constructor then this method
   * implementation should call {@link #getConfigurableIfItsUiComponentInitialized()} (which is guaranteed to be not-null) and update UI
   * on the other page in Settings so that it shows the same state as on the 'Actions on Save' page.
   */
  public abstract void setActionOnSaveEnabled(boolean enabled);

  /**
   * {@link ActionLink}s which are visible only when the corresponding table row is hovered. One of the standard use cases is to return a
   * <code>Configure...</code> link that leads to the corresponding page in Settings (Preferences).
   * <br/><br/>
   * <b>Note:</b> do not return {@link DropDownLink}s. The problem with them is that they show a popup on click, and the popup is bigger
   * in height than the current table row. When user clicks something in this popup - the original {@link DropDownLink} is not visible anymore
   * because the mouse pointer hovers a different table row at this moment. Implement {@link #getInPlaceConfigDropDownLink()} if needed - it is visible
   * always, not ony on hover.
   *
   * @see ActionsOnSaveConfigurable#createGoToPageInSettingsLink(String)
   * @see #getInPlaceConfigDropDownLink()
   */
  public @NotNull List<? extends ActionLink> getActionLinks() { return Collections.emptyList(); }

  /**
   * Implementations may return a {@link DropDownLink} for quick in-place configuration of the corresponding 'action on save'.
   * Unlike {@link #getActionLinks()} all {@link DropDownLink}s are always visible for all rows of the 'actions on save' table.
   * <br/><br/>
   * Implementation may use {@link #getConfigurableIfItsUiComponentInitialized()} to decide whether its logic should depend on the persisted
   * state of this 'action on save' or on the current UI components state on the corresponding page in Settings.
   */
  public @Nullable DropDownLink<?> getInPlaceConfigDropDownLink() { return null; }

  /**
   * This component is shown in the 'Activated on' column. This is either a label (if there is no choice) or a {@link DropDownLink} with
   * options. By default it is a label with 'Any save' text. If needed, implementations may override either
   * {@link #getActivatedOnDefaultText()} or {@link #getActivatedOnDropDownLink()}.
   */
  final @NotNull JComponent getActivatedOnComponent() {
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
