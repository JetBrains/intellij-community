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

import com.intellij.openapi.util.Factory;
import com.intellij.util.Alarm;

import javax.swing.*;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
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
    for (Iterator<SettingsEditor<Settings>> iterator = myEditors.iterator(); iterator.hasNext();) {
      iterator.next().resetEditorFrom(settings);
    }
  }

  public void applyEditorTo(Settings settings) throws ConfigurationException {
    for (Iterator<SettingsEditor<Settings>> iterator = myEditors.iterator(); iterator.hasNext();) {
      iterator.next().applyTo(settings);
    }
  }

  public void uninstallWatcher() {
    for (Iterator<SettingsEditor<Settings>> iterator = myEditors.iterator(); iterator.hasNext();) {
      SettingsEditor<Settings> editor = iterator.next();
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

    for (Iterator<SettingsEditor<Settings>> iterator = myEditors.iterator(); iterator.hasNext();) {
      SettingsEditor<Settings> editor = iterator.next();
      editor.addSettingsEditorListener(myChildSettingsListener);
    }
  }

  protected final JComponent createEditor() {
    CompositeSettingsBuilder<Settings> builder = getBuilder();
    myEditors = builder.getEditors();
    for (Iterator<SettingsEditor<Settings>> iterator = myEditors.iterator(); iterator.hasNext();) {
      iterator.next().setOwner(this);
    }
    return builder.createCompoundEditor();
  }

  public void disposeEditor() {
    for (Iterator<SettingsEditor<Settings>> iterator = myEditors.iterator(); iterator.hasNext();) {
      iterator.next().dispose();
    }
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
        for (Iterator<SettingsEditor<Settings>> iterator = myEditors.iterator(); iterator.hasNext();) {
          SettingsEditor<Settings> editor = iterator.next();
          if (myChangedEditors.contains(editor)) continue;
          editor.resetFrom(snapshot);
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