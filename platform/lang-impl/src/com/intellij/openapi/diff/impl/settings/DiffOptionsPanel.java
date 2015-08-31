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

package com.intellij.openapi.diff.impl.settings;

import com.intellij.application.options.colors.*;
import com.intellij.openapi.diff.impl.util.TextDiffType;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.*;
import com.intellij.util.EventDispatcher;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.List;
import java.util.Set;

public class DiffOptionsPanel implements OptionsPanel {
  @NotNull private final ColorAndFontOptions myOptions;
  private final EventDispatcher<ColorAndFontSettingsListener> myDispatcher = EventDispatcher.create(ColorAndFontSettingsListener.class);

  private ColorPanel myBackgroundColorPanel;
  private ColorPanel myStripeMarkColorPanel;
  private JList myOptionsList;
  private JPanel myWholePanel;

  private final CollectionListModel<MyDescription> myOptionsModel = new CollectionListModel<MyDescription>();

  public DiffOptionsPanel(@NotNull ColorAndFontOptions options) {
    myOptions = options;

    //noinspection unchecked
    myOptionsList.setCellRenderer(new MyCellRenderer());
    //noinspection unchecked
    myOptionsList.setModel(myOptionsModel);

    ListSelectionModel selectionModel = myOptionsList.getSelectionModel();
    selectionModel.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    selectionModel.addListSelectionListener(new ListSelectionListener() {
      @Override
      public void valueChanged(ListSelectionEvent e) {
        MyDescription description = getSelectedDescription();
        if (description == null) {
          myBackgroundColorPanel.setEnabled(false);
          myStripeMarkColorPanel.setEnabled(false);
        }
        else {
          myBackgroundColorPanel.setEnabled(true);
          myStripeMarkColorPanel.setEnabled(true);
          myBackgroundColorPanel.setSelectedColor(description.getBackgroundColor());
          myStripeMarkColorPanel.setSelectedColor(description.getStripeMarkColor());
        }

        myDispatcher.getMulticaster().selectedOptionChanged(description);
      }
    });

    myBackgroundColorPanel.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        MyDescription selectedDescription = getSelectedDescription();
        if (selectedDescription == null) return;
        if (!checkModifiableScheme()) {
          myBackgroundColorPanel.setSelectedColor(selectedDescription.getBackgroundColor());
        }
        else {
          selectedDescription.setBackgroundColor(myBackgroundColorPanel.getSelectedColor());
          myDispatcher.getMulticaster().settingsChanged();
        }
      }
    });
    myStripeMarkColorPanel.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        MyDescription selectedDescription = getSelectedDescription();
        if (selectedDescription == null) return;
        if (!checkModifiableScheme()) {
          myStripeMarkColorPanel.setSelectedColor(selectedDescription.getStripeMarkColor());
        }
        else {
          selectedDescription.setStripeMarkColor(myStripeMarkColorPanel.getSelectedColor());
          myDispatcher.getMulticaster().settingsChanged();
        }
      }
    });
  }

  @Override
  public void addListener(ColorAndFontSettingsListener listener) {
    myDispatcher.addListener(listener);
  }

  @Override
  public JPanel getPanel() {
    return myWholePanel;
  }

  @Override
  public void updateOptionsList() {
    myOptionsModel.removeAll();
    for (int i = 0; i < myOptions.getCurrentDescriptions().length; i++) {
      EditorSchemeAttributeDescriptor description = myOptions.getCurrentDescriptions()[i];
      if (ColorAndFontOptions.DIFF_GROUP.equals(description.getGroup()) && description instanceof MyDescription) {
        myOptionsModel.add((MyDescription)description);
      }
    }
    ScrollingUtil.ensureSelectionExists(myOptionsList);
  }

  @Override
  public Runnable showOption(String option) {
    for (int i = 0; i < myOptionsModel.getSize(); i++) {
      MyDescription description = myOptionsModel.getElementAt(i);
      if (StringUtil.containsIgnoreCase(description.getDisplayName(), option)) {
        final int index = i;
        return new Runnable() {
          @Override
          public void run() {
            ScrollingUtil.selectItem(myOptionsList, index);
          }
        };
      }
    }
    return null;
  }

  @Override
  public Set<String> processListOptions() {
    Set<String> result = ContainerUtil.newHashSet();
    for (int i = 0; i < myOptions.getCurrentDescriptions().length; i++) {
      EditorSchemeAttributeDescriptor description = myOptions.getCurrentDescriptions()[i];
      if (ColorAndFontOptions.DIFF_GROUP.equals(description.getGroup()) && description instanceof MyDescription) {
        result.add(description.getType());
      }
    }

    return result;
  }

  @Override
  public void applyChangesToScheme() {
    MyDescription description = getSelectedDescription();
    if (description != null) {
      description.apply(myOptions.getSelectedScheme());
    }
  }

  @Override
  public void selectOption(String typeToSelect) {
    for (int i = 0; i < myOptionsModel.getItems().size(); i++) {
      MyDescription description = myOptionsModel.getElementAt(i);
      if (typeToSelect.equals(description.getDisplayName())) {
        ScrollingUtil.selectItem(myOptionsList, i);
        return;
      }
    }
  }

  private boolean checkModifiableScheme() {
    boolean isReadOnly = myOptions.currentSchemeIsReadOnly();
    if (isReadOnly) {
      FontOptions.showReadOnlyMessage(myWholePanel, myOptions.currentSchemeIsShared());
    }
    return !isReadOnly;
  }

  @Nullable
  private MyDescription getSelectedDescription() {
    return (MyDescription)myOptionsList.getSelectedValue();
  }

  public static void addSchemeDescriptions(@NotNull List<EditorSchemeAttributeDescriptor> descriptions,
                                           @NotNull EditorColorsScheme scheme) {
    for (TextDiffType diffType : TextDiffType.MERGE_TYPES) {
      descriptions.add(new MyDescription(diffType, scheme));
    }
  }

  private static class MyCellRenderer extends ColoredListCellRenderer {
    @Override
    protected void customizeCellRenderer(JList list, Object value, int index, boolean selected, boolean hasFocus) {
      MyDescription description = (MyDescription)value;
      append(description.getDisplayName(), SimpleTextAttributes.REGULAR_ATTRIBUTES);
    }
  }

  private static class MyDescription implements EditorSchemeAttributeDescriptor {
    private final EditorColorsScheme myScheme;
    private final TextDiffType myDiffType;

    private final Color myOriginalBackground;
    private final Color myOriginalStripe;
    private Color myBackgroundColor;
    private Color myStripeColor;

    public MyDescription(@NotNull TextDiffType diffType, @NotNull EditorColorsScheme scheme) {
      myScheme = scheme;
      myDiffType = diffType;
      TextAttributes textAttributes = getAttributes();
      myBackgroundColor = textAttributes.getBackgroundColor();
      myStripeColor = textAttributes.getErrorStripeColor();
      myOriginalBackground = myBackgroundColor;
      myOriginalStripe = myStripeColor;
    }

    @NotNull
    public String getDisplayName() {
      return myDiffType.getDisplayName();
    }

    @NotNull
    public TextAttributes getAttributes() {
      return myScheme.getAttributes(myDiffType.getAttributesKey());
    }

    @Override
    public void apply(EditorColorsScheme scheme) {
      TextAttributesKey key = myDiffType.getAttributesKey();
      TextAttributes attrs = new TextAttributes();
      attrs.setBackgroundColor(myBackgroundColor);
      attrs.setErrorStripeColor(myStripeColor);
      scheme.setAttributes(key, attrs);
    }

    @Override
    public String getGroup() {
      return ColorAndFontOptions.DIFF_GROUP;
    }

    @Override
    public EditorColorsScheme getScheme() {
      return myScheme;
    }

    @Override
    public String getType() {
      return myDiffType.getAttributesKey().getExternalName();
    }

    @Override
    public boolean isModified() {
      TextAttributes textAttributes = getAttributes();
      return !Comparing.equal(myOriginalBackground, textAttributes.getBackgroundColor()) ||
             !Comparing.equal(myOriginalStripe, textAttributes.getErrorStripeColor());
    }

    public void setBackgroundColor(Color selectedColor) {
      myBackgroundColor = selectedColor;
    }

    public Color getBackgroundColor() {
      return myBackgroundColor;
    }

    public void setStripeMarkColor(Color selectedColor) {
      myStripeColor = selectedColor;
    }

    public Color getStripeMarkColor() {
      return myStripeColor;
    }
  }
}
