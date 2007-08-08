package com.intellij.debugger.ui.impl;

import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.impl.DebuggerContextImpl;
import com.intellij.debugger.impl.DebuggerUtilsEx;
import com.intellij.debugger.ui.impl.watch.*;
import com.intellij.debugger.ui.tree.ValueDescriptor;
import com.intellij.debugger.ui.tree.ValueMarkup;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.SimpleColoredText;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.Icons;

import javax.swing.*;
import java.awt.*;

public class DebuggerTreeRenderer extends ColoredTreeCellRenderer {
  private static final Logger LOG = Logger.getInstance("#com.intellij.debugger.ui.impl.DebuggerTreeRenderer");

  private static final Color myHighlightColor = new Color(128, 0, 0);
  private static final Color myChangedValueHighlightColor = Color.blue;
  private static final Color myEvaluatingHighlightColor = Color.lightGray;
  private static final Color myExceptionHighlightColor = Color.red;

  private static final Icon myThreadGroupIcon = IconLoader.getIcon("/debugger/threadGroup.png");
  private static final Icon myCurrentThreadGroupIcon = IconLoader.getIcon("/debugger/threadGroupCurrent.png");
  private static final Icon myValueIcon = IconLoader.getIcon("/debugger/value.png");
  private static final Icon myWatchedValueIcon = IconLoader.getIcon("/debugger/watch.png");
  private static final Icon myArrayValueIcon = IconLoader.getIcon("/debugger/db_array.png");
  private static final Icon myPrimitiveValueIcon = IconLoader.getIcon("/debugger/db_primitive.png");
  private static final Icon myStaticFieldIcon = Icons.FIELD_ICON;

  private static Icon myStaticIcon = IconLoader.getIcon("/nodes/static.png");

  private static final Icon myErrorMessageIcon = IconLoader.getIcon("/debugger/db_error.png");
  private static final Icon myInformationMessageIcon = IconLoader.getIcon("/compiler/information.png");
  private static final SimpleTextAttributes DEFAULT_ATTR = new SimpleTextAttributes(Font.PLAIN, null);
  private static final SimpleTextAttributes GRAY_ATTR = new SimpleTextAttributes(Font.PLAIN, Color.lightGray);
  private static final SimpleTextAttributes HIGHLIGHT_ATTR = new SimpleTextAttributes(Font.PLAIN, myHighlightColor);
  private static final SimpleTextAttributes EVALUATING_HIGHLIGHT_ATTR = new SimpleTextAttributes(Font.PLAIN, myEvaluatingHighlightColor);
  private static final SimpleTextAttributes EXCEPTION_HIGHLIGHT_ATTR = new SimpleTextAttributes(Font.PLAIN, myExceptionHighlightColor);

  public DebuggerTreeRenderer() {
  }

