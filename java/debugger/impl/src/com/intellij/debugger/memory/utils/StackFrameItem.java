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
import com.intellij.debugger.engine.PositionManagerImpl;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.impl.DebuggerUtilsEx;
import com.intellij.debugger.jdi.StackFrameProxyImpl;
import com.intellij.debugger.jdi.ThreadReferenceProxyImpl;
import com.intellij.debugger.settings.NodeRendererSettings;
import com.intellij.debugger.ui.breakpoints.StackCapturingLineBreakpoint;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NullableLazyValue;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiCompiledFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.ui.ColoredTextContainer;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.PlatformIcons;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.EmptyIcon;
import com.intellij.util.ui.JBUI;
import com.intellij.xdebugger.XSourcePosition;
import com.intellij.xdebugger.frame.*;
import com.sun.jdi.Location;
import com.sun.jdi.ObjectReference;
import com.sun.jdi.Value;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class StackFrameItem extends XStackFrame {
  private static final Logger LOG = Logger.getInstance(StackFrameItem.class);

  private final Project myProject;
  private final GlobalSearchScope mySearchScope;

  private final String myFilePath;
  private final String myMethodName;
  private final int myLineNumber;
  private List<VariableItem> myVariables = null;
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
      SourcePosition position = SourcePosition.createFromLine(psiClass.getContainingFile(), myLineNumber - 1);
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

  void addVariable(VariableItem var) {
    if (myVariables == null) {
      myVariables = new ArrayList<>();
    }
    myVariables.add(var);
  }

  public static List<StackFrameItem> createFrames(@Nullable ThreadReferenceProxyImpl threadReferenceProxy,
                                                  DebugProcessImpl process,
                                                  boolean withVars)
    throws EvaluateException {
    if (threadReferenceProxy != null) {
      List<StackFrameItem> res = new ArrayList<>();
      for (StackFrameProxyImpl frame : threadReferenceProxy.frames()) {
        try {
          Location loc = frame.location();
          StackFrameItem frameItem = new StackFrameItem(process.getProject(),
                                                        process.getSearchScope(),
                                                        loc.declaringType().name(),
                                                        loc.method().name(),
                                                        loc.lineNumber());
          if (res.isEmpty()) {
            frameItem.myFirst = true;
          }
          res.add(frameItem);

          if (withVars) {
            List<StackFrameItem> relatedStack = StackCapturingLineBreakpoint.getRelatedStack(frame, process);
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
              LOG.debug(e);
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
