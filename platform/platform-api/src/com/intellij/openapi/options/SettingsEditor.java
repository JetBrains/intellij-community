// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.options;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Factory;
import com.intellij.ui.UserActivityListener;
import com.intellij.ui.UserActivityWatcher;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.List;

/**
 * This class presents an abstraction of user interface transactional editor provider of some abstract data type.
 * {@link #getComponent()} should be called before {@link #resetFrom(Object)}
 */
public abstract class SettingsEditor<Settings> implements Disposable {
  private final List<SettingsEditorListener<Settings>> myListeners = ContainerUtil.createLockFreeCopyOnWriteList();
  private UserActivityWatcher myWatcher;
  private boolean myIsInUpdate = false;
  private final Factory<Settings> mySettingsFactory;
  private CompositeSettingsEditor<Settings> myOwner;
  private JComponent myEditorComponent;

  protected abstract void resetEditorFrom(@NotNull Settings s);
  protected abstract void applyEditorTo(@NotNull Settings s) throws ConfigurationException;

  @NotNull
  protected abstract JComponent createEditor();

  protected void disposeEditor() {
  }

  public SettingsEditor() {
    this(null);
  }

  public SettingsEditor(Factory<Settings> settingsFactory) {
    mySettingsFactory = settingsFactory;
    Disposer.register(this, new Disposable() {
      @Override
      public void dispose() {
        disposeEditor();
        uninstallWatcher();
      }
    });
  }

  @NotNull
  public Settings getSnapshot() throws ConfigurationException {
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

  public Factory<Settings> getFactory() {
    return mySettingsFactory;
  }

  public final void resetFrom(Settings s) {
    bulkUpdate(() -> resetEditorFrom(s));
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

  @NotNull
  protected UserActivityWatcher createWatcher() {
    return new UserActivityWatcher();
  }

  public final void addSettingsEditorListener(SettingsEditorListener<Settings> listener) {
    myListeners.add(listener);
  }

  public final void removeSettingsEditorListener(SettingsEditorListener<Settings> listener) {
    myListeners.remove(listener);
  }

  protected final void fireEditorStateChanged() {
    if (myIsInUpdate) return;
    for (SettingsEditorListener<Settings> listener : myListeners) {
      listener.stateChanged(this);
    }
  }
}