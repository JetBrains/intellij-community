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
package com.intellij.debugger.actions;

import com.intellij.debugger.engine.DebugProcessImpl;
import com.intellij.debugger.engine.DebuggerUtils;
import com.intellij.debugger.engine.events.DebuggerContextCommandImpl;
import com.intellij.debugger.impl.DebuggerContextImpl;
import com.intellij.debugger.impl.DebuggerSession;
import com.intellij.debugger.ui.impl.watch.DebuggerTree;
import com.intellij.debugger.ui.impl.watch.DebuggerTreeNodeImpl;
import com.intellij.debugger.ui.impl.watch.NodeDescriptorImpl;
import com.intellij.debugger.ui.impl.watch.ValueDescriptorImpl;
import com.intellij.debugger.ui.tree.ValueDescriptor;
import com.intellij.debugger.ui.tree.ValueMarkup;
import com.intellij.idea.ActionsBundle;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Ref;
import com.intellij.util.containers.HashMap;
import com.sun.jdi.*;

import javax.swing.*;
import java.awt.*;
import java.lang.reflect.InvocationTargetException;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/*
 * Class SetValueAction
 * @author Jeka
 */
public class MarkObjectAction extends DebuggerAction {
  private static final Logger LOG = Logger.getInstance("#com.intellij.debugger.actions.MarkObjectAction");
  private final String MARK_TEXT = ActionsBundle.message("action.Debugger.MarkObject.text");
  private final String UNMARK_TEXT = ActionsBundle.message("action.Debugger.MarkObject.unmark.text");

  public void actionPerformed(final AnActionEvent event) {
    final DebuggerTreeNodeImpl node = getSelectedNode(event.getDataContext());
    if (node == null) {
      return;
    }
    final NodeDescriptorImpl descriptor = node.getDescriptor();
    if (!(descriptor instanceof ValueDescriptorImpl)) {
      return;
    }
    
    final DebuggerTree tree = node.getTree();
    tree.saveState(node);
    
    final ValueDescriptorImpl valueDescriptor = ((ValueDescriptorImpl)descriptor);
    final DebuggerContextImpl debuggerContext = tree.getDebuggerContext();
    final DebugProcessImpl debugProcess = debuggerContext.getDebugProcess();
    final ValueMarkup markup = valueDescriptor.getMarkup(debugProcess);
    debugProcess.getManagerThread().invoke(new DebuggerContextCommandImpl(debuggerContext) {
      public Priority getPriority() {
        return Priority.HIGH;
      }
      public void threadAction() {
        boolean sessionRefreshNeeded = true;
        try {
          if (markup != null) {
            valueDescriptor.setMarkup(debugProcess, null);
          }
          else {
            final ValueMarkup suggestedMarkup = new ValueMarkup(valueDescriptor.getName(), Color.RED);
            final Ref<Pair<ValueMarkup,Boolean>> result = new Ref<Pair<ValueMarkup, Boolean>>(null);
            try {
              final boolean suggestAdditionalMarkup = canSuggestAdditionalMarkup(debugProcess, valueDescriptor.getValue());
              SwingUtilities.invokeAndWait(new Runnable() {
                public void run() {
                  result.set(ObjectMarkupPropertiesDialog.chooseMarkup(suggestedMarkup, suggestAdditionalMarkup));
                }
              });
            }
            catch (InterruptedException ignored) {
            }
            catch (InvocationTargetException e) {
              LOG.error(e);
            }
            final Pair<ValueMarkup, Boolean> pair = result.get();
            if (pair != null) {
              valueDescriptor.setMarkup(debugProcess, pair.first);
              if (pair.second) {
                final Value value = valueDescriptor.getValue();
                final Map<ObjectReference, ValueMarkup> additionalMarkup = suggestMarkup((ObjectReference)value);
                if (!additionalMarkup.isEmpty()) {
                  final Map<ObjectReference, ValueMarkup> map = NodeDescriptorImpl.getMarkupMap(debugProcess);
                  if (map != null) {
                    for (Map.Entry<ObjectReference, ValueMarkup> entry : additionalMarkup.entrySet()) {
                      final ObjectReference key = entry.getKey();
                      if (!map.containsKey(key)) {
                        map.put(key, entry.getValue());
                      }
                    }
                  }
                }
              }
            }
            else {
              sessionRefreshNeeded = false;
            }
          }
        }
        finally {
          final boolean _sessionRefreshNeeded = sessionRefreshNeeded;
          SwingUtilities.invokeLater(new Runnable() {
            public void run() {
              tree.restoreState(node);
              node.labelChanged();
              if (_sessionRefreshNeeded) {
                final DebuggerSession session = debuggerContext.getDebuggerSession();
                if (session != null) {
                  session.refresh(true);
                }
              }
            }
          });
        }
      }
    });
  }

