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
package com.intellij.debugger.ui.impl;

import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.impl.DebuggerContextImpl;
import com.intellij.debugger.impl.DebuggerUtilsEx;
import com.intellij.debugger.ui.impl.watch.*;
import com.intellij.debugger.ui.tree.ValueDescriptor;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.RowIcon;
import com.intellij.ui.SimpleColoredText;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.PlatformIcons;
import com.intellij.xdebugger.impl.ui.XDebuggerUIConstants;
import com.intellij.xdebugger.impl.ui.tree.ValueMarkup;
import com.intellij.xdebugger.ui.DebuggerIcons;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

public class DebuggerTreeRenderer extends ColoredTreeCellRenderer {
  private static final Icon myThreadGroupIcon = IconLoader.getIcon("/debugger/threadGroup.png");
  private static final Icon myCurrentThreadGroupIcon = IconLoader.getIcon("/debugger/threadGroupCurrent.png");
  private static final Icon myStaticFieldIcon = PlatformIcons.FIELD_ICON;

  private static final Icon myStaticIcon = IconLoader.getIcon("/nodes/static.png");

  private static final SimpleTextAttributes DEFAULT_ATTRIBUTES = new SimpleTextAttributes(Font.PLAIN, null);
  private static final SimpleTextAttributes SPECIAL_NODE_ATTRIBUTES = new SimpleTextAttributes(Font.PLAIN, Color.lightGray);
  private static final SimpleTextAttributes OBJECT_ID_HIGHLIGHT_ATTRIBUTES = new SimpleTextAttributes(Font.PLAIN, Color.lightGray);

  public void customizeCellRenderer(JTree tree, Object value, boolean selected, boolean expanded, boolean leaf, int row, boolean hasFocus) {
    final DebuggerTreeNodeImpl node = (DebuggerTreeNodeImpl) value;

    if (node != null) {
      final SimpleColoredText text = node.getText();
      if (text != null) {
        text.appendToComponent(this);
      }
      setIcon(node.getIcon());
    }
  }

  @Nullable
  public static Icon getDescriptorIcon(NodeDescriptorImpl descriptor) {
    Icon nodeIcon = null;
    if (descriptor instanceof ThreadGroupDescriptorImpl) {
      nodeIcon = (((ThreadGroupDescriptorImpl)descriptor).isCurrent() ? myCurrentThreadGroupIcon : myThreadGroupIcon);
    }
    else if (descriptor instanceof ThreadDescriptorImpl) {
      ThreadDescriptorImpl threadDescriptor = (ThreadDescriptorImpl)descriptor;
      nodeIcon = threadDescriptor.getIcon();
    }
    else if (descriptor instanceof StackFrameDescriptorImpl) {
      StackFrameDescriptorImpl stackDescriptor = (StackFrameDescriptorImpl)descriptor;
      nodeIcon = stackDescriptor.getIcon();
    }
    else if (descriptor instanceof ValueDescriptorImpl) {
      final ValueDescriptorImpl valueDescriptor = (ValueDescriptorImpl)descriptor;
      if (valueDescriptor instanceof FieldDescriptorImpl && ((FieldDescriptorImpl)valueDescriptor).isStatic()) {
        nodeIcon = myStaticFieldIcon;
      }
      else if (valueDescriptor.isArray()) {
        nodeIcon = DebuggerIcons.ARRAY_VALUE_ICON;
      }
      else if (valueDescriptor.isPrimitive()) {
        nodeIcon = DebuggerIcons.PRIMITIVE_VALUE_ICON;
      }
      else {
        if (valueDescriptor instanceof WatchItemDescriptor) {
          nodeIcon = DebuggerIcons.WATCHED_VALUE_ICON;
        }
        else {
          nodeIcon = DebuggerIcons.VALUE_ICON;
        }
      }
      final Icon valueIcon = valueDescriptor.getValueIcon();
      if (nodeIcon != null && valueIcon != null) {
        final RowIcon composite = new RowIcon(2);
        composite.setIcon(nodeIcon, 0);
        composite.setIcon(valueIcon, 1);
        nodeIcon = composite;
      }
    }
    else if (descriptor instanceof MessageDescriptor) {
      MessageDescriptor messageDescriptor = (MessageDescriptor)descriptor;
      if (messageDescriptor.getKind() == MessageDescriptor.ERROR) {
        nodeIcon = XDebuggerUIConstants.ERROR_MESSAGE_ICON;
      }
      else if (messageDescriptor.getKind() == MessageDescriptor.INFORMATION) {
        nodeIcon = XDebuggerUIConstants.INFORMATION_MESSAGE_ICON;
      }
      else if (messageDescriptor.getKind() == MessageDescriptor.SPECIAL) {
        nodeIcon = null;
      }
    }
    else if (descriptor instanceof StaticDescriptorImpl) {
      nodeIcon = myStaticIcon;
    }

    return nodeIcon;
  }

  public static SimpleColoredText getDescriptorText(final DebuggerContextImpl debuggerContext, NodeDescriptorImpl descriptor, boolean multiline) {
    return getDescriptorText(debuggerContext, descriptor, multiline, true);
  }

