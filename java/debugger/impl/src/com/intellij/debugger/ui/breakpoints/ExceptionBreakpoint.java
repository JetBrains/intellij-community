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

/*
 * Class ExceptionBreakpoint
 * @author Jeka
 */
package com.intellij.debugger.ui.breakpoints;

import com.intellij.debugger.DebuggerBundle;
import com.intellij.debugger.DebuggerManagerEx;
import com.intellij.debugger.SourcePosition;
import com.intellij.debugger.engine.*;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.intellij.debugger.engine.requests.RequestManagerImpl;
import com.intellij.debugger.impl.DebuggerUtilsEx;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMExternalizerUtil;
import com.intellij.openapi.util.Key;
import com.intellij.psi.CommonClassNames;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.ui.classFilter.ClassFilter;
import com.intellij.xdebugger.breakpoints.XBreakpoint;
import com.sun.jdi.AbsentInformationException;
import com.sun.jdi.Location;
import com.sun.jdi.ObjectReference;
import com.sun.jdi.ReferenceType;
import com.sun.jdi.event.ExceptionEvent;
import com.sun.jdi.event.LocatableEvent;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.java.debugger.breakpoints.properties.JavaExceptionBreakpointProperties;

import javax.swing.*;

public class ExceptionBreakpoint extends Breakpoint<JavaExceptionBreakpointProperties> {
  private static final Logger LOG = Logger.getInstance("#com.intellij.debugger.ui.breakpoints.ExceptionBreakpoint");

  protected final static String READ_NO_CLASS_NAME = DebuggerBundle.message("error.absent.exception.breakpoint.class.name");
  public static final @NonNls Key<ExceptionBreakpoint> CATEGORY = BreakpointCategory.lookup("exception_breakpoints");

  public ExceptionBreakpoint(Project project, XBreakpoint<JavaExceptionBreakpointProperties> xBreakpoint) {
    super(project, xBreakpoint);
  }

  public Key<? extends ExceptionBreakpoint> getCategory() {
    return CATEGORY;
  }

  protected ExceptionBreakpoint(Project project, String qualifiedName, String packageName, XBreakpoint<JavaExceptionBreakpointProperties> xBreakpoint) {
    super(project, xBreakpoint);
    setQualifiedName(qualifiedName);
    if (packageName == null) {
      setPackageName(calcPackageName(qualifiedName));
    }
    else {
      setPackageName(packageName);
    }
  }

  private static String calcPackageName(String qualifiedName) {
    if (qualifiedName == null) {
      return null;
    }
    int dotIndex = qualifiedName.lastIndexOf('.');
    return dotIndex >= 0? qualifiedName.substring(0, dotIndex) : "";
  }

  public String getClassName() {
    return getQualifiedName();
  }

  public String getPackageName() {
    return getProperties().myPackageName;
  }

  public PsiClass getPsiClass() {
    return ReadAction.compute(() -> getQualifiedName() != null ? DebuggerUtils.findClass(getQualifiedName(), myProject, GlobalSearchScope.allScope(myProject)) : null);
  }

  public String getDisplayName() {
    return DebuggerBundle.message("breakpoint.exception.breakpoint.display.name", getQualifiedName());
  }

  public Icon getIcon() {
    if (!isEnabled()) {
      final Breakpoint master = DebuggerManagerEx.getInstanceEx(myProject).getBreakpointManager().findMasterBreakpoint(this);
      return master == null? AllIcons.Debugger.Db_disabled_exception_breakpoint : AllIcons.Debugger.Db_dep_exception_breakpoint;
    }
    return AllIcons.Debugger.Db_exception_breakpoint;
  }

  public void reload() {
  }

  public void createRequest(final DebugProcessImpl debugProcess) {
    DebuggerManagerThreadImpl.assertIsManagerThread();
    if (!shouldCreateRequest(debugProcess)) {
      return;
    }

    SourcePosition classPosition = ReadAction.compute(() -> {
      PsiClass psiClass = DebuggerUtils.findClass(getQualifiedName(), myProject, debugProcess.getSearchScope());
      return psiClass != null ? SourcePosition.createFromElement(psiClass) : null;
    });

    if(classPosition == null) {
      createOrWaitPrepare(debugProcess, getQualifiedName());
    }
    else {
      createOrWaitPrepare(debugProcess, classPosition);
    }
  }

  public void processClassPrepare(DebugProcess process, ReferenceType refType) {
    DebugProcessImpl debugProcess = (DebugProcessImpl)process;
    if (shouldCreateRequest(debugProcess, true)) {
      // trying to create a request
      RequestManagerImpl manager = debugProcess.getRequestsManager();
      manager.enableRequest(manager.createExceptionRequest(this, refType, isNotifyCaught(), isNotifyUncaught()));

      if (LOG.isDebugEnabled()) {
        if (refType != null) {
          LOG.debug("Created exception request for reference type " + refType.name());
        }
        else {
          LOG.debug("Created exception request for reference type null");
        }
      }
    }
  }

