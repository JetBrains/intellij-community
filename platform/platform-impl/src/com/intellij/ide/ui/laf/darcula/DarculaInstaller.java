/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.ide.ui.laf.darcula;

import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.colors.impl.DefaultColorsScheme;
import com.intellij.openapi.ui.Messages;

/**
 * @author Konstantin Bulenkov
 */
public class DarculaInstaller {

  public static void uninstall() {
    if (DarculaLaf.NAME.equals(EditorColorsManager.getInstance().getGlobalScheme().getName())) {
      final EditorColorsScheme scheme = EditorColorsManager.getInstance().getScheme(DefaultColorsScheme.DEFAULT_SCHEME_NAME);
      if (scheme != null) {
        EditorColorsManager.getInstance().setGlobalScheme(scheme);
      }
    }

    restart();
  }

  private static void restart() {
    if (Messages.showOkCancelDialog("You must restart the IDE to changes take effect. Restart now?", "Restart Is Required", "Restart", "Postpone", Messages.getQuestionIcon()) == Messages.OK) {
      ApplicationManagerEx.getApplicationEx().restart();
    }
  }

  public static void install() {
    if (!DarculaLaf.NAME.equals(EditorColorsManager.getInstance().getGlobalScheme().getName())) {
      final EditorColorsScheme scheme = EditorColorsManager.getInstance().getScheme(DarculaLaf.NAME);
      if (scheme != null) {
        EditorColorsManager.getInstance().setGlobalScheme(scheme);
      }
    }

    restart();
  }
}
