/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

package com.intellij.codeInspection.ex;

import com.intellij.application.options.colors.ColorAndFontDescriptionPanel;
import com.intellij.application.options.colors.ColorAndFontOptions;
import com.intellij.application.options.colors.InspectionColorSettingsPage;
import com.intellij.application.options.colors.TextAttributesDescription;
import com.intellij.application.options.editor.EditorOptionsProvider;
import com.intellij.codeInsight.daemon.impl.HighlightInfoType;
import com.intellij.codeInsight.daemon.impl.SeverityRegistrar;
import com.intellij.codeInspection.InspectionsBundle;
import com.intellij.execution.ExecutionBundle;
import com.intellij.ide.DataManager;
import com.intellij.ide.IdeBundle;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.colors.CodeInsightColors;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.options.newEditor.OptionsEditor;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.InputValidator;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Factory;
import com.intellij.openapi.util.IconLoader;
import com.intellij.ui.LightColors;
import com.intellij.ui.ListUtil;
import com.intellij.ui.ReorderableListController;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.JBScrollPane;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.*;
import java.util.List;

/**
 * User: anna
 * Date: 24-Feb-2006
 */
public class SeverityEditorDialog extends DialogWrapper {
  private final JPanel myPanel;

  private final JList myOptionsList = new JBList();
  private final ColorAndFontDescriptionPanel myOptionsPanel = new ColorAndFontDescriptionPanel();

  private SeverityRegistrar.SeverityBasedTextAttributes myCurrentSelection;
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInspection.ex.SeverityEditorDialog");
  private final SeverityRegistrar mySeverityRegistrar;
  private final CardLayout myCard;
  private final JPanel myRightPanel;
  @NonNls private static final String DEFAULT = "DEFAULT";
  @NonNls private static final String EDITABLE = "EDITABLE";

