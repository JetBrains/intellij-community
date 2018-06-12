// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.ui.panel;

import com.intellij.openapi.ui.ComponentWithBrowseButton;
import com.intellij.openapi.ui.LabeledComponent;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.ColorUtil;
import com.intellij.ui.ContextHelpLabel;
import com.intellij.ui.JBColor;
import com.intellij.ui.TextComponent;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.panels.NonOpaquePanel;
import com.intellij.util.ui.JBEmptyBorder;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;

public class ComponentPanelBuilder implements GridBagPanelBuilder {

  private final JComponent myComponent;

  private String myLabelText;
  private boolean myLabelOnTop;
  private String myCommentText;
  private boolean myCommentBelow = true;
  private String myHTDescription;
  private String myHTLinkText;
  private Runnable myHTAction;
  private JComponent myTopRightComponent;
  private UI.Anchor myAnchor = UI.Anchor.Center;
  private boolean myResizeY;
  private boolean myResizeX = true;
  private boolean valid = true;

  public ComponentPanelBuilder(JComponent component) {
    myComponent = component;
  }

  /**
   * Allow resizing component vertically when the panel is resized. Useful when {@link JTextArea} or
   * {@link JTextPane} need to be resized along with the dialog window.
   *
   * @param resize <code>true</code> to enable resize, <code>false</code> to disable.
   *              Default is <code>false</code>
   * @return <code>this</code>
   */
  public ComponentPanelBuilder resizeY(boolean resize) {
    myResizeY = resize;
    return this;
  }

  /**
   * Allow resizing component horizontally when the panel is resized. Useful for
   * limiting {@link JComboBox} and other resizable component to preferred width.
   *
   * @param resize <code>true</code> to enable resize, <code>false</code> to disable.
   *              Default is <code>true</code>
   * @return <code>this</code>
   */
  public ComponentPanelBuilder resizeX(boolean resize) {
    myResizeX = resize;
    return this;
  }

  /**
   * @param labelText text for the label.
   * @return <code>this</code>
   */
  public ComponentPanelBuilder withLabel(@NotNull String labelText) {
    myLabelText = labelText;
    return this;
  }

  /**
   * Move label on top of the owner component. Default position is on the left of the owner component.
   *
   * @return <code>this</code>
   */
  public ComponentPanelBuilder moveLabelOnTop() {
    myLabelOnTop = true;
    valid = StringUtil.isEmpty(myCommentText) || StringUtil.isEmpty(myHTDescription);
    return this;
  }

  public ComponentPanelBuilder anchorLabelOn(UI.Anchor anchor) {
    myAnchor = anchor;
    return this;
  }

  /**
   * @param comment help context styled text written below the owner component.
   * @return <code>this</code>
   */
  public ComponentPanelBuilder withComment(@NotNull String comment) {
    myCommentText = comment;
    valid = StringUtil.isEmpty(comment) || StringUtil.isEmpty(myHTDescription);
    return this;
  }

  /**
   * Adds a custom (one line) component to the top right location of the main component.
   * Useful for adding control like {@link com.intellij.ui.components.labels.LinkLabel} or
   * {@link com.intellij.ui.components.labels.DropDownLink}
   *
   * @param topRightComponent the component to be added
   * @return <code>this</code>
   */
  public ComponentPanelBuilder withTopRightComponent(@NotNull JComponent topRightComponent) {
    myTopRightComponent = topRightComponent;
    valid = StringUtil.isEmpty(myCommentText) || StringUtil.isEmpty(myHTDescription);
    return this;
  }

  /**
   * Move comment to the right of the owner component. Default position is below the owner component.
   *
   * @return <code>this</code>
   */
  public ComponentPanelBuilder moveCommentRight() {
    myCommentBelow = false;
    return this;
  }

  /**
   * Enables the help tooltip icon on the right of the owner component and sets the description text for the tooltip.
   *
   * @param description help tooltip description.
   * @return <code>this</code>
   */
  public ComponentPanelBuilder withTooltip(@NotNull String description) {
    myHTDescription = description;
    valid = StringUtil.isEmpty(myCommentText) || StringUtil.isEmpty(description);
    return this;
  }

  /**
   * Sets optional help tooltip link and link action.
   *
   * @param linkText help tooltip link text.
   *
   * @param action help tooltip link action.
   *
   * @return <code>this</code>
   */
  public ComponentPanelBuilder withTooltipLink(@NotNull String linkText, @NotNull Runnable action) {
    myHTLinkText = linkText;
    myHTAction = action;
    return this;
  }

