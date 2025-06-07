// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

/*
 * Class ExceptionBreakpoint
 * @author Jeka
 */
package com.intellij.debugger.ui.breakpoints;

import com.intellij.debugger.JavaDebuggerBundle;
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
import com.intellij.openapi.util.NlsSafe;
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
  private static final Logger LOG = Logger.getInstance(ExceptionBreakpoint.class);

  public static final @NonNls Key<ExceptionBreakpoint> CATEGORY = BreakpointCategory.lookup("exception_breakpoints");

  public ExceptionBreakpoint(Project project, XBreakpoint<JavaExceptionBreakpointProperties> xBreakpoint) {
    super(project, xBreakpoint);
  }

  @Override
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
    return dotIndex >= 0 ? qualifiedName.substring(0, dotIndex) : "";
  }

  @Override
  public String getClassName() {
    return getQualifiedName();
  }

  @Override
  public String getPackageName() {
    return getProperties().myPackageName;
  }

  @Override
  public PsiClass getPsiClass() {
    return ReadAction.compute(() -> {
      if (myProject.isDisposed()) {
        return null;
      }

      String qualifiedName = getQualifiedName();
      if (qualifiedName == null) {
        return null;
      }
      return DebuggerUtils.findClass(qualifiedName, myProject, GlobalSearchScope.allScope(myProject));
    });
  }

  @Override
  public String getDisplayName() {
    return getQualifiedName();
  }

  @Override
  public Icon getIcon() {
    return AllIcons.Debugger.Db_exception_breakpoint;
  }

  @Override
  public void reload() {
  }

  @Override
  public void createRequest(final DebugProcessImpl debugProcess) {
    DebuggerManagerThreadImpl.assertIsManagerThread();
    if (!shouldCreateRequest(debugProcess)) {
      return;
    }

    SourcePosition classPosition = ReadAction.compute(() -> {
      PsiClass psiClass = DebuggerUtils.findClass(getQualifiedName(), myProject, debugProcess.getSearchScope());
      return psiClass != null ? SourcePosition.createFromElement(psiClass) : null;
    });

    if (classPosition == null) {
      createOrWaitPrepare(debugProcess, getQualifiedName());
    }
    else {
      createOrWaitPrepare(debugProcess, classPosition);
    }
  }

  @Override
  public void processClassPrepare(DebugProcess process, ReferenceType refType) {
    DebugProcessImpl debugProcess = (DebugProcessImpl)process;
    if (shouldCreateRequest(debugProcess, true) && !debugProcess.getRequestsManager().checkReadOnly(this)) {
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

  @Override
  protected String calculateEventClass(EvaluationContextImpl context, LocatableEvent event) throws EvaluateException {
    return event.location().declaringType().name();
  }

  @Override
  protected ObjectReference getThisObject(SuspendContextImpl context, LocatableEvent event) throws EvaluateException {
    if (event instanceof ExceptionEvent) {
      return ((ExceptionEvent)event).exception();
    }
    return super.getThisObject(context, event);
  }

  @Override
  public String getEventMessage(LocatableEvent event) {
    String exceptionName = (getQualifiedName() != null) ? getQualifiedName() : CommonClassNames.JAVA_LANG_THROWABLE;
    String threadName = null;
    if (event instanceof ExceptionEvent exceptionEvent) {
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
      locationInfo = JavaDebuggerBundle.message("exception.breakpoint.console.message.location.info", file, line);
    }
    catch (AbsentInformationException e) {
      locationInfo = JavaDebuggerBundle.message("exception.breakpoint.console.message.location.info.absent");
    }
    if (threadName != null) {
      return JavaDebuggerBundle.message("exception.breakpoint.console.message.with.thread.info",
                                        exceptionName, threadName, locationQName, locationInfo
      );
    }
    else {
      return JavaDebuggerBundle.message("exception.breakpoint.console.message", exceptionName, locationQName, locationInfo);
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

  @Override
  public boolean isValid() {
    return true;
  }

  //@SuppressWarnings({"HardCodedStringLiteral"}) public void writeExternal(Element parentNode) throws WriteExternalException {
  //  super.writeExternal(parentNode);
  //  if (getQualifiedName() != null) {
  //    parentNode.setAttribute("class_name", getQualifiedName());
  //  }
  //  if (getPackageName() != null) {
  //    parentNode.setAttribute("package_name", getPackageName());
  //  }
  //}

  @Override
  public PsiElement getEvaluationElement() {
    if (getClassName() == null) {
      return null;
    }
    return JavaPsiFacade.getInstance(myProject).findClass(getClassName(), GlobalSearchScope.allScope(myProject));
  }

  @Override
  public void readExternal(Element parentNode) throws InvalidDataException {
    super.readExternal(parentNode);

    String packageName = parentNode.getAttributeValue("package_name");
    setPackageName(packageName != null ? packageName : calcPackageName(packageName));

    try {
      getProperties().NOTIFY_CAUGHT = Boolean.parseBoolean(JDOMExternalizerUtil.readField(parentNode, "NOTIFY_CAUGHT"));
    }
    catch (Exception ignore) {
    }
    try {
      getProperties().NOTIFY_UNCAUGHT = Boolean.parseBoolean(JDOMExternalizerUtil.readField(parentNode, "NOTIFY_UNCAUGHT"));
    }
    catch (Exception ignore) {
    }

    String className = parentNode.getAttributeValue("class_name");
    setQualifiedName(className);
    if (className == null) {
      throw new InvalidDataException(getReadNoClassName());
    }
  }

  private boolean isNotifyCaught() {
    return getProperties().NOTIFY_CAUGHT;
  }

  private boolean isNotifyUncaught() {
    return getProperties().NOTIFY_UNCAUGHT;
  }

  private @NlsSafe String getQualifiedName() {
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

  protected static String getReadNoClassName() {
    return JavaDebuggerBundle.message("error.absent.exception.breakpoint.class.name");
  }
}
