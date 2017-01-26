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

import com.intellij.debugger.SourcePosition;
import com.intellij.debugger.engine.DebugProcessImpl;
import com.intellij.debugger.engine.JavaStackFrame;
import com.intellij.debugger.engine.PositionManagerImpl;
import com.intellij.debugger.engine.SuspendContextImpl;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.impl.DebuggerUtilsEx;
import com.intellij.debugger.jdi.DecompiledLocalVariable;
import com.intellij.debugger.jdi.LocalVariablesUtil;
import com.intellij.debugger.jdi.StackFrameProxyImpl;
import com.intellij.debugger.jdi.ThreadReferenceProxyImpl;
import com.intellij.debugger.settings.NodeRendererSettings;
import com.intellij.debugger.ui.breakpoints.StackCapturingLineBreakpoint;
import com.intellij.debugger.ui.impl.watch.MessageDescriptor;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NullableLazyValue;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
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

public class StackFrameItem extends XStackFrame {
  private static final Logger LOG = Logger.getInstance(StackFrameItem.class);

  private final Project myProject;
  private final GlobalSearchScope mySearchScope;

  private final String myFilePath;
  private final String myMethodName;
  private final int myLineNumber;
  private List<XNamedValue> myVariables = null;
  private boolean myFirst;

  private final NullableLazyValue<XSourcePosition> mySourcePosition;

  public StackFrameItem(Project project,
                        GlobalSearchScope scope,
                        @NotNull String path,
                        @NotNull String methodName,
                        int line) {
    myProject = project;
    mySearchScope = scope;
    myFilePath = path.replace('\\', '.');
    myMethodName = methodName;
    myLineNumber = line;

    //TODO: need to reuse PositionManager somehow
    mySourcePosition = NullableLazyValue.createValue(() -> {
      PsiClass psiClass = PositionManagerImpl.findClass(myProject, myFilePath, mySearchScope);
      if (psiClass == null) {
        return null;
      }
      PsiElement element = psiClass.getNavigationElement();
      // see IDEA-137167, prefer not compiled elements
      if (element instanceof PsiCompiledElement) {
        PsiElement fileElement = psiClass.getContainingFile().getNavigationElement();
        if (!(fileElement instanceof PsiCompiledElement)) {
          element = fileElement;
        }
      }
      SourcePosition position = SourcePosition.createFromLine(element.getContainingFile(), myLineNumber - 1);
      PsiFile psiFile = psiClass.getContainingFile().getOriginalFile();
      if (psiFile instanceof PsiCompiledFile) {
        position = new PositionManagerImpl.ClsSourcePosition(position, myLineNumber - 1);
      }
      return DebuggerUtilsEx.toXSourcePosition(position);
    });
  }

  @NotNull
  public String path() {
    return myFilePath;
  }

  @NotNull
  public String methodName() {
    return myMethodName;
  }

  @NotNull
  public String className() {
    return StringUtil.getShortName(myFilePath);
  }

  @NotNull
  public String packageName() {
    return StringUtil.getPackageName(myFilePath);
  }

  public int line() {
    return myLineNumber;
  }

  @Override
  public void customizePresentation(@NotNull ColoredTextContainer component) {
    component.setIcon(myFirst ? AllIcons.Actions.Menu_cut : JBUI.scale(EmptyIcon.create(6)));
    component.append(String.format("%s:%d, %s", myMethodName, myLineNumber, className()), SimpleTextAttributes.REGULAR_ATTRIBUTES);
    String packageName = packageName();
    if (!packageName.trim().isEmpty()) {
      component.append(String.format(" (%s)", packageName), SimpleTextAttributes.GRAYED_ITALIC_ATTRIBUTES);
    }
  }

  @Nullable
  @Override
  public XSourcePosition getSourcePosition() {
    return mySourcePosition.getValue();
  }

  void addVariable(XNamedValue var) {
    if (myVariables == null) {
      myVariables = new ArrayList<>();
    }
    myVariables.add(var);
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
          Location loc = frame.location();
          DebugProcessImpl debugProcess = suspendContext.getDebugProcess();
          StackFrameItem frameItem = new StackFrameItem(debugProcess.getProject(),
                                                        debugProcess.getSearchScope(),
                                                        loc.declaringType().name(),
                                                        loc.method().name(),
                                                        loc.lineNumber());
          if (res.isEmpty()) {
            frameItem.myFirst = true;
          }
          res.add(frameItem);

          if (withVars) {
            List<StackFrameItem> relatedStack = StackCapturingLineBreakpoint.getRelatedStack(frame, suspendContext);
            if (!ContainerUtil.isEmpty(relatedStack)) {
              res.addAll(relatedStack);
              break;
            }

            try {
              ObjectReference thisObject = frame.thisObject();
              if (thisObject != null) {
                frameItem.addVariable(createVariable(thisObject, "this", VariableItem.VarType.OBJECT));
              }
            }
            catch (EvaluateException e) {
              LOG.debug(e);
            }

            try {
              frame.visibleVariables().forEach(v -> {
                try {
                  Value value = frame.getValue(v);
                  VariableItem.VarType varType = VariableItem.VarType.OBJECT;
                  if (v.getVariable().isArgument()) {
                    varType = VariableItem.VarType.PARAM;
                  }
                  frameItem.addVariable(createVariable(value, v.name(), varType));
                }
                catch (EvaluateException e) {
                  LOG.debug(e);
                }
              });
            }
            catch (EvaluateException e) {
              if (e.getCause() instanceof AbsentInformationException) {
                frameItem.addVariable(JavaStackFrame.createMessageNode(MessageDescriptor.LOCAL_VARIABLES_INFO_UNAVAILABLE.getLabel(),
                                                                       XDebuggerUIConstants.INFORMATION_MESSAGE_ICON));
                // trying to collect values from variable slots
                try {
                  for (Map.Entry<DecompiledLocalVariable, Value> entry : LocalVariablesUtil.fetchValues(frame, debugProcess).entrySet()) {
                    frameItem.addVariable(createVariable(entry.getValue(), entry.getKey().getDisplayName(), VariableItem.VarType.PARAM));
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
}
