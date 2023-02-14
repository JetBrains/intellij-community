// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui;

import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.ScalableIcon;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.function.Supplier;

final class ScalableIconWrapperWithToolTip extends IconWrapperWithToolTip implements ScalableIcon {
  ScalableIconWrapperWithToolTip(@SuppressWarnings("TypeMayBeWeakened") @NotNull ScalableIcon icon, @NotNull Supplier<@NlsContexts.Tooltip String> toolTip) {
    super(icon, toolTip);
  }

  private ScalableIconWrapperWithToolTip(IconWrapperWithToolTip another) {
    super(another);
  }

  @Override
  public float getScale() {
    return ((ScalableIcon)retrieveIcon()).getScale();
  }

  @Override
  public @NotNull Icon scale(float scaleFactor) {
    return ((ScalableIcon)retrieveIcon()).scale(scaleFactor);
  }

  @Override
  public @NotNull ScalableIconWrapperWithToolTip replaceBy(@NotNull IconReplacer replacer) {
    return new ScalableIconWrapperWithToolTip(super.replaceBy(replacer));
  }
}
