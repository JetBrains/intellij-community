// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.lang.regexp.inspection.custom;

import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInspection.InspectionProfile;
import com.intellij.codeInspection.InspectionsBundle;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.ex.InspectionProfileModifiableModel;
import com.intellij.codeInspection.ex.InspectionToolWrapper;
import com.intellij.find.FindModel;
import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.profile.codeInspection.ui.InspectionMetaDataDialog;
import com.intellij.profile.codeInspection.ui.SingleInspectionProfilePanel;
import com.intellij.ui.AnActionButton;
import com.intellij.ui.DoubleClickListener;
import com.intellij.ui.LayeredIcon;
import com.intellij.ui.ToolbarDecorator;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.components.JBList;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.FormBuilder;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.intellij.lang.regexp.RegExpBundle;
import org.intellij.lang.regexp.inspection.custom.RegExpInspectionConfiguration.InspectionPattern;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.util.Collections;
import java.util.List;

/**
 * @author Bas Leijdekkers
 */
public class CustomRegExpFakeInspection extends LocalInspectionTool {

  private static final String GROUP = "RegExp";
  private final @NotNull RegExpInspectionConfiguration myConfiguration;

  public CustomRegExpFakeInspection(@NotNull RegExpInspectionConfiguration configuration) {
    if (configuration.getPatterns().isEmpty()) throw new IllegalArgumentException();

    myConfiguration = configuration;
  }

  public @NotNull RegExpInspectionConfiguration getConfiguration() {
    return myConfiguration;
  }

  @Override
  public @Nls(capitalization = Nls.Capitalization.Sentence) @NotNull String getDisplayName() {
    return myConfiguration.getName();
  }

  @Override
  public @NotNull String getShortName() {
    return myConfiguration.getUuid();
  }

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  public boolean isCleanup() {
    return isCleanupAllowed() && myConfiguration.isCleanup();
  }

  private boolean isCleanupAllowed() {
    return ContainerUtil.exists(myConfiguration.getPatterns(), p -> p.replacement() != null);
  }

  @Override
  public @NotNull String getID() {
    final HighlightDisplayKey key = HighlightDisplayKey.find(getShortName());
    if (key != null) {
      return key.getID(); // to avoid using a new suppress id before it is registered.
    }
    final String suppressId = myConfiguration.getSuppressId();
    return !StringUtil.isEmpty(suppressId) ? suppressId : CustomRegExpInspection.SHORT_NAME;
  }

  @Override
  public @Nullable String getAlternativeID() {
    return CustomRegExpInspection.SHORT_NAME;
  }

  @Override
  public @Nullable String getMainToolId() {
    return CustomRegExpInspection.SHORT_NAME;
  }

  public static String[] getGroup() {
    return new String[] {InspectionsBundle.message("group.names.user.defined"), GROUP};
  }

  @Override
  public @Nls(capitalization = Nls.Capitalization.Sentence) @NotNull String getGroupDisplayName() {
    return GROUP;
  }

  @Override
  public @Nls(capitalization = Nls.Capitalization.Sentence) String @NotNull [] getGroupPath() {
    return getGroup();
  }

  @Override
  public @Nullable String getStaticDescription() {
    final String description = myConfiguration.getDescription();
    if (StringUtil.isEmpty(description)) {
      return RegExpBundle.message("no.description.provided.description");
    }
    return description;
  }

