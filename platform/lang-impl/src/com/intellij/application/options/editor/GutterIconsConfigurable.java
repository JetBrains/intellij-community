// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.application.options.editor;

import com.intellij.codeInsight.daemon.*;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.lang.LanguageExtensionPoint;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehaviorSpecification;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.ex.EditorSettingsExternalizable;
import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.CheckBoxList;
import com.intellij.ui.ListSpeedSearch;
import com.intellij.ui.SeparatorWithText;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.speedSearch.SpeedSearchSupply;
import com.intellij.util.NullableFunction;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import com.intellij.util.ui.EmptyIcon;
import org.jetbrains.annotations.*;

import javax.swing.*;
import javax.swing.event.ChangeListener;
import java.awt.*;
import java.util.List;
import java.util.*;

/**
 * @author Dmitry Avdeev
 */
public final class GutterIconsConfigurable implements SearchableConfigurable, Configurable.NoScroll {
  public static final @NonNls String ID = "editor.preferences.gutterIcons";

  private JPanel myPanel;
  private CheckBoxList<GutterIconDescriptor> myList;
  private JBCheckBox myShowGutterIconsJBCheckBox;
  private final List<GutterIconDescriptor> myDescriptors = new ArrayList<>();
  private final Map<GutterIconDescriptor, PluginDescriptor> myFirstDescriptors = new HashMap<>();

  @Override
  public @Nls String getDisplayName() {
    return IdeBundle.message("configurable.GutterIconsConfigurable.display.name");
  }

  @Override
  public @Nullable String getHelpTopic() {
    return "reference.settings.editor.gutter.icons";
  }

  @Override
  public @Nullable JComponent createComponent() {
    LanguageExtensionPoint<LineMarkerProvider>[] extensions = LineMarkerProviders.EP_NAME.getExtensions();
    NullableFunction<LanguageExtensionPoint<LineMarkerProvider>, PluginDescriptor> function =
      point1 -> {
        LineMarkerProvider instance = point1.getInstance();
        return instance instanceof LineMarkerProviderDescriptor && ((LineMarkerProviderDescriptor)instance).getName() != null ? point1.getPluginDescriptor() : null;
      };
    MultiMap<PluginDescriptor, LanguageExtensionPoint<LineMarkerProvider>> map = ContainerUtil.groupBy(Arrays.asList(extensions), function);
    Map<GutterIconDescriptor, PluginDescriptor> pluginDescriptorMap = new HashMap<>();
    Set<String> ids = new HashSet<>();
    for (final PluginDescriptor descriptor : map.keySet()) {
      Collection<LanguageExtensionPoint<LineMarkerProvider>> points = map.get(descriptor);
      for (LanguageExtensionPoint<LineMarkerProvider> extensionPoint : points) {
        GutterIconDescriptor instance = (GutterIconDescriptor)extensionPoint.getInstance();
        if (instance.getOptions().length > 0) {
          for (GutterIconDescriptor option : instance.getOptions()) {
            if (ids.add(option.getId())) {
              myDescriptors.add(option);
              pluginDescriptorMap.put(option, descriptor);
            }
          }
        }
        else {
          if (ids.add(instance.getId())) {
            myDescriptors.add(instance);
          }
          pluginDescriptorMap.put(instance, descriptor);
        }
      }
    }
    /*
    List<GutterIconDescriptor> options = new ArrayList<GutterIconDescriptor>();
    for (Iterator<GutterIconDescriptor> iterator = myDescriptors.iterator(); iterator.hasNext(); ) {
      GutterIconDescriptor descriptor = iterator.next();
      if (descriptor.getOptions().length > 0) {
        options.addAll(Arrays.asList(descriptor.getOptions()));
        iterator.remove();
      }
    }
    myDescriptors.addAll(options);
    */
    myDescriptors.sort((o1, o2) -> {
      int byPlugin = StringUtil.naturalCompare(getPluginDisplayName(pluginDescriptorMap.get(o1)),
                                               getPluginDisplayName(pluginDescriptorMap.get(o2)));
      return byPlugin != 0 ?
             byPlugin :
             StringUtil.naturalCompare(o1.getName(), o2.getName());
    });
    PluginId current = null;
    for (GutterIconDescriptor descriptor : myDescriptors) {
      PluginDescriptor pluginDescriptor = pluginDescriptorMap.get(descriptor);
      PluginId pluginId = pluginDescriptor != null ? pluginDescriptor.getPluginId() : null;
      // You get different plugin descriptors for `GutterIconDescriptor`s from the same plugin
      // if they are located in different plugin modules.
      // But there is no reason to put them into separate UI groups especially taking into account
      // we sort `myDescriptors` only by plugin name, so compare plugin descriptors by their id
      if (pluginId != null && !pluginId.equals(current)) {
        myFirstDescriptors.put(descriptor, pluginDescriptor);
        current = pluginId;
      }
    }

    myList.setItems(myDescriptors, GutterIconDescriptor::getName);
    myShowGutterIconsJBCheckBox.addChangeListener(e -> myList.setEnabled(myShowGutterIconsJBCheckBox.isSelected()));
    SwingUtilities.updateComponentTreeUI(myPanel); // TODO: create Swing components in this method (see javadoc)
    return myPanel;
  }

