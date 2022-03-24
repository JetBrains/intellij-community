// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actionsOnSave;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.options.UnnamedConfigurable;
import com.intellij.openapi.options.ex.ConfigurableWrapper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Consider extending this class instead of {@link ActionOnSaveInfo} if the state of this 'action on save' is configured
 * not only on the 'Actions on Save' page in Settings but also on some other (e.g., technology-specific) page.
 * The state of the corresponding 'action enabled' check boxes (and maybe other UI components) must be
 * the same on both pages at any time.
 * <br/><br/>
 * Getter implementations (like {@link #isActionOnSaveEnabled()} or {@link #getActionOnSaveName()}) must call
 * {@link #getValueFromSavedStateOrFromUiState(Supplier, Function)}.
 * <br/><br/>
 * Setter implementations ({@link #setActionOnSaveEnabled(boolean)}), as well as handlers of {@link #getActivatedOnDropDownLink()},
 * {@link #getDropDownLinks()}, and {@link #getActivatedOnDropDownLink()} must call {@link #updateUiOnOwnPage(Consumer)}.
 *
 * @see #getValueFromSavedStateOrFromUiState(Supplier, Function)
 * @see #updateUiOnOwnPage(Consumer)
 */
public abstract class ActionOnSaveBackedByOwnConfigurable<Conf extends UnnamedConfigurable> extends ActionOnSaveInfo {
  private static final Logger LOG = Logger.getInstance(ActionOnSaveBackedByOwnConfigurable.class);

  private final @NotNull String myConfigurableId;
  private final @NotNull Class<Conf> myConfigurableClass;

  /**
   * If this field is not-null, it means that {@link UnnamedConfigurable#createComponent()} and {@link UnnamedConfigurable#reset()} have
   * been already called for this configurable.
   */
  private @Nullable Conf myConfigurableWithInitializedUiComponent;

  public ActionOnSaveBackedByOwnConfigurable(@NotNull ActionOnSaveContext context,
                                             @NotNull String configurableId,
                                             @NotNull Class<Conf> configurableClass) {
    super(context);
    myConfigurableId = configurableId;
    myConfigurableClass = configurableClass;

    UnnamedConfigurable configurable = getSettings().getConfigurableWithInitializedUiComponent(myConfigurableId, false);

    if (configurable instanceof ConfigurableWrapper) {
      configurable = ((ConfigurableWrapper)configurable).getRawConfigurable();
    }

    if (configurable == null) {
      // This means that there were no reasons to initialize the 'own' page yet.
      return;
    }

    if (!myConfigurableClass.isInstance(configurable)) {
      LOG.error("Unexpected configurable type:" + configurable.getClass() + "\n" + configurable);
      return;
    }

    //noinspection unchecked
    myConfigurableWithInitializedUiComponent = (Conf)configurable;
  }

  void resetUiOnOwnPageThatIsMirroredOnActionsOnSavePage() {
    if (myConfigurableWithInitializedUiComponent != null) {
      resetUiOnOwnPageThatIsMirroredOnActionsOnSavePage(myConfigurableWithInitializedUiComponent);
      getSettings().checkModified(myConfigurableId);
    }
  }

  /**
   * Called when 'Reset' link is clicked in the top-right corner of the 'Actions on Save' page. Implementations should restore the state of
   * the UI components on its own page but only those that are mirrored on the 'Actions on Save' page. The goal is to make 'Actions on Save'
   * page not modified. Own page may stay modified if there are any changes that can't be controlled by user from the 'Actions on Save' page.
   * <br/><br/>
   * The default method implementation restores the initial state of the 'action on save enabled' checkbox. Override this method if
   * your implementation can also change the state of this 'action on save' via handlers of {@link #getActivatedOnDropDownLink()},
   * {@link #getDropDownLinks()}, and {@link #getActivatedOnDropDownLink()}.
   */
  protected void resetUiOnOwnPageThatIsMirroredOnActionsOnSavePage(@NotNull Conf configurable) {
    setActionOnSaveEnabled(configurable, isActionOnSaveEnabledAccordingToStoredState());
  }

  /**
   * Normally, {@link ActionOnSaveBackedByOwnConfigurable} implementations don't need to do anything on <code>apply()</code> method because
   * all changes are applied when Platform calls <code>myConfigurableWithInitializedUiComponent.apply()</code>.
   */
  @Override
  protected void apply() { }

  @Override
  protected final boolean isModified() {
    return myConfigurableWithInitializedUiComponent != null &&
           areOptionsMirroredOnActionsOnSavePageModified(myConfigurableWithInitializedUiComponent);
  }

  /**
   * Called from the constructor when this {@link ActionOnSaveBackedByOwnConfigurable} is created because user has switched from some other
   * page in Settings (references) to the 'Actions on Save' page. 'Actions on Save' page may be already modified right from the start
   * because options shown there have already been altered on the
   * <br/><br/>
   * The default method implementation only checks the state of the 'action on save enabled' checkbox. Override this method if
   * your implementation can also change the state of this 'action on save' via handlers of {@link #getActivatedOnDropDownLink()},
   * {@link #getDropDownLinks()}, and {@link #getActivatedOnDropDownLink()}.
   */
  protected boolean areOptionsMirroredOnActionsOnSavePageModified(@NotNull Conf configurable) {
    return isActionOnSaveEnabledAccordingToStoredState() != isActionOnSaveEnabledAccordingToUiState(configurable);
  }

  /**
   * This method calls either <code>ifConfigurableNotYetInitialized</code> or <code>ifConfigurableAlreadyInitialized</code>
   * depending on whether the UI components on the corresponding page in Settings have been already initialized.
   *
   * @param ifConfigurableNotYetInitialized  implementation should return the value according to its stored state. Typical implementation is
   *                                         <code>FooConfig.getInstance(myProject).getSomeValue()</code>
   * @param ifConfigurableAlreadyInitialized implementation should return the value according to UI components state on the corresponding
   *                                         page in Settings. Typical implementation is like <code>configurable.myFeatureCheckBox.isSelected()</code>.
   */
  protected final <T> T getValueFromSavedStateOrFromUiState(@NotNull Supplier<? extends T> ifConfigurableNotYetInitialized,
                                                            @NotNull Function<Conf, ? extends T> ifConfigurableAlreadyInitialized) {
    if (myConfigurableWithInitializedUiComponent == null) {
      return ifConfigurableNotYetInitialized.get();
    }
    else {
      return ifConfigurableAlreadyInitialized.apply(myConfigurableWithInitializedUiComponent);
    }
  }

  /**
   * Ensures that the corresponding page in Settings is initialized (it means that {@link UnnamedConfigurable#createComponent()} and
   * {@link UnnamedConfigurable#reset()} have been called) and passes the corresponding <code>Configurable</code> to the <code>uiUpdater</code>.
   *
   * @param uiUpdater typical implementation is like <code>configurable.myFeatureCheckBox.setSelected(outerVariable)</code>.
   */
  protected final void updateUiOnOwnPage(@NotNull Consumer<Conf> uiUpdater) {
    ensureUiComponentsOnOwnPageInitialized();

    if (myConfigurableWithInitializedUiComponent != null) {
      uiUpdater.accept(myConfigurableWithInitializedUiComponent);
      getSettings().checkModified(myConfigurableId);
    }
  }

  private void ensureUiComponentsOnOwnPageInitialized() {
    if (myConfigurableWithInitializedUiComponent != null) return;

    UnnamedConfigurable configurable = getSettings().getConfigurableWithInitializedUiComponent(myConfigurableId, true);
    if (configurable instanceof ConfigurableWrapper) {
      configurable = ((ConfigurableWrapper)configurable).getRawConfigurable();
    }

    if (configurable == null) {
      LOG.error("Failed to initialize configurable with id=" + myConfigurableId);
      return;
    }

    if (!myConfigurableClass.isInstance(configurable)) {
      LOG.error("Unexpected configurable type:" + configurable.getClass() + "\n" + configurable);
      return;
    }

    //noinspection unchecked
    myConfigurableWithInitializedUiComponent = (Conf)configurable;
  }

  @Override
  public final boolean isSaveActionApplicable() {
    return getValueFromSavedStateOrFromUiState(this::isApplicableAccordingToStoredState, this::isApplicableAccordingToUiState);
  }

  /**
   * @see ActionOnSaveInfo#isSaveActionApplicable()
   */
  protected boolean isApplicableAccordingToStoredState() {
    return true;
  }

  /**
   * @see ActionOnSaveInfo#isSaveActionApplicable()
   */
  protected boolean isApplicableAccordingToUiState(@NotNull Conf configurable) {
    return true;
  }

  @Override
  public final @Nullable ActionOnSaveComment getComment() {
    return getValueFromSavedStateOrFromUiState(this::getCommentAccordingToStoredState, this::getCommentAccordingToUiState);
  }

  /**
   * If {@link #isActionOnSaveEnabled()} is false then the implementation should return either <code>null</code> or {@link ActionOnSaveComment#info(String)}.
   * The recommended style is to use {@link ActionOnSaveComment#warning(String)} only for enabled 'actions on save' that are not configured properly.
   *
   * @see ActionOnSaveInfo#getComment()
   */
  protected @Nullable ActionOnSaveComment getCommentAccordingToStoredState() {
    return null;
  }

  /**
   * If {@link #isActionOnSaveEnabled()} is false then the implementation should return either <code>null</code> or {@link ActionOnSaveComment#info(String)}.
   * The recommended style is to use {@link ActionOnSaveComment#warning(String)} only for enabled 'actions on save' that are not configured properly.
   *
   * @see ActionOnSaveInfo#getComment()
   */
  protected @Nullable ActionOnSaveComment getCommentAccordingToUiState(@NotNull Conf configurable) {
    return null;
  }

  @Override
  public final boolean isActionOnSaveEnabled() {
    return getValueFromSavedStateOrFromUiState(this::isActionOnSaveEnabledAccordingToStoredState,
                                               this::isActionOnSaveEnabledAccordingToUiState);
  }

  /**
   * @see ActionOnSaveInfo#isActionOnSaveEnabled()
   */
  protected boolean isActionOnSaveEnabledAccordingToStoredState() {
    return false;
  }

  /**
   * @see ActionOnSaveInfo#isActionOnSaveEnabled()
   */
  protected boolean isActionOnSaveEnabledAccordingToUiState(@NotNull Conf configurable) {
    return false;
  }

  @Override
  public final void setActionOnSaveEnabled(boolean enabled) {
    updateUiOnOwnPage(configurable -> setActionOnSaveEnabled(configurable, enabled));
  }

  /**
   * @see ActionOnSaveInfo#setActionOnSaveEnabled(boolean)
   */
  protected abstract void setActionOnSaveEnabled(@NotNull Conf configurable, boolean enabled);
}
