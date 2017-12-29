/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.debugger.memory.utils;

import com.intellij.debugger.DebuggerBundle;
import com.intellij.debugger.engine.*;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.impl.DebuggerUtilsEx;
import com.intellij.debugger.jdi.*;
import com.intellij.debugger.settings.CaptureConfigurable;
import com.intellij.debugger.settings.DebuggerSettings;
import com.intellij.debugger.settings.NodeRendererSettings;
import com.intellij.debugger.settings.ThreadsViewSettings;
import com.intellij.debugger.ui.breakpoints.StackCapturingLineBreakpoint;
import com.intellij.debugger.ui.tree.render.ClassRenderer;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.CommonClassNames;
import com.intellij.ui.ColoredTextContainer;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.PlatformIcons;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.EmptyIcon;
import com.intellij.util.ui.JBUI;
import com.intellij.xdebugger.XSourcePosition;
import com.intellij.xdebugger.frame.*;
import com.intellij.xdebugger.frame.presentation.XStringValuePresentation;
import com.intellij.xdebugger.impl.frame.XDebuggerFramesList;
import com.intellij.xdebugger.impl.ui.XDebuggerUIConstants;
import com.sun.jdi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class StackFrameItem {
  private static final Logger LOG = Logger.getInstance(StackFrameItem.class);
  private static final List<XNamedValue> VARS_CAPTURE_DISABLED = Collections.singletonList(
    JavaStackFrame.createMessageNode(DebuggerBundle.message("message.node.local.variables.capture.disabled"), null));
  private static final List<XNamedValue> VARS_NOT_CAPTURED = Collections.singletonList(
    JavaStackFrame.createMessageNode(DebuggerBundle.message("message.node.local.variables.not.captured"),
                                     XDebuggerUIConstants.INFORMATION_MESSAGE_ICON));

  public static final XDebuggerTreeNodeHyperlink CAPTURE_SETTINGS_OPENER = new XDebuggerTreeNodeHyperlink(" settings") {
    @Override
    public void onClick(MouseEvent event) {
      ShowSettingsUtil.getInstance().showSettingsDialog(null, CaptureConfigurable.class);
      event.consume();
    }
  };

  private final Location myLocation;
  private final List<XNamedValue> myVariables;

  public StackFrameItem(@NotNull Location location, List<XNamedValue> variables) {
    myLocation = location;
    myVariables = variables;
  }

  @NotNull
  public String path() {
    return myLocation.declaringType().name();
  }

  @NotNull
  public String method() {
    return myLocation.method().name();
  }

  public int line() {
    return DebuggerUtilsEx.getLineNumber(myLocation, false);
  }

  @NotNull
  public static List<StackFrameItem> createFrames(@NotNull SuspendContextImpl suspendContext, boolean withVars) throws EvaluateException {
    ThreadReferenceProxyImpl threadReferenceProxy = suspendContext.getThread();
    if (threadReferenceProxy != null) {
      List<StackFrameProxyImpl> frameProxies = threadReferenceProxy.forceFrames();
      List<StackFrameItem> res = new ArrayList<>(frameProxies.size());
      for (StackFrameProxyImpl frame : frameProxies) {
        try {
          List<XNamedValue> vars = null;
          Location location = frame.location();
          Method method = location.method();
          if (withVars) {
            if (!DebuggerSettings.getInstance().CAPTURE_VARIABLES) {
              vars = VARS_CAPTURE_DISABLED;
            }
            else if (method.isNative() || method.isBridge() || DefaultSyntheticProvider.checkIsSynthetic(method)) {
              vars = VARS_NOT_CAPTURED;
            }
            else {
              vars = new ArrayList<>();

              try {
                ObjectReference thisObject = frame.thisObject();
                if (thisObject != null) {
                  vars.add(createVariable(thisObject, "this", VariableItem.VarType.OBJECT));
                }
              }
              catch (EvaluateException e) {
                LOG.debug(e);
              }

              try {
                for (LocalVariableProxyImpl v : frame.visibleVariables()) {
                  try {
                    VariableItem.VarType varType = v.getVariable().isArgument() ? VariableItem.VarType.PARAM : VariableItem.VarType.OBJECT;
                    vars.add(createVariable(frame.getValue(v), v.name(), varType));
                  }
                  catch (EvaluateException e) {
                    LOG.debug(e);
                  }
                }
              }
              catch (EvaluateException e) {
                if (e.getCause() instanceof AbsentInformationException) {
                  vars.add(JavaStackFrame.LOCAL_VARIABLES_INFO_UNAVAILABLE_MESSAGE_NODE);
                  // only args for frames w/o debug info for now
                  try {
                    for (Map.Entry<DecompiledLocalVariable, Value> entry : LocalVariablesUtil
                      .fetchValues(frame, suspendContext.getDebugProcess(), false).entrySet()) {
                      vars.add(createVariable(entry.getValue(), entry.getKey().getDisplayName(), VariableItem.VarType.PARAM));
                    }
                  }
                  catch (Exception ex) {
                    LOG.info(ex);
                  }
                }
                else {
                  LOG.debug(e);
                }
              }
            }
          }

          StackFrameItem frameItem = new StackFrameItem(location, vars);
          res.add(frameItem);

          List<StackFrameItem> relatedStack = StackCapturingLineBreakpoint.getRelatedStack(frame, suspendContext, false);
          if (!ContainerUtil.isEmpty(relatedStack)) {
            res.add(null); // separator
            res.addAll(relatedStack);
            break;
          }
        }
        catch (EvaluateException e) {
          LOG.debug(e);
        }
      }
      return res;
    }
    return Collections.emptyList();
  }

  private static VariableItem createVariable(Value value, String name, VariableItem.VarType varType) {
    String type = null;
    String valueText = "null";
    if (value instanceof ObjectReference) {
      valueText = value instanceof StringReference ? ((StringReference)value).value() : "";
      type = value.type().name() + "@" + ((ObjectReference)value).uniqueID();
    }
    else if (value != null) {
      valueText = value.toString();
    }
    return new VariableItem(name, type, valueText, varType);
  }

  @Override
  public String toString() {
    return myLocation.toString();
  }

  private static class VariableItem extends XNamedValue {
    enum VarType {PARAM, OBJECT}

    private final String myType;
    private final String myValue;
    private final VarType myVarType;

    public VariableItem(String name, String type, String value, VarType varType) {
      super(name);
      myType = type;
      myValue = value;
      myVarType = varType;
    }

    @Override
    public void computePresentation(@NotNull XValueNode node, @NotNull XValuePlace place) {
      ClassRenderer classRenderer = NodeRendererSettings.getInstance().getClassRenderer();
      String type = Registry.is("debugger.showTypes") ? classRenderer.renderTypeName(myType) : null;
      Icon icon = myVarType == VariableItem.VarType.PARAM ? PlatformIcons.PARAMETER_ICON : AllIcons.Debugger.Value;
      if (myType != null && myType.startsWith(CommonClassNames.JAVA_LANG_STRING + "@")) {
        node.setPresentation(icon, new XStringValuePresentation(myValue) {
          @Nullable
          @Override
          public String getType() {
            return classRenderer.SHOW_STRINGS_TYPE ? type : null;
          }
        }, false);
        return;
      }
      node.setPresentation(icon, type, myValue, false);
    }
  }

  public CapturedStackFrame createFrame(DebugProcessImpl debugProcess) {
    return new CapturedStackFrame(debugProcess, this);
  }

  public static class CapturedStackFrame extends XStackFrame implements JVMStackFrameInfoProvider,
                                                                        XDebuggerFramesList.ItemWithSeparatorAbove {
    private static final String ASYNC_STACKTRACE_MESSAGE = DebuggerBundle.message("frame.panel.async.stacktrace");
    private final XSourcePosition mySourcePosition;
    private final boolean myIsSynthetic;
    private final boolean myIsInLibraryContent;

    private final String myPath;
    private final String myMethodName;
    private final int myLineNumber;

    private final List<XNamedValue> myVariables;

    private volatile boolean myWithSeparator;

    public CapturedStackFrame(DebugProcessImpl debugProcess, StackFrameItem item) {
      DebuggerManagerThreadImpl.assertIsManagerThread();
      myPath = item.path();
      myMethodName = item.method();
      myLineNumber = item.line();
      myVariables = item.myVariables;

      Location location = item.myLocation;
      mySourcePosition = DebuggerUtilsEx.toXSourcePosition(debugProcess.getPositionManager().getSourcePosition(location));
      myIsSynthetic = DebuggerUtils.isSynthetic(location.method());
      myIsInLibraryContent =
        DebuggerUtilsEx.isInLibraryContent(mySourcePosition != null ? mySourcePosition.getFile() : null, debugProcess.getProject());
    }

    @Nullable
    @Override
    public XSourcePosition getSourcePosition() {
      return mySourcePosition;
    }

    public boolean isSynthetic() {
      return myIsSynthetic;
    }

    public boolean isInLibraryContent() {
      return myIsInLibraryContent;
    }

    @Override
    public void customizePresentation(@NotNull ColoredTextContainer component) {
      component.setIcon(JBUI.scale(EmptyIcon.create(6)));
      component.append(String.format("%s:%d", myMethodName, myLineNumber), getAttributes());
      ThreadsViewSettings settings = ThreadsViewSettings.getInstance();
      if (settings.SHOW_CLASS_NAME) {
        component.append(String.format(", %s", StringUtil.getShortName(myPath)), getAttributes());
        String packageName = StringUtil.getPackageName(myPath);
        if (settings.SHOW_PACKAGE_NAME && !packageName.trim().isEmpty()) {
          component.append(String.format(" (%s)", packageName), SimpleTextAttributes.GRAYED_ITALIC_ATTRIBUTES);
        }
      }
    }

    @Override
    public void computeChildren(@NotNull XCompositeNode node) {
      XValueChildrenList children = XValueChildrenList.EMPTY;
      if (myVariables == VARS_CAPTURE_DISABLED) {
        node.setMessage(DebuggerBundle.message("message.node.local.variables.capture.disabled"), null,
                        SimpleTextAttributes.REGULAR_ATTRIBUTES, CAPTURE_SETTINGS_OPENER);
      }
      else if (myVariables != null) {
        children = new XValueChildrenList(myVariables.size());
        myVariables.forEach(children::add);
      }
      node.addChildren(children, true);
    }

    private SimpleTextAttributes getAttributes() {
      if (isSynthetic() || isInLibraryContent()) {
        return SimpleTextAttributes.GRAYED_ATTRIBUTES;
      }
      return SimpleTextAttributes.REGULAR_ATTRIBUTES;
    }

    @Override
    public String getCaptionAboveOf() {
      return ASYNC_STACKTRACE_MESSAGE;
    }

    @Override
    public boolean hasSeparatorAbove() {
      return myWithSeparator;
    }

    public void setWithSeparator(boolean withSeparator) {
      myWithSeparator = withSeparator;
    }

    @Override
    public String toString() {
      if (mySourcePosition != null) {
        return mySourcePosition.getFile().getName() + ":" + (mySourcePosition.getLine() + 1);
      }
      return "<position unknown>";
    }
  }
}
