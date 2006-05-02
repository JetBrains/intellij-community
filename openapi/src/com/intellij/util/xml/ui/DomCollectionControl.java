/*
 * Copyright (c) 2005 Your Corporation. All Rights Reserved.
 */
package com.intellij.util.xml.ui;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.project.Project;
import com.intellij.util.EventDispatcher;
import com.intellij.util.ui.ColumnInfo;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomUtil;
import com.intellij.util.xml.highlighting.DomCollectionProblemDescriptor;
import com.intellij.util.xml.highlighting.DomElementAnnotationsManager;
import com.intellij.util.xml.highlighting.DomElementProblemDescriptor;
import com.intellij.util.xml.reflect.DomCollectionChildDescription;
import com.intellij.util.xml.ui.actions.DefaultAddAction;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * @author peter
 */
public class DomCollectionControl<T extends DomElement> implements DomUIControl {
  private final EventDispatcher<CommitListener> myDispatcher = EventDispatcher.create(CommitListener.class);
  private DomCollectionPanel myCollectionPanel;

  private final List<T> myData = new ArrayList<T>();
  private final DomElement myParentDomElement;
  private final DomCollectionChildDescription myChildDescription;
  private ColumnInfo<T, ?>[] myColumnInfos;
  private boolean myEditable = false;

  public DomCollectionControl(DomElement parentElement,
                              DomCollectionChildDescription description,
                              final boolean editable,
                              ColumnInfo<T, ?>... columnInfos) {
    myChildDescription = description;
    myParentDomElement = parentElement;
    myColumnInfos = columnInfos;
    myEditable = editable;
  }

  public DomCollectionControl(DomElement parentElement,
                              @NonNls String subTagName,
                              final boolean editable,
                              ColumnInfo<T, ?>... columnInfos) {
    this(parentElement, parentElement.getGenericInfo().getCollectionChildDescription(subTagName), editable, columnInfos);
  }

  public DomCollectionControl(DomElement parentElement, DomCollectionChildDescription description) {
    myChildDescription = description;
    myParentDomElement = parentElement;
  }

  public DomCollectionControl(DomElement parentElement, @NonNls String subTagName) {
    this(parentElement, parentElement.getGenericInfo().getCollectionChildDescription(subTagName));
  }

  public boolean isEditable() {
    return myEditable;
  }

  public void bind(JComponent component) {
    assert component instanceof DomCollectionPanel;

    initialize((DomCollectionPanel)component);
  }

  public void addCommitListener(CommitListener listener) {
    myDispatcher.addListener(listener);
  }

  public void removeCommitListener(CommitListener listener) {
    myDispatcher.removeListener(listener);
  }


  public boolean canNavigate(DomElement element) {
    final Class<DomElement> aClass = (Class<DomElement>)DomUtil.getRawType(myChildDescription.getType());

    final DomElement domElement = element.getParentOfType(aClass, false);

    return domElement != null && myData.contains(domElement);
  }

  public void navigate(DomElement element) {
    final Class<DomElement> aClass = (Class<DomElement>)DomUtil.getRawType(myChildDescription.getType());
    final DomElement domElement = element.getParentOfType(aClass, false);

    int index = myData.indexOf(domElement);
    if (index < 0) index = 0;

    myCollectionPanel.getTable().setRowSelectionInterval(index, index);
  }

  protected void initialize(final DomCollectionPanel boundComponent) {
    if (boundComponent == null) {
      myCollectionPanel = new DomCollectionPanel();
    }
    else {
      myCollectionPanel = boundComponent;
    }
    myCollectionPanel.setControl(this);
    myCollectionPanel.initializeTable();
    myCollectionPanel.setColumnInfos(createColumnInfos(myParentDomElement));
    reset();
  }

  protected ColumnInfo[] createColumnInfos(DomElement parent) {
    return myColumnInfos;
  }

  public final void columnsChanged() {
    myCollectionPanel.setColumnInfos(createColumnInfos(myParentDomElement));
  }

  protected final void doEdit() {
    doEdit(myData.get(myCollectionPanel.getTable().getSelectedRow()));
  }

  protected void doEdit(final T t) {
    final DomEditorManager manager = getDomEditorManager(this);
    if (manager != null) {
      manager.openDomElementEditor(t);
    }
  }

