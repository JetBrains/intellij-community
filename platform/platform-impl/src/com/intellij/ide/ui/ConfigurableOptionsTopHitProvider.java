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
package com.intellij.ide.ui;

import com.intellij.ide.ui.search.BooleanOptionDescription;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.ex.ConfigurableVisitor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.util.*;

/**
 * @author Sergey.Malenkov
 */
public abstract class ConfigurableOptionsTopHitProvider extends OptionsTopHitProvider {
  private static final Logger LOG = Logger.getInstance(ConfigurableOptionsTopHitProvider.class);
  private final Deque<String> myPrefix = new ArrayDeque<>();

  protected abstract Configurable getConfigurable(Project project);

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
    String name = StringUtil.stripHtml(checkbox.getText(), false);
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

  protected void init(Collection<BooleanOptionDescription> options, Configurable configurable, Component component) {
    initRecursively(options, configurable, component);
  }

  private void initRecursively(Collection<BooleanOptionDescription> options, Configurable configurable, Component component) {
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
  public Collection<BooleanOptionDescription> getOptions(@Nullable Project project) {
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

  private static final class Option extends BooleanOptionDescription {
    private final Configurable myConfigurable;
    private final JCheckBox myCheckBox;

    private Option(Configurable configurable, JCheckBox checkbox, String option) {
      super(option, ConfigurableVisitor.ByID.getID(configurable));
      myConfigurable = configurable;
      myCheckBox = checkbox;
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
}
