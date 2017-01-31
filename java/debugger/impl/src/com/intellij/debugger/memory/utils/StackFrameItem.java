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
package com.intellij.debugger.memory.utils;

import com.intellij.debugger.engine.*;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.impl.DebuggerUtilsEx;
import com.intellij.debugger.jdi.*;
import com.intellij.debugger.settings.NodeRendererSettings;
import com.intellij.debugger.ui.breakpoints.StackCapturingLineBreakpoint;
import com.intellij.debugger.ui.impl.watch.MessageDescriptor;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.ColoredTextContainer;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.PlatformIcons;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.EmptyIcon;
import com.intellij.util.ui.JBUI;
import com.intellij.xdebugger.XSourcePosition;
import com.intellij.xdebugger.frame.*;
import com.intellij.xdebugger.impl.ui.XDebuggerUIConstants;
import com.sun.jdi.AbsentInformationException;
import com.sun.jdi.Location;
import com.sun.jdi.ObjectReference;
import com.sun.jdi.Value;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class StackFrameItem {
  private static final Logger LOG = Logger.getInstance(StackFrameItem.class);

  private final Location myLocation;
  private final List<XNamedValue> myVariables;

  public StackFrameItem(Location location, List<XNamedValue> variables) {
    myLocation = location;
    myVariables = variables;
  }

  @NotNull
  public String path() {
    return myLocation.declaringType().name();
  }

  public int line() {
    return myLocation.lineNumber();
  }

  @NotNull
  public static List<StackFrameItem> createFrames(@Nullable ThreadReferenceProxyImpl threadReferenceProxy,
                                                  @NotNull SuspendContextImpl suspendContext,
                                                  boolean withVars)
    throws EvaluateException {
    if (threadReferenceProxy != null) {
      List<StackFrameItem> res = new ArrayList<>();
      for (StackFrameProxyImpl frame : threadReferenceProxy.frames()) {
        try {
          List<XNamedValue> vars = null;
          if (withVars) {
            vars = new ArrayList<>();
            List<StackFrameItem> relatedStack = StackCapturingLineBreakpoint.getRelatedStack(frame, suspendContext);
            if (!ContainerUtil.isEmpty(relatedStack)) {
              res.addAll(relatedStack);
              break;
            }

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
                  VariableItem.VarType varType = v.getVariable().isArgument() ? VariableItem.VarType.PARAM :VariableItem.VarType.OBJECT;
                  vars.add(createVariable(frame.getValue(v), v.name(), varType));
                }
                catch (EvaluateException e) {
                  LOG.debug(e);
                }
              }
            }
            catch (EvaluateException e) {
              if (e.getCause() instanceof AbsentInformationException) {
                vars.add(JavaStackFrame.createMessageNode(MessageDescriptor.LOCAL_VARIABLES_INFO_UNAVAILABLE.getLabel(),
                                                                       XDebuggerUIConstants.INFORMATION_MESSAGE_ICON));
                // trying to collect values from variable slots
                try {
                  for (Map.Entry<DecompiledLocalVariable, Value> entry : LocalVariablesUtil
                    .fetchValues(frame, suspendContext.getDebugProcess()).entrySet()) {
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

          StackFrameItem frameItem = new StackFrameItem(frame.location(), vars);
          res.add(frameItem);
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
      valueText = "";
      type = value.type().name() + "@" + ((ObjectReference)value).uniqueID();
    }
    else if (value != null) {
      valueText = value.toString();
    }
    return new VariableItem(name, type, valueText, varType);
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
      String type = NodeRendererSettings.getInstance().getClassRenderer().renderTypeName(myType);
      Icon icon = myVarType == VariableItem.VarType.PARAM ? PlatformIcons.PARAMETER_ICON : AllIcons.Debugger.Value;
      node.setPresentation(icon, type, myValue, false);
    }
  }

  public CapturedStackFrame createFrame(DebugProcessImpl debugProcess) {
    return new CapturedStackFrame(debugProcess, this);
  }

  public static class CapturedStackFrame extends XStackFrame implements JVMStackFrameInfoProvider {
    private final XSourcePosition mySourcePosition;
    private final boolean myIsSynthetic;
    private final boolean myIsInLibraryContent;

    private final String myPath;
    private final String myMethodName;
    private final int myLineNumber;

    private final List<XNamedValue> myVariables;

    public CapturedStackFrame(DebugProcessImpl debugProcess, StackFrameItem item) {
      DebuggerManagerThreadImpl.assertIsManagerThread();
      mySourcePosition = DebuggerUtilsEx.toXSourcePosition(debugProcess.getPositionManager().getSourcePosition(item.myLocation));
      myIsSynthetic = DebuggerUtils.isSynthetic(item.myLocation.method());
      myIsInLibraryContent =
        DebuggerUtilsEx.isInLibraryContent(mySourcePosition != null ? mySourcePosition.getFile() : null, debugProcess.getProject());
      myPath = item.path();
      myMethodName = item.myLocation.method().name();
      myLineNumber = item.line();
      myVariables = item.myVariables;
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
      //component.setIcon(myFirst ? AllIcons.Actions.Menu_cut : JBUI.scale(EmptyIcon.create(6)));
      component.setIcon(JBUI.scale(EmptyIcon.create(6)));
      component.append(String.format("%s:%d, %s", myMethodName, myLineNumber, StringUtil.getShortName(myPath)), getAttributes());
      String packageName = StringUtil.getPackageName(myPath);
      if (!packageName.trim().isEmpty()) {
        component.append(String.format(" (%s)", packageName), SimpleTextAttributes.GRAYED_ITALIC_ATTRIBUTES);
      }
    }

    @Override
    public void computeChildren(@NotNull XCompositeNode node) {
      if (myVariables != null) {
        XValueChildrenList children = new XValueChildrenList();
        myVariables.forEach(children::add);
        node.addChildren(children, true);
      }
      else {
        node.addChildren(XValueChildrenList.EMPTY, true);
      }
    }

    private SimpleTextAttributes getAttributes() {
      if (isSynthetic() || isInLibraryContent()) {
        return SimpleTextAttributes.GRAYED_ATTRIBUTES;
      }
      return SimpleTextAttributes.REGULAR_ATTRIBUTES;
    }
  }
}
