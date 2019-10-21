// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.options;

import com.intellij.openapi.util.Disposer;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.function.Function;

public final class SettingsEditorWrapper <Src, Dst> extends SettingsEditor<Src> {
  private final Function<? super Src, ? extends Dst> mySrcToDstConvertor;
  private final SettingsEditor<Dst> myWrapped;

  private final SettingsEditorListener<Dst> myListener;

  public SettingsEditorWrapper(SettingsEditor<Dst> wrapped, Function<? super Src, ? extends Dst> convertor) {
    mySrcToDstConvertor = convertor;
    myWrapped = wrapped;
    myListener = settingsEditor -> fireEditorStateChanged();
    myWrapped.addSettingsEditorListener(myListener);
  }

  @Override
  public void resetEditorFrom(@NotNull Src src) {
    myWrapped.resetFrom(mySrcToDstConvertor.apply(src));
  }

  @Override
  public void applyEditorTo(@NotNull Src src) throws ConfigurationException {
    myWrapped.applyTo(mySrcToDstConvertor.apply(src));
  }

  @Override
  @NotNull
  public JComponent createEditor() {
    return myWrapped.createEditor();
  }

  @Override
  public void disposeEditor() {
    myWrapped.removeSettingsEditorListener(myListener);
    Disposer.dispose(myWrapped);
  }
}
