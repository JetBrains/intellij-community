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

package com.intellij.application.options.colors;

import com.intellij.ide.DataManager;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.options.ex.Settings;
import com.intellij.openapi.util.ActionCallback;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.util.EventDispatcher;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.text.BadLocationException;
import javax.swing.text.Element;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.util.EventListener;
import java.util.HashSet;
import java.util.Set;

public class OptionsPanelImpl extends JPanel implements OptionsPanel {
  public static final String SELECTED_COLOR_OPTION_PROPERTY = "selected.color.option.type";

  private final ColorOptionsTree myOptionsTree;
  private final ColorDescriptionPanel myOptionsPanel;

  private final ColorAndFontOptions myOptions;
  private final SchemesPanel mySchemesProvider;
  private final String myCategoryName;

  private final PropertiesComponent myProperties;

  private final EventDispatcher<ColorAndFontSettingsListener> myDispatcher = EventDispatcher.create(ColorAndFontSettingsListener.class);

  public OptionsPanelImpl(ColorAndFontOptions options,
                          SchemesPanel schemesProvider,
                          String categoryName) {
    this(options, schemesProvider, categoryName, new ColorAndFontDescriptionPanel());
  }

  public OptionsPanelImpl(ColorAndFontOptions options,
                          SchemesPanel schemesProvider,
                          String categoryName,
                          ColorDescriptionPanel optionsPanel) {
    super(new BorderLayout());
    myOptions = options;
    mySchemesProvider = schemesProvider;
    myCategoryName = categoryName;
    myProperties = PropertiesComponent.getInstance();

    myOptionsPanel = optionsPanel;
    myOptionsPanel.addListener(new ColorDescriptionPanel.Listener() {
      @Override
      public void onSettingsChanged(ActionEvent e) {
        myDispatcher.getMulticaster().settingsChanged();
      }

      @Override
      public void onHyperLinkClicked(HyperlinkEvent e) {
        if (e.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
          Settings settings = Settings.KEY.getData(DataManager.getInstance().getDataContext(OptionsPanelImpl.this));
          String attrName = e.getDescription();
          Element element = e.getSourceElement();
          String pageName;
          try {
            pageName = element.getDocument().getText(element.getStartOffset(), element.getEndOffset() - element.getStartOffset());
          }
          catch (BadLocationException e1) {
            return;
          }
          final SearchableConfigurable page = myOptions.findSubConfigurable(pageName);
          if (page != null && settings != null) {
            Runnable runnable = page.enableSearch(attrName);
            ActionCallback callback = settings.select(page);
            if (runnable != null) callback.doWhenDone(runnable);
          }
        }
      }
    });

    myOptionsTree = new ColorOptionsTree(myCategoryName);

    myOptionsTree.addTreeSelectionListener(new TreeSelectionListener() {
      @Override
      public void valueChanged(TreeSelectionEvent e) {
        if (!mySchemesProvider.areSchemesLoaded()) return;
        processListValueChanged();
      }
    });

    JScrollPane scrollPane = ScrollPaneFactory.createScrollPane(myOptionsTree);
    add(scrollPane, BorderLayout.CENTER);
    add(myOptionsPanel.getPanel(), BorderLayout.EAST);

  }

  @Override
  public void addListener(ColorAndFontSettingsListener listener) {
    myDispatcher.addListener(listener);
  }

  private void processListValueChanged() {
    Object selectedValue = myOptionsTree.getSelectedValue();
    ColorAndFontDescription description = selectedValue instanceof ColorAndFontDescription ? (ColorAndFontDescription)selectedValue : null;
    if (description == null) {
      if (selectedValue == null) {
        String preselectedType = myProperties.getValue(SELECTED_COLOR_OPTION_PROPERTY);
        if (preselectedType != null) {
          myOptionsTree.selectOptionByType(preselectedType);
          description = myOptionsTree.getSelectedDescriptor();
        }
      }
    }
    if (description != null) {
      myProperties.setValue(SELECTED_COLOR_OPTION_PROPERTY, description.getType());
      myOptionsPanel.reset(description);
      myDispatcher.getMulticaster().selectedOptionChanged(description);
    }
    else {
      myOptionsPanel.resetDefault();
    }
  }

  private void fillOptionsList() {
    myOptionsTree.fillOptions(myOptions);
  }

  @Override
  public JPanel getPanel() {
    return this;
  }

  @Override
  public void updateOptionsList() {
    fillOptionsList();
    processListValueChanged();
  }

  @Override
  public Runnable showOption(final String attributeDisplayName) {
    return () -> myOptionsTree.selectOptionByName(attributeDisplayName);
  }

  @Override
  public void applyChangesToScheme() {
    ColorAndFontDescription descriptor = myOptionsTree.getSelectedDescriptor();
    if (descriptor != null) {
      myOptionsPanel.apply(descriptor, myOptions.getSelectedScheme());
    }
  }

  @Override
  public void selectOption(String attributeType) {
    myOptionsTree.selectOptionByType(attributeType);
  }

  @Override
  public Set<String> processListOptions() {
    HashSet<String> result = new HashSet<>();
    EditorSchemeAttributeDescriptor[] descriptions = myOptions.getCurrentDescriptions();
    for (EditorSchemeAttributeDescriptor description : descriptions) {
      if (description.getGroup().equals(myCategoryName)) {
        result.add(description.toString());
      }
    }
    return result;
  }

  public interface ColorDescriptionPanel {
    @NotNull
    JComponent getPanel();

    void resetDefault();

    void reset(@NotNull ColorAndFontDescription description);

    void apply(@NotNull ColorAndFontDescription descriptor, EditorColorsScheme scheme);

    void addListener(@NotNull Listener listener);

    interface Listener extends EventListener {
      void onSettingsChanged(ActionEvent e);

      void onHyperLinkClicked(HyperlinkEvent e);
    }
  }
}