  @Override
  public boolean isModified() {
    for (GutterIconDescriptor descriptor : myDescriptors) {
      if (myList.isItemSelected(descriptor) != LineMarkerSettings.getSettings().isEnabled(descriptor)) {
        return true;
      }
    }
    return myShowGutterIconsJBCheckBox.isSelected() != EditorSettingsExternalizable.getInstance().areGutterIconsShown();
  }

  @Override
  public void apply() throws ConfigurationException {
    EditorSettingsExternalizable editorSettings = EditorSettingsExternalizable.getInstance();
    if (myShowGutterIconsJBCheckBox.isSelected() != editorSettings.areGutterIconsShown()) {
      editorSettings.setGutterIconsShown(myShowGutterIconsJBCheckBox.isSelected());
      EditorOptionsPanelKt.reinitAllEditors();
    }
    for (GutterIconDescriptor descriptor : myDescriptors) {
      LineMarkerSettings.getSettings().setEnabled(descriptor, myList.isItemSelected(descriptor));
    }
    for (Project project : ProjectManager.getInstance().getOpenProjects()) {
      DaemonCodeAnalyzer.getInstance(project).restart();
    }
    ApplicationManager.getApplication().getMessageBus().syncPublisher(EditorOptionsListener.GUTTER_ICONS_CONFIGURABLE_TOPIC).changesApplied();
  }

  @Override
  public void reset() {
    for (GutterIconDescriptor descriptor : myDescriptors) {
      myList.setItemSelected(descriptor, LineMarkerSettings.getSettings().isEnabled(descriptor));
    }
    boolean gutterIconsShown = EditorSettingsExternalizable.getInstance().areGutterIconsShown();
    myShowGutterIconsJBCheckBox.setSelected(gutterIconsShown);
    myList.setEnabled(gutterIconsShown);
  }

  @Override
  public void disposeUIResources() {
    for (ChangeListener listener : myShowGutterIconsJBCheckBox.getChangeListeners()) {
      myShowGutterIconsJBCheckBox.removeChangeListener(listener);
    }
  }

  private static @Nls String getPluginDisplayName(@NotNull PluginDescriptor pluginDescriptor) {
    return pluginDescriptor instanceof IdeaPluginDescriptor &&
           PluginManagerCore.CORE_ID.equals(pluginDescriptor.getPluginId()) ?
           IdeBundle.message("title.common") :
           pluginDescriptor.getName();
  }

  private void createUIComponents() {
    myList = new CheckBoxList<>() {
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
        if (pluginDescriptor != null) {
          SeparatorWithText separator = new SeparatorWithText();
          String name = getPluginDisplayName(pluginDescriptor);
          separator.setCaption(name);
          panel.add(separator, BorderLayout.NORTH);
        }

        return panel;
      }

      @Override
      protected @Nullable Point findPointRelativeToCheckBox(int x, int y, @NotNull JCheckBox checkBox, int index) {
        return super.findPointRelativeToCheckBoxWithAdjustedRendering(x, y, checkBox, index);
      }
    };
    myList.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
    myList.setBorder(BorderFactory.createEmptyBorder());
    ListSpeedSearch.installOn(myList, JCheckBox::getText);
  }

  @Override
  public @NotNull String getId() {
    return ID;
  }

  @Override
  public @Nullable Runnable enableSearch(String option) {
    return () -> Objects.requireNonNull(SpeedSearchSupply.getSupply(myList, true)).findAndSelectElement(option);
  }

  @TestOnly
  public List<GutterIconDescriptor> getDescriptors() { return myDescriptors; }

  public static final class ShowSettingsAction extends DumbAwareAction implements ActionRemoteBehaviorSpecification.Frontend {
    public ShowSettingsAction() {
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      ShowSettingsUtil.getInstance().showSettingsDialog(e.getProject(), GutterIconsConfigurable.class);
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
      return ActionUpdateThread.BGT;
    }
  }
}
