/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.options;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.containers.Convertor;

import javax.swing.*;

public class SettingsEditorWrapper <Src, Dst> extends SettingsEditor<Src> {

  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.options.SettingsEditorWrapper");

  private final Convertor<Src, Dst> mySrcToDstConvertor;
  private final SettingsEditor<Dst> myWrapped;

  private SettingsEditorListener<Dst> myListener;

  public SettingsEditorWrapper(SettingsEditor<Dst> wrapped, Convertor<Src, Dst> convertor) {
    mySrcToDstConvertor = convertor;
    myWrapped = wrapped;
    myListener = new SettingsEditorListener<Dst>() {
      public void stateChanged(SettingsEditor<Dst> settingsEditor) {
        fireEditorStateChanged();
      }
    };
    myWrapped.addSettingsEditorListener(myListener);
  }

  public void resetEditorFrom(Src src) {
    myWrapped.resetFrom(mySrcToDstConvertor.convert(src));
  }

  public void applyEditorTo(Src src) throws ConfigurationException {
    myWrapped.applyTo(mySrcToDstConvertor.convert(src));
  }

  public JComponent createEditor() {
    return myWrapped.createEditor();
  }

  public void disposeEditor() {
    myWrapped.removeSettingsEditorListener(myListener);
    myWrapped.disposeEditor();
  }

}