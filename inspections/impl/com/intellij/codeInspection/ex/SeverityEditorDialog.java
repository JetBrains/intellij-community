/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.codeInspection.ex;

import com.intellij.application.options.colors.ColorAndFontDescriptionPanel;
import com.intellij.application.options.colors.TextAttributesDescription;
import com.intellij.codeInsight.daemon.impl.HighlightInfoType;
import com.intellij.codeInsight.daemon.impl.SeverityRegistrar;
import com.intellij.codeInspection.InspectionsBundle;
import com.intellij.execution.ExecutionBundle;
import com.intellij.ide.IdeBundle;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.colors.CodeInsightColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Factory;
import com.intellij.openapi.util.IconLoader;
import com.intellij.ui.LightColors;
import com.intellij.ui.ListUtil;
import com.intellij.ui.ReorderableListController;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.util.*;
import java.util.List;

/**
 * User: anna
 * Date: 24-Feb-2006
 */
public class SeverityEditorDialog extends DialogWrapper {
  private JPanel myPanel;

  private JList myOptionsList = new JList();
  private ColorAndFontDescriptionPanel myOptionsPanel = new ColorAndFontDescriptionPanel();

  private int myCurrentSelection = -1;
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInspection.ex.SeverityEditorDialog");
  private final SeverityRegistrar mySeverityRegistrar;
  private CardLayout myCard;
  private JPanel myRightPanel;
  @NonNls private static final String DEFAULT = "DEFAULT";
  @NonNls private static final String EDITABLE = "EDITABLE";

