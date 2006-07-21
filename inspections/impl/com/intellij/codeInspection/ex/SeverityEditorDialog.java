/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.codeInspection.ex;

import com.intellij.application.options.colors.ColorAndFontDescriptionPanel;
import com.intellij.application.options.colors.ColorAndFontOptions;
import com.intellij.application.options.colors.TextAttributesDescription;
import com.intellij.codeInsight.daemon.impl.HighlightInfoType;
import com.intellij.codeInsight.daemon.impl.SeverityRegistrar;
import com.intellij.codeInspection.InspectionsBundle;
import com.intellij.execution.ExecutionBundle;
import com.intellij.ide.IdeBundle;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.colors.CodeInsightColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.colors.impl.DefaultColorsScheme;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.options.ShowSettingsUtil;
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
import java.io.IOException;
import java.util.*;

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

  public SeverityEditorDialog(JComponent parent) {
    super(parent, true);
    fillList();
    myOptionsList.setCellRenderer(new DefaultListCellRenderer() {
      public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
        final Component rendererComponent = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
        if (value instanceof MyHighlightInfoTypeWithAtrributesDescription) {
          setText(((MyHighlightInfoTypeWithAtrributesDescription)value).getSeverity().toString());
        }
        return rendererComponent;
      }
    });
    myOptionsList.addListSelectionListener(new ListSelectionListener() {
      public void valueChanged(ListSelectionEvent e) {
        final ListModel model = myOptionsList.getModel();
        if (myCurrentSelection != -1 && myCurrentSelection < model.getSize()) {
          processListValueChanged((MyHighlightInfoTypeWithAtrributesDescription)model.getElementAt(myCurrentSelection), true);
        }
        final int index = myOptionsList.getSelectedIndex();
        if (index == -1) {
          myCurrentSelection = index;
        } else if (myCurrentSelection != index) {
          processListValueChanged((MyHighlightInfoTypeWithAtrributesDescription)myOptionsList.getSelectedValue(), false);
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
    myPanel.add(myOptionsPanel, BorderLayout.CENTER);
    myOptionsList.setSelectedIndex(0);
    init();
    setTitle(InspectionsBundle.message("severities.editor.dialog.title"));
  }

  private void fillList() {
    DefaultListModel model = new DefaultListModel();
    fillModel(model);
    myOptionsList.setModel(model);
  }

  public static void fillModel(final DefaultListModel model) {
    model.removeAllElements();
    final TreeSet<HighlightInfoType.HighlightInfoTypeImpl> infoTypes =
      new TreeSet<HighlightInfoType.HighlightInfoTypeImpl>(new Comparator<HighlightInfoType.HighlightInfoTypeImpl>() {
        public int compare(final HighlightInfoType.HighlightInfoTypeImpl o1, final HighlightInfoType.HighlightInfoTypeImpl o2) {
          return - o1.getSeverity(null).compareTo(o2.getSeverity(null));
        }
      });
    infoTypes.addAll(SeverityRegistrar.getRegisteredHighlightingInfoTypes());
    infoTypes.add((HighlightInfoType.HighlightInfoTypeImpl)HighlightInfoType.ERROR);
    infoTypes.add((HighlightInfoType.HighlightInfoTypeImpl)HighlightInfoType.WARNING);
    infoTypes.add((HighlightInfoType.HighlightInfoTypeImpl)HighlightInfoType.INFO);
    final EditorColorsScheme scheme = EditorColorsManager.getInstance().getGlobalScheme();
    for (HighlightInfoType.HighlightInfoTypeImpl type : infoTypes) {
      model.addElement(new MyHighlightInfoTypeWithAtrributesDescription(scheme.getAttributes(type.getAttributesKey()), type));
    }
  }

  private void processListValueChanged(final MyHighlightInfoTypeWithAtrributesDescription info, boolean apply) {
    if (apply) {
      final MyTextAttributesDescription description = new MyTextAttributesDescription(info.getHighlightInfoType().toString(),
                                                                                      null,
                                                                                      new TextAttributes(),
                                                                                      info.getHighlightInfoType().getAttributesKey());
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
      final MyTextAttributesDescription description = new MyTextAttributesDescription(info.getHighlightInfoType().toString(),
                                                                                      null,
                                                                                      info.getAttributes(),
                                                                                      info.getHighlightInfoType().getAttributesKey());
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
    final ReorderableListController<MyHighlightInfoTypeWithAtrributesDescription> controller =
      ReorderableListController.create(myOptionsList, group);
    controller.addAddAction(IdeBundle.message("action.add"), new Factory<MyHighlightInfoTypeWithAtrributesDescription>() {
      public MyHighlightInfoTypeWithAtrributesDescription create() {
        final String name = Messages.showInputDialog(myPanel, InspectionsBundle.message("highlight.severity.create.dialog.name.label"), InspectionsBundle.message("highlight.severity.create.dialog.title"), Messages.getQuestionIcon());
        if (name == null) return null;
        final TextAttributes textAttributes = CodeInsightColors.WARNINGS_ATTRIBUTES.getDefaultAttributes();
        HighlightInfoType.HighlightInfoTypeImpl info = new HighlightInfoType.HighlightInfoTypeImpl(new HighlightSeverity(name, 0),
                                                                                                   TextAttributesKey.createTextAttributesKey(name));
        return new MyHighlightInfoTypeWithAtrributesDescription(textAttributes.clone(), info);
      }
    }, true);
    final ReorderableListController<MyHighlightInfoTypeWithAtrributesDescription>.RemoveActionDescription removeAction =
      controller.addRemoveAction(IdeBundle.message("action.remove"));
    removeAction.setEnableCondition(new Condition<MyHighlightInfoTypeWithAtrributesDescription>() {
      public boolean value(final MyHighlightInfoTypeWithAtrributesDescription pair) {
        final HighlightInfoType info = pair.getHighlightInfoType();
        return info != null && !isDefaultSetting(info);
      }
    });
    controller.addAction(new AnAction(ExecutionBundle.message("move.up.action.name"), null, IconLoader.getIcon("/actions/moveUp.png")) {
      public void actionPerformed(final AnActionEvent e) {
        processListValueChanged((MyHighlightInfoTypeWithAtrributesDescription)myOptionsList.getSelectedValue(), true);
        myCurrentSelection = -1;
        ListUtil.moveSelectedItemsUp(myOptionsList);
      }

      public void update(final AnActionEvent e) {
        boolean canMove = ListUtil.canMoveSelectedItemsUp(myOptionsList);
        MyHighlightInfoTypeWithAtrributesDescription pair =
          (MyHighlightInfoTypeWithAtrributesDescription)myOptionsList.getSelectedValue();
        if (pair != null) {
          if (pair.getSeverity() == HighlightSeverity.WARNING) {
            final int newPosition = myOptionsList.getSelectedIndex() - 1;
            if (newPosition >= 0) {
              pair = (MyHighlightInfoTypeWithAtrributesDescription)myOptionsList.getModel().getElementAt(newPosition);
              if (pair.getSeverity() == HighlightSeverity.ERROR) {
                canMove = false;
              }
            }
          } else if (pair.getSeverity() == HighlightSeverity.INFO) {
            final int newPosition = myOptionsList.getSelectedIndex() - 1;
            if (newPosition >= 0) {
              pair = (MyHighlightInfoTypeWithAtrributesDescription)myOptionsList.getModel().getElementAt(newPosition);
              if (pair.getSeverity() == HighlightSeverity.WARNING) {
                canMove = false;
              }
            }
          }
        }
        e.getPresentation().setEnabled(canMove);
      }
    });
    controller.addAction(new AnAction(ExecutionBundle.message("move.down.action.name"), null, IconLoader.getIcon("/actions/moveDown.png")) {
      public void actionPerformed(final AnActionEvent e) {
        processListValueChanged((MyHighlightInfoTypeWithAtrributesDescription)myOptionsList.getSelectedValue(), true);
        myCurrentSelection = -1;
        ListUtil.moveSelectedItemsDown(myOptionsList);
      }

      public void update(final AnActionEvent e) {
        boolean canMove = ListUtil.canMoveSelectedItemsDown(myOptionsList);
        MyHighlightInfoTypeWithAtrributesDescription pair =
          (MyHighlightInfoTypeWithAtrributesDescription)myOptionsList.getSelectedValue();
        if (pair != null) {
          if (pair.getSeverity() == HighlightSeverity.ERROR) {
            final int newPosition = myOptionsList.getSelectedIndex() + 1;
            final ListModel model = myOptionsList.getModel();
            if (newPosition < model.getSize()) {
              pair = (MyHighlightInfoTypeWithAtrributesDescription)model.getElementAt(newPosition);
              if (pair.getSeverity() == HighlightSeverity.WARNING) {
                canMove = false;
              }
            }
          } else if (pair.getSeverity() == HighlightSeverity.WARNING) {
            final int newPosition = myOptionsList.getSelectedIndex() + 1;
            final ListModel model = myOptionsList.getModel();
            if (newPosition < model.getSize()) {
              pair = (MyHighlightInfoTypeWithAtrributesDescription)model.getElementAt(newPosition);
              if (pair.getSeverity() == HighlightSeverity.INFO) {
                canMove = false;
              }
            }
          }
        }
        e.getPresentation().setEnabled(canMove);
      }
    });
    ActionToolbar toolbar = ActionManager.getInstance().createActionToolbar(ActionPlaces.UNKNOWN, group, true);
    return toolbar.getComponent();
  }

  public JList getOptionsList() {
    return myOptionsList;
  }

  private static boolean isDefaultSetting(HighlightInfoType info) {
    HighlightSeverity severity = info.getSeverity(null);
    if (severity == HighlightSeverity.ERROR || severity == HighlightSeverity.WARNING || severity == HighlightSeverity.INFORMATION || severity == HighlightSeverity.INFO) {
      return true;
    }
    return false;
  }

  protected void doOKAction() {
    final EditorColorsManager editorColorsManager = EditorColorsManager.getInstance();
    final EditorColorsScheme colorsScheme = editorColorsManager.getGlobalScheme();
    if (colorsScheme instanceof DefaultColorsScheme) {
      final int res = Messages.showYesNoCancelDialog(myPanel, InspectionsBundle.message("highlight.severity.default.color.scheme.warning"), ApplicationBundle.message("title.cannot.modify.default.scheme"), Messages.getQuestionIcon());
      if (res == DialogWrapper.OK_EXIT_CODE){
        ShowSettingsUtil.getInstance().editConfigurable(myPanel, ApplicationManager.getApplication().getComponent(ColorAndFontOptions.class));
        return;
      } else if (res != DialogWrapper.CANCEL_EXIT_CODE) {
        return;
      }
    }
    processListValueChanged((MyHighlightInfoTypeWithAtrributesDescription)myOptionsList.getSelectedValue(), true);
    final Collection<HighlightInfoType.HighlightInfoTypeImpl> infoTypes =
      new HashSet<HighlightInfoType.HighlightInfoTypeImpl>(SeverityRegistrar.getRegisteredHighlightingInfoTypes());
    Set<HighlightInfoType.HighlightInfoTypeImpl> currentTypes = new HashSet<HighlightInfoType.HighlightInfoTypeImpl>();
    final ListModel listModel = myOptionsList.getModel();
    for (int i = 0; i < listModel.getSize(); i++) {
      final MyHighlightInfoTypeWithAtrributesDescription info =
        (MyHighlightInfoTypeWithAtrributesDescription)listModel.getElementAt(i);
      info.getSeverity().setVal((listModel.getSize() - i + 1) * 100); //last value from server
      if (!isDefaultSetting(info.getHighlightInfoType())) {
        currentTypes.add(info.getHighlightInfoType());
        final Color stripeColor = info.getAttributes().getErrorStripeColor();
        SeverityRegistrar.registerSeverity(info.getHighlightInfoType(), stripeColor != null ? stripeColor : LightColors.YELLOW);
      }
      colorsScheme.setAttributes(info.getHighlightInfoType().getAttributesKey(), info.getAttributes());
    }
    infoTypes.removeAll(currentTypes);
    for (HighlightInfoType.HighlightInfoTypeImpl info : infoTypes) {
      SeverityRegistrar.unregisterSeverity(info.getSeverity(null));
    }
    try {
      editorColorsManager.saveAllSchemes();
    }
    catch (IOException e) {
      LOG.error(e);
    }
    super.doOKAction();
  }

  @Nullable
  protected JComponent createCenterPanel() {
    return myPanel;
  }

  private static class MyHighlightInfoTypeWithAtrributesDescription {
    private TextAttributes myAttributes;
    private HighlightInfoType.HighlightInfoTypeImpl myHighlightInfoType;

    public MyHighlightInfoTypeWithAtrributesDescription(final TextAttributes attributes,
                                                        final HighlightInfoType.HighlightInfoTypeImpl highlightInfoType) {
      myAttributes = attributes;
      myHighlightInfoType = highlightInfoType;
    }


    public TextAttributes getAttributes() {
      return myAttributes;
    }

    public void setAttributes(final TextAttributes attributes) {
      myAttributes = attributes;
    }

    public HighlightInfoType.HighlightInfoTypeImpl getHighlightInfoType() {
      return myHighlightInfoType;
    }

    public void setHighlightInfoType(final HighlightInfoType.HighlightInfoTypeImpl highlightInfoType) {
      myHighlightInfoType = highlightInfoType;
    }

    public HighlightSeverity getSeverity(){
      return myHighlightInfoType.getSeverity(null);
    }
  }

  private static class MyTextAttributesDescription extends TextAttributesDescription {
    public MyTextAttributesDescription(final String name, final String group, final TextAttributes attributes, final TextAttributesKey type) {
      super(name, group, attributes, type, null);
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
}