  @Override
  public @NotNull JComponent createOptionsPanel() {
    final MyListModel model = new MyListModel();
    final JButton button = new JButton(RegExpBundle.message("edit.metadata.button"));
    button.addActionListener(__ -> performEditMetaData(button));

    final JList<InspectionPattern> list = new JBList<>(model);
    list.setVisibleRowCount(3);
    list.setCellRenderer(new RegExpInspectionConfigurationCellRenderer());
    final JPanel listPanel = ToolbarDecorator.createDecorator(list)
      .setAddAction(b -> performAdd(list, b))
      .setAddActionName(RegExpBundle.message("action.add.pattern.text"))
      .setAddIcon(LayeredIcon.ADD_WITH_DROPDOWN)
      .setRemoveAction(b -> performRemove(list))
      .setRemoveActionUpdater(e -> list.getSelectedValuesList().size() < list.getModel().getSize())
      .setEditAction(b -> performEdit(list))
      .setMoveUpAction(b -> performMove(list, true))
      .setMoveDownActionUpdater(e -> list.getSelectedValuesList().size() == 1)
      .setMoveDownAction(b -> performMove(list, false))
      .setMoveDownActionUpdater(e -> list.getSelectedValuesList().size() == 1)
      .createPanel();

    new DoubleClickListener() {
      @Override
      protected boolean onDoubleClick(@NotNull MouseEvent e) {
        performEdit(list);
        return true;
      }
    }.installOn(list);

    final JPanel panel = new FormBuilder()
      .addComponent(button)
      .addVerticalGap(UIUtil.DEFAULT_VGAP)
      .addLabeledComponentFillVertically(RegExpBundle.message("label.regexp.patterns"), listPanel)
      .getPanel();
    panel.setBorder(JBUI.Borders.emptyTop(10));
    return panel;
  }

  private void performEditMetaData(@NotNull Component context) {
    final Project project = CommonDataKeys.PROJECT.getData(DataManager.getInstance().getDataContext(context));
    final InspectionProfileModifiableModel profile = getInspectionProfile(context);
    if (profile == null || project == null) {
      return;
    }
    final CustomRegExpInspection inspection = getRegExpInspection(profile);
    final InspectionMetaDataDialog dialog =
      inspection.createMetaDataDialog(project, profile.getDisplayName(), myConfiguration);
    if (isCleanupAllowed()) {
      dialog.showCleanupOption(myConfiguration.isCleanup());
    }
    if (!dialog.showAndGet()) {
      return;
    }
    myConfiguration.setName(dialog.getName());
    myConfiguration.setDescription(dialog.getDescription());
    myConfiguration.setSuppressId(dialog.getSuppressId());
    myConfiguration.setProblemDescriptor(dialog.getProblemDescriptor());
    myConfiguration.setCleanup(dialog.isCleanup());

    inspection.updateConfiguration(myConfiguration);
    profile.setModified(true);
    profile.getProfileManager().fireProfileChanged(profile);
  }

  private void performMove(JList<InspectionPattern> list, boolean up) {
    final MyListModel model = (MyListModel)list.getModel();
    final int index = list.getSelectedIndex();
    final List<InspectionPattern> patterns = myConfiguration.getPatterns();
    final int newIndex = up ? index - 1 : index + 1;
    Collections.swap(patterns, index, newIndex);
    model.fireContentsChanged(list);
    list.setSelectedIndex(newIndex);
    saveChangesToProfile(list);
  }

  private void performAdd(JList<InspectionPattern> list, @NotNull AnActionButton b) {
    final AnAction[] children = new AnAction[]{new AddTemplateAction(list, false), new AddTemplateAction(list, true)};
    final RelativePoint point = b.getPreferredPopupPoint();
    JBPopupFactory.getInstance().createActionGroupPopup(null, new DefaultActionGroup(children),
                                                        DataManager.getInstance().getDataContext(b.getContextComponent()),
                                                        JBPopupFactory.ActionSelectionAid.SPEEDSEARCH, true).show(point);
  }

  private void performRemove(JList<InspectionPattern> list) {
    final List<InspectionPattern> patterns = myConfiguration.getPatterns();
    for (InspectionPattern pattern : list.getSelectedValuesList()) {
      myConfiguration.removePattern(pattern);
    }
    final int size = patterns.size();
    final int maxIndex = list.getMaxSelectionIndex();
    if (maxIndex != list.getMinSelectionIndex()) {
      list.setSelectedIndex(maxIndex);
    }
    ((MyListModel)list.getModel()).fireContentsChanged(list);
    if (list.getSelectedIndex() >= size) {
      list.setSelectedIndex(size - 1);
    }
    final int index = list.getSelectedIndex();
    list.scrollRectToVisible(list.getCellBounds(index, index));
    saveChangesToProfile(list);
  }

