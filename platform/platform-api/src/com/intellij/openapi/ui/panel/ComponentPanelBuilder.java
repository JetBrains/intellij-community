// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.ui.panel;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.ui.ComponentValidator;
import com.intellij.openapi.ui.ComponentWithBrowseButton;
import com.intellij.openapi.ui.LabeledComponent;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.text.HtmlBuilder;
import com.intellij.openapi.util.text.HtmlChunk;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.*;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.panels.NonOpaquePanel;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.SystemProperties;
import com.intellij.util.ui.*;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.event.HyperlinkListener;
import javax.swing.plaf.FontUIResource;
import javax.swing.plaf.LabelUI;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.function.Supplier;

public class ComponentPanelBuilder implements GridBagPanelBuilder {

  public static final int MAX_COMMENT_WIDTH = 70;

  private final JComponent myComponent;

  private @NlsContexts.Label String myLabelText;
  private boolean myLabelOnTop;
  private @NlsContexts.DetailedDescription String myCommentText;
  private Icon myCommentIcon;
  private HyperlinkListener myHyperlinkListener = BrowserHyperlinkListener.INSTANCE;
  private boolean myCommentBelow = true;
  private boolean myCommentAllowAutoWrapping = true;
  private @NlsContexts.Tooltip String myHTDescription;
  private @NlsContexts.LinkLabel String myHTLinkText;
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
  public ComponentPanelBuilder withLabel(@NotNull @NlsContexts.Label String labelText) {
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
  public ComponentPanelBuilder withComment(@NotNull @NlsContexts.DetailedDescription String comment) {
    return withComment(comment, true);
  }

  public ComponentPanelBuilder withComment(@NotNull @NlsContexts.DetailedDescription String comment, boolean allowAutoWrapping) {
    myCommentText = comment;
    myCommentAllowAutoWrapping = allowAutoWrapping;
    valid = StringUtil.isEmpty(comment) || StringUtil.isEmpty(myHTDescription);
    return this;
  }

  public ComponentPanelBuilder withCommentIcon(@NotNull Icon icon) {
    myCommentIcon = icon;
    return this;
  }

  /**
   * Sets the hyperlink listener to be executed on clicking any reference in comment
   * text. Reference is represented by the HTML <code>&lt;a href&gt;</code> tags.
   * By default <code>BrowserHyperlinkListener.INSTANCE</code> is used which opens
   * a web browser.
   * @param listener new <code>HyperlinkListener</code>
   * @return <code>this</code>
   */
  public ComponentPanelBuilder withCommentHyperlinkListener(@NotNull HyperlinkListener listener) {
    myHyperlinkListener = listener;
    return this;
  }

  /**
   * Adds a custom (one line) component to the top right location of the main component.
   * Useful for adding control like {@link com.intellij.ui.components.labels.LinkLabel} or
   * {@link com.intellij.ui.components.DropDownLink}
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
  public ComponentPanelBuilder withTooltip(@NotNull @NlsContexts.Tooltip String description) {
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
  public ComponentPanelBuilder withTooltipLink(@NotNull @NlsContexts.LinkLabel String linkText, @NotNull Runnable action) {
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
    addToPanel(panel, gc, false);
    return panel;
  }

  @Override
  public boolean constrainsValid() {
    return valid;
  }

  @Override
  public int gridWidth() {
    return myCommentBelow ? 2 : myResizeX ? 4 : 3;
  }

  @Override
  public void addToPanel(JPanel panel, GridBagConstraints gc, boolean splitColumns) {
    if (constrainsValid()) {
      new ComponentPanelImpl(splitColumns).addToPanel(panel, gc);
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
        bottom = isWin10 ? 10 : isMacDefault ? 8 : 9;
        if (component instanceof JCheckBox) {
          left = UIUtil.getCheckBoxTextHorizontalOffset((JCheckBox)component); // the value returned from this method is already scaled

          //noinspection UseDPIAwareInsets
          return new Insets(0, left, JBUIScale.scale(bottom), 0);
        }
        else {
          left = isMacDefault ? 26 : isWin10 ? 17 : 23;
        }
      }
      else if (component instanceof JTextField || component instanceof EditorTextComponent ||
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
      else if (component instanceof JTextField || component instanceof EditorTextComponent ||
               component instanceof JComboBox || component instanceof ComponentWithBrowseButton) {
        left = isMacDefault ? 13 : 14;
      }
      return JBUI.insetsLeft(left);
    }
  }

  public static @NotNull JLabel createCommentComponent(@Nullable @NlsContexts.DetailedDescription String commentText,
                                                       boolean isCommentBelow) {
    return createCommentComponent(commentText, isCommentBelow, MAX_COMMENT_WIDTH, true);
  }

  public static @NotNull JLabel  createCommentComponent(@Nullable @NlsContexts.DetailedDescription String commentText,
                                                       boolean isCommentBelow,
                                                       int maxLineLength) {
    return createCommentComponent(commentText, isCommentBelow, maxLineLength, true);
  }

  public static @NotNull JLabel createCommentComponent(@Nullable @NlsContexts.DetailedDescription String commentText,
                                                       boolean isCommentBelow,
                                                       int maxLineLength,
                                                       boolean allowAutoWrapping) {
    return createCommentComponent(() -> new CommentLabel(""), commentText, isCommentBelow, maxLineLength, allowAutoWrapping);
  }

  private static JLabel createCommentComponent(@NotNull Supplier<? extends JBLabel> labelSupplier,
                                               @Nullable @NlsContexts.DetailedDescription String commentText,
                                               boolean isCommentBelow,
                                               int maxLineLength,
                                               boolean allowAutoWrapping) {
    // todo why our JBLabel cannot render html if render panel without frame (test only)
    boolean isCopyable = SystemProperties.getBooleanProperty("idea.ui.comment.copyable", true);
    JLabel component = labelSupplier.get().setCopyable(isCopyable).setAllowAutoWrapping(allowAutoWrapping);

    component.setVerticalTextPosition(SwingConstants.TOP);
    component.setFocusable(false);

    if (isCopyable) {
      setCommentText(component, commentText, isCommentBelow, maxLineLength);
    }
    else {
      component.setText(commentText);
    }
    return component;
  }

  public static JLabel createNonWrappingCommentComponent(@NotNull @NlsContexts.DetailedDescription String commentText) {
    return new CommentLabel(commentText);
  }

  public static Font getCommentFont(Font font) {
    return new FontUIResource(RelativeFont.NORMAL.fromResource("ContextHelp.fontSizeOffset", -2).derive(font));
  }

  private static void setCommentText(@NotNull JLabel component,
                                     @Nullable @NlsContexts.DetailedDescription String commentText,
                                     boolean isCommentBelow,
                                     int maxLineLength) {
    if (commentText != null) {
      @NonNls String css = "<head><style type=\"text/css\">\n" +
                           "a, a:link {color:#" + ColorUtil.toHex(JBUI.CurrentTheme.Link.Foreground.ENABLED) + ";}\n" +
                           "a:visited {color:#" + ColorUtil.toHex(JBUI.CurrentTheme.Link.Foreground.VISITED) + ";}\n" +
                           "a:hover {color:#" + ColorUtil.toHex(JBUI.CurrentTheme.Link.Foreground.HOVERED) + ";}\n" +
                           "a:active {color:#" + ColorUtil.toHex(JBUI.CurrentTheme.Link.Foreground.PRESSED) + ";}\n" +
                           //"body {background-color:#" + ColorUtil.toHex(JBColor.YELLOW) + ";}\n" + // Left for visual debugging
                           "</style>\n</head>";
      HtmlChunk text = HtmlChunk.raw(commentText);
      if (maxLineLength > 0 && commentText.length() > maxLineLength && isCommentBelow) {
        int width = component.getFontMetrics(component.getFont()).stringWidth(commentText.substring(0, maxLineLength));
        text = text.wrapWith(HtmlChunk.div().attr("width", width));
      }
      else {
        text = text.wrapWith(HtmlChunk.div());
      }
      component.setText(new HtmlBuilder()
        .append(HtmlChunk.raw(css))
        .append(text.wrapWith("body"))
        .wrapWith("html")
        .toString());
    }
  }

  private static class CommentLabel extends JBLabel {
    private CommentLabel(@NotNull @NlsContexts.Label String text) {
      super(text);
      setForeground(JBUI.CurrentTheme.ContextHelp.FOREGROUND);
    }

    @Override
    public void setUI(LabelUI ui) {
      super.setUI(ui);
      setFont(getCommentFont(getFont()));
    }
  }

  private final class ComponentPanelImpl extends ComponentPanel {
    private final JLabel label;
    private final JLabel comment;
    private final boolean splitColumns;

    private ComponentPanelImpl(boolean splitColumns) {
      this.splitColumns = splitColumns;

      if ((StringUtil.isNotEmpty(myLabelText))) {
        label = new JLabel();
        LabeledComponent.TextWithMnemonic.fromTextWithMnemonic(myLabelText).setToLabel(label);
        label.setLabelFor(myComponent);
      } else {
        label = new JLabel("");
      }

      comment = createCommentComponent(() -> new CommentLabel("") {
        @Override
        @NotNull
        protected HyperlinkListener createHyperlinkListener() {
          return myHyperlinkListener;
        }
      }, myCommentText, myCommentBelow, MAX_COMMENT_WIDTH, myCommentAllowAutoWrapping);

      if (myCommentIcon != null) {
        comment.setIcon(myCommentIcon);
      }

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
      ComponentPanelBuilder.setCommentText(comment, commentText, myCommentBelow, MAX_COMMENT_WIDTH);
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
      gc.insets = JBInsets.emptyInsets();
      gc.fill = myResizeY ? GridBagConstraints.BOTH : myResizeX ? GridBagConstraints.HORIZONTAL: GridBagConstraints.NONE;
      gc.weighty = myResizeY ? 1.0 : 0.0;

      if (splitColumns) {
        panel.add(myComponent, gc);
      }

      if (StringUtil.isNotEmpty(myHTDescription) || !myCommentBelow) {
        JPanel componentPanel = new JPanel();
        componentPanel.setLayout(new BoxLayout(componentPanel, BoxLayout.X_AXIS));

        if (!splitColumns) {
          componentPanel.add(myComponent);
        }

        if (StringUtil.isNotEmpty(myHTDescription)) {
          ContextHelpLabel lbl = StringUtil.isNotEmpty(myHTLinkText) && myHTAction != null ?
                                 ContextHelpLabel.createWithLink(null, myHTDescription, myHTLinkText, myHTAction) :
                                 ContextHelpLabel.create(myHTDescription);
          JBUI.Borders.emptyLeft(7).wrap(lbl);
          componentPanel.add(lbl);

          ComponentValidator.getInstance(myComponent).ifPresent(v -> {
              JLabel iconLabel = new JLabel();
              JBUI.Borders.emptyLeft(7).wrap(iconLabel);
              iconLabel.setVisible(false);
              componentPanel.add(iconLabel);

              iconLabel.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent e) {
                  myComponent.dispatchEvent(convertMouseEvent(e));
                  e.consume();
                }

                @Override
                public void mouseExited(MouseEvent e) {
                  myComponent.dispatchEvent(convertMouseEvent(e));
                  e.consume();
                }
              });

              myComponent.addPropertyChangeListener("JComponent.outline", evt -> {
                if (evt.getNewValue() == null) {
                  iconLabel.setVisible(false);
                  lbl.setVisible(true);
                } else if ("warning".equals(evt.getNewValue())) {
                  iconLabel.setIcon(AllIcons.General.BalloonWarning);
                  iconLabel.setVisible(true);
                  lbl.setVisible(false);
                } else if ("error".equals(evt.getNewValue())) {
                  iconLabel.setIcon(AllIcons.General.BalloonError);
                  iconLabel.setVisible(true);
                  lbl.setVisible(false);
                }
                componentPanel.revalidate();
                componentPanel.repaint();
              });
            });

          panel.add(componentPanel, gc);
        }
        else if (!myCommentBelow) {
          if (splitColumns) {
            gc.gridx ++;
            gc.weightx = 0;
            gc.fill = GridBagConstraints.NONE;
            gc.weighty = 0.0;
            panel.add(comment, gc);
          }
          else {
            comment.setBorder(getCommentBorder());
            componentPanel.add(comment);
            panel.add(componentPanel, gc);
          }
        }
      }
      else if (!splitColumns) {
        panel.add(myComponent, gc);
      }

      if (!splitColumns && !myResizeX) {
        gc.gridx ++;
        gc.weightx = 1.0;
        gc.fill = GridBagConstraints.REMAINDER;
        panel.add(new JPanel(), gc);
      }

      gc.fill = GridBagConstraints.HORIZONTAL;
      gc.weighty = 0.0;
      if (myCommentBelow) {
        gc.gridx = 1;
        gc.gridy++;
        gc.weightx = 0.0;
        gc.anchor = GridBagConstraints.NORTHWEST;
        gc.insets = JBInsets.emptyInsets();

        comment.setBorder(getCommentBorder());
        panel.add(comment, gc);

        if (!myResizeX) {
          gc.gridx ++;
          gc.weightx = 1.0;
          gc.fill = GridBagConstraints.REMAINDER;
          panel.add(new JPanel(), gc);
        }
      }

      myComponent.putClientProperty(DECORATED_PANEL_PROPERTY, this);
      gc.gridy++;
    }

    private MouseEvent convertMouseEvent(MouseEvent e) {
      Point p = e.getPoint();
      SwingUtilities.convertPoint(e.getComponent(), p, myComponent);
      return new MouseEvent(myComponent, e.getID(), e.getWhen(), e.getModifiers(),
                            p.x, p.y, e.getXOnScreen(), e.getYOnScreen(),
                            e.getClickCount(), e.isPopupTrigger(), e.getButton());
    }
  }
}