  @Override
  @NotNull
  public JPanel createPanel() {
    JPanel panel = new NonOpaquePanel(new GridBagLayout());
    GridBagConstraints gc = new GridBagConstraints(0, 0, 1, 1, 0, 0, GridBagConstraints.LINE_START, GridBagConstraints.HORIZONTAL,
                                                   null, 0, 0);
    addToPanel(panel, gc);
    return panel;
  }

  @Override
  public boolean constrainsValid() {
    return valid;
  }

  @Override
  public int gridWidth() {
    return 2;
  }

  @Override
  public void addToPanel(JPanel panel, GridBagConstraints gc) {
    if (constrainsValid()) {
      new ComponentPanelImpl().addToPanel(panel, gc);
    }
  }


  private Border getCommentBorder() {
    if (StringUtil.isNotEmpty(myCommentText)) {
      return new JBEmptyBorder(computeCommentInsets(myComponent, myCommentBelow));
    } else {
      return JBUI.Borders.empty();
    }
  }

  @NotNull
  public static Insets computeCommentInsets(@NotNull JComponent component, boolean commentBelow) {
    boolean isMacDefault = UIUtil.isUnderDefaultMacTheme();
    boolean isWin10 = UIUtil.isUnderWin10LookAndFeel();

    if (commentBelow) {
      int top = 8, left = 2, bottom = 0;

      if (component instanceof JRadioButton || component instanceof JCheckBox) {
        top = 0;
        left = isMacDefault ? 26 : isWin10 ? 17 : 23;
        bottom = isWin10 ? 10 : isMacDefault ? 8 : 9;
      }
      else if (component instanceof JTextField || component instanceof TextComponent ||
               component instanceof JComboBox || component instanceof ComponentWithBrowseButton) {
        top = isWin10 ? 3 : 4;
        left = isWin10 ? 2 : isMacDefault ? 5 : 4;
        bottom = isWin10 ? 10 : isMacDefault ? 8 : 9;
      }
      else if (component instanceof JButton) {
        top = isWin10 ? 2 : 4;
        left = isWin10 ? 2 : isMacDefault ? 5 : 4;
        bottom = 0;
      }

      return JBUI.insets(top, left, bottom, 0);
    } else {
      int left = 14;

      if (component instanceof JRadioButton || component instanceof JCheckBox) {
        left = isMacDefault ? 8 : 13;
      }
      else if (component instanceof JTextField || component instanceof TextComponent ||
               component instanceof JComboBox || component instanceof ComponentWithBrowseButton) {
        left = isMacDefault ? 13 : 14;
      }
      return JBUI.insetsLeft(left);
    }
  }

  @NotNull
  public static JLabel createCommentComponent(@Nullable String commentText, boolean isCommentBelow) {
    JLabel component = new JBLabel("").setCopyable(true).setAllowAutoWrapping(true);
    component.setVerticalTextPosition(SwingConstants.TOP);
    component.setFocusable(false);
    component.setForeground(UIUtil.getContextHelpForeground());
    if (SystemInfo.isMac) {
      Font font = component.getFont();
      float size = font.getSize2D();
      Font smallFont = font.deriveFont(size - 2.0f);
      component.setFont(smallFont);
    }

    setCommentText(component, commentText, isCommentBelow);
    return component;
  }

  private static void setCommentText(@NotNull JLabel component, @Nullable String commentText, boolean isCommentBelow) {
    if (commentText != null) {
      String css = "<head><style type=\"text/css\">\n" +
                         "a, a:link {color:#" + ColorUtil.toHex(JBColor.link()) + ";}\n" +
                         "a:visited {color:#" + ColorUtil.toHex(JBColor.linkVisited()) + ";}\n" +
                         "a:hover {color:#" + ColorUtil.toHex(JBColor.linkHover()) + ";}\n" +
                         "a:active {color:#" + ColorUtil.toHex(JBColor.linkPressed()) + ";}\n" +
                         //"body {background-color:#" + ColorUtil.toHex(JBColor.YELLOW) + ";}\n" + // Left for visual debugging
                         "</style>\n</head>";
      if (commentText.length() > 70 && isCommentBelow) {
        int width = component.getFontMetrics(component.getFont()).stringWidth(commentText.substring(0, 70));
        component.setText(String.format("<html>" + css + "<body><div width=%d>%s</div></body></html>", width, commentText));
      }
      else {
        component.setText(String.format("<html>" + css + "<body><div>%s</div></body></html>", commentText));
      }
    }
  }

  private class ComponentPanelImpl extends ComponentPanel {
    private final JLabel label;
    private final JLabel comment;

