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
package com.intellij.application.options.editor;

import com.intellij.codeInsight.daemon.*;
import com.intellij.codeInsight.daemon.LineMarkerSettings;
import com.intellij.lang.LanguageExtensionPoint;
import com.intellij.openapi.extensions.ExtensionPoint;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.ui.CheckBoxList;
import com.intellij.util.Function;
import com.intellij.util.NullableFunction;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.EmptyIcon;
import gnu.trove.THashSet;
import gnu.trove.TObjectHashingStrategy;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

/**
 * @author Dmitry Avdeev
 */
public class GutterIconsConfigurable implements Configurable {
  private JPanel myPanel;
  private CheckBoxList<GutterIconDescriptor> myList;
  private List<GutterIconDescriptor> myDescriptors;

  @Nls
  @Override
  public String getDisplayName() {
    return "Gutter Icons";
  }

  @Nullable
  @Override
  public String getHelpTopic() {
    return null;
  }

  @Nullable
  @Override
  public JComponent createComponent() {
    ExtensionPoint<LineMarkerProvider> point = Extensions.getRootArea().getExtensionPoint(LineMarkerProviders.EP_NAME);
    LanguageExtensionPoint<LineMarkerProvider>[] extensions = (LanguageExtensionPoint<LineMarkerProvider>[])point.getExtensions();
    List<LineMarkerProviderDescriptor> descriptors = ContainerUtil
      .mapNotNull(extensions, new NullableFunction<LanguageExtensionPoint<LineMarkerProvider>, LineMarkerProviderDescriptor>() {
        @Nullable
        @Override
        public LineMarkerProviderDescriptor fun(LanguageExtensionPoint<LineMarkerProvider> point) {
          LineMarkerProvider instance = point.getInstance();
          if (instance instanceof LineMarkerProviderDescriptor) {
            LineMarkerProviderDescriptor descriptor = (LineMarkerProviderDescriptor)instance;
            return descriptor.getName() == null ? null : descriptor;
          }
          return null;
        }
      });
    myDescriptors = new ArrayList<GutterIconDescriptor>(new THashSet<LineMarkerProviderDescriptor>(descriptors,
                                                                                                           new TObjectHashingStrategy<LineMarkerProviderDescriptor>() {
                                                                                                             @Override
                                                                                                             public int computeHashCode(LineMarkerProviderDescriptor object) {
                                                                                                               return object.getClass().hashCode();
                                                                                                             }

                                                                                                             @Override
                                                                                                             public boolean equals(LineMarkerProviderDescriptor o1,
                                                                                                                                   LineMarkerProviderDescriptor o2) {
                                                                                                               return o1.getClass().equals(o2.getClass());
                                                                                                             }
                                                                                                           }));
    List<GutterIconDescriptor> options = new ArrayList<GutterIconDescriptor>();
    for (Iterator<GutterIconDescriptor> iterator = myDescriptors.iterator(); iterator.hasNext(); ) {
      GutterIconDescriptor descriptor = iterator.next();
      if (descriptor.getOptions().length > 0) {
        options.addAll(Arrays.asList(descriptor.getOptions()));
        iterator.remove();
      }
    }
    myDescriptors.addAll(options);
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
        GutterIconDescriptor descriptor = myList.getItemAt(index);
        Icon icon = descriptor == null ? null : descriptor.getIcon();
        JLabel label = new JLabel(icon == null ? EmptyIcon.ICON_16 : icon);
        label.setPreferredSize(new Dimension(25, -1));
        label.setHorizontalAlignment(SwingConstants.CENTER);
        panel.add(label, BorderLayout.WEST);
        panel.add(checkBox, BorderLayout.CENTER);
        panel.setBackground(rootComponent.getBackground());
        checkBox.setBorder(null);
        return panel;
      }
    };
  }
}
