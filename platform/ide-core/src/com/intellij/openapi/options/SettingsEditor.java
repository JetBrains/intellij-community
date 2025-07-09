// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.options;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Factory;
import com.intellij.ui.UserActivityListener;
import com.intellij.ui.UserActivityWatcher;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.List;

/**
 * This class presents an abstraction of user interface transactional editor provider of some abstract data type.
 * <p>
 * {@link #getComponent()} should be called before {@link #resetFrom(Object)}
 * 
 * @see <a href="https://plugins.jetbrains.com/docs/intellij/run-configurations.html#settingseditor">SettingsEditor (IntelliJ Platform Docs)</a>
 * @see <a href="https://plugins.jetbrains.com/docs/intellij/run-configurations.html#simplifying-settings-editors">Simplifying Settings Editors (IntelliJ Platform Docs)</a>
 */
public abstract class SettingsEditor<Settings> implements Disposable {
  private final List<SettingsEditorListener<Settings>> myListeners = ContainerUtil.createLockFreeCopyOnWriteList();
  private UserActivityWatcher myWatcher;
  private boolean myIsInUpdate = false;
  private final Factory<? extends Settings> mySettingsFactory;
  private CompositeSettingsEditor<Settings> myOwner;
  private JComponent myEditorComponent;
  private final boolean myMaximizeEditorHeight;

  /**
   * @see SettingsEditor#SettingsEditor(Factory, boolean)
   */
  @ApiStatus.Internal
  public final boolean isMaximizeEditorHeight() {
    return myMaximizeEditorHeight;
  }

  /**
   * Resets the current editor UI state to the initial settings state.
   */
  protected abstract void resetEditorFrom(@NotNull Settings s);

  /**
   * Copies the current editor UI state into the target settings object {@code s}.
   */
  protected abstract void applyEditorTo(@NotNull Settings s) throws ConfigurationException;

  protected abstract @NotNull JComponent createEditor();

  @ApiStatus.Internal
  public static @NotNull JComponent createEditorComponent(@NotNull SettingsEditor<?> editor) {
    return editor.createEditor();
  }

  protected void disposeEditor() {
  }

  public SettingsEditor() {
    this(null);
  }


  public SettingsEditor(@Nullable Factory<? extends Settings> settingsFactory) {
    this(settingsFactory, false);
  }

  /**
   * @param maximizeEditorHeight settings are edited in a view. This view might be configured to occupy max possible height, so
   *                             settings might be centralized vertically
   * @see #isMaximizeEditorHeight()
   */
  @ApiStatus.Internal
  protected SettingsEditor(@Nullable Factory<? extends Settings> settingsFactory, boolean maximizeEditorHeight) {
    mySettingsFactory = settingsFactory;
    myMaximizeEditorHeight = maximizeEditorHeight;
    Disposer.register(this, new Disposable() {
      @Override
      public void dispose() {
        disposeEditor();
        uninstallWatcher();
      }
    });
  }

  public @NotNull Settings getSnapshot() throws ConfigurationException {
    if (myOwner != null) return myOwner.getSnapshot();

    Settings settings = mySettingsFactory.create();
    applyTo(settings);
    return settings;
  }

  final void setOwner(CompositeSettingsEditor<Settings> owner) {
    myOwner = owner;
  }

  public final CompositeSettingsEditor<Settings> getOwner() {
    return myOwner;
  }

  public Factory<? extends Settings> getFactory() {
    return mySettingsFactory;
  }

  public final void resetFrom(Settings s) {
    bulkUpdate(() -> {
      if (myEditorComponent == null) getComponent();
      resetEditorFrom(s);
    });
  }

  public final void bulkUpdate(Runnable runnable) {
    boolean wasInUpdate = myIsInUpdate;
    try {
      myIsInUpdate = true;
      runnable.run();
    }
    finally {
      myIsInUpdate = wasInUpdate;
    }
    fireEditorStateChanged();
  }

  public final void applyTo(Settings s) throws ConfigurationException {
    applyEditorTo(s);
  }

  public final JComponent getComponent() {
    if (myEditorComponent == null) {
      myEditorComponent = createEditor();
      installWatcher(myEditorComponent);
    }
    return myEditorComponent;
  }

  @Override
  public final void dispose() {
    myListeners.clear();
  }

  protected void uninstallWatcher() {
    myWatcher = null;
  }

  protected void installWatcher(JComponent c) {
    myWatcher = createWatcher();
    myWatcher.register(c);
    UserActivityListener userActivityListener = new UserActivityListener() {
      @Override
      public void stateChanged() {
        fireEditorStateChanged();
      }
    };
    myWatcher.addUserActivityListener(userActivityListener, this);
  }

  protected @NotNull UserActivityWatcher createWatcher() {
    return new UserActivityWatcher();
  }

  public final void addSettingsEditorListener(SettingsEditorListener<Settings> listener) {
    myListeners.add(listener);
  }

  public final void removeSettingsEditorListener(SettingsEditorListener<Settings> listener) {
    myListeners.remove(listener);
  }

  protected final void fireEditorStateChanged() {
    if (myIsInUpdate) {
      return;
    }
    for (SettingsEditorListener<Settings> listener : myListeners) {
      listener.stateChanged(this);
    }
  }

  public boolean isSpecificallyModified() {
    return false;
  }

  public boolean isReadyForApply() {
    return true;
  }
}
