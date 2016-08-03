/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.openapi.editor.markup;

import com.intellij.openapi.diagnostic.Logger;
import org.intellij.lang.annotations.JdkConstants;
import org.jetbrains.annotations.NotNull;

import java.awt.*;

/**
 * Read-only attributes can not be modified.
 */
public class ReadOnlyTextAttributes extends TextAttributes {
  
  private static Logger LOG = Logger.getInstance("#" + ReadOnlyTextAttributes.class.getName());
  
  public ReadOnlyTextAttributes(@NotNull TextAttributes original) {
    super(original.getForegroundColor(),
          original.getBackgroundColor(),
          original.getEffectColor(),
          original.getEffectType(),
          original.getErrorStripeColor(),
          original.getFontType());
  }
  
  @Override
  public void setAttributes(Color foregroundColor,
                            Color backgroundColor,
                            Color effectColor,
                            Color errorStripeColor,
                            EffectType effectType,
                            @JdkConstants.FontStyle int fontType) {
    logReadOnlyError();
  }

  @Override
  public void setBackgroundColor(Color color) {
    logReadOnlyError();
  }

  @Override
  public void setForegroundColor(Color color) {
    logReadOnlyError();
  }

  @Override
  public void setEffectColor(Color color) {
    logReadOnlyError();
  }

  @Override
  public void setEffectType(EffectType effectType) {
    logReadOnlyError();
  }

  @Override
  public void setErrorStripeColor(Color color) {
    logReadOnlyError();
  }

  @Override
  public void setFontType(@JdkConstants.FontStyle int type) {
    logReadOnlyError();
  }

  @Override
  public void setEnforceEmpty(boolean enforceEmpty) {
    logReadOnlyError();
  }
  
  
  private static void logReadOnlyError() {
    LOG.error("An attempt to modify read-only text attributes");
  }
}
