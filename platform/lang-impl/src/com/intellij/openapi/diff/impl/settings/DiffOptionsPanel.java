/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
import com.intellij.openapi.editor.markup.EffectType;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.ui.LabeledComponent;
import com.intellij.openapi.util.Comparing;
import com.intellij.ui.*;
import com.intellij.util.EventDispatcher;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.HashMap;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class DiffOptionsPanel implements OptionsPanel {
  private final ColorAndFontOptions myOptions;
  private final EventDispatcher<ColorAndFontSettingsListener> myDispatcher = EventDispatcher.create(ColorAndFontSettingsListener.class);
  private LabeledComponent<ColorPanel> myBackgoundColorPanelComponent;
  private JList myOptionsList;
  private JPanel myWholePanel;
  private LabeledComponent<ColorPanel> myStripeMarkColorComponent;

  public DiffOptionsPanel(ColorAndFontOptions options) {

    myOptions = options;

    myOptionsList.setCellRenderer(new OptionsReneder());
    myOptionsList.setModel(myOptionsModel);

    ListSelectionModel selectionModel = myOptionsList.getSelectionModel();
    selectionModel.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    selectionModel.addListSelectionListener(new ListSelectionListener() {
          @Override
          public void valueChanged(ListSelectionEvent e) {
            TextDiffType selection = getSelectedOption();
            ColorPanel background = getBackgroundColorPanel();
            ColorPanel stripeMark = getStripeMarkColorPanel();
            if (selection == null) {
              background.setEnabled(false);
              stripeMark.setEnabled(false);
            } else {
              background.setEnabled(true);
              stripeMark.setEnabled(true);
              MyColorAndFontDescription description = getSelectedDescription();
              if (description != null) {
                background.setSelectedColor(description.getBackgroundColor());
                stripeMark.setSelectedColor(description.getStripeMarkColor());
              }
            }

            myDispatcher.getMulticaster().selectedOptionChanged(selection);
          }
        });

    getBackgroundColorPanel().addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        MyColorAndFontDescription selectedDescription = getSelectedDescription();
        ColorPanel colorPanel = getBackgroundColorPanel();
        if (!checkModifiableScheme()) {
          colorPanel.setSelectedColor(selectedDescription.getBackgroundColor());
          return;
        }
        selectedDescription.setBackgroundColor(colorPanel.getSelectedColor());
        myDispatcher.getMulticaster().settingsChanged();
      }
    });
    getStripeMarkColorPanel().addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        MyColorAndFontDescription selectedDescription = getSelectedDescription();
        ColorPanel colorPanel = getStripeMarkColorPanel();
        if (!checkModifiableScheme()) {
          colorPanel.setSelectedColor(selectedDescription.getStripeMarkColor());
          return;
        }
        selectedDescription.setStripeMarkColor(colorPanel.getSelectedColor());
        myDispatcher.getMulticaster().settingsChanged();
      }
    });
  }




  @Override
  public void addListener(final ColorAndFontSettingsListener listener) {
    myDispatcher.addListener(listener);

  }

  @Override
  public JPanel getPanel() {
    return myWholePanel;
  }

  @Override
  public void updateOptionsList() {
    myOptionsModel.clear();
    myDescriptions.clear();
    Map<TextAttributesKey, TextDiffType> typesByKey = ContainerUtil.newMapFromValues(TextDiffType.MERGE_TYPES.iterator(),
                                                                                     TextDiffType.ATTRIBUTES_KEY);
    for (int i = 0; i < myOptions.getCurrentDescriptions().length; i++) {
      EditorSchemeAttributeDescriptor description = myOptions.getCurrentDescriptions()[i];
      TextAttributesKey type = TextAttributesKey.find(description.getType());
      if (description.getGroup() == ColorAndFontOptions.DIFF_GROUP &&
          typesByKey.keySet().contains(type)) {
        myOptionsModel.add(typesByKey.get(type));
        myDescriptions.put(type.getExternalName(), (MyColorAndFontDescription)description);
      }
    }
    ListScrollingUtil.ensureSelectionExists(myOptionsList);
  }

  @Override
  public Runnable showOption(final String option) {


    AbstractListModel model = (AbstractListModel)myOptionsList.getModel();

    for (int i = 0; i < model.getSize(); i++) {
      Object o = model.getElementAt(i);
      if (o instanceof TextDiffType) {
        String type = ((TextDiffType)o).getDisplayName();
        if (type.toLowerCase().contains(option.toLowerCase())) {
          final int i1 = i;
          return new Runnable() {
            @Override
            public void run() {
              ListScrollingUtil.selectItem(myOptionsList, i1);
            }
          };

        }
      }
    }

    return null;


  }

  @Override
  public Set<String> processListOptions() {
    Set<String> result = ContainerUtil.newHashSet();
    Map<TextAttributesKey, TextDiffType> typesByKey = ContainerUtil.newMapFromValues(TextDiffType.MERGE_TYPES.iterator(),
                                                                                     TextDiffType.ATTRIBUTES_KEY);
    for (int i = 0; i < myOptions.getCurrentDescriptions().length; i++) {
      EditorSchemeAttributeDescriptor description = myOptions.getCurrentDescriptions()[i];
      TextAttributesKey type = TextAttributesKey.find(description.getType());
      if (description.getGroup() == ColorAndFontOptions.DIFF_GROUP &&
          typesByKey.keySet().contains(type)) {
        result.add(type.getExternalName());
      }
    }

    return result;
  }


  @Override
  public void applyChangesToScheme() {
    MyColorAndFontDescription description = getSelectedDescription();
    if (description != null) {
      description.apply(myOptions.getSelectedScheme());
    }
  }

  @Override
  public void selectOption(final String typeToSelect) {

    for (int i = 0; i < myOptionsModel.getItems().size(); i++) {
      Object o = myOptionsModel.get(i);
      if (o instanceof TextDiffType) {
        if (typeToSelect.equals(((TextDiffType)o).getDisplayName())) {
          ListScrollingUtil.selectItem(myOptionsList, i);
          return;
        }
      }
    }


  }


  private static final Comparator<TextDiffType> TEXT_DIFF_TYPE_COMPARATOR = new Comparator<TextDiffType>() {
      @Override
      public int compare(TextDiffType textDiffType, TextDiffType textDiffType1) {
        return textDiffType.getDisplayName().compareToIgnoreCase(textDiffType1.getDisplayName());
      }
    };
  private final SortedListModel<TextDiffType> myOptionsModel = new SortedListModel<TextDiffType>(TEXT_DIFF_TYPE_COMPARATOR);
  private final HashMap<String, MyColorAndFontDescription> myDescriptions = new HashMap<String,MyColorAndFontDescription>();
  private TextDiffType getSelectedOption() {
    return (TextDiffType)myOptionsList.getSelectedValue();
  }

  private boolean checkModifiableScheme() {
    boolean isReadOnly = myOptions.currentSchemeIsReadOnly();
    if (isReadOnly) {
      FontOptions.showReadOnlyMessage(myWholePanel, myOptions.currentSchemeIsShared());
    }
    return !isReadOnly;
  }

  private MyColorAndFontDescription getSelectedDescription() {
    TextDiffType selection = getSelectedOption();
    if (selection == null) return null;
    return myDescriptions.get(selection.getAttributesKey().getExternalName());
  }

  private ColorPanel getBackgroundColorPanel() {
    return myBackgoundColorPanelComponent.getComponent();
  }

  private ColorPanel getStripeMarkColorPanel() {
    return myStripeMarkColorComponent.getComponent();
  }

  public static void addSchemeDescriptions(@NotNull List<EditorSchemeAttributeDescriptor> descriptions, @NotNull EditorColorsScheme scheme) {
    for (TextDiffType diffType : TextDiffType.MERGE_TYPES) {
      descriptions.add(new MyColorAndFontDescription(diffType, scheme));
    }
  }

  private static class OptionsReneder extends ColoredListCellRenderer {
    @Override
    protected void customizeCellRenderer(JList list, Object value, int index, boolean selected, boolean hasFocus) {
      TextDiffType diffType = (TextDiffType)value;
      append(diffType.getDisplayName(), SimpleTextAttributes.REGULAR_ATTRIBUTES);
    }
  }


  private static class MyColorAndFontDescription implements EditorSchemeAttributeDescriptor {
    private Color myBackgroundColor;
    private Color myStripebarColor;
    private final Color myOriginalBackground;
    private final Color myOriginalStripebar;
    private final EditorColorsScheme myScheme;
    private final TextDiffType myDiffType;

    public MyColorAndFontDescription(@NotNull TextDiffType diffType, @NotNull EditorColorsScheme scheme) {
      myScheme = scheme;
      myDiffType = diffType;
      TextAttributes attrs = diffType.getTextAttributes(myScheme);
      myBackgroundColor = attrs.getBackgroundColor();
      myStripebarColor = attrs.getErrorStripeColor();
      myOriginalBackground = myBackgroundColor;
      myOriginalStripebar = myStripebarColor;
    }

    @Override
    public void apply(EditorColorsScheme scheme) {
      TextAttributesKey key = myDiffType.getAttributesKey();
      TextAttributes attrs = new TextAttributes(null, myBackgroundColor, null, EffectType.BOXED, Font.PLAIN);
      attrs.setErrorStripeColor(myStripebarColor);
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
      TextAttributes attrs = myDiffType.getTextAttributes(myScheme);
      return !Comparing.equal(myOriginalBackground, attrs.getBackgroundColor()) ||
             !Comparing.equal(myOriginalStripebar, attrs.getErrorStripeColor());
    }

    public void setBackgroundColor(Color selectedColor) {
      myBackgroundColor = selectedColor;
    }

    public Color getBackgroundColor() {
      return myBackgroundColor;
    }

    public void setStripeMarkColor(Color selectedColor) {
      myStripebarColor = selectedColor;
    }

    public Color getStripeMarkColor() {
      return myStripebarColor;
    }
  }
}
