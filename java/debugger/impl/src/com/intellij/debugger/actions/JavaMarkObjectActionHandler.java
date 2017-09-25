/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

import com.intellij.codeInsight.daemon.impl.JavaHighlightInfoTypes;
import com.intellij.debugger.engine.DebugProcess;
import com.intellij.debugger.engine.DebugProcessImpl;
import com.intellij.debugger.engine.DebuggerUtils;
import com.intellij.debugger.engine.SuspendContextImpl;
import com.intellij.debugger.engine.events.DebuggerContextCommandImpl;
import com.intellij.debugger.impl.DebuggerContextImpl;
import com.intellij.debugger.impl.DebuggerSession;
import com.intellij.debugger.ui.impl.tree.TreeBuilder;
import com.intellij.debugger.ui.impl.watch.DebuggerTree;
import com.intellij.debugger.ui.impl.watch.DebuggerTreeNodeImpl;
import com.intellij.debugger.ui.impl.watch.NodeDescriptorImpl;
import com.intellij.debugger.ui.impl.watch.ValueDescriptorImpl;
import com.intellij.debugger.ui.tree.ValueDescriptor;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Ref;
import com.intellij.util.containers.HashMap;
import com.intellij.xdebugger.impl.actions.MarkObjectActionHandler;
import com.intellij.xdebugger.impl.ui.tree.ValueMarkup;
import com.sun.jdi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.lang.reflect.InvocationTargetException;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static com.intellij.openapi.actionSystem.PlatformDataKeys.CONTEXT_COMPONENT;

/*
 * Class SetValueAction
 * @author Jeka
 */
public class JavaMarkObjectActionHandler extends MarkObjectActionHandler {
  private static final Logger LOG = Logger.getInstance("#com.intellij.debugger.actions.JavaMarkObjectActionHandler");
  public static final long AUTO_MARKUP_REFERRING_OBJECTS_LIMIT = 100L; // todo: some reasonable limit

  @Override
  public void perform(@NotNull Project project, AnActionEvent event) {
    final DebuggerTreeNodeImpl node = DebuggerAction.getSelectedNode(event.getDataContext());
    if (node == null) {
      return;
    }
    final NodeDescriptorImpl descriptor = node.getDescriptor();
    if (!(descriptor instanceof ValueDescriptorImpl)) {
      return;
    }
    
    final DebuggerTree tree = node.getTree();
    tree.saveState(node);

    final Component parent = event.getData(CONTEXT_COMPONENT);
    final ValueDescriptorImpl valueDescriptor = ((ValueDescriptorImpl)descriptor);
    final DebuggerContextImpl debuggerContext = tree.getDebuggerContext();
    final DebugProcessImpl debugProcess = debuggerContext.getDebugProcess();
    final ValueMarkup markup = valueDescriptor.getMarkup(debugProcess);
    debugProcess.getManagerThread().invoke(new DebuggerContextCommandImpl(debuggerContext) {
      public Priority getPriority() {
        return Priority.HIGH;
      }

      @Override
      public void threadAction(@NotNull SuspendContextImpl suspendContext) {
        boolean sessionRefreshNeeded = true;
        try {
          if (markup != null) {
            valueDescriptor.setMarkup(debugProcess, null);
          }
          else {
            final String defaultText = valueDescriptor.getName();
            final Ref<Pair<ValueMarkup,Boolean>> result = new Ref<>(null);
            try {
              final boolean suggestAdditionalMarkup = canSuggestAdditionalMarkup(debugProcess, valueDescriptor.getValue());
              SwingUtilities.invokeAndWait(() -> {
                ObjectMarkupPropertiesDialog dialog = new ObjectMarkupPropertiesDialog(parent, defaultText, suggestAdditionalMarkup);
                if (dialog.showAndGet()) {
                  result.set(Pair.create(dialog.getConfiguredMarkup(), dialog.isMarkAdditionalFields()));
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
              final TreeBuilder model = tree.getMutableModel();
              refreshLabelsRecursively(model.getRoot(), model, valueDescriptor.getValue());
              if (_sessionRefreshNeeded) {
                final DebuggerSession session = debuggerContext.getDebuggerSession();
                if (session != null) {
                  session.refresh(true);
                }
              }
            }

            private void refreshLabelsRecursively(Object node, TreeBuilder model, Value value) {
              if (node instanceof DebuggerTreeNodeImpl) {
                final DebuggerTreeNodeImpl _node = (DebuggerTreeNodeImpl)node;
                final NodeDescriptorImpl descriptor = _node.getDescriptor();
                if (descriptor instanceof ValueDescriptor && Comparing.equal(value, ((ValueDescriptor)descriptor).getValue())) {
                  _node.labelChanged();
                }
              }
              final int childCount = model.getChildCount(node);
              for (int idx = 0; idx < childCount; idx++) {
                refreshLabelsRecursively(model.getChild(node, idx), model, value);
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
    final Map<ObjectReference, ValueMarkup> result = new HashMap<>();
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
        final Color autoMarkupColor = getAutoMarkupColor();
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
    try {
      return value.referringObjects(AUTO_MARKUP_REFERRING_OBJECTS_LIMIT);
    }
    catch (UnsupportedOperationException e) {
      LOG.info(e);
    }
    return Collections.emptyList();
  }

  private static String createMarkupTooltipText(@Nullable String prefix, ReferenceType refType, String fieldName) {
    final StringBuilder builder = new StringBuilder();
    if (prefix == null) {
      builder.append("Value referenced from:");
    }
    else {
      builder.append(prefix);
    }
    return builder.append("<br><b>").append(refType.name()).append(".").append(fieldName).append("</b>").toString();
  }

  @Override
  public boolean isEnabled(@NotNull Project project, AnActionEvent event) {
    final DebuggerTreeNodeImpl node = DebuggerAction.getSelectedNode(event.getDataContext());
    return node != null && node.getDescriptor() instanceof ValueDescriptor;
  }

  @Override
  public boolean isHidden(@NotNull Project project, AnActionEvent event) {
    return DebuggerAction.getSelectedNode(event.getDataContext()) == null;
  }

  @Override
  public boolean isMarked(@NotNull Project project, @NotNull AnActionEvent event) {
    final DebuggerTreeNodeImpl node = DebuggerAction.getSelectedNode(event.getDataContext());
    if (node == null) return false;

    final NodeDescriptorImpl descriptor = node.getDescriptor();
    if (!(descriptor instanceof ValueDescriptor)) return false;

    DebugProcess debugProcess = node.getTree().getDebuggerContext().getDebugProcess();
    return ((ValueDescriptor)descriptor).getMarkup(debugProcess) != null;
  }

  public static Color getAutoMarkupColor() {
    final EditorColorsManager manager = EditorColorsManager.getInstance();
    final TextAttributes textAttributes = manager.getGlobalScheme().getAttributes(JavaHighlightInfoTypes.STATIC_FIELD.getAttributesKey());
    return textAttributes.getForegroundColor();
  }
}
