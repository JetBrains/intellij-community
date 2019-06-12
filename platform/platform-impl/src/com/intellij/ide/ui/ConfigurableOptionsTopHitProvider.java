// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.ui;

import com.intellij.ide.ui.search.BooleanOptionDescription;
import com.intellij.ide.ui.search.OptionDescription;
import com.intellij.ide.ui.search.SearchUtil;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.ex.ConfigurableVisitor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.util.List;
import java.util.*;

/**
 * @author Sergey.Malenkov
 */
public abstract class ConfigurableOptionsTopHitProvider extends OptionsTopHitProvider {
  private static final Logger LOG = Logger.getInstance(ConfigurableOptionsTopHitProvider.class);
  private final Deque<String> myPrefix = new ArrayDeque<>();

  protected abstract Configurable getConfigurable(@Nullable Project project);

  /**
   * Returns a name that will be added to all option names.
   * This implementation returns the display name of the configurable.
   *
   * @param configurable the configurable to process
   * @return a main prefix or {@code null} to ignore
   */
  protected String getName(Configurable configurable) {
    return configurable.getDisplayName();
  }

  /**
   * Returns a section name that will be added to some option names,
   * which are located within the specified container.
   * This implementation returns the border title of the component.
   *
   * @param component the component to process
   * @return a section prefix or {@code null} to ignore
   */
  protected String getSection(Component component) {
    if (component instanceof JComponent) {
      Border border = ((JComponent)component).getBorder();
      if (border instanceof TitledBorder) {
        return ((TitledBorder)border).getTitle();
      }
    }
    return null;
  }

  /**
   * Returns an option name that will be used to create an option.
   *
   * @param checkbox the candidate to create an option
   * @return an option prefix or {@code null} to ignore this option
   */
  protected String getOptionName(JCheckBox checkbox) {
    return getOptionName(checkbox.getText());
  }

  @Nullable
  protected String getOptionName(@NotNull String text) {
    String name = StringUtil.stripHtml(text, false);
    if (StringUtil.isEmpty(name)) {
      return null;
    }
    if (myPrefix.isEmpty()) {
      return name;
    }
    StringBuilder sb = new StringBuilder();
    Iterator<String> iterator = myPrefix.descendingIterator();
    while (iterator.hasNext()) {
      sb.append(iterator.next()).append(": ");
    }
    return sb.append(name).toString();
  }

  protected void init(Collection<? super BooleanOptionDescription> options, Configurable configurable, Component component) {
    initRecursively(options, configurable, component);
  }

  private void initRecursively(Collection<? super BooleanOptionDescription> options, Configurable configurable, Component component) {
    String section = getSection(component);
    if (section != null) {
      myPrefix.push(section);
    }
    if (component instanceof JCheckBox) {
      JCheckBox checkbox = (JCheckBox)component;
      String option = getOptionName(checkbox);
      if (option != null) {
        options.add(new Option(configurable, checkbox, option));
      }
    }
    else if (component instanceof JComboBox) {
      JComboBox combo = (JComboBox)component;
      if (!combo.isEditable()) {
        JLabel label = UIUtil.uiChildren(combo.getParent()).takeWhile(o -> o != combo).filter(JLabel.class).last();
        String baseName = label == null ? null : getOptionName(label.getText());
        if (baseName != null) {
          String optionName = (baseName + " ").replace(": ", " | ");
          List<String> list = SearchUtil.getItemsFromComboBox(combo);
          for (int i = 0, len = list.size(); i < len; i++) {
            String s = optionName + list.get(i);
            options.add(new Option2(configurable, combo, s, i));
          }
        }
      }
    }
    else if (component instanceof Container) {
      Container container = (Container)component;
      for (int i = 0; i < container.getComponentCount(); i++) {
        initRecursively(options, configurable, container.getComponent(i));
      }
    }
    if (section != null) {
      myPrefix.pop();
    }
  }

  @NotNull
  @Override
  public Collection<OptionDescription> getOptions(@Nullable Project project) {
    try {
      Configurable configurable = getConfigurable(project);
      Component component = configurable.createComponent();
      configurable.reset();
      myPrefix.clear();
      String name = getName(configurable);
      if (name != null) {
        myPrefix.push(name);
      }
      Collection<BooleanOptionDescription> options = new ArrayList<>();
      init(options, configurable, component);
      return Collections.unmodifiableCollection(options);
    }
    catch (Exception exception) {
      LOG.debug(exception);
    }
    return Collections.emptyList();
  }

  private static final class Option extends BooleanOptionDescription implements Disposable {
    final Configurable myConfigurable;
    final JCheckBox myCheckBox;

    Option(Configurable configurable, JCheckBox checkbox, String option) {
      super(option, ConfigurableVisitor.ByID.getID(configurable));
      myConfigurable = configurable;
      myCheckBox = checkbox;
    }

    @Override
    public void dispose() {
      myConfigurable.disposeUIResources();
    }

    @Override
    public boolean isOptionEnabled() {
      return myCheckBox.isSelected();
    }

    @Override
    public void setOptionState(boolean selected) {
      if (selected != myCheckBox.isSelected()) {
        myCheckBox.setSelected(selected);
        try {
          myConfigurable.apply();
        }
        catch (ConfigurationException exception) {
          LOG.debug(exception);
        }
      }
    }
  }

  private static final class Option2 extends BooleanOptionDescription implements Disposable {
    final Configurable myConfigurable;
    final JComboBox<?> myComboBox;
    final int myIndex;
    final int myPrevIndex;

    Option2(Configurable configurable, JComboBox<?> comboBox, String option, int index) {
      super(option, ConfigurableVisitor.ByID.getID(configurable));
      myConfigurable = configurable;
      myComboBox = comboBox;
      myIndex = index;
      myPrevIndex = myComboBox.getSelectedIndex();
    }

    @Override
    public void dispose() {
      myConfigurable.disposeUIResources();
    }

    @Override
    public boolean isOptionEnabled() {
      return myComboBox.getSelectedIndex() == myIndex;
    }

    @Override
    public void setOptionState(boolean selected) {
      if (selected != (myComboBox.getSelectedIndex() == myIndex)) {
        myComboBox.setSelectedIndex(selected ? myIndex : myPrevIndex);
        try {
          myConfigurable.apply();
        }
        catch (ConfigurationException exception) {
          LOG.debug(exception);
        }
      }
    }
  }
}