  private void performEdit(JList<InspectionPattern> list) {
    final Project project = CommonDataKeys.PROJECT.getData(DataManager.getInstance().getDataContext(list));
    if (project == null) return;
    final int index = list.getSelectedIndex();
    if (index < 0) return;
    final List<InspectionPattern> patterns = myConfiguration.getPatterns();
    final InspectionPattern configuration = patterns.get(index);
    if (configuration == null) return;
    final RegExpDialog dialog = new RegExpDialog(project, true, configuration);
    if (!dialog.showAndGet()) return;
    final InspectionPattern newConfiguration = dialog.getPattern();
    patterns.set(index, newConfiguration);
    ((MyListModel)list.getModel()).fireContentsChanged(list);
    saveChangesToProfile(list);
  }

  private void saveChangesToProfile(JList<InspectionPattern> list) {
    final InspectionProfileModifiableModel profile = getInspectionProfile(list);
    if (profile == null) return;
    final CustomRegExpInspection inspection = getRegExpInspection(profile);
    inspection.updateConfiguration(myConfiguration);
    profile.setModified(true);
  }

  private static @NotNull CustomRegExpInspection getRegExpInspection(@NotNull InspectionProfile profile) {
    final InspectionToolWrapper<?, ?> wrapper = profile.getInspectionTool(CustomRegExpInspection.SHORT_NAME, (Project)null);
    assert wrapper != null;
    return (CustomRegExpInspection)wrapper.getTool();
  }

  private static InspectionProfileModifiableModel getInspectionProfile(@NotNull Component c) {
    final SingleInspectionProfilePanel panel = UIUtil.uiParents(c, true).filter(SingleInspectionProfilePanel.class).first();
    if (panel == null) return null;
    return panel.getProfile();
  }

  private final class AddTemplateAction extends DumbAwareAction {
    private final JList<InspectionPattern> myList;
    private final boolean myReplace;

    private AddTemplateAction(JList<InspectionPattern> list, boolean replace) {
      super(replace
            ? RegExpBundle.message("action.add.regexp.replace.template.text")
            : RegExpBundle.message("action.add.regexp.search.template.text"));
      myList = list;
      myReplace = replace;
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      final Project project = e.getData(CommonDataKeys.PROJECT);
      assert project != null;

      FileType fileType = null;
      int flags = 0;
      FindModel.SearchContext context = FindModel.SearchContext.ANY;
      if (myList.getModel().getSize() > 0) {
        final InspectionPattern pattern = myList.getModel().getElementAt(0);
        fileType = pattern.fileType();
        flags = pattern.flags;
        context = pattern.searchContext();
      }
      final String replace = myReplace ? "" : null;
      final InspectionPattern defaultPattern = new InspectionPattern("", fileType, flags, context, replace);

      final RegExpDialog dialog = new RegExpDialog(project, true, defaultPattern);
      if (!dialog.showAndGet()) return;

      final InspectionPattern pattern = dialog.getPattern();
      myConfiguration.addPattern(pattern);
      ((MyListModel)myList.getModel()).fireContentsChanged(myList);
      saveChangesToProfile(myList);
    }
  }

  private class MyListModel extends AbstractListModel<InspectionPattern> {
    @Override
    public int getSize() {
      return myConfiguration.getPatterns().size();
    }

    @Override
    public InspectionPattern getElementAt(int index) {
      return myConfiguration.getPatterns().get(index);
    }

    public void fireContentsChanged(Object source) {
      fireContentsChanged(source, -1, -1);
    }
  }
}
