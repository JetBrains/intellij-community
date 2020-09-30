// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.options;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Factory;
import com.intellij.util.Alarm;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

public abstract class CompositeSettingsEditor<Settings> extends SettingsEditor<Settings> {
  public static final Logger LOG = Logger.getInstance(CompositeSettingsEditor.class);

  private Collection<SettingsEditor<Settings>> myEditors;
  private SettingsEditorListener<Settings> myChildSettingsListener;
  private SynchronizationController mySyncController;
  private boolean myIsDisposed;

  public CompositeSettingsEditor() {}

  public CompositeSettingsEditor(@Nullable Factory<Settings> factory) {
    super(factory);

    if (factory != null) {
      mySyncController = new SynchronizationController();
    }
  }

  public abstract CompositeSettingsBuilder<Settings> getBuilder();

  @Override
  public void resetEditorFrom(@NotNull Settings settings) {
    for (SettingsEditor<Settings> myEditor : myEditors) {
      try {
        myEditor.resetEditorFrom(settings);
      }
      catch (Exception e) {
        LOG.error(e);
      }
    }
  }

  @Override
  public void applyEditorTo(@NotNull Settings settings) throws ConfigurationException {
    for (final SettingsEditor<Settings> myEditor : myEditors) {
      try {
        myEditor.applyTo(settings);
      }
      catch (ConfigurationException e) {
        throw e;
      }
      catch (Exception e) {
        LOG.error(e);
      }
    }
  }

  @Override
  public void uninstallWatcher() {
    for (SettingsEditor<Settings> editor : myEditors) {
      editor.removeSettingsEditorListener(myChildSettingsListener);
    }
  }

  @Override
  public void installWatcher(JComponent c) {
    myChildSettingsListener = new SettingsEditorListener<Settings>() {
      @Override
      public void stateChanged(@NotNull SettingsEditor<Settings> editor) {
        fireEditorStateChanged();
        if (mySyncController != null && !myIsDisposed) {
          mySyncController.handleStateChange(editor, CompositeSettingsEditor.this);
        }
      }
    };

    for (SettingsEditor<Settings> editor : myEditors) {
      editor.addSettingsEditorListener(myChildSettingsListener);
    }
  }

  @Override
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

  @Override
  public void disposeEditor() {
    myIsDisposed = true;
  }

  private final class SynchronizationController {
    private final Set<SettingsEditor<?>> myChangedEditors = new HashSet<>();
    private Alarm mySyncAlarm;
    private boolean myIsInSync;

    public void handleStateChange(@NotNull SettingsEditor<?> editor, @NotNull Disposable parentDisposable) {
      if (myIsInSync) {
        return;
      }

      myChangedEditors.add(editor);

      if (mySyncAlarm == null) {
        mySyncAlarm = new Alarm(Alarm.ThreadToUse.SWING_THREAD, parentDisposable);
      }
      else {
        mySyncAlarm.cancelAllRequests();
      }
      mySyncAlarm.addRequest(this::sync, 300);
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
      catch (ConfigurationException ignored) {
      }
      finally {
        myChangedEditors.clear();
        myIsInSync = false;
      }
    }
  }
}