  protected ObjectReference getThisObject(SuspendContextImpl context, LocatableEvent event) throws EvaluateException {
    if(event instanceof ExceptionEvent) {
      return ((ExceptionEvent) event).exception();
    }
    return super.getThisObject(context, event);    
  }

  public String getEventMessage(LocatableEvent event) {
    String exceptionName = (getQualifiedName() != null)? getQualifiedName() : CommonClassNames.JAVA_LANG_THROWABLE;
    String threadName    = null;
    if (event instanceof ExceptionEvent) {
      ExceptionEvent exceptionEvent = (ExceptionEvent)event;
      try {
        exceptionName = exceptionEvent.exception().type().name();
        threadName = exceptionEvent.thread().name();
      }
      catch (Exception ignore) {
      }
    }
    Location location = event.location();
    String locationQName = DebuggerUtilsEx.getLocationMethodQName(location);
    String locationInfo;
    try {
      String file = location.sourceName();
      int line = DebuggerUtilsEx.getLineNumber(location, false);
      locationInfo = DebuggerBundle.message("exception.breakpoint.console.message.location.info", file, line);
    }
    catch (AbsentInformationException e) {
      locationInfo = DebuggerBundle.message("exception.breakpoint.console.message.location.info.absent");
    }
    if (threadName != null) {
      return DebuggerBundle.message("exception.breakpoint.console.message.with.thread.info",
                                    exceptionName, threadName, locationQName, locationInfo
      );
    }
    else {
      return DebuggerBundle.message("exception.breakpoint.console.message", exceptionName, locationQName, locationInfo);
    }
  }

  @Override
  public boolean evaluateCondition(EvaluationContextImpl context, LocatableEvent event) throws EvaluateException {
    if (getProperties().isCatchFiltersEnabled() && event instanceof ExceptionEvent) {
      Location location = ((ExceptionEvent)event).catchLocation();
      if (location != null && !typeMatchesClassFilters(location.declaringType().name(),
                                   getProperties().getCatchClassFilters(),
                                   getProperties().getCatchClassExclusionFilters())) {
        return false;
      }
    }
    return super.evaluateCondition(context, event);
  }

  public boolean isValid() {
    return true;
  }

  //@SuppressWarnings({"HardCodedStringLiteral"}) public void writeExternal(Element parentNode) throws WriteExternalException {
  //  super.writeExternal(parentNode);
  //  if(getQualifiedName() != null) {
  //    parentNode.setAttribute("class_name", getQualifiedName());
  //  }
  //  if(getPackageName() != null) {
  //    parentNode.setAttribute("package_name", getPackageName());
  //  }
  //}

  public PsiElement getEvaluationElement() {
    if (getClassName() == null) {
      return null;
    }
    return JavaPsiFacade.getInstance(myProject).findClass(getClassName(), GlobalSearchScope.allScope(myProject));
  }

  public void readExternal(Element parentNode) throws InvalidDataException {
    super.readExternal(parentNode);

    //noinspection HardCodedStringLiteral
    String packageName = parentNode.getAttributeValue("package_name");
    setPackageName(packageName != null? packageName : calcPackageName(packageName));

    try {
      getProperties().NOTIFY_CAUGHT = Boolean.valueOf(JDOMExternalizerUtil.readField(parentNode, "NOTIFY_CAUGHT"));
    } catch (Exception ignore) {
    }
    try {
      getProperties().NOTIFY_UNCAUGHT = Boolean.valueOf(JDOMExternalizerUtil.readField(parentNode, "NOTIFY_UNCAUGHT"));
    } catch (Exception ignore) {
    }

    //noinspection HardCodedStringLiteral
    String className = parentNode.getAttributeValue("class_name");
    setQualifiedName(className);
    if(className == null) {
      throw new InvalidDataException(READ_NO_CLASS_NAME);
    }
  }

  private boolean isNotifyCaught() {
    return getProperties().NOTIFY_CAUGHT;
  }

  private boolean isNotifyUncaught() {
    return getProperties().NOTIFY_UNCAUGHT;
  }

  private String getQualifiedName() {
    return getProperties().myQualifiedName;
  }

  void setQualifiedName(String qualifiedName) {
    getProperties().myQualifiedName = qualifiedName;
  }

  void setPackageName(String packageName) {
    getProperties().myPackageName = packageName;
  }

  public void setCatchFiltersEnabled(boolean enabled) {
    getProperties().setCatchFiltersEnabled(enabled);
  }

  public void setCatchClassFilters(ClassFilter[] filters) {
    getProperties().setCatchClassFilters(filters);
  }

  public void setCatchClassExclusionFilters(ClassFilter[] filters) {
    getProperties().setCatchClassExclusionFilters(filters);
  }
}