    private ComponentPanelImpl() {
      if ((StringUtil.isNotEmpty(myLabelText))) {
        label = new JLabel();
        LabeledComponent.TextWithMnemonic.fromTextWithMnemonic(myLabelText).setToLabel(label);
        label.setLabelFor(myComponent);
      } else {
        label = new JLabel("");
      }

      comment = createCommentComponent(myCommentText, myCommentBelow);
      comment.setBorder(getCommentBorder());
    }

    @Override
    public String getCommentText() {
      return myCommentText;
    }

    @Override
    public void setCommentText(String commentText) {
      if (!StringUtil.equals(myCommentText, commentText)) {
        myCommentText = commentText;
        setCommentTextImpl(commentText);
      }
    }

    private void setCommentTextImpl(String commentText) {
      ComponentPanelBuilder.setCommentText(comment, commentText, myCommentBelow);
    }

    private void addToPanel(JPanel panel, GridBagConstraints gc) {
      gc.gridx = 0;
      gc.gridwidth = 1;
      gc.weightx = 0.0;
      gc.anchor = GridBagConstraints.LINE_START;

      if (StringUtil.isNotEmpty(myLabelText)) {
        if (myLabelOnTop || myTopRightComponent != null) {
          gc.insets = JBUI.insetsBottom(4);
          gc.gridx = 1;

          JPanel topPanel = new JPanel();
          topPanel.setLayout(new BoxLayout(topPanel, BoxLayout.X_AXIS));
          if (myLabelOnTop) {
            topPanel.add(label);
          }

          if (myTopRightComponent != null) {
            topPanel.add(new Box.Filler(JBUI.size(UIUtil.DEFAULT_HGAP, 0),
                                        JBUI.size(UIUtil.DEFAULT_HGAP, 0),
                                        JBUI.size(Integer.MAX_VALUE)));
            topPanel.add(myTopRightComponent);
          }

          panel.add(topPanel, gc);
          gc.gridy++;
        }

        if (!myLabelOnTop) {
          gc.gridx = 0;
          switch (myAnchor) {
            case Top:
              gc.anchor = GridBagConstraints.PAGE_START;
              gc.insets = JBUI.insets(4, 0, 0, 8);
              break;
            case Center:
              gc.anchor = GridBagConstraints.LINE_START;
              gc.insets = JBUI.insetsRight(8);
              break;
            case Bottom:
              gc.anchor = GridBagConstraints.PAGE_END;
              gc.insets = JBUI.insets(0, 0, 4, 8);
              break;
          }
          panel.add(label, gc);
        }
      }

      gc.gridx += myLabelOnTop ? 0 : 1;
      gc.weightx = 1.0;
      gc.insets = JBUI.emptyInsets();
      gc.fill = myResizeY ? GridBagConstraints.BOTH : myResizeX ? GridBagConstraints.HORIZONTAL: GridBagConstraints.NONE;
      gc.weighty = myResizeY ? 1.0 : 0.0;

      if (StringUtil.isNotEmpty(myHTDescription) || !myCommentBelow) {
        JPanel componentPanel = new JPanel();
        componentPanel.setLayout(new BoxLayout(componentPanel, BoxLayout.X_AXIS));
        componentPanel.add(myComponent);

        if (StringUtil.isNotEmpty(myHTDescription)) {
          ContextHelpLabel lbl = StringUtil.isNotEmpty(myHTLinkText) && myHTAction != null ?
                                 ContextHelpLabel.createWithLink(null, myHTDescription, myHTLinkText, myHTAction) :
                                 ContextHelpLabel.create(myHTDescription);
          componentPanel.add(Box.createRigidArea(JBUI.size(7, 0)));
          componentPanel.add(lbl);
        }
        else if (!myCommentBelow) {
          comment.setBorder(getCommentBorder());
          componentPanel.add(comment);
        }

        panel.add(componentPanel, gc);
      } else {
        panel.add(myComponent, gc);
      }

      gc.fill = GridBagConstraints.HORIZONTAL;
      gc.weighty = 0.0;
      if (myCommentBelow) {
        gc.gridx = 1;
        gc.gridy++;
        gc.weightx = 0.0;
        gc.anchor = GridBagConstraints.NORTHWEST;
        gc.insets = JBUI.emptyInsets();

        comment.setBorder(getCommentBorder());
        panel.add(comment, gc);
      }

      myComponent.putClientProperty(DECORATED_PANEL_PROPERTY, this);
      gc.gridy++;
    }
  }
}