  public static SimpleColoredText getDescriptorTitle(final DebuggerContextImpl debuggerContext, NodeDescriptorImpl descriptor) {
    return getDescriptorText(debuggerContext, descriptor, false, false);
  }

  private static SimpleColoredText getDescriptorText(final DebuggerContextImpl debuggerContext, final NodeDescriptorImpl descriptor, boolean multiline,
                                                     boolean appendValue) {
    SimpleColoredText descriptorText = new SimpleColoredText();

    String text;
    String nodeName;

    if (descriptor == null) {
      text = "";
      nodeName = null;
    }
    else {
      text = descriptor.getLabel();
      nodeName = descriptor.getName();
    }

    if(text.equals(XDebuggerUIConstants.COLLECTING_DATA_MESSAGE)) {
      descriptorText.append(XDebuggerUIConstants.COLLECTING_DATA_MESSAGE, XDebuggerUIConstants.COLLECTING_DATA_HIGHLIGHT_ATTRIBUTES);
      return descriptorText;
    }

    if (descriptor instanceof ValueDescriptor) {
      final ValueMarkup markup = ((ValueDescriptor)descriptor).getMarkup(debuggerContext.getDebugProcess());
      if (markup != null) {
        descriptorText.append("[" + markup.getText() + "] ", new SimpleTextAttributes(SimpleTextAttributes.STYLE_BOLD, markup.getColor()));
      }
    }

    String[] strings = breakString(text, nodeName);

    if (strings[0] != null) {
      if (descriptor instanceof MessageDescriptor && ((MessageDescriptor)descriptor).getKind() == MessageDescriptor.SPECIAL) {
        descriptorText.append(strings[0], SPECIAL_NODE_ATTRIBUTES);
      }
      else {
        descriptorText.append(strings[0], DEFAULT_ATTRIBUTES);
      }
    }
    if (strings[1] != null) {
      descriptorText.append(strings[1], XDebuggerUIConstants.VALUE_NAME_ATTRIBUTES);
    }
    if (strings[2] != null) {
      if (descriptor instanceof ValueDescriptorImpl) {
        if(multiline && strings[2].indexOf('\n') >=0) {
          strings = breakString(strings[2], "=");
          if(strings[2] != null) {
            strings[2] = strings[0] + strings[1] + "\n" + strings[2];
          }
        }


        ValueDescriptorImpl valueDescriptor = (ValueDescriptorImpl)descriptor;
        String valueLabel = valueDescriptor.getValueLabel();

        strings = breakString(strings[2], valueLabel);
        if (strings[0] != null) {
          descriptorText.append(strings[0], DEFAULT_ATTRIBUTES);
        }
        if (appendValue && strings[1] != null) {
          if(valueLabel != null && StringUtil.startsWithChar(valueLabel, '{') && valueLabel.indexOf('}') > 0 && !StringUtil.endsWithChar(valueLabel, '}')) {
            int idx = valueLabel.indexOf('}');
            String objectId = valueLabel.substring(0, idx + 1);
            valueLabel = valueLabel.substring(idx + 1);
            descriptorText.append(objectId, OBJECT_ID_HIGHLIGHT_ATTRIBUTES);
          }

          valueLabel =  DebuggerUtilsEx.truncateString(valueLabel);

          final EvaluateException exception = descriptor.getEvaluateException();
          if(exception != null) {
            final String errorMessage = exception.getMessage();

            if(valueLabel.endsWith(errorMessage)) {
              descriptorText.append(valueLabel.substring(0, valueLabel.length() - errorMessage.length()), DEFAULT_ATTRIBUTES);
              descriptorText.append(errorMessage, XDebuggerUIConstants.EXCEPTION_ATTRIBUTES);
            }
            else {
              descriptorText.append(valueLabel, valueDescriptor.isDirty() ? XDebuggerUIConstants.CHANGED_VALUE_ATTRIBUTES : DEFAULT_ATTRIBUTES);
              descriptorText.append(errorMessage, XDebuggerUIConstants.EXCEPTION_ATTRIBUTES);
            }
          }
          else {
            if(valueLabel.equals(XDebuggerUIConstants.COLLECTING_DATA_MESSAGE)) {
              descriptorText.append(XDebuggerUIConstants.COLLECTING_DATA_MESSAGE, XDebuggerUIConstants.COLLECTING_DATA_HIGHLIGHT_ATTRIBUTES);
            }
            else {
              descriptorText.append(valueLabel, valueDescriptor.isDirty() ? XDebuggerUIConstants.CHANGED_VALUE_ATTRIBUTES : DEFAULT_ATTRIBUTES);
            }
          }
        }
      }
      else {
        descriptorText.append(strings[2], DEFAULT_ATTRIBUTES);
      }
    }

    return descriptorText;
  }

  private static String[] breakString(String source, String substr) {
    if (substr != null && substr.length() > 0) {
      int index = Math.max(source.indexOf(substr), 0);
      String prefix = (index > 0) ? source.substring(0, index) : null;
      index += substr.length();
      String suffix = (index < source.length() - 1) ? source.substring(index) : null;
      return new String[]{prefix, substr, suffix};
    }
    return new String[]{source, null, null};
  }
}
