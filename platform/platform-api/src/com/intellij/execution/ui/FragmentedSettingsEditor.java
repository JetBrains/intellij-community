// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.ui;

import com.intellij.ide.DataManager;
import com.intellij.ide.IdeBundle;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.options.*;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.NotNullLazyValue;
import com.intellij.ui.components.labels.LinkLabel;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.*;
import java.util.stream.Collectors;

import static com.intellij.icons.AllIcons.Actions.FindAndShowNextMatches;

public abstract class FragmentedSettingsEditor<Settings extends FragmentedSettings> extends CompositeSettingsEditor<Settings> {

  private final NotNullLazyValue<Collection<SettingsEditorFragment<Settings, ?>>> myFragments =
    NotNullLazyValue.createValue(() -> createFragments());

  protected abstract Collection<SettingsEditorFragment<Settings, ?>> createFragments();

  protected Collection<SettingsEditorFragment<Settings, ?>> getFragments() {
    return new ArrayList<>(myFragments.getValue());
  }

  protected abstract String getTitle();

  @Override
  public void resetEditorFrom(@NotNull Settings settings) {
    super.resetEditorFrom(settings);
    @Nullable Set<String> visibleFragments = settings.getVisibleFragments();
    for (SettingsEditorFragment<Settings, ?> fragment : getFragments()) {
      fragment.setVisible(visibleFragments.isEmpty() ?
                          fragment.isInitiallyVisible(settings) :
                          visibleFragments.contains(fragment.getId()));
    }
  }

  @Override
  public void applyEditorTo(@NotNull Settings settings) throws ConfigurationException {
    super.applyEditorTo(settings);
    settings.setVisibleFragments(
      getFragments().stream().filter(fragment -> fragment.isVisible()).map(fragment -> fragment.getId()).collect(Collectors.toSet()));
  }

  @Override
  public CompositeSettingsBuilder<Settings> getBuilder() {
    return new CompositeSettingsBuilder<Settings>() {

      private final JPanel result = new JPanel(new GridBagLayout());
      private final GridBagConstraints c =
        new GridBagConstraints(0, 0, 1, 1, 1, 0, GridBagConstraints.CENTER, GridBagConstraints.BOTH, JBUI.insetsTop(5), 0, 0);
      private LinkLabel<?> linkLabel;

      @Override
      public Collection<SettingsEditor<Settings>> getEditors() {
        return new ArrayList<>(getFragments());
      }

      @Override
      public JComponent createCompoundEditor() {
        result.setBorder(JBUI.Borders.emptyLeft(5));
        addLine(new JSeparator());
        addLine(buildHeader());

        List<SettingsEditorFragment<Settings, ?>> commandLineFragments =
          ContainerUtil.filter(getFragments(), fragment -> fragment.getCommandLinePosition() >= 0);
        commandLineFragments.sort(Comparator.comparingInt(SettingsEditorFragment::getCommandLinePosition));
        JComponent commandLinePanel = buildCommandLinePanel(commandLineFragments);
        addLine(commandLinePanel);

        JPanel tagsPanel = new JPanel(new FlowLayout(FlowLayout.LEADING));
        tagsPanel.setBorder(JBUI.Borders.empty(5, 0));
        Collection<SettingsEditorFragment<Settings, ?>> fragments = getFragments();
        fragments.removeAll(commandLineFragments);
        for (SettingsEditorFragment<Settings, ?> fragment : fragments) {
          if (fragment.isTag()) {
            tagsPanel.add(fragment.getComponent());
          }
          else {
            addLine(fragment.getComponent());
          }
        }
        addLine(tagsPanel);
        c.weighty = 1;
        result.add(new JPanel(), c);
        return result;
      }

      private void addLine(Component component) {
        result.add(component, c.clone());
        c.gridy++;
      }

      private JComponent buildHeader() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(JBUI.Borders.empty(5, 0));
        JLabel label = new JLabel(getTitle());
        label.setFont(JBUI.Fonts.label().deriveFont(Font.BOLD));
        panel.add(label, BorderLayout.WEST);

        linkLabel = LinkLabel.create(OptionsBundle.message("settings.editor.modify.options"), () -> showOptions());
        linkLabel.setIcon(FindAndShowNextMatches);
        linkLabel.setHorizontalTextPosition(SwingConstants.LEADING);
        panel.add(linkLabel, BorderLayout.EAST);
        return panel;
      }

      private void showOptions() {
        List<SettingsEditorFragment<Settings, ?>> fragments =
          ContainerUtil.filter(myFragments.getValue(), fragment -> fragment.getName() != null);
        DefaultActionGroup actionGroup = new DefaultActionGroup();
        String group = null;
        for (SettingsEditorFragment<Settings, ?> fragment : fragments) {
          if (!Objects.equals(group, fragment.getGroup())) {
            group = fragment.getGroup();
            actionGroup.add(new Separator(group));
          }
          actionGroup.add(new ToggleFragmentAction(fragment));
        }
        DataContext dataContext = DataManager.getInstance().getDataContext(getComponent());
        JBPopupFactory.getInstance().createActionGroupPopup(IdeBundle.message("popup.title.add.run.options"),
                                                            actionGroup,
                                                            dataContext,
                                                            JBPopupFactory.ActionSelectionAid.SPEEDSEARCH, true).showInBestPositionFor(dataContext);
      }

      private JComponent buildCommandLinePanel(List<SettingsEditorFragment<Settings, ?>> fragments) {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(JBUI.Borders.emptyBottom(5));
        GridBagConstraints c =
          new GridBagConstraints(0, 0, 1, 1, 1, 0, GridBagConstraints.CENTER, GridBagConstraints.BOTH, JBUI.emptyInsets(), 0, 0);
        for (SettingsEditorFragment<Settings, ?> fragment : fragments) {
          JComponent editor = fragment.createEditor();
          panel.add(editor, c.clone());
          c.gridx++;
        }
        return panel;
      }
    };
  }

  private static class ToggleFragmentAction extends ToggleAction {

    private final SettingsEditorFragment<?, ?> myFragment;

    private ToggleFragmentAction(SettingsEditorFragment<?, ?> fragment) {
      super(fragment.getName());
      myFragment = fragment;
    }

    @Override
    public boolean isSelected(@NotNull AnActionEvent e) {
      return myFragment.isVisible();
    }

    @Override
    public void setSelected(@NotNull AnActionEvent e, boolean state) {
      myFragment.setVisible(state);
    }
  }
}
