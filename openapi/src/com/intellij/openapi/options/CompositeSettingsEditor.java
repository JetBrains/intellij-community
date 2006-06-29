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

import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Factory;
import com.intellij.util.Alarm;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

public abstract class CompositeSettingsEditor<Settings> extends SettingsEditor<Settings> {
  private Collection<SettingsEditor<Settings>> myEditors;
  private SettingsEditorListener<Settings> myChildSettingsListener;
  private SynchronizationConroller mySyncConroller;
  private boolean myIsDisposed = false;

  public CompositeSettingsEditor() {}

  public CompositeSettingsEditor(Factory<Settings> factory) {
    super(factory);
    if (factory != null) {
      mySyncConroller = new SynchronizationConroller();
    }
  }

  public abstract CompositeSettingsBuilder<Settings> getBuilder();

  public void resetEditorFrom(Settings settings) {
    for (final SettingsEditor<Settings> myEditor : myEditors) {
      myEditor.resetEditorFrom(settings);
    }
  }

  public void applyEditorTo(Settings settings) throws ConfigurationException {
    for (final SettingsEditor<Settings> myEditor : myEditors) {
      myEditor.applyTo(settings);
    }
  }

  public void uninstallWatcher() {
    for (SettingsEditor<Settings> editor : myEditors) {
      editor.removeSettingsEditorListener(myChildSettingsListener);
    }
  }

  public void installWatcher(JComponent c) {
    myChildSettingsListener = new SettingsEditorListener<Settings>() {
      public void stateChanged(SettingsEditor<Settings> editor) {
        fireEditorStateChanged();
        if (mySyncConroller != null) mySyncConroller.handleStateChange(editor);
      }
    };

    for (SettingsEditor<Settings> editor : myEditors) {
      editor.addSettingsEditorListener(myChildSettingsListener);
    }
  }

  @NotNull
  protected final JComponent createEditor() {
    CompositeSettingsBuilder<Settings> builder = getBuilder();
    myEditors = builder.getEditors();
    for (final SettingsEditor<Settings> editor : myEditors) {
      Disposer.register(this, editor);
      editor.setOwner(this);
    }
    return builder.createCompoundEditor();
  }

  public void disposeEditor() {
    Disposer.dispose(this);
    myIsDisposed = true;
  }

  private class SynchronizationConroller {
    private final Set<SettingsEditor> myChangedEditors = new HashSet<SettingsEditor>();
    private final Alarm mySyncAlarm = new Alarm(Alarm.ThreadToUse.SWING_THREAD);
    private boolean myIsInSync = false;

    public void handleStateChange(SettingsEditor editor) {
      if (myIsInSync || myIsDisposed) return;
      myChangedEditors.add(editor);
      mySyncAlarm.cancelAllRequests();
      mySyncAlarm.addRequest(new Runnable() {
        public void run() {
          if (!myIsDisposed) {
            sync();
          }
        }
      }, 300);
    }

    public void sync() {
      myIsInSync = true;
      try {
        Settings snapshot = getSnapshot();
        for (SettingsEditor<Settings> editor : myEditors) {
          if (!myChangedEditors.contains(editor)) {
            editor.resetFrom(snapshot);
          }
        }
      }
      catch (ConfigurationException e) {
      }
      finally{
        myChangedEditors.clear();
        myIsInSync = false;
      }
    }
  }
}