  protected void doRemove(final List<T> toDelete) {
    new WriteCommandAction(getProject()) {
      protected void run(Result result) throws Throwable {
        for (final T t : toDelete) {
          if (t.isValid()) {
            t.undefine();
          }
        }
      }
    }.execute();
  }

  protected final void doRemove() {
    ApplicationManager.getApplication().invokeLater(new Runnable() {
      public void run() {
        final int[] selected = myCollectionPanel.getTable().getSelectedRows();
        if (selected == null || selected.length == 0) return;
        final List<T> selectedElements = new ArrayList<T>(selected.length);
        for (final int i : selected) {
          selectedElements.add(myData.get(i));
        }

        doRemove(selectedElements);
        reset();
        int selection = selected[0];
        if (selection >= myData.size()) {
          selection = myData.size() - 1;
        }
        if (selection >= 0) {
          myCollectionPanel.getTable().setRowSelectionInterval(selection, selection);
        }
      }
    });
  }

  protected static void performWriteCommandAction(final WriteCommandAction writeCommandAction) {
    writeCommandAction.execute();
  }

  public void commit() {
    final CommitListener listener = myDispatcher.getMulticaster();
    listener.beforeCommit(this);
    listener.afterCommit(this);
    validate();
  }

  private void validate() {
    final List<DomElementProblemDescriptor> list = DomElementAnnotationsManager.getInstance().getProblems(getDomElement());
    final List<String> messages = new ArrayList<String>();
    for (final DomElementProblemDescriptor descriptor : list) {
      if (descriptor instanceof DomCollectionProblemDescriptor
          && myChildDescription.equals(((DomCollectionProblemDescriptor)descriptor).getChildDescription())) {
        messages.add(descriptor.getDescriptionTemplate());
      }
    }
    myCollectionPanel.setErrorMessages(messages.toArray(new String[messages.size()]));
  }

  public void dispose() {
    if (myCollectionPanel != null) {
      myCollectionPanel.dispose();
    }
  }

  protected final Project getProject() {
    return myParentDomElement.getManager().getProject();
  }

  public JComponent getComponent() {
    if (myCollectionPanel == null) initialize(null);

    return myCollectionPanel;
  }

  public final DomCollectionChildDescription getChildDescription() {
    return myChildDescription;
  }

  public final List<? extends T> getCollectionElements() {
    return (List<? extends T>)myChildDescription.getValues(myParentDomElement);
  }

  public final DomElement getDomElement() {
    return myParentDomElement;
  }

  public final void reset() {
    final List<T> newData = getData();
    if (!myData.equals(newData)) {
      myData.clear();
      myData.addAll(newData);
      if (myCollectionPanel != null) {
        myCollectionPanel.setItems(myData);
      }
    }
    validate();
  }

  public List<T> getData() {
    return (List<T>)myChildDescription.getValues(myParentDomElement);
  }

  @Nullable
  protected AnAction[] createAdditionActions() {
    return null;
  }

  protected DefaultAddAction createDefaultAction(final String name, final Icon icon, final Class s) {
    return new ControlAddAction(name, "", icon) {
      protected Class getElementClass() {
        return s;
      }
    };
  }

  protected final Class<? extends T> getCollectionElementClass() {
    return (Class<? extends T>)DomUtil.getRawType(myChildDescription.getType());
  }


  @Nullable
  private static DomEditorManager getDomEditorManager(DomUIControl control) {
    JComponent component = control.getComponent();
    while (component != null && !(component instanceof DomEditorManager)) {
      final Container parent = component.getParent();
      if (!(parent instanceof JComponent)) {
        return null;
      }
      component = (JComponent)parent;
    }
    return (DomEditorManager)component;
  }

  public class ControlAddAction extends DefaultAddAction<T> {

    public ControlAddAction() {
    }

    public ControlAddAction(final String text) {
      super(text);
    }

    public ControlAddAction(final String text, final String description, final Icon icon) {
      super(text, description, icon);
    }

    protected final DomCollectionChildDescription getDomCollectionChildDescription() {
      return myChildDescription;
    }

    protected final DomElement getParentDomElement() {
      return myParentDomElement;
    }

    protected void afterAddition(final JTable table, final int rowIndex) {
      table.setRowSelectionInterval(rowIndex, rowIndex);
    }

    protected final void afterAddition(final AnActionEvent e, final DomElement newElement) {
      if (newElement != null) {
        reset();
        afterAddition(myCollectionPanel.getTable(), myData.size() - 1);
      }
    }
  }


}
