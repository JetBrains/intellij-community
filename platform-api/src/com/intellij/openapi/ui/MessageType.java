package com.intellij.openapi.ui;

import javax.swing.*;
import java.awt.*;

public class MessageType {

  public static final MessageType ERROR = new MessageType(Messages.getErrorIcon(), new Color(255, 204, 204, 230));
  public static final MessageType INFO = new MessageType(Messages.getInformationIcon(), new Color(186, 238, 186, 230));
  public static final MessageType WARNING = new MessageType(Messages.getWarningIcon(), new Color(186, 238, 186, 230));

  private Icon myDefaultIcon;
  private Color myPopupBackground;

  private MessageType(final Icon defaultIcon, Color popupBackground) {
    myDefaultIcon = defaultIcon;
    myPopupBackground = popupBackground;
  }

  public Icon getDefaultIcon() {
    return myDefaultIcon;
  }

  public Color getPopupBackground() {
    return myPopupBackground;
  }
}