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
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.intellij.debugger.engine.requests.RequestManagerImpl;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.util.StringBuilderSpinAllocator;
import com.sun.jdi.AbsentInformationException;
import com.sun.jdi.Location;
import com.sun.jdi.Method;
import com.sun.jdi.ReferenceType;
import com.sun.jdi.event.LocatableEvent;
import com.sun.jdi.event.MethodEntryEvent;
import com.sun.jdi.event.MethodExitEvent;
import com.sun.jdi.request.EventRequest;
import com.sun.jdi.request.MethodEntryRequest;
import com.sun.jdi.request.MethodExitRequest;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Iterator;
import java.util.Set;

public class WildcardMethodBreakpoint extends Breakpoint {
  private static final Logger LOG = Logger.getInstance("#com.intellij.debugger.ui.breakpoints.ExceptionBreakpoint");

  public boolean WATCH_ENTRY = true;
  public boolean WATCH_EXIT  = true;
  private String myClassPattern;
  private String myMethodName;

  public static final String JDOM_LABEL = "wildcard_breakpoint";

  public WildcardMethodBreakpoint(Project project) {
    super(project);
  }

  public Key<MethodBreakpoint> getCategory() {
    return MethodBreakpoint.CATEGORY;
  }

  protected WildcardMethodBreakpoint(Project project, @NotNull String classPattern, @NotNull String methodName) {
    super(project);
    myClassPattern = classPattern;
    myMethodName = methodName;
  }

  public String getClassName() {
    return myClassPattern;
  }

  public @Nullable String getShortClassName() {
    return getClassName();
  }

  public String getMethodName() {
    return myMethodName;
  }

  public PsiClass getPsiClass() {
    return null;
  }

  public String getDisplayName() {
    if (!isValid()) {
      return DebuggerBundle.message("status.breakpoint.invalid");
    }
    final StringBuilder buffer = StringBuilderSpinAllocator.alloc();
    try {
      buffer.append(myClassPattern);
      buffer.append(".");
      buffer.append(myMethodName);
      buffer.append("()");
      return buffer.toString();
    }
    finally {
      StringBuilderSpinAllocator.dispose(buffer);
    }
  }

  public Icon getIcon() {
    if (!ENABLED) {
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
    if (!ENABLED || !debugProcess.isAttached() || debugProcess.areBreakpointsMuted() || !debugProcess.getRequestsManager().findRequests(this).isEmpty()) {
      return;
    }
    try {
      RequestManagerImpl requestManager = debugProcess.getRequestsManager();
      if (WATCH_ENTRY) {
        MethodEntryRequest entryRequest = (MethodEntryRequest)findRequest(debugProcess, MethodEntryRequest.class);
        if (entryRequest == null) {
          entryRequest = requestManager.createMethodEntryRequest(this);
        }
        else {
          entryRequest.disable();
        }
        entryRequest.addClassFilter(myClassPattern);
        debugProcess.getRequestsManager().enableRequest(entryRequest);
      }
      if (WATCH_EXIT) {
        MethodExitRequest exitRequest = (MethodExitRequest)findRequest(debugProcess, MethodExitRequest.class);
        if (exitRequest == null) {
          exitRequest = requestManager.createMethodExitRequest(this);
        }
        else {
          exitRequest.disable();
        }
        exitRequest.addClassFilter(myClassPattern);
        debugProcess.getRequestsManager().enableRequest(exitRequest);
      }
    }
    catch (Exception e) {
      LOG.debug(e);
    }
  }

  private EventRequest findRequest(DebugProcessImpl debugProcess, Class requestClass) {
    Set reqSet = debugProcess.getRequestsManager().findRequests(this);
    for (Iterator iterator = reqSet.iterator(); iterator.hasNext();) {
      EventRequest eventRequest = (EventRequest) iterator.next();
      if(eventRequest.getClass().equals(requestClass)) {
        return eventRequest;
      }
    }

    return null;
  }

  public void processClassPrepare(DebugProcess debugProcess, ReferenceType refType) {
    // should be emty - does not make sense for this breakpoint
  }

  public String getEventMessage(LocatableEvent event) {
    final Location location = event.location();
    final String locationQName = location.declaringType().name() + "." + location.method().name();
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
    return myClassPattern != null && myMethodName != null;
  }

  @SuppressWarnings({"HardCodedStringLiteral"}) public void writeExternal(Element parentNode) throws WriteExternalException {
    super.writeExternal(parentNode);
    parentNode.setAttribute(JDOM_LABEL, "true");
    if (myClassPattern != null) {
      parentNode.setAttribute("class_name", myClassPattern);
    }
    if (myMethodName != null) {
      parentNode.setAttribute("method_name", myMethodName);
    }
  }

  public PsiElement getEvaluationElement() {
    return null;
  }

  public void readExternal(Element parentNode) throws InvalidDataException {
    super.readExternal(parentNode);

    //noinspection HardCodedStringLiteral
    String className = parentNode.getAttributeValue("class_name");
    myClassPattern = className;

    //noinspection HardCodedStringLiteral
    String methodName = parentNode.getAttributeValue("method_name");
    myMethodName = methodName;

    if(className == null || methodName == null) {
      throw new InvalidDataException();
    }
  }

  public boolean matchesEvent(final LocatableEvent event){
    final Method method = event.location().method();
    return method != null && myMethodName.equals(method.name());
  }

  public static WildcardMethodBreakpoint create(Project project, final String classPattern, final String methodName) {
    return new WildcardMethodBreakpoint(project, classPattern, methodName);
  }
}