  public SeverityEditorDialog(JComponent parent, final HighlightSeverity severity, final SeverityRegistrar severityRegistrar) {
    super(parent, true);
    mySeverityRegistrar = severityRegistrar;
    myOptionsList.setCellRenderer(new DefaultListCellRenderer() {
      public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
        final Component rendererComponent = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
        if (value instanceof SeverityRegistrar.SeverityBasedTextAttributes) {
          setText(((SeverityRegistrar.SeverityBasedTextAttributes)value).getSeverity().toString());
        }
        return rendererComponent;
      }
    });
    myOptionsList.addListSelectionListener(new ListSelectionListener() {
      public void valueChanged(ListSelectionEvent e) {
        final ListModel model = myOptionsList.getModel();
        if (myCurrentSelection != -1 && myCurrentSelection < model.getSize()) {
          processListValueChanged((SeverityRegistrar.SeverityBasedTextAttributes)model.getElementAt(myCurrentSelection), true);
        }
        final int index = myOptionsList.getSelectedIndex();
        if (index == -1) {
          myCurrentSelection = index;
        } else if (myCurrentSelection != index) {
          final SeverityRegistrar.SeverityBasedTextAttributes highlightInfo = (SeverityRegistrar.SeverityBasedTextAttributes)myOptionsList.getSelectedValue();
          processListValueChanged(highlightInfo, false);
          myCard.show(myRightPanel, isDefaultSetting(highlightInfo.getType()) ? DEFAULT : EDITABLE);
          myCurrentSelection = index;
        }
      }
    });
    myOptionsList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    JScrollPane scrollPane = new JScrollPane(myOptionsList);
    scrollPane.setPreferredSize(new Dimension(230, 60));
    JPanel leftPanel = new JPanel(new BorderLayout());
    leftPanel.add(scrollPane, BorderLayout.CENTER);
    leftPanel.add(createListToolbar(), BorderLayout.NORTH);
    myPanel = new JPanel(new BorderLayout());
    myPanel.add(leftPanel, BorderLayout.WEST);
    myCard = new CardLayout();
    myRightPanel = new JPanel(myCard);
    final JPanel disabled = new JPanel(new BorderLayout());
    disabled.add(new JLabel(InspectionsBundle.message("severities.default.settings.message"), SwingConstants.CENTER), BorderLayout.CENTER);
    myRightPanel.add(DEFAULT, disabled);
    myRightPanel.add(EDITABLE, myOptionsPanel);
    myCard.show(myRightPanel, EDITABLE);
    myPanel.add(myRightPanel, BorderLayout.CENTER);
    fillList(severity);
    init();
    setTitle(InspectionsBundle.message("severities.editor.dialog.title"));
  }

  private void fillList(final HighlightSeverity severity) {
    DefaultListModel model = new DefaultListModel();
    model.removeAllElements();
    final List<HighlightInfoType.HighlightInfoTypeImpl> infoTypes = new ArrayList<HighlightInfoType.HighlightInfoTypeImpl>();
    infoTypes.addAll(mySeverityRegistrar.getRegisteredHighlightingInfoTypes());
    infoTypes.add((HighlightInfoType.HighlightInfoTypeImpl)HighlightInfoType.ERROR);
    infoTypes.add((HighlightInfoType.HighlightInfoTypeImpl)HighlightInfoType.WARNING);
    infoTypes.add((HighlightInfoType.HighlightInfoTypeImpl)HighlightInfoType.INFO);
    infoTypes.add((HighlightInfoType.HighlightInfoTypeImpl)HighlightInfoType.GENERIC_WARNINGS_OR_ERRORS_FROM_SERVER);
    Collections.sort(infoTypes, new Comparator<HighlightInfoType.HighlightInfoTypeImpl>() {
      public int compare(final HighlightInfoType.HighlightInfoTypeImpl o1, final HighlightInfoType.HighlightInfoTypeImpl o2) {
        return mySeverityRegistrar.compare(o1.getSeverity(null), o2.getSeverity(null));
      }
    });
    final EditorColorsScheme scheme = EditorColorsManager.getInstance().getGlobalScheme();
    SeverityRegistrar.SeverityBasedTextAttributes preselection = null;
    for (HighlightInfoType.HighlightInfoTypeImpl type : infoTypes) {
      final SeverityRegistrar.SeverityBasedTextAttributes typeWithAtrributesDescription =
        new SeverityRegistrar.SeverityBasedTextAttributes(scheme.getAttributes(type.getAttributesKey()), type);
      model.addElement(typeWithAtrributesDescription);
      if (type.getSeverity(null).equals(severity)) {
        preselection = typeWithAtrributesDescription;
      }
    }
    myOptionsList.setModel(model);
    myOptionsList.setSelectedValue(preselection, true);
  }

  private void processListValueChanged(final SeverityRegistrar.SeverityBasedTextAttributes info, boolean apply) {
    if (apply) {
      final MyTextAttributesDescription description = new MyTextAttributesDescription(info.getType().toString(),
                                                                                      null,
                                                                                      new TextAttributes(),
                                                                                      info.getType().getAttributesKey());
      myOptionsPanel.apply(description, null);
      @NonNls Element textAttributes = new Element("temp");
      try {
        description.getTextAttributes().writeExternal(textAttributes);
        info.getAttributes().readExternal(textAttributes);
      }
      catch (Exception e) {
        LOG.error(e);
      }

    }
    else {
      final MyTextAttributesDescription description = new MyTextAttributesDescription(info.getType().toString(),
                                                                                      null,
                                                                                      info.getAttributes(),
                                                                                      info.getType().getAttributesKey());
      @NonNls Element textAttributes = new Element("temp");
      try {
        info.getAttributes().writeExternal(textAttributes);
        description.getTextAttributes().readExternal(textAttributes);
      }
      catch (Exception e) {
        LOG.error(e);
      }
      myOptionsPanel.reset(description);
    }
  }

  private JComponent createListToolbar() {
    DefaultActionGroup group = new DefaultActionGroup();
    final ReorderableListController<SeverityRegistrar.SeverityBasedTextAttributes> controller =
      ReorderableListController.create(myOptionsList, group);
    controller.addAddAction(IdeBundle.message("action.add"), new Factory<SeverityRegistrar.SeverityBasedTextAttributes>() {
      public SeverityRegistrar.SeverityBasedTextAttributes create() {
        final String name = Messages.showInputDialog(myPanel, InspectionsBundle.message("highlight.severity.create.dialog.name.label"), InspectionsBundle.message("highlight.severity.create.dialog.title"), Messages.getQuestionIcon());
        if (name == null) return null;
        final TextAttributes textAttributes = CodeInsightColors.WARNINGS_ATTRIBUTES.getDefaultAttributes();
        HighlightInfoType.HighlightInfoTypeImpl info = new HighlightInfoType.HighlightInfoTypeImpl(new HighlightSeverity(name, 50),
                                                                                                   TextAttributesKey.createTextAttributesKey(name));
        return new SeverityRegistrar.SeverityBasedTextAttributes(textAttributes.clone(), info);
      }
    }, true);
    final ReorderableListController<SeverityRegistrar.SeverityBasedTextAttributes>.RemoveActionDescription removeAction =
      controller.addRemoveAction(IdeBundle.message("action.remove"));
    removeAction.setEnableCondition(new Condition<SeverityRegistrar.SeverityBasedTextAttributes>() {
      public boolean value(final SeverityRegistrar.SeverityBasedTextAttributes pair) {
        final HighlightInfoType info = pair.getType();
        return info != null && !isDefaultSetting(info);
      }
    });
    controller.addAction(new MyMoveUpAction());
    controller.addAction(new MyMoveDownAction());
    ActionToolbar toolbar = ActionManager.getInstance().createActionToolbar(ActionPlaces.UNKNOWN, group, true);
    return toolbar.getComponent();
  }

  public JList getOptionsList() {
    return myOptionsList;
  }

  private static boolean isDefaultSetting(HighlightInfoType info) {
    HighlightSeverity severity = info.getSeverity(null);
    if (severity == HighlightSeverity.ERROR || severity == HighlightSeverity.WARNING || severity == HighlightSeverity.INFORMATION || severity == HighlightSeverity.INFO
        || severity == HighlightSeverity.GENERIC_SERVER_ERROR_OR_WARNING) {
      return true;
    }
    return false;
  }

  protected void doOKAction() {
    processListValueChanged((SeverityRegistrar.SeverityBasedTextAttributes)myOptionsList.getSelectedValue(), true);
    final Collection<HighlightInfoType.HighlightInfoTypeImpl> infoTypes =
      new HashSet<HighlightInfoType.HighlightInfoTypeImpl>(mySeverityRegistrar.getRegisteredHighlightingInfoTypes());
    Set<HighlightInfoType> currentTypes = new HashSet<HighlightInfoType>();
    final ListModel listModel = myOptionsList.getModel();
    final List<String> order = new ArrayList<String>();
    for (int i = 0; i < listModel.getSize(); i++) {
      final SeverityRegistrar.SeverityBasedTextAttributes info =
        (SeverityRegistrar.SeverityBasedTextAttributes)listModel.getElementAt(i);
      order.add(info.getSeverity().myName);
      if (!isDefaultSetting(info.getType())) {
        currentTypes.add(info.getType());
        final Color stripeColor = info.getAttributes().getErrorStripeColor();
        mySeverityRegistrar.registerSeverity(info, stripeColor != null ? stripeColor : LightColors.YELLOW);
      }
    }
    infoTypes.removeAll(currentTypes);
    for (HighlightInfoType.HighlightInfoTypeImpl info : infoTypes) {
      mySeverityRegistrar.unregisterSeverity(info.getSeverity(null));
    }
    mySeverityRegistrar.setOrder(order);
    super.doOKAction();
  }

  @Nullable
  protected JComponent createCenterPanel() {
    return myPanel;
  }

  public HighlightInfoType getSelectedType() {
    return ((SeverityRegistrar.SeverityBasedTextAttributes)myOptionsList.getSelectedValue()).getType();
  }

  private static class MyTextAttributesDescription extends TextAttributesDescription {
    public MyTextAttributesDescription(final String name, final String group, final TextAttributes attributes, final TextAttributesKey type) {
      super(name, group, attributes, type, null, null, null);
    }

    public void apply(EditorColorsScheme scheme) {

    }

    public boolean isErrorStripeEnabled() {
      return true;
    }


    public TextAttributes getTextAttributes() {
      return super.getTextAttributes();
    }
  }

  private class MyMoveUpAction extends AnAction {
    public MyMoveUpAction() {
      super(ExecutionBundle.message("move.up.action.name"), null, IconLoader.getIcon("/actions/moveUp.png"));
    }

    public void actionPerformed(final AnActionEvent e) {
      processListValueChanged((SeverityRegistrar.SeverityBasedTextAttributes)myOptionsList.getSelectedValue(), true);
      myCurrentSelection = -1;
      ListUtil.moveSelectedItemsUp(myOptionsList);
    }

    public void update(final AnActionEvent e) {
      boolean canMove = ListUtil.canMoveSelectedItemsUp(myOptionsList);
      SeverityRegistrar.SeverityBasedTextAttributes pair =
        (SeverityRegistrar.SeverityBasedTextAttributes)myOptionsList.getSelectedValue();
      if (pair != null && isDefaultSetting(pair.getType())) {
        final int newPosition = myOptionsList.getSelectedIndex() - 1;
        if (newPosition >0 ) {
          pair = (SeverityRegistrar.SeverityBasedTextAttributes)myOptionsList.getModel().getElementAt(newPosition);
          if (isDefaultSetting(pair.getType())) {
            canMove = false;
          }
        }
      }
      e.getPresentation().setEnabled(canMove);
    }
  }

  private class MyMoveDownAction extends AnAction {
    public MyMoveDownAction() {
      super(ExecutionBundle.message("move.down.action.name"), null, IconLoader.getIcon("/actions/moveDown.png"));
    }

    public void actionPerformed(final AnActionEvent e) {
      processListValueChanged((SeverityRegistrar.SeverityBasedTextAttributes)myOptionsList.getSelectedValue(), true);
      myCurrentSelection = -1;
      ListUtil.moveSelectedItemsDown(myOptionsList);
    }

    public void update(final AnActionEvent e) {
      boolean canMove = ListUtil.canMoveSelectedItemsDown(myOptionsList);
      SeverityRegistrar.SeverityBasedTextAttributes pair =
        (SeverityRegistrar.SeverityBasedTextAttributes)myOptionsList.getSelectedValue();
      if (pair != null && isDefaultSetting(pair.getType())) {
        final int newPosition = myOptionsList.getSelectedIndex() + 1;
        if (newPosition < myOptionsList.getModel().getSize()) {
          pair = (SeverityRegistrar.SeverityBasedTextAttributes)myOptionsList.getModel().getElementAt(newPosition);
          if (isDefaultSetting(pair.getType())) {
            canMove = false;
          }
        }
      }
      e.getPresentation().setEnabled(canMove);
    }
  }
}
