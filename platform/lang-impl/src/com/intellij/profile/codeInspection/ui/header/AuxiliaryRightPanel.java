/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.profile.codeInspection.ui.header;

import com.intellij.openapi.ui.ex.MultiLineLabel;
import com.intellij.ui.ClickListener;
import com.intellij.ui.JBColor;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;

/**
 * @author Dmitry Batkovich
 */
public class AuxiliaryRightPanel extends JPanel {
  private static final String SHOW_DESCRIPTION_CARD = "show.description.card";
  private static final String EDIT_DESCRIPTION_CARD = "edit.description.card";
  private static final String ERROR_CARD = "error.card";

  private final DescriptionLabel myDescriptionLabel;
  private final JLabel myErrorLabel;
  private final ValidatedTextField myValidatedTextField;
  private final CardLayout myLayout;
  private final JPanel myCardPanel;

  public AuxiliaryRightPanel(final DescriptionSaveListener descriptionListener) {
    myCardPanel = new JPanel();
    myDescriptionLabel = new DescriptionLabel();

    myErrorLabel = new JLabel();
    myErrorLabel.setBackground(UIUtil.isUnderDarcula() ? JBColor.PINK.darker() : JBColor.PINK);
    myErrorLabel.setForeground(JBColor.BLACK);
    myErrorLabel.setOpaque(true);

    myValidatedTextField = new ValidatedTextField(new SaveInputComponentValidator() {
      @Override
      public void doSave(@NotNull String text) {
        descriptionListener.saveDescription(text.trim());
      }

      @Override
      public boolean checkValid(@NotNull String text) {
        return true;
      }

      @Override
      public void cancel() {
        descriptionListener.cancel();
      }
    });

    myLayout = new CardLayout();
    myCardPanel.setLayout(myLayout);
    myCardPanel.setBorder(BorderFactory.createEmptyBorder(2, 0, 4, 0));

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

    myCardPanel.add(myDescriptionLabel, SHOW_DESCRIPTION_CARD);
    myCardPanel.add(myErrorLabel, ERROR_CARD);
    myCardPanel.add(myValidatedTextField, EDIT_DESCRIPTION_CARD);

    showDescription(null);

    setLayout(new BorderLayout());
    add(myValidatedTextField.getHintLabel(), BorderLayout.NORTH);
    add(myCardPanel, BorderLayout.CENTER);
  }

  public void showDescription(@Nullable String newDescription) {
    if (newDescription == null) {
      newDescription = "";
    }
    myDescriptionLabel.setAllText(newDescription);
    myLayout.show(myCardPanel, SHOW_DESCRIPTION_CARD);
  }

  public void editDescription(@Nullable String startValue) {
    if (startValue == null) {
      startValue = "";
    }
    myValidatedTextField.setText(startValue);
    myLayout.show(myCardPanel, EDIT_DESCRIPTION_CARD);
    myValidatedTextField.requestFocus();
  }

  public void showError(final @NotNull String errorText) {
    myErrorLabel.setText(errorText);
    myLayout.show(myCardPanel, ERROR_CARD);
  }

  public interface DescriptionSaveListener {
    void saveDescription(@NotNull String description);

    void cancel();
  }

  private static class DescriptionLabel extends MultiLineLabel {
    private String myAllText;

    public DescriptionLabel() {
      UIUtil.applyStyle(UIUtil.ComponentStyle.SMALL, this);
      setVerticalAlignment(CENTER);
      setHorizontalAlignment(LEFT);
    }

    public void setAllText(String allText) {
      myAllText = allText;
      revalidate();
      repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
      int width = getSize().width;
      FontMetrics fontMetrics = getFontMetrics(getFont());
      int charWidth = fontMetrics.charWidth('a');
      int firstLineSize = Math.max(0, width / charWidth);
      if (myAllText.length() <= firstLineSize) {
        setText(myAllText);
        setToolTipText(null);
      } else {
        String firstLine = myAllText.substring(0, firstLineSize);
        String remainPart = myAllText.substring(firstLineSize);
        int lastWhitespace = firstLine.lastIndexOf(' ');
        if (lastWhitespace > -1) {
          remainPart = firstLine.substring(lastWhitespace) + remainPart;
          firstLine = firstLine.substring(0, lastWhitespace);
        }
        String visibleText = firstLine + "\n";

        int secondLineSize = Math.max(0, (width - 3 * fontMetrics.charWidth('.')) / charWidth);
        if (remainPart.length() <= secondLineSize) {
          setText(visibleText + remainPart.trim());
          setToolTipText(null);
        } else {
          setText(visibleText + remainPart.trim().substring(0, secondLineSize) + "...");
          setToolTipText("..." + remainPart.substring(secondLineSize));
        }
      }
      super.paintComponent(g);
    }
  }
}