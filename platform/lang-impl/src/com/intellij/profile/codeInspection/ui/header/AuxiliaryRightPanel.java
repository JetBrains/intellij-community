/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
  private final SaveInputComponent mySaveInputComponent;
  private final CardLayout myLayout;

  public AuxiliaryRightPanel(final DescriptionSaveListener descriptionListener) {
    myDescriptionLabel = new DescriptionLabel();

    myErrorLabel = new JLabel();
    myErrorLabel.setBackground(UIUtil.isUnderDarcula() ? JBColor.PINK.darker() :JBColor.PINK);
    myErrorLabel.setForeground(JBColor.BLACK);
    myErrorLabel.setOpaque(true);
    myErrorLabel.setBorder(BorderFactory.createEmptyBorder(0, 2, 0, 0));

    mySaveInputComponent = new SaveInputComponent(new SaveInputComponentValidator() {
      @Override
      public void doSave(@NotNull String text) {
        descriptionListener.saveDescription(text.trim());
      }

      @Override
      public boolean checkValid(@NotNull String text) {
        return true;
      }
    });

    myLayout = new CardLayout();
    setLayout(myLayout);

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

    add(myDescriptionLabel, SHOW_DESCRIPTION_CARD);
    add(myErrorLabel, ERROR_CARD);
    add(mySaveInputComponent, EDIT_DESCRIPTION_CARD);

    showDescription(null);
  }

  public void showDescription(@Nullable String newDescription) {
    if (newDescription == null) {
      newDescription = "";
    }
    myDescriptionLabel.setAllText(newDescription);
    myLayout.show(this, SHOW_DESCRIPTION_CARD);
  }

  public void editDescription(@Nullable String startValue) {
    if (startValue == null) {
      startValue = "";
    }
    mySaveInputComponent.setText(startValue);
    myLayout.show(this, EDIT_DESCRIPTION_CARD);
    mySaveInputComponent.requestFocusToTextField();
  }

  public void showError(final @NotNull String errorText) {
    myErrorLabel.setText(errorText);
    myLayout.show(this, ERROR_CARD);
  }

  public interface DescriptionSaveListener {
    void saveDescription(@NotNull String description);
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