// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.ui.laf;

import com.intellij.ide.ui.LafManager;
import com.intellij.ide.ui.UITheme;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.colors.EditorColorsListener;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.colors.impl.AbstractColorsScheme;
import com.intellij.openapi.editor.colors.impl.DefaultColorsScheme;
import com.intellij.openapi.editor.colors.impl.EditorColorsManagerImpl;
import com.intellij.openapi.editor.colors.impl.EditorColorsSchemeImpl;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.messages.MessageBusConnection;
import org.jdom.Element;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * @author Konstantin Bulenkov
 */
public final class TempUIThemeBasedLookAndFeelInfo extends UIThemeBasedLookAndFeelInfo {
  private final VirtualFile mySchemeFile;
  private UIManager.LookAndFeelInfo myPreviousLaf;
  private static final Logger LOG = Logger.getInstance(TempUIThemeBasedLookAndFeelInfo.class);

  public TempUIThemeBasedLookAndFeelInfo(UITheme theme, VirtualFile editorSchemeFile) {
    super(theme);
    mySchemeFile = editorSchemeFile;
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
    if (name != null && mySchemeFile != null) {
      EditorColorsManagerImpl cm = (EditorColorsManagerImpl)EditorColorsManager.getInstance();
      AbstractColorsScheme tmpScheme = new DefaultColorsScheme();
      boolean loaded = false;
      try {
        Element xml = JDOMUtil.load(mySchemeFile.getInputStream());
        String parentSchemeName = xml.getAttributeValue("parent_scheme", EditorColorsManager.DEFAULT_SCHEME_NAME);
        EditorColorsScheme parentScheme = EditorColorsManager.getInstance().getScheme(parentSchemeName);
        tmpScheme = new EditorColorsSchemeImpl(parentScheme);
        tmpScheme.readExternal(xml);
        loaded = true;
      }
      catch (Exception e) {
        LOG.warn(e);
      }

      if (loaded) {
        AbstractColorsScheme scheme = tmpScheme;
        EditorColorsManagerImpl.setTempScheme(scheme, mySchemeFile);
        cm.setGlobalScheme(scheme);
        MessageBusConnection connect = ApplicationManager.getApplication().getMessageBus().connect();
        connect.subscribe(EditorColorsManager.TOPIC, new EditorColorsListener() {
          @Override
          public void globalSchemeChange(@Nullable EditorColorsScheme editorColorsScheme) {
            if (editorColorsScheme == scheme || editorColorsScheme == null) {
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
