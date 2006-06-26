/*
 * Copyright 2000-2005 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.options;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.Factory;
import com.intellij.openapi.util.Disposer;
import com.intellij.ui.UserActivityListener;
import com.intellij.ui.UserActivityWatcher;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;

/**
 * This class presents an abstraction of user interface transactional editor provider of some abstract data type.
 * {@link #getComponent()} should be called before {@link #resetFrom(Object)}
 */
public abstract class SettingsEditor<Settings> implements Disposable {
  private List<SettingsEditorListener<Settings>> myListeners;
  private UserActivityWatcher myWatcher;
  private UserActivityListener myUserActivityListener;
  private boolean myIsInUpdate = false;
  private Factory<Settings> mySettingsFactory;
  private CompositeSettingsEditor<Settings> myOwner;
  private JComponent myEditorComponent;

  protected abstract void resetEditorFrom(Settings s);
  protected abstract void applyEditorTo(Settings s) throws ConfigurationException;

  @NotNull
  protected abstract JComponent createEditor();
  protected abstract void disposeEditor();

  public SettingsEditor() {
    this(null);
  }

  public SettingsEditor(Factory<Settings> settingsFactory) {
    mySettingsFactory = settingsFactory;
    //just to test, that this editor is always disposed
    Disposer.register(this, new Disposable() {
      public void dispose() {
      }
    });
  }

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
    myIsInUpdate = true;
    try {
      resetEditorFrom(s);
    }
    finally {
      myIsInUpdate = false;
    }
  }

  public final void applyTo(Settings s) throws ConfigurationException {
    applyEditorTo(s);
  }

  public final JComponent getComponent() {
    if (myEditorComponent == null) {
      myEditorComponent = createEditor();
      if (myEditorComponent != null) {
        installWatcher(myEditorComponent);
      }
    }
    return myEditorComponent;
  }

  public final void dispose() {
    uninstallWatcher();
    disposeEditor();
    Disposer.dispose(this);
  }

  protected void uninstallWatcher() {
    if (myWatcher != null) {
      myWatcher.removeUserActivityListener(myUserActivityListener);
      myWatcher = null;
    }
  }

  protected void installWatcher(JComponent c) {
    myWatcher = new UserActivityWatcher();
    myWatcher.register(c);
    myUserActivityListener = new UserActivityListener() {
      public void stateChanged() {
        fireEditorStateChanged();
      }
    };
    myWatcher.addUserActivityListener(myUserActivityListener);
  }

  public final void addSettingsEditorListener(SettingsEditorListener<Settings> listener) {
    if (myListeners == null) myListeners = new ArrayList<SettingsEditorListener<Settings>>();
    myListeners.add(listener);
  }

  public final void removeSettingsEditorListener(SettingsEditorListener<Settings> listener) {
    if (myListeners == null) return;
    myListeners.remove(listener);
    if (myListeners.size() == 0) myListeners = null;
  }

  protected final void fireEditorStateChanged() {
    if (myIsInUpdate || myListeners == null) return;
    SettingsEditorListener[] listeners = myListeners.toArray(new SettingsEditorListener[myListeners.size()]);
    for (int i = 0; i < listeners.length; i++) {
      listeners[i].stateChanged(this);
    }
  }
}