  public SeverityEditorDialog(final JComponent parent, final HighlightSeverity severity, final SeverityRegistrar severityRegistrar) {
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
        if (myCurrentSelection != null) {
          apply(myCurrentSelection);
        }
        myCurrentSelection = (SeverityRegistrar.SeverityBasedTextAttributes)myOptionsList.getSelectedValue();
        if (myCurrentSelection != null) {
          reset(myCurrentSelection);
          myCard.show(myRightPanel, mySeverityRegistrar.isDefaultSeverity(myCurrentSelection.getSeverity()) ? DEFAULT : EDITABLE);
        }
      }
    });
    myOptionsList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    JBScrollPane scrollPane = new JBScrollPane(myOptionsList);
    scrollPane.setPreferredSize(new Dimension(230, 60));
    JPanel leftPanel = new JPanel(new BorderLayout());
    leftPanel.add(scrollPane, BorderLayout.CENTER);
    leftPanel.add(createListToolbar(), BorderLayout.NORTH);
    myPanel = new JPanel(new BorderLayout());
    myPanel.add(leftPanel, BorderLayout.WEST);
    myCard = new CardLayout();
    myRightPanel = new JPanel(myCard);
    final JPanel disabled = new JPanel(new GridBagLayout());
    final JButton button = new JButton(InspectionsBundle.message("severities.default.settings.message"));
    button.addActionListener(new ActionListener(){
      public void actionPerformed(final ActionEvent e) {
        final String toConfigure = getSelectedType().getSeverity(null).myName;
        doOKAction();
        myOptionsList.clearSelection();
        ColorAndFontOptions colorAndFontOptions = null;
        for (EditorOptionsProvider provider : Extensions.getExtensions(EditorOptionsProvider.EP_NAME)) {
          if (provider.getClass().isAssignableFrom(ColorAndFontOptions.class)) {
            colorAndFontOptions = (ColorAndFontOptions)provider;
            break;
          }
        }
        assert colorAndFontOptions != null;
        final SearchableConfigurable javaPage = colorAndFontOptions.findSubConfigurable(InspectionColorSettingsPage.class);
        LOG.assertTrue(javaPage != null);
        final OptionsEditor optionsEditor = OptionsEditor.KEY.getData(DataManager.getInstance().getDataContext());
        if (optionsEditor != null) {
          optionsEditor.select(javaPage).doWhenDone(new Runnable(){
            public void run() {
              final Runnable runnable = javaPage.enableSearch(toConfigure);
              if (runnable != null) {
                SwingUtilities.invokeLater(runnable);
              }
            }
          });
        } else {
          ShowSettingsUtil.getInstance().editConfigurable(PlatformDataKeys.PROJECT.getData(DataManager.getInstance().getDataContext()), javaPage);
        }
      }
    });
    disabled.add(button, new GridBagConstraints(0,0,1,1,0,0, GridBagConstraints.CENTER, GridBagConstraints.NONE, new Insets(0,0,0,0),0,0));
    myRightPanel.add(DEFAULT, disabled);
    myRightPanel.add(EDITABLE, myOptionsPanel);
    myCard.show(myRightPanel, EDITABLE);
    myPanel.add(myRightPanel, BorderLayout.CENTER);
    fillList(severity);
    init();
    setTitle(InspectionsBundle.message("severities.editor.dialog.title"));
    reset((SeverityRegistrar.SeverityBasedTextAttributes)myOptionsList.getSelectedValue());
  }

  private void fillList(final HighlightSeverity severity) {
    DefaultListModel model = new DefaultListModel();
    model.removeAllElements();
    final List<SeverityRegistrar.SeverityBasedTextAttributes> infoTypes = new ArrayList<SeverityRegistrar.SeverityBasedTextAttributes>();
    infoTypes.addAll(mySeverityRegistrar.getRegisteredHighlightingInfoTypes());
    Collections.sort(infoTypes, new Comparator<SeverityRegistrar.SeverityBasedTextAttributes>() {
      public int compare(SeverityRegistrar.SeverityBasedTextAttributes attributes1, SeverityRegistrar.SeverityBasedTextAttributes attributes2) {
        return - mySeverityRegistrar.compare(attributes1.getSeverity(), attributes2.getSeverity());
      }
    });
    SeverityRegistrar.SeverityBasedTextAttributes preselection = null;
    for (SeverityRegistrar.SeverityBasedTextAttributes type : infoTypes) {
      model.addElement(type);
      if (type.getSeverity().equals(severity)) {
        preselection = type;
      }
    }
    myOptionsList.setModel(model);
    myOptionsList.setSelectedValue(preselection, true);
  }


  private void apply(SeverityRegistrar.SeverityBasedTextAttributes info) {
    final MyTextAttributesDescription description =
      new MyTextAttributesDescription(info.getType().toString(), null, new TextAttributes(), info.getType().getAttributesKey());
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

  private void reset(SeverityRegistrar.SeverityBasedTextAttributes info) {
    final MyTextAttributesDescription description =
      new MyTextAttributesDescription(info.getType().toString(), null, info.getAttributes(), info.getType().getAttributesKey());
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

  private JComponent createListToolbar() {
    DefaultActionGroup group = new DefaultActionGroup();
    final ReorderableListController<SeverityRegistrar.SeverityBasedTextAttributes> controller =
      ReorderableListController.create(myOptionsList, group);
    controller.addAddAction(IdeBundle.message("action.add"), new Factory<SeverityRegistrar.SeverityBasedTextAttributes>() {
      @Nullable
      public SeverityRegistrar.SeverityBasedTextAttributes create() {
        final String name = Messages.showInputDialog(myPanel, InspectionsBundle.message("highlight.severity.create.dialog.name.label"),
                                                     InspectionsBundle.message("highlight.severity.create.dialog.title"), Messages.getQuestionIcon(),
                                                     "", new InputValidator() {
          public boolean checkInput(final String inputString) {
            final ListModel listModel = myOptionsList.getModel();
            for (int i = 0; i < listModel.getSize(); i++) {
              final String severityName = ((SeverityRegistrar.SeverityBasedTextAttributes)listModel.getElementAt(i)).getSeverity().myName;
              if (Comparing.strEqual(severityName, inputString)) return false;
            }
            return true;
          }

          public boolean canClose(final String inputString) {
            return checkInput(inputString);
          }
        });
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
        return !mySeverityRegistrar.isDefaultSeverity(pair.getSeverity());
      }
    });
    controller.addAction(new MyMoveUpAction());
    controller.addAction(new MyMoveDownAction());
    ActionToolbar toolbar = ActionManager.getInstance().createActionToolbar(ActionPlaces.UNKNOWN, group, true);
    return toolbar.getComponent();
  }


  protected void doOKAction() {
    apply((SeverityRegistrar.SeverityBasedTextAttributes)myOptionsList.getSelectedValue());
    final Collection<SeverityRegistrar.SeverityBasedTextAttributes> infoTypes =
      new HashSet<SeverityRegistrar.SeverityBasedTextAttributes>(mySeverityRegistrar.getRegisteredHighlightingInfoTypes());
    final ListModel listModel = myOptionsList.getModel();
    final List<String> order = new ArrayList<String>();
    for (int i = listModel.getSize() - 1; i >= 0 ;i--) {
      final SeverityRegistrar.SeverityBasedTextAttributes info =
        (SeverityRegistrar.SeverityBasedTextAttributes)listModel.getElementAt(i);
      order.add(info.getSeverity().myName);
      if (!mySeverityRegistrar.isDefaultSeverity(info.getSeverity())) {
        infoTypes.remove(info);
        final Color stripeColor = info.getAttributes().getErrorStripeColor();
        mySeverityRegistrar.registerSeverity(info, stripeColor != null ? stripeColor : LightColors.YELLOW);
      }
    }
    for (SeverityRegistrar.SeverityBasedTextAttributes info : infoTypes) {
      mySeverityRegistrar.unregisterSeverity(info.getSeverity());
    }
    mySeverityRegistrar.setOrder(order);
    super.doOKAction();
  }

  @Nullable
  protected JComponent createCenterPanel() {
    return myPanel;
  }

  @Nullable
  public HighlightInfoType getSelectedType() {
    final SeverityRegistrar.SeverityBasedTextAttributes selection =
      (SeverityRegistrar.SeverityBasedTextAttributes)myOptionsList.getSelectedValue();
    return selection != null ? selection.getType() : null;
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
      apply(myCurrentSelection);
      ListUtil.moveSelectedItemsUp(myOptionsList);
    }

    public void update(final AnActionEvent e) {
      boolean canMove = ListUtil.canMoveSelectedItemsUp(myOptionsList);
      if (canMove) {
        SeverityRegistrar.SeverityBasedTextAttributes pair =
          (SeverityRegistrar.SeverityBasedTextAttributes)myOptionsList.getSelectedValue();
        if (pair != null && mySeverityRegistrar.isDefaultSeverity(pair.getSeverity())) {
          final int newPosition = myOptionsList.getSelectedIndex() - 1;
          pair = (SeverityRegistrar.SeverityBasedTextAttributes)myOptionsList.getModel().getElementAt(newPosition);
          if (mySeverityRegistrar.isDefaultSeverity(pair.getSeverity())) {
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
      apply(myCurrentSelection);
      ListUtil.moveSelectedItemsDown(myOptionsList);
    }

    public void update(final AnActionEvent e) {
      boolean canMove = ListUtil.canMoveSelectedItemsDown(myOptionsList);
      if (canMove) {
        SeverityRegistrar.SeverityBasedTextAttributes pair =
          (SeverityRegistrar.SeverityBasedTextAttributes)myOptionsList.getSelectedValue();
        if (pair != null && mySeverityRegistrar.isDefaultSeverity(pair.getSeverity())) {
          final int newPosition = myOptionsList.getSelectedIndex() + 1;
          pair = (SeverityRegistrar.SeverityBasedTextAttributes)myOptionsList.getModel().getElementAt(newPosition);
          if (mySeverityRegistrar.isDefaultSeverity(pair.getSeverity())) {
            canMove = false;
          }
        }
      }
      e.getPresentation().setEnabled(canMove);
    }
  }
}
