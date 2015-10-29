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
import com.intellij.diff.util.TextDiffTypeFactory;
import com.intellij.diff.util.TextDiffTypeFactory.TextDiffTypeImpl;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.*;
import com.intellij.ui.components.JBCheckBox;
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
  private ColorPanel myIgnoredColorPanel;
  private ColorPanel myStripeMarkColorPanel;
  private JBCheckBox myInheritIgnoredCheckBox;

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
          myIgnoredColorPanel.setEnabled(false);
          myStripeMarkColorPanel.setEnabled(false);
          myInheritIgnoredCheckBox.setEnabled(false);
        }
        else {
          myBackgroundColorPanel.setEnabled(true);
          myIgnoredColorPanel.setEnabled(true && !description.isInheritIgnoredColor());
          myStripeMarkColorPanel.setEnabled(true);
          myInheritIgnoredCheckBox.setEnabled(true);

          myBackgroundColorPanel.setSelectedColor(description.getBackgroundColor());
          myIgnoredColorPanel.setSelectedColor(description.getIgnoredColor());
          myStripeMarkColorPanel.setSelectedColor(description.getStripeMarkColor());
          myInheritIgnoredCheckBox.setSelected(description.isInheritIgnoredColor());
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
    myIgnoredColorPanel.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        MyDescription selectedDescription = getSelectedDescription();
        if (selectedDescription == null) return;
        if (!checkModifiableScheme()) {
          myIgnoredColorPanel.setSelectedColor(selectedDescription.getIgnoredColor());
        }
        else {
          selectedDescription.setIgnoredColor(myIgnoredColorPanel.getSelectedColor());
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
    myInheritIgnoredCheckBox.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        MyDescription selectedDescription = getSelectedDescription();
        if (selectedDescription == null) return;
        if (!checkModifiableScheme()) {
          myInheritIgnoredCheckBox.setSelected(selectedDescription.isInheritIgnoredColor());
        }
        else {
          selectedDescription.setInheritIgnoredColor(myInheritIgnoredCheckBox.isSelected());
          myIgnoredColorPanel.setEnabled(!myInheritIgnoredCheckBox.isSelected());
          myIgnoredColorPanel.setSelectedColor(selectedDescription.myIgnoredColor);
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
        result.add(((MyDescription)description).getDisplayName());
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
    for (TextDiffTypeImpl diffType : TextDiffTypeFactory.getInstance().getAllDiffTypes()) {
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
    private final TextDiffTypeImpl myDiffType;

    private final Color myOriginalBackground;
    private final Color myOriginalIgnored;
    private final Color myOriginalStripe;
    private Color myBackgroundColor;
    private Color myIgnoredColor;
    private Color myStripeColor;

    public MyDescription(@NotNull TextDiffTypeImpl diffType, @NotNull EditorColorsScheme scheme) {
      myScheme = scheme;
      myDiffType = diffType;
      TextAttributes textAttributes = getAttributes();
      myBackgroundColor = textAttributes.getBackgroundColor();
      myIgnoredColor = textAttributes.getForegroundColor();
      myStripeColor = textAttributes.getErrorStripeColor();
      myOriginalBackground = myBackgroundColor;
      myOriginalIgnored = myIgnoredColor;
      myOriginalStripe = myStripeColor;
    }

    @NotNull
    public String getDisplayName() {
      return myDiffType.getName();
    }

    @NotNull
    public TextAttributes getAttributes() {
      return myScheme.getAttributes(myDiffType.getKey());
    }

    @Override
    public void apply(EditorColorsScheme scheme) {
      TextAttributesKey key = myDiffType.getKey();
      TextAttributes attrs = new TextAttributes();
      attrs.setBackgroundColor(myBackgroundColor);
      attrs.setForegroundColor(myIgnoredColor);
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
      return myDiffType.getKey().getExternalName();
    }

    @Override
    public boolean isModified() {
      TextAttributes textAttributes = getAttributes();
      return !Comparing.equal(myOriginalBackground, textAttributes.getBackgroundColor()) ||
             !Comparing.equal(myOriginalIgnored, textAttributes.getForegroundColor()) ||
             !Comparing.equal(myOriginalStripe, textAttributes.getErrorStripeColor());
    }

    public void setBackgroundColor(Color selectedColor) {
      myBackgroundColor = selectedColor;
    }

    public Color getBackgroundColor() {
      return myBackgroundColor;
    }

    public void setIgnoredColor(Color selectedColor) {
      myIgnoredColor = selectedColor;
    }

    public Color getIgnoredColor() {
      return myIgnoredColor;
    }

    public void setStripeMarkColor(Color selectedColor) {
      myStripeColor = selectedColor;
    }

    public Color getStripeMarkColor() {
      return myStripeColor;
    }

    public boolean isInheritIgnoredColor() {
      return myIgnoredColor == null;
    }

    public void setInheritIgnoredColor(boolean value) {
      myIgnoredColor = value ? null : TextDiffTypeFactory.getMiddleColor(myBackgroundColor, myScheme.getDefaultBackground());
    }
  }
}
