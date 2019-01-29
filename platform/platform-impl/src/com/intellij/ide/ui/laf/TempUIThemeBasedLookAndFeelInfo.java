// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.ui.laf;

import com.intellij.ide.ui.LafManager;
import com.intellij.ide.ui.UITheme;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.colors.EditorColorsListener;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.colors.impl.EditorColorsManagerImpl;
import com.intellij.util.messages.MessageBusConnection;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.nio.file.Path;

/**
 * @author Konstantin Bulenkov
 */
public class TempUIThemeBasedLookAndFeelInfo extends UIThemeBasedLookAndFeelInfo {
  private final Path myEditorSchemeFile;
  private UIManager.LookAndFeelInfo myPreviousLaf;

  public TempUIThemeBasedLookAndFeelInfo(UITheme theme, Path editorSchemeFile) {
    super(theme);
    myEditorSchemeFile = editorSchemeFile;
    myPreviousLaf = LafManager.getInstance().getCurrentLookAndFeel();
    if (myPreviousLaf instanceof TempUIThemeBasedLookAndFeelInfo) {
      myPreviousLaf = ((TempUIThemeBasedLookAndFeelInfo)myPreviousLaf).getPreviousLaf();
    }
  }

  public UIManager.LookAndFeelInfo getPreviousLaf() {
    return myPreviousLaf;
  }

  @Override
  protected void installEditorScheme() {
    String name = getTheme().getEditorScheme();
    if (name != null && myEditorSchemeFile != null) {
      EditorColorsManagerImpl cm = (EditorColorsManagerImpl)EditorColorsManager.getInstance();
      cm.getSchemeManager().loadBundledScheme(myEditorSchemeFile.toString(), this);
      EditorColorsScheme scheme = cm.getScheme(getTheme().getEditorSchemeName());
      if (scheme != null) {
        EditorColorsManagerImpl.setTempScheme(scheme);
        cm.setGlobalScheme(scheme);
        MessageBusConnection connect = ApplicationManager.getApplication().getMessageBus().connect();
        connect.subscribe(EditorColorsManager.TOPIC, new EditorColorsListener() {
          @Override
          public void globalSchemeChange(@Nullable EditorColorsScheme editorColorsScheme) {
            if (editorColorsScheme == scheme) {
              return;
            }
            cm.getSchemeManager().removeScheme(scheme);
            connect.disconnect();
          }
        });
      }
    }

  }
}
