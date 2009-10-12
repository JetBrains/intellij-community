package com.intellij.codeInsight.lookup;

import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * @author peter
 */
public class RealLookupElementPresentation extends LookupElementPresentation {
  private final int myMaximumWidth;
  private final int myFontWidth;

  public RealLookupElementPresentation(int maximumWidth, int fontWidth) {
    super();
    myMaximumWidth = maximumWidth;
    myFontWidth = fontWidth;
  }

  @Override
  public boolean isReal() {
    return true;
  }

  public int getRemainingTextLength() {
    return (myMaximumWidth - calculateWidth(this, myFontWidth)) / myFontWidth;
  }

  public static int calculateWidth(LookupElementPresentation presentation, final int fontWidth) {
    int result = 0;
    result += getStringWidth(presentation.getItemText(), fontWidth);
    result += getStringWidth(presentation.getTailText(), fontWidth);
    final String typeText = presentation.getTypeText();
    if (StringUtil.isNotEmpty(typeText)) {
      result += getStringWidth("XXX", fontWidth); //3 spaces for nice tail-type separation
      result += getStringWidth(typeText, fontWidth);
    }
    result += fontWidth; //for unforeseen Swing size adjustments
    final Icon typeIcon = presentation.getTypeIcon();
    if (typeIcon != null) {
      result += typeIcon.getIconWidth();
    }
    return result;
  }

  public static int getStringWidth(@Nullable final String text, final int fontWidth) {
    if (text != null) {
      return text.length() * fontWidth;
    }
    return 0;
  }
}
