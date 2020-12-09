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

package com.intellij.ui.debugger.extensions;

import com.intellij.icons.AllIcons;
import com.intellij.ide.IdeBundle;
import com.intellij.lang.LangBundle;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.ui.Splitter;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.ui.*;
import com.intellij.ui.components.JBList;
import com.intellij.ui.debugger.UiDebuggerExtension;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.text.DefaultCaret;
import java.awt.*;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;

public class FocusDebugger implements UiDebuggerExtension, PropertyChangeListener, ListSelectionListener  {

  private JComponent myComponent;

  private JList<FocusElement> myLog;
  private DefaultListModel<FocusElement> myLogModel;
  private JEditorPane myAllocation;

  @Override
  public JComponent getComponent() {
    if (myComponent == null) {
      myComponent = init();
    }

    return myComponent;
  }

  private JComponent init() {
    final JPanel result = new JPanel(new BorderLayout());

    myLogModel = new DefaultListModel<>();
    myLog = new JBList<>(myLogModel);
    myLog.setCellRenderer(new FocusElementRenderer());


    myAllocation = new JEditorPane();
    final DefaultCaret caret = new DefaultCaret();
    myAllocation.setCaret(caret);
    caret.setUpdatePolicy(DefaultCaret.NEVER_UPDATE);
    myAllocation.setEditable(false);


    final Splitter splitter = new Splitter(true);
    splitter.setFirstComponent(ScrollPaneFactory.createScrollPane(myLog));
    splitter.setSecondComponent(ScrollPaneFactory.createScrollPane(myAllocation));

    myLog.addListSelectionListener(this);

    KeyboardFocusManager.getCurrentKeyboardFocusManager().addPropertyChangeListener(this);

    result.add(splitter, BorderLayout.CENTER);


    final DefaultActionGroup group = new DefaultActionGroup();
    group.add(new ClearAction());

    result.add(ActionManager.getInstance().createActionToolbar("FocusDbg", group, true).getComponent(), BorderLayout.NORTH);

    return result;
  }

  class ClearAction extends AnAction {
  ClearAction() {
    super(IdeBundle.messagePointer("action.AnAction.text.clear"),
          IdeBundle.messagePointer("action.AnAction.description.clear"), AllIcons.Actions.Close);
  }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      myLogModel.clear();
    }
  }

  @Override
  public void valueChanged(ListSelectionEvent e) {
    if (myLog.getSelectedIndex() == -1) {
      myAllocation.setText(null);
    } else {
      FocusElement element = myLog.getSelectedValue();
      final StringWriter s = new StringWriter();
      final PrintWriter writer = new PrintWriter(s);
      element.getAllocation().printStackTrace(writer);

      @NlsSafe String trace = s.toString();
      myAllocation.setText(trace);
    }
  }

  private boolean isInsideDebuggerDialog(Component c) {
    final Window debuggerWindow = SwingUtilities.getWindowAncestor(myComponent);
    if (!(debuggerWindow instanceof Dialog)) return false;

    return c == debuggerWindow || SwingUtilities.getWindowAncestor(c) == debuggerWindow;
  }

  @Override
  public void propertyChange(PropertyChangeEvent evt) {
    final Object newValue = evt.getNewValue();
    final Object oldValue = evt.getOldValue();

    boolean affectsDebugger = false;

    if (newValue instanceof Component && isInsideDebuggerDialog((Component)newValue)) {
      affectsDebugger = true;
    }

    if (oldValue instanceof Component && isInsideDebuggerDialog((Component)oldValue)) {
      affectsDebugger = true;
    }



    SimpleColoredText text = new SimpleColoredText();
    @NlsSafe String propertyName = evt.getPropertyName();
    text.append(propertyName, maybeGrayOut(new SimpleTextAttributes(SimpleTextAttributes.STYLE_UNDERLINE, null), affectsDebugger));
    text.append(" " + IdeBundle.message("focus.debugger.label.newvalue"), maybeGrayOut(SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES, affectsDebugger));
    text.append(String.valueOf(evt.getNewValue()), maybeGrayOut(SimpleTextAttributes.REGULAR_ATTRIBUTES, affectsDebugger));
    text.append(" " + IdeBundle.message("focus.debugger.label.oldvalue") + evt.getOldValue(), maybeGrayOut(SimpleTextAttributes.REGULAR_ATTRIBUTES, affectsDebugger));


    myLogModel.addElement(new FocusElement(text, new Throwable()));
    SwingUtilities.invokeLater(() -> {
      if (myLog != null && myLog.isShowing()) {
        final int h = myLog.getFixedCellHeight();
        myLog.scrollRectToVisible(new Rectangle(0, myLog.getPreferredSize().height - h, myLog.getWidth(), h));
        if (myLog.getModel().getSize() > 0) {
          myLog.setSelectedIndex(myLog.getModel().getSize() - 1);
        }
      }
    });
  }

  private static SimpleTextAttributes maybeGrayOut(SimpleTextAttributes attr, boolean greyOut) {
    return greyOut ? attr.derive(attr.getStyle(), JBColor.GRAY, attr.getBgColor(), attr.getWaveColor()) : attr;
  }

  static class FocusElementRenderer extends ColoredListCellRenderer<FocusElement> {
    @Override
    protected void customizeCellRenderer(@NotNull JList<? extends FocusElement> list,
                                         FocusElement value,
                                         int index,
                                         boolean selected,
                                         boolean hasFocus) {
      clear();
      final SimpleColoredText text = value.getText();
      final ArrayList<@Nls String> strings = text.getTexts();
      final ArrayList<SimpleTextAttributes> attributes = value.getText().getAttributes();
      for (int i = 0; i < strings.size(); i++) {
        append(strings.get(i), attributes.get(i));
      }
    }
  }

  static class FocusElement {
    private final SimpleColoredText myText;
    private final Throwable myAllocation;

    FocusElement(SimpleColoredText text, Throwable allocation) {
      myText = text;
      myAllocation = allocation;
    }

    public SimpleColoredText getText() {
      return myText;
    }

    public Throwable getAllocation() {
      return myAllocation;
    }
  }

  @Override
  public @NlsContexts.TabTitle String getName() {
    return LangBundle.message("tab.title.focus");
  }

  @Override
  public void disposeUiResources() {
    myComponent = null;
    KeyboardFocusManager.getCurrentKeyboardFocusManager().removePropertyChangeListener(this);
  }
}
