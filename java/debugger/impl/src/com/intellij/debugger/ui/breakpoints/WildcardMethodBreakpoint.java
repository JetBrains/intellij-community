/*
 * Copyright 2004-2006 Alexey Efimov
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
package com.intellij.debugger.ui.breakpoints;

import com.intellij.debugger.DebuggerBundle;
import com.intellij.debugger.DebuggerManagerEx;
import com.intellij.debugger.engine.DebugProcess;
import com.intellij.debugger.engine.DebugProcessImpl;
import com.intellij.debugger.engine.DebuggerManagerThreadImpl;
import com.intellij.debugger.engine.DebuggerUtils;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.intellij.debugger.engine.requests.RequestManagerImpl;
import com.intellij.debugger.impl.DebuggerUtilsEx;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMExternalizerUtil;
import com.intellij.openapi.util.Key;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.StringBuilderSpinAllocator;
import com.intellij.xdebugger.breakpoints.XBreakpoint;
import com.sun.jdi.AbsentInformationException;
import com.sun.jdi.Location;
import com.sun.jdi.Method;
import com.sun.jdi.ReferenceType;
import com.sun.jdi.event.LocatableEvent;
import com.sun.jdi.event.MethodEntryEvent;
import com.sun.jdi.event.MethodExitEvent;
import com.sun.jdi.request.MethodEntryRequest;
import com.sun.jdi.request.MethodExitRequest;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.java.debugger.breakpoints.properties.JavaMethodBreakpointProperties;

import javax.swing.*;

public class WildcardMethodBreakpoint extends Breakpoint<JavaMethodBreakpointProperties> {
  private static final Logger LOG = Logger.getInstance("#com.intellij.debugger.ui.breakpoints.ExceptionBreakpoint");

  public static final String JDOM_LABEL = "wildcard_breakpoint";

  public WildcardMethodBreakpoint(Project project, XBreakpoint<JavaMethodBreakpointProperties> breakpoint) {
    super(project, breakpoint);
  }

  public Key<MethodBreakpoint> getCategory() {
    return MethodBreakpoint.CATEGORY;
  }

  protected WildcardMethodBreakpoint(Project project, @NotNull String classPattern, @NotNull String methodName, XBreakpoint<JavaMethodBreakpointProperties> breakpoint) {
    super(project, breakpoint);
    setClassPattern(classPattern);
    setMethodName(methodName);
  }

  public String getClassName() {
    return getClassPattern();
  }

  public @Nullable String getShortClassName() {
    return getClassName();
  }

  public String getMethodName() {
    return getProperties().myMethodName;
  }

  public PsiClass getPsiClass() {
    return PsiDocumentManager.getInstance(myProject).commitAndRunReadAction(new Computable<PsiClass>() {
      public PsiClass compute() {
        return getClassName() != null ? DebuggerUtils.findClass(getClassName(), myProject, GlobalSearchScope.allScope(myProject)) : null;
      }
    });
  }

  public String getDisplayName() {
    if (!isValid()) {
      return DebuggerBundle.message("status.breakpoint.invalid");
    }
    final StringBuilder buffer = StringBuilderSpinAllocator.alloc();
    try {
      buffer.append(getClassPattern());
      buffer.append(".");
      buffer.append(getMethodName());
      buffer.append("()");
      return buffer.toString();
    }
    finally {
      StringBuilderSpinAllocator.dispose(buffer);
    }
  }

  public Icon getIcon() {
    if (!isEnabled()) {
      final Breakpoint master = DebuggerManagerEx.getInstanceEx(myProject).getBreakpointManager().findMasterBreakpoint(this);
      return master == null? AllIcons.Debugger.Db_disabled_method_breakpoint : AllIcons.Debugger.Db_dep_method_breakpoint;
    }
    return AllIcons.Debugger.Db_method_breakpoint;
  }

  public void reload() {
  }

  public boolean evaluateCondition(EvaluationContextImpl context, LocatableEvent event) throws EvaluateException {
    return matchesEvent(event) && super.evaluateCondition(context, event);
  }

  public void createRequest(DebugProcessImpl debugProcess) {
    DebuggerManagerThreadImpl.assertIsManagerThread();
    if (!shouldCreateRequest(debugProcess)) {
      return;
    }
    try {
      RequestManagerImpl requestManager = debugProcess.getRequestsManager();
      if (isWatchEntry()) {
        MethodEntryRequest entryRequest = MethodBreakpoint.findRequest(debugProcess, MethodEntryRequest.class, this);
        if (entryRequest == null) {
          entryRequest = requestManager.createMethodEntryRequest(this);
        }
        else {
          entryRequest.disable();
        }
        entryRequest.addClassFilter(getClassPattern());
        debugProcess.getRequestsManager().enableRequest(entryRequest);
      }
      if (isWatchExit()) {
        MethodExitRequest exitRequest = MethodBreakpoint.findRequest(debugProcess, MethodExitRequest.class, this);
        if (exitRequest == null) {
          exitRequest = requestManager.createMethodExitRequest(this);
        }
        else {
          exitRequest.disable();
        }
        exitRequest.addClassFilter(getClassPattern());
        debugProcess.getRequestsManager().enableRequest(exitRequest);
      }
    }
    catch (Exception e) {
      LOG.debug(e);
    }
  }

  public void processClassPrepare(DebugProcess debugProcess, ReferenceType refType) {
    // should be emty - does not make sense for this breakpoint
  }

  public String getEventMessage(LocatableEvent event) {
    final Location location = event.location();
    final String locationQName = DebuggerUtilsEx.getLocationMethodQName(location);
    String locationFileName = "";
    try {
      locationFileName = location.sourceName();
    }
    catch (AbsentInformationException e) {
      locationFileName = "";
    }
    final int locationLine = location.lineNumber();
    
    if (event instanceof MethodEntryEvent) {
      MethodEntryEvent entryEvent = (MethodEntryEvent)event;
      final Method method = entryEvent.method();
      return DebuggerBundle.message(
        "status.method.entry.breakpoint.reached", 
        method.declaringType().name() + "." + method.name() + "()",
        locationQName,
        locationFileName,
        locationLine
      );
    }
    
    if (event instanceof MethodExitEvent) {
      MethodExitEvent exitEvent = (MethodExitEvent)event;
      final Method method = exitEvent.method();
      return DebuggerBundle.message(
        "status.method.exit.breakpoint.reached", 
        method.declaringType().name() + "." + method.name() + "()",
        locationQName,
        locationFileName,
        locationLine
      );
    }
    return "";
  }

  public boolean isValid() {
    return getClassPattern() != null && getMethodName() != null;
  }

  //@SuppressWarnings({"HardCodedStringLiteral"}) public void writeExternal(Element parentNode) throws WriteExternalException {
  //  super.writeExternal(parentNode);
  //  parentNode.setAttribute(JDOM_LABEL, "true");
  //  if (getClassPattern() != null) {
  //    parentNode.setAttribute("class_name", getClassPattern());
  //  }
  //  if (getMethodName() != null) {
  //    parentNode.setAttribute("method_name", getMethodName());
  //  }
  //}

  public PsiElement getEvaluationElement() {
    return null;
  }

  public void readExternal(Element parentNode) throws InvalidDataException {
    super.readExternal(parentNode);

    //noinspection HardCodedStringLiteral
    String className = parentNode.getAttributeValue("class_name");
    setClassPattern(className);

    //noinspection HardCodedStringLiteral
    String methodName = parentNode.getAttributeValue("method_name");
    setMethodName(methodName);

    try {
      getProperties().WATCH_ENTRY = Boolean.valueOf(JDOMExternalizerUtil.readField(parentNode, "WATCH_ENTRY"));
    } catch (Exception ignored) {
    }
    try {
      getProperties().WATCH_EXIT = Boolean.valueOf(JDOMExternalizerUtil.readField(parentNode, "WATCH_EXIT"));
    } catch (Exception ignored) {
    }

    if(className == null || methodName == null) {
      throw new InvalidDataException();
    }
  }

  public boolean matchesEvent(final LocatableEvent event){
    final Method method = event.location().method();
    return method != null && getMethodName().equals(method.name());
  }

  public static WildcardMethodBreakpoint create(Project project,
                                                final String classPattern,
                                                final String methodName,
                                                XBreakpoint<JavaMethodBreakpointProperties> xBreakpoint) {
    return new WildcardMethodBreakpoint(project, classPattern, methodName, xBreakpoint);
  }

  private boolean isWatchEntry() {
    return getProperties().WATCH_ENTRY;
  }

  private void setWatchEntry(boolean WATCH_ENTRY) {
    getProperties().WATCH_ENTRY = WATCH_ENTRY;
  }

  private boolean isWatchExit() {
    return getProperties().WATCH_EXIT;
  }

  private void setWatchExit(boolean WATCH_EXIT) {
    getProperties().WATCH_EXIT = WATCH_EXIT;
  }

  private String getClassPattern() {
    return getProperties().myClassPattern;
  }

  private void setClassPattern(String classPattern) {
    getProperties().myClassPattern = classPattern;
  }

  private void setMethodName(String methodName) {
    getProperties().myMethodName = methodName;
  }
}
