package com.intellij.openapi.ui;

import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import java.awt.*;

public class MessageType {

  public static final MessageType ERROR = new MessageType(UIUtil.getBalloonErrorIcon(), new Color(255, 204, 204, 230));
  public static final MessageType INFO = new MessageType(UIUtil.getBalloonInformationIcon(), new Color(186, 238, 186, 230));
  public static final MessageType WARNING = new MessageType(UIUtil.getBalloonWarningIcon(), new Color(249, 247, 142, 230));

  private final Icon myDefaultIcon;
  private final Color myPopupBackground;

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
