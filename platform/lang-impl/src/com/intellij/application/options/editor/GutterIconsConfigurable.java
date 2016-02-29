/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.application.options.editor;

import com.intellij.codeInsight.daemon.*;
import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.lang.LanguageExtensionPoint;
import com.intellij.openapi.extensions.ExtensionPoint;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.util.Comparing;
import com.intellij.ui.CheckBoxList;
import com.intellij.ui.SeparatorWithText;
import com.intellij.util.Function;
import com.intellij.util.NullableFunction;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import com.intellij.util.ui.EmptyIcon;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.*;
import java.util.List;

/**
 * @author Dmitry Avdeev
 */
public class GutterIconsConfigurable implements Configurable, Configurable.NoScroll {
  private JPanel myPanel;
  private CheckBoxList<GutterIconDescriptor> myList;
  private List<GutterIconDescriptor> myDescriptors;
  private Map<GutterIconDescriptor, PluginDescriptor> myFirstDescriptors = new HashMap<GutterIconDescriptor, PluginDescriptor>();

  @Nls
  @Override
  public String getDisplayName() {
    return "Gutter Icons";
  }

  @Nullable
  @Override
  public String getHelpTopic() {
    return "reference.settings.editor.gutter.icons";
  }

  @Nullable
  @Override
  public JComponent createComponent() {
    ExtensionPoint<LineMarkerProvider> point = Extensions.getRootArea().getExtensionPoint(LineMarkerProviders.EP_NAME);
    @SuppressWarnings("unchecked")
    LanguageExtensionPoint<LineMarkerProvider>[] extensions = (LanguageExtensionPoint<LineMarkerProvider>[])point.getExtensions();
    NullableFunction<LanguageExtensionPoint<LineMarkerProvider>, PluginDescriptor> function =
      new NullableFunction<LanguageExtensionPoint<LineMarkerProvider>, PluginDescriptor>() {
        @Nullable
        @Override
        public PluginDescriptor fun(LanguageExtensionPoint<LineMarkerProvider> point) {
          LineMarkerProvider instance = point.getInstance();
          return instance instanceof LineMarkerProviderDescriptor && ((LineMarkerProviderDescriptor)instance).getName() != null ? point.getPluginDescriptor() : null;
        }
      };
    MultiMap<PluginDescriptor, LanguageExtensionPoint<LineMarkerProvider>> map = ContainerUtil.groupBy(Arrays.asList(extensions), function);
    Map<GutterIconDescriptor, PluginDescriptor> pluginDescriptorMap = ContainerUtil.newHashMap();
    myDescriptors = new ArrayList<GutterIconDescriptor>();
    for (final PluginDescriptor descriptor : map.keySet()) {
      Collection<LanguageExtensionPoint<LineMarkerProvider>> points = map.get(descriptor);
      for (LanguageExtensionPoint<LineMarkerProvider> extensionPoint : points) {
        GutterIconDescriptor instance = (GutterIconDescriptor)extensionPoint.getInstance();
        if (instance.getOptions().length > 0) {
          for (GutterIconDescriptor option : instance.getOptions()) {
            myDescriptors.add(option);
            pluginDescriptorMap.put(option, descriptor);
          }
        }
        else {
          myDescriptors.add(instance);
          pluginDescriptorMap.put(instance, descriptor);
        }
      }
    }
    List<GutterIconDescriptor> options = new ArrayList<GutterIconDescriptor>();
    for (Iterator<GutterIconDescriptor> iterator = myDescriptors.iterator(); iterator.hasNext(); ) {
      GutterIconDescriptor descriptor = iterator.next();
      if (descriptor.getOptions().length > 0) {
        options.addAll(Arrays.asList(descriptor.getOptions()));
        iterator.remove();
      }
    }
    myDescriptors.addAll(options);
    myDescriptors.sort(new Comparator<GutterIconDescriptor>() {
      @Override
      public int compare(GutterIconDescriptor o1, GutterIconDescriptor o2) {
        if (pluginDescriptorMap.get(o1) != pluginDescriptorMap.get(o2)) return 0;
        return Comparing.compare(o1.getName(), o2.getName());
      }
    });
    PluginDescriptor current = null;
    for (GutterIconDescriptor descriptor : myDescriptors) {
      PluginDescriptor pluginDescriptor = pluginDescriptorMap.get(descriptor);
      if (pluginDescriptor != current) {
        myFirstDescriptors.put(descriptor, pluginDescriptor);
        current = pluginDescriptor;
      }
    }

    myList.setItems(myDescriptors, new Function<GutterIconDescriptor, String>() {
      @Override
      public String fun(GutterIconDescriptor descriptor) {
        return descriptor.getName();
      }
    });
    return myPanel;
  }

  @Override
  public boolean isModified() {
    for (GutterIconDescriptor descriptor : myDescriptors) {
      if (myList.isItemSelected(descriptor) != LineMarkerSettings.getSettings().isEnabled(descriptor)) {
        return true;
      }
    }
    return false;
  }

  @Override
  public void apply() throws ConfigurationException {
    for (GutterIconDescriptor descriptor : myDescriptors) {
      LineMarkerSettings.getSettings().setEnabled(descriptor, myList.isItemSelected(descriptor));
    }
    for (Project project : ProjectManager.getInstance().getOpenProjects()) {
      DaemonCodeAnalyzer.getInstance(project).restart();
    }
  }

  @Override
  public void reset() {
    for (GutterIconDescriptor descriptor : myDescriptors) {
      myList.setItemSelected(descriptor, LineMarkerSettings.getSettings().isEnabled(descriptor));
    }
  }

  @Override
  public void disposeUIResources() {

  }

  private void createUIComponents() {
    myList = new CheckBoxList<GutterIconDescriptor>() {
      @Override
      protected JComponent adjustRendering(JComponent rootComponent, JCheckBox checkBox, int index, boolean selected, boolean hasFocus) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createEmptyBorder());
        GutterIconDescriptor descriptor = myList.getItemAt(index);
        Icon icon = descriptor == null ? null : descriptor.getIcon();
        JLabel label = new JLabel(icon == null ? EmptyIcon.ICON_16 : icon);
        label.setOpaque(true);
        label.setPreferredSize(new Dimension(25, -1));
        label.setHorizontalAlignment(SwingConstants.CENTER);
        panel.add(label, BorderLayout.WEST);
        panel.add(checkBox, BorderLayout.CENTER);
        panel.setBackground(getBackground(false));
        label.setBackground(getBackground(selected));
        if (!checkBox.isOpaque()) {
          checkBox.setOpaque(true);
        }
        checkBox.setBorder(null);

        PluginDescriptor pluginDescriptor = myFirstDescriptors.get(descriptor);
        if (pluginDescriptor instanceof IdeaPluginDescriptor) {
          SeparatorWithText separator = new SeparatorWithText();
          String name = ((IdeaPluginDescriptor)pluginDescriptor).getName();
          separator.setCaption("IDEA CORE".equals(name) ? "Common" : name);
          panel.add(separator, BorderLayout.NORTH);
        }

        return panel;
      }

      @Nullable
      @Override
      protected Point findPointRelativeToCheckBox(int x, int y, @NotNull JCheckBox checkBox, int index) {
        return super.findPointRelativeToCheckBoxWithAdjustedRendering(x, y, checkBox, index);
      }
    };
    myList.setBorder(BorderFactory.createEmptyBorder());
  }
}
