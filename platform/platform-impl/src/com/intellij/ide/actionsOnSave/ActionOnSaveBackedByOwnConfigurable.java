// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actionsOnSave;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.options.UnnamedConfigurable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Consider extending this class instead of {@link ActionOnSaveInfo} if the state of this 'action on save' is configured
 * not only on the 'Actions on Save' page in Settings but also on some other (e.g technology-specific) page.
 * The state of the corresponding 'action enabled' check boxes (and maybe other UI components) must be
 * the same on both pages at any time.
 *
 * @see ActionOnSaveInfo
 */
public abstract class ActionOnSaveBackedByOwnConfigurable<Conf extends UnnamedConfigurable> extends ActionOnSaveInfo {
  private static final Logger LOG = Logger.getInstance(ActionOnSaveBackedByOwnConfigurable.class);

  private final Class<Conf> myConfigurableClass;

  public ActionOnSaveBackedByOwnConfigurable(@NotNull String configurableId, @NotNull Class<Conf> configurableClass) {
    super(configurableId);
    myConfigurableClass = configurableClass;
  }

  protected final <T> T getSomeValue(@NotNull @SuppressWarnings("BoundedWildcard") Supplier<T> ifConfigurableNotYetInitialized,
                                     @NotNull Function<Conf, T> ifConfigurableAlreadyInitialized) {
    UnnamedConfigurable configurable = getConfigurableIfItsUiComponentInitialized();
    if (configurable == null) {
      return ifConfigurableNotYetInitialized.get();
    }

    if (!myConfigurableClass.isInstance(configurable)) {
      LOG.error("Unexpected configurable type: " + configurable.getClass() + "\n" + configurable);
      return ifConfigurableNotYetInitialized.get();
    }

    //noinspection unchecked
    return ifConfigurableAlreadyInitialized.apply((Conf)configurable);
  }

  @Override
  public final boolean isSaveActionApplicable() {
    return getSomeValue(this::isApplicableAccordingToStoredState, this::isApplicableAccordingToUiState);
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
  public final @Nullable String getComment() {
    return getSomeValue(this::getCommentAccordingToStoredState, this::getCommentAccordingToUiState);
  }

  /**
   * @see ActionOnSaveInfo#getComment()
   */
  protected @Nullable String getCommentAccordingToStoredState() {
    return null;
  }

  /**
   * @see ActionOnSaveInfo#getComment()
   */
  protected @Nullable String getCommentAccordingToUiState(@NotNull Conf configurable) {
    return null;
  }

  @Override
  public final boolean isWarningComment() {
    return getSomeValue(this::isWarningCommentAccordingToStoredState, this::isWarningCommentAccordingToUiState);
  }

  /**
   * @see ActionOnSaveInfo#isWarningComment()
   */
  protected boolean isWarningCommentAccordingToStoredState() {
    return false;
  }

  /**
   * @see ActionOnSaveInfo#isWarningComment()
   */
  protected boolean isWarningCommentAccordingToUiState(@NotNull Conf configurable) {
    return false;
  }

  @Override
  public final boolean isActionOnSaveEnabled() {
    return getSomeValue(this::isActionOnSaveEnabledAccordingToStoredState, this::isActionOnSaveEnabledAccordingToUiState);
  }

  /**
   * @see ActionOnSaveInfo#isActionOnSaveEnabled()
   */
  protected boolean isActionOnSaveEnabledAccordingToStoredState() {
    return true;
  }

  /**
   * @see ActionOnSaveInfo#isActionOnSaveEnabled()
   */
  protected boolean isActionOnSaveEnabledAccordingToUiState(@NotNull Conf configurable) {
    return true;
  }

  @Override
  public final void setActionOnSaveEnabled(boolean enabled) {
    UnnamedConfigurable configurable = getConfigurableIfItsUiComponentInitialized();
    if (configurable == null) {
      LOG.error("Configurable is expected to be initialized at this point");
      return;
    }

    if (!myConfigurableClass.isInstance(configurable)) {
      LOG.error("Unexpected configurable type:" + configurable.getClass() + "\n" + configurable);
      return;
    }

    //noinspection unchecked
    setActionOnSaveEnabled((Conf)configurable, enabled);
  }

  /**
   * @see ActionOnSaveInfo#setActionOnSaveEnabled(boolean)
   */
  protected abstract void setActionOnSaveEnabled(@NotNull Conf configurable, boolean enabled);
}
