/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.application.options.schemes;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.options.Scheme;
import com.intellij.openapi.ui.AbstractPainter;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.openapi.wm.IdeGlassPaneUtil;
import com.intellij.ui.ClickListener;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBTextField;
import com.intellij.util.ui.GraphicsUtil;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.Arrays;

public abstract class AbstractDescriptionAwareSchemesPanel<T extends Scheme> extends AbstractSchemesPanel<T, JPanel> implements Disposable {
  private static final String SHOW_DESCRIPTION_CARD = "show.description.card";
  private static final String EDIT_DESCRIPTION_CARD = "edit.description.card";
  private static final String ERROR_CARD = "error.card";

  private final static KeyStroke ESC_KEY_STROKE = KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0, false);
  private final static KeyStroke ENTER_KEY_STROKE = KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0, false);

  private DescriptionLabel myDescriptionLabel;
  private JLabel myWarningLabel;
  private JBTextField myDescriptionTextField;
  private CardLayout myLayout;
  private AbstractPainter myPainter;

  public AbstractDescriptionAwareSchemesPanel() {
    super(0);
  }

  @NotNull
  @Override
  protected JPanel createInfoComponent() {
    JPanel panel = new JPanel();
    myLayout = new CardLayout();
    panel.setLayout(myLayout);

    myDescriptionTextField = new JBTextField();
    myDescriptionTextField.addFocusListener(new FocusAdapter() {
      @Override
      public void focusLost(FocusEvent e) {
        showDescription();
      }
    });
    myDescriptionTextField.registerKeyboardAction(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        showDescription();
        IdeFocusManager.getGlobalInstance().doWhenFocusSettlesDown(() -> IdeFocusManager.getGlobalInstance().requestFocus(getConfigurableFocusComponent(), true));
      }
    }, ESC_KEY_STROKE, JComponent.WHEN_FOCUSED);
    myDescriptionTextField.registerKeyboardAction(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        applyDescription();
        IdeFocusManager.getGlobalInstance().doWhenFocusSettlesDown(() -> IdeFocusManager.getGlobalInstance().requestFocus(getConfigurableFocusComponent(), true));
      }
    }, ENTER_KEY_STROKE, JComponent.WHEN_FOCUSED);

    myDescriptionLabel = new DescriptionLabel();
    new ClickListener() {
      @Override
      public boolean onClick(@NotNull MouseEvent event, int clickCount) {
        if (clickCount != 2) {
          return false;
        }
        editDescription(myDescriptionLabel.getText());
        return true;
      }
    }.installOn(myDescriptionLabel);

    myWarningLabel = new JLabel();

    panel.add(myDescriptionTextField, EDIT_DESCRIPTION_CARD);
    panel.add(myDescriptionLabel, SHOW_DESCRIPTION_CARD);
    panel.add(myWarningLabel, ERROR_CARD);

    myLayout.show(panel, ERROR_CARD);

    myPainter = new AbstractPainter() {
      @Override
      public boolean needsRepaint() {
        return true;
      }

      @Override
      public void executePaint(Component component, Graphics2D g) {
        if (myDescriptionTextField.isShowing()) {
          GraphicsUtil.setupAntialiasing(g);
          g.setColor(JBColor.GRAY);
          g.drawString(EditableSchemesCombo.EDITING_HINT, 0, -JBUI.scale(5));
        }
      }
    };
    IdeGlassPaneUtil.installPainter(panel, myPainter, this);
    return panel;
  }

  @Override
  public final void showMessage(@Nullable String message, @NotNull MessageType messageType) {
    showMessage(message, messageType, myWarningLabel);
    myLayout.show(myInfoComponent, ERROR_CARD);
  }

  @Override
  public void selectScheme(@Nullable T scheme) {
    super.selectScheme(scheme);
    if (scheme != null) {
      showDescription();
    }
  }

  @Override
  public final void clearMessage() {
    myLayout.show(myInfoComponent, SHOW_DESCRIPTION_CARD);
  }

  public void showDescription() {
    String newDescription = (((DescriptionAwareSchemeActions<T>)getActions()).getDescription(getSelectedScheme()));
    myDescriptionLabel.setAllText(StringUtil.notNullize(newDescription));
    myLayout.show(myInfoComponent, SHOW_DESCRIPTION_CARD);
    myPainter.setNeedsRepaint(true);
  }

  public void editDescription(@Nullable String startValue) {
    myLayout.show(myInfoComponent, EDIT_DESCRIPTION_CARD);
    myDescriptionTextField.setText(StringUtil.notNullize(startValue));
    IdeFocusManager.getGlobalInstance().doWhenFocusSettlesDown(() -> IdeFocusManager.getGlobalInstance().requestFocus(myDescriptionTextField, true));
    myPainter.setNeedsRepaint(true);
  }

  @NotNull
  protected abstract JComponent getConfigurableFocusComponent();

  private void applyDescription() {
    (((DescriptionAwareSchemeActions<T>)getActions())).setDescription(getSelectedScheme(), myDescriptionTextField.getText());
    showDescription();
  }

  private static class DescriptionLabel extends JBLabel {
    private String myAllText;

    public DescriptionLabel() {
      setForeground(JBColor.GRAY);
      setVerticalAlignment(CENTER);
      setHorizontalAlignment(LEFT);
      addComponentListener(new ComponentAdapter() {
        @Override
        public void componentResized(ComponentEvent e) {
          calculateText();
        }
      });
    }

    public void setAllText(String allText) {
      myAllText = allText;
      calculateText();
      revalidate();
      repaint();
    }

    private void calculateText() {
      FontMetrics metrics = getFontMetrics(getFont());
      int width = getSize().width - metrics.stringWidth("...");
      if (width <= 0) {
        setText("...");
        setToolTipText(myAllText);
      }
      char[] text = myAllText.toCharArray();
      int[] charsWidth = new int[text.length];
      for (int i = 0; i < text.length; i++) {
        int w = metrics.charWidth(text[i]);
        for (int j = 0; j <= i; j++) {
          charsWidth[i] += w;
        }
      }
      int idx = Arrays.binarySearch(charsWidth, 0, charsWidth.length, width);
      if (idx < 0) {
        idx = -idx - 1;
      }

      if (idx < myAllText.length()) {
        setText(myAllText.substring(0, idx) + "...");
        setToolTipText(myAllText.substring(idx));
      } else {
        setText(myAllText.substring(0, idx));
      }
    }
  }

  @Override
  public void dispose() {

  }
}