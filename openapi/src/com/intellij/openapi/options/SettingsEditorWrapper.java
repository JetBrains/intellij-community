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

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Disposer;
import com.intellij.util.containers.Convertor;
import org.jetbrains.annotations.NotNull;

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

  @NotNull
  public JComponent createEditor() {
    return myWrapped.createEditor();
  }

  public void disposeEditor() {
    myWrapped.removeSettingsEditorListener(myListener);
    Disposer.dispose(myWrapped);
  }

}