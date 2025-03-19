package com.intellij.database.run.ui.grid;

import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.Nullable;

import java.awt.*;

public class CellAttributes {
  private final @Nullable Color myBackgroundColor;
  private final @Nullable Color myEffectColor;
  private final boolean myUnderlined;

  public CellAttributes(@Nullable Color backgroundColor, @Nullable Color effectColor, boolean underlined) {
    myBackgroundColor = backgroundColor;
    myEffectColor = effectColor;
    myUnderlined = underlined;
  }

  public @Nullable Color getBackgroundColor() {
    return myBackgroundColor;
  }

  public @Nullable Color getEffectColor() {
    return myEffectColor;
  }

  public boolean isUnderlined() {
    return myUnderlined;
  }

  public static CellAttributes merge(@Nullable CellAttributes under, @Nullable CellAttributes above) {
    if(above == null) return under;
    if(under == null) return above;
    return new CellAttributes(
      ObjectUtils.chooseNotNull(above.getBackgroundColor(), under.getBackgroundColor()),
      ObjectUtils.chooseNotNull(above.getEffectColor(), under.getEffectColor()),
      above.isUnderlined() || under.isUnderlined()
    );
  }
}
