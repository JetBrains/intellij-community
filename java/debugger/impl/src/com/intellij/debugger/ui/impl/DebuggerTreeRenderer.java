// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.ui.impl;

import com.intellij.debugger.ui.impl.watch.ArgumentValueDescriptorImpl;
import com.intellij.debugger.ui.impl.watch.FieldDescriptorImpl;
import com.intellij.debugger.ui.impl.watch.LocalVariableDescriptorImpl;
import com.intellij.debugger.ui.impl.watch.MessageDescriptor;
import com.intellij.debugger.ui.impl.watch.MethodReturnValueDescriptorImpl;
import com.intellij.debugger.ui.impl.watch.StackFrameDescriptorImpl;
import com.intellij.debugger.ui.impl.watch.StaticDescriptorImpl;
import com.intellij.debugger.ui.impl.watch.ThrownExceptionValueDescriptorImpl;
import com.intellij.debugger.ui.impl.watch.UserExpressionDescriptorImpl;
import com.intellij.debugger.ui.impl.watch.ValueDescriptorImpl;
import com.intellij.debugger.ui.impl.watch.WatchItemDescriptor;
import com.intellij.debugger.ui.tree.NodeDescriptor;
import com.intellij.debugger.ui.tree.render.EnumerationChildrenRenderer;
import com.intellij.icons.AllIcons;
import com.intellij.ui.IconManager;
import com.intellij.ui.LayeredIcon;
import com.intellij.util.PlatformIcons;
import com.intellij.xdebugger.impl.ui.XDebuggerUIConstants;
import com.sun.jdi.ObjectReference;
import com.sun.jdi.Value;
import org.jetbrains.annotations.Nullable;

import javax.swing.Icon;

public final class DebuggerTreeRenderer {

  private DebuggerTreeRenderer() {
  }

  public static @Nullable Icon getDescriptorIcon(NodeDescriptor descriptor) {
    if (descriptor instanceof StackFrameDescriptorImpl stackDescriptor) {
      return stackDescriptor.getIcon();
    }
    if (descriptor instanceof ValueDescriptorImpl valueDescriptor) {
      return getValueIcon(valueDescriptor, null);
    }
    if (descriptor instanceof MessageDescriptor messageDescriptor) {
      return switch (messageDescriptor.getKind()) {
        case MessageDescriptor.ERROR -> XDebuggerUIConstants.ERROR_MESSAGE_ICON;
        case MessageDescriptor.INFORMATION -> XDebuggerUIConstants.INFORMATION_MESSAGE_ICON;
        default -> null;
      };
    }
    if (descriptor instanceof StaticDescriptorImpl) {
      return AllIcons.Nodes.Static;
    }

    return null;
  }

  public static Icon getValueIcon(ValueDescriptorImpl valueDescriptor, @Nullable ValueDescriptorImpl parentDescriptor) {
    Icon nodeIcon;
    if (valueDescriptor instanceof WatchItemDescriptor) {
      nodeIcon = AllIcons.Debugger.Db_watch;
    }
    else if (valueDescriptor instanceof FieldDescriptorImpl fieldDescriptor) {
      nodeIcon = IconManager.getInstance().getPlatformIcon(com.intellij.ui.PlatformIcons.Field);
      if (parentDescriptor != null) {
        Value value = valueDescriptor.getValue();
        if (value instanceof ObjectReference && value.equals(parentDescriptor.getValue())) {
          nodeIcon = AllIcons.Debugger.Selfreference;
        }
      }
      if (fieldDescriptor.getField().isFinal()) {
        nodeIcon = LayeredIcon.layeredIcon(new Icon[]{nodeIcon, IconManager.getInstance().getPlatformIcon(com.intellij.ui.PlatformIcons.FinalMark)});
      }
      if (fieldDescriptor.isStatic()) {
        nodeIcon = LayeredIcon.layeredIcon(new Icon[]{nodeIcon, IconManager.getInstance().getPlatformIcon(com.intellij.ui.PlatformIcons.StaticMark)});
      }
    }
    else if (valueDescriptor instanceof ThrownExceptionValueDescriptorImpl) {
      nodeIcon = AllIcons.Nodes.ExceptionClass;
    }
    else if (valueDescriptor instanceof MethodReturnValueDescriptorImpl) {
      nodeIcon = AllIcons.Debugger.WatchLastReturnValue;
    }
    else if (isParameter(valueDescriptor)) {
      nodeIcon = IconManager.getInstance().getPlatformIcon(com.intellij.ui.PlatformIcons.Parameter);
    }
    else if (valueDescriptor.isEnumConstant()) {
      nodeIcon = PlatformIcons.ENUM_ICON;
    }
    else if (valueDescriptor.isArray()) {
      nodeIcon = AllIcons.Debugger.Db_array;
    }
    else if (valueDescriptor.isPrimitive()) {
      nodeIcon = AllIcons.Debugger.Db_primitive;
    }
    else {
      nodeIcon = AllIcons.Debugger.Value;
    }

    if (valueDescriptor instanceof UserExpressionDescriptorImpl) {
      EnumerationChildrenRenderer enumerationChildrenRenderer =
        EnumerationChildrenRenderer.getCurrent(((UserExpressionDescriptorImpl)valueDescriptor).getParentDescriptor());
      if (enumerationChildrenRenderer != null && enumerationChildrenRenderer.isAppendDefaultChildren()) {
        nodeIcon = AllIcons.Debugger.Db_watch;
      }
    }

    final Icon valueIcon = valueDescriptor.getValueIcon();
    if (valueIcon != null) {
      // Keep watch icon to make clear the source of a node, prefer the provided icon otherwise
      nodeIcon = nodeIcon == AllIcons.Debugger.Db_watch
                 ? IconManager.getInstance().createRowIcon(nodeIcon, valueIcon)
                 : valueIcon;
    }
    return nodeIcon;
  }

  private static boolean isParameter(ValueDescriptorImpl valueDescriptor) {
    if (valueDescriptor instanceof LocalVariableDescriptorImpl) {
      return ((LocalVariableDescriptorImpl)valueDescriptor).isParameter();
    }
    else if (valueDescriptor instanceof ArgumentValueDescriptorImpl) {
      return ((ArgumentValueDescriptorImpl)valueDescriptor).isParameter();
    }
    return false;
  }

}