  private static boolean canSuggestAdditionalMarkup(DebugProcessImpl debugProcess, Value value) {
    if (!debugProcess.getVirtualMachineProxy().canGetInstanceInfo()) {
      return false;
    }
    if (!(value instanceof ObjectReference)) {
      return false;
    }
    final ObjectReference objRef = (ObjectReference)value;
    if (objRef instanceof ArrayReference || objRef instanceof ClassObjectReference || objRef instanceof ThreadReference || objRef instanceof ThreadGroupReference || objRef instanceof ClassLoaderReference) {
      return false;
    }
    return true;
  }

  private static Map<ObjectReference, ValueMarkup> suggestMarkup(ObjectReference objRef) {
    final Map<ObjectReference, ValueMarkup> result = new HashMap<ObjectReference, ValueMarkup>();
    for (ObjectReference ref : getReferringObjects(objRef)) {
      if (!(ref instanceof ClassObjectReference)) {
        // consider references from statisc fields only
        continue;
      }
      final ReferenceType refType = ((ClassObjectReference)ref).reflectedType();
      if (!refType.isAbstract()) {
        continue;
      }
      for (Field field : refType.visibleFields()) {
        if (!(field.isStatic() && field.isFinal())) {
          continue;
        }
        if (DebuggerUtils.isPrimitiveType(field.typeName())) {
          continue;
        }
        final Value fieldValue = refType.getValue(field);
        if (!(fieldValue instanceof ObjectReference)) {
          continue;
        }
        final ValueMarkup markup = result.get((ObjectReference)fieldValue);

        final String fieldName = field.name();
        final Color autoMarkupColor = ValueMarkup.getAutoMarkupColor();
        if (markup == null) {
          result.put((ObjectReference)fieldValue, new ValueMarkup(fieldName, autoMarkupColor, createMarkupTooltipText(null, refType, fieldName)));
        }
        else {
          final String currentText = markup.getText();
          if (!currentText.contains(fieldName)) {
            final String currentTooltip = markup.getToolTipText();
            final String tooltip = createMarkupTooltipText(currentTooltip, refType, fieldName);
            result.put((ObjectReference)fieldValue, new ValueMarkup(currentText + ", " + fieldName, autoMarkupColor, tooltip));
          }
        }
      }
    }
    return result;
  }

  private static List<ObjectReference> getReferringObjects(ObjectReference value) {
    // invoke the following method using Reflection in order to remain compilable on jdk 1.5
    //  java.util.List<com.sun.jdi.ObjectReference> referringObjects(long l);
    try {
      final java.lang.reflect.Method apiMethod = ObjectReference.class.getMethod("referringObjects", long.class);
      return (List<ObjectReference>)apiMethod.invoke(value, ValueMarkup.AUTO_MARKUP_REFERRING_OBJECTS_LIMIT);
    }
    catch (IllegalAccessException e) {
      LOG.error(e); // should not happen
    }
    catch (InvocationTargetException e) {
      throw new RuntimeException(e);
    }
    catch (NoSuchMethodException ignored) {
    }
    return Collections.emptyList();
  }

  private static String createMarkupTooltipText(String prefix, ReferenceType refType, String fieldName) {
    final StringBuilder builder = new StringBuilder();
    if (prefix == null) {
      builder.append("Value referenced from:");
    }
    else {
      builder.append(prefix);
    }
    return builder.append("<br><b>").append(refType.name()).append(".").append(fieldName).append("</b>").toString();
  }


  public void update(AnActionEvent e) {
    boolean enable = false;
    String text = MARK_TEXT;
    final DebuggerTreeNodeImpl node = getSelectedNode(e.getDataContext());
    if (node != null) {
      final NodeDescriptorImpl descriptor = node.getDescriptor();
      enable = (descriptor instanceof ValueDescriptor);
      if (enable) {
        final ValueMarkup markup = ((ValueDescriptor)descriptor).getMarkup(node.getTree().getDebuggerContext().getDebugProcess());
        if (markup != null) { // already exists
          text = UNMARK_TEXT; 
        }
      }
    }
    final Presentation presentation = e.getPresentation();
    presentation.setVisible(enable);
    presentation.setText(text);
  }
}