  public void customizeCellRenderer(JTree tree, Object value, boolean selected, boolean expanded, boolean leaf, int row, boolean hasFocus) {
    DebuggerTreeNodeImpl node = (DebuggerTreeNodeImpl) value;

    if(node.getText() != null) {
      node.getText().appendToComponent(this);
    }

    setIcon(node.getIcon());
  }

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
      ValueDescriptorImpl valueDescriptor = (ValueDescriptorImpl)descriptor;
      if (valueDescriptor instanceof FieldDescriptorImpl && ((FieldDescriptorImpl)valueDescriptor).isStatic()) {
        nodeIcon = myStaticFieldIcon;
      }
      else if (valueDescriptor.isArray()) {
        nodeIcon = myArrayValueIcon;
      }
      else if (valueDescriptor.isPrimitive()) {
        nodeIcon = myPrimitiveValueIcon;
      }
      else {
        if (valueDescriptor instanceof WatchItemDescriptor) {
          nodeIcon = myWatchedValueIcon;           
        } else {
          nodeIcon = myValueIcon;
        }
      }
    }
    else if (descriptor instanceof MessageDescriptor) {
      MessageDescriptor messageDescriptor = (MessageDescriptor)descriptor;
      if (messageDescriptor.getKind() == MessageDescriptor.ERROR) {
        nodeIcon = myErrorMessageIcon;
      }
      else if (messageDescriptor.getKind() == MessageDescriptor.INFORMATION) {
        nodeIcon = myInformationMessageIcon;
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

    if(text.equals(NodeDescriptorImpl.EVALUATING_MESSAGE)) {
      descriptorText.append(NodeDescriptorImpl.EVALUATING_MESSAGE, EVALUATING_HIGHLIGHT_ATTR);
      return descriptorText;
    }

    if (descriptor instanceof ValueDescriptor) {
      final ValueMarkup markup = ((ValueDescriptor)descriptor).getMarkup(debuggerContext.getDebugProcess());
      if (markup != null) {
        descriptorText.append(markup.getText(), new SimpleTextAttributes(SimpleTextAttributes.STYLE_BOLD, markup.getColor()));
      }
    }

    String[] strings = breakString(text, nodeName);

    if (strings[0] != null) {
      if (descriptor instanceof MessageDescriptor && ((MessageDescriptor)descriptor).getKind() == MessageDescriptor.SPECIAL) {
        descriptorText.append(strings[0], GRAY_ATTR);
      }
      else {
        descriptorText.append(strings[0], DEFAULT_ATTR);
      }
    }
    if (strings[1] != null) {
      descriptorText.append(strings[1], HIGHLIGHT_ATTR);
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
          descriptorText.append(strings[0], DEFAULT_ATTR);
        }
        if (appendValue && strings[1] != null) {
          if(valueLabel != null && StringUtil.startsWithChar(valueLabel, '{') && valueLabel.indexOf('}') > 0 && !StringUtil.endsWithChar(valueLabel, '}')) {
            int idx = valueLabel.indexOf('}');
            String objectId = valueLabel.substring(0, idx + 1);
            valueLabel = valueLabel.substring(idx + 1);
            descriptorText.append(objectId, EVALUATING_HIGHLIGHT_ATTR);
          }

          valueLabel =  DebuggerUtilsEx.truncateString(valueLabel);

          final EvaluateException exception = descriptor.getEvaluateException();
          if(exception != null) {
            final String errorMessage = exception.getMessage();

            if(valueLabel.endsWith(errorMessage)) {
              descriptorText.append(valueLabel.substring(0, valueLabel.length() - errorMessage.length()), DEFAULT_ATTR);
              descriptorText.append(errorMessage, EXCEPTION_HIGHLIGHT_ATTR);
            }
            else {
              descriptorText.append(valueLabel, valueDescriptor.isDirty() ? new SimpleTextAttributes(Font.PLAIN, myChangedValueHighlightColor) : DEFAULT_ATTR);
              descriptorText.append(errorMessage, EXCEPTION_HIGHLIGHT_ATTR);
            }
          }
          else {
            if(valueLabel.equals(NodeDescriptorImpl.EVALUATING_MESSAGE)) {
              descriptorText.append(NodeDescriptorImpl.EVALUATING_MESSAGE, EVALUATING_HIGHLIGHT_ATTR);
            }
            else {
              descriptorText.append(valueLabel, valueDescriptor.isDirty() ? new SimpleTextAttributes(Font.PLAIN, myChangedValueHighlightColor) : DEFAULT_ATTR);
            }
          }
        }
      }
      else {
        descriptorText.append(strings[2], DEFAULT_ATTR);
      }
    }

    return descriptorText;
  }

  private static String[] breakString(String source, String substr) {
    if (substr != null && substr.length() > 0) {
      String prefix, suffix;
      int index = Math.max(source.indexOf(substr), 0);
      prefix = (index > 0)? source.substring(0, index) : null;
      index += substr.length();
      suffix = (index < source.length() - 1)? source.substring(index) : null;
      return new String[]{prefix, substr, suffix};
    }
    return new String[]{source, null, null};
  }
}