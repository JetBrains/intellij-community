/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
 * Class MethodBreakpoint
 * @author Jeka
 */
package com.intellij.debugger.ui.breakpoints;

import com.intellij.debugger.DebuggerBundle;
import com.intellij.debugger.DebuggerManagerEx;
import com.intellij.debugger.SourcePosition;
import com.intellij.debugger.engine.DebugProcessImpl;
import com.intellij.debugger.engine.JVMName;
import com.intellij.debugger.engine.JVMNameUtil;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.intellij.debugger.engine.requests.RequestManagerImpl;
import com.intellij.debugger.impl.DebuggerUtilsEx;
import com.intellij.debugger.impl.PositionUtil;
import com.intellij.debugger.requests.Requestor;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMExternalizerUtil;
import com.intellij.openapi.util.Key;
import com.intellij.psi.*;
import com.intellij.util.StringBuilderSpinAllocator;
import com.intellij.util.text.CharArrayUtil;
import com.intellij.xdebugger.breakpoints.XBreakpoint;
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
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.java.debugger.breakpoints.properties.JavaMethodBreakpointProperties;

import javax.swing.*;
import java.util.Set;

public class MethodBreakpoint extends BreakpointWithHighlighter<JavaMethodBreakpointProperties> {
  private static final Logger LOG = Logger.getInstance("#com.intellij.debugger.ui.breakpoints.MethodBreakpoint");
  @Nullable private JVMName mySignature;
  private boolean myIsStatic;

  public static final @NonNls Key<MethodBreakpoint> CATEGORY = BreakpointCategory.lookup("method_breakpoints");

  protected MethodBreakpoint(@NotNull Project project, XBreakpoint breakpoint) {
    super(project, breakpoint);
  }

  public boolean isStatic() {
    return myIsStatic;
  }

  @NotNull
  public Key<MethodBreakpoint> getCategory() {
    return CATEGORY;
  }

  @Nullable
  public PsiMethod getPsiMethod() {
    Document document = getDocument();
    if(document == null) {
      return null;
    }
    PsiFile psiFile = PsiDocumentManager.getInstance(myProject).getPsiFile(document);
    if(psiFile instanceof PsiJavaFile) {
      int line = getLineIndex();
      final int offset = CharArrayUtil.shiftForward(document.getCharsSequence(), document.getLineStartOffset(line), " \t");
      return DebuggerUtilsEx.findPsiMethod(psiFile, offset);
    }
    return null;
  }

  public boolean isValid() {
    return super.isValid() && getMethodName() != null;
  }

  protected void reload(@NotNull PsiFile psiFile) {
    setMethodName(null);
    mySignature = null;

    MethodDescriptor descriptor = getMethodDescriptor(myProject, psiFile, getSourcePosition());
    if (descriptor != null) {
      setMethodName(descriptor.methodName);
      mySignature = descriptor.methodSignature;
      myIsStatic = descriptor.isStatic;
    }
    PsiClass psiClass = getPsiClass();
    if (psiClass != null) {
      getProperties().myClassPattern = psiClass.getQualifiedName();
    }
    if (myIsStatic) {
      setInstanceFiltersEnabled(false);
    }
  }

  protected void createRequestForPreparedClass(@NotNull DebugProcessImpl debugProcess, @NotNull ReferenceType classType) {
    try {
      boolean hasMethod = false;
      for (Method method : classType.allMethods()) {
        String signature = method.signature();
        String name = method.name();

        if (getMethodName().equals(name) && mySignature.getName(debugProcess).equals(signature)) {
          hasMethod = true;
          break;
        }
      }

      if(!hasMethod) {
        debugProcess.getRequestsManager().setInvalid(
          this, DebuggerBundle.message("error.invalid.breakpoint.method.not.found", classType.name())
        );
        return;
      }

      RequestManagerImpl requestManager = debugProcess.getRequestsManager();
      if (isWatchEntry()) {
        MethodEntryRequest entryRequest = findRequest(debugProcess, MethodEntryRequest.class, this);
        if (entryRequest == null) {
          entryRequest = requestManager.createMethodEntryRequest(this);
        }
        else {
          entryRequest.disable();
        }
        //entryRequest.addClassFilter(myClassQualifiedName);
        // use addClassFilter(ReferenceType) in order to stop on subclasses also!
        entryRequest.addClassFilter(classType);
        debugProcess.getRequestsManager().enableRequest(entryRequest);
      }
      if (isWatchExit()) {
        MethodExitRequest exitRequest = findRequest(debugProcess, MethodExitRequest.class, this);
        if (exitRequest == null) {
          exitRequest = requestManager.createMethodExitRequest(this);
        }
        else {
          exitRequest.disable();
        }
        //exitRequest.addClassFilter(myClassQualifiedName);
        exitRequest.addClassFilter(classType);
        debugProcess.getRequestsManager().enableRequest(exitRequest);
      }
    }
    catch (Exception e) {
      LOG.debug(e);
    }
  }


  public String getEventMessage(@NotNull LocatableEvent event) {
    final Location location = event.location();
    final String locationQName = DebuggerUtilsEx.getLocationMethodQName(location);
    String locationFileName = "";
    try {
      locationFileName = location.sourceName();
    }
    catch (AbsentInformationException e) {
      locationFileName = getFileName();
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

  public PsiElement getEvaluationElement() {
    return getPsiClass();
  }

  @NotNull
  protected Icon getDisabledIcon(boolean isMuted) {
    final Breakpoint master = DebuggerManagerEx.getInstanceEx(myProject).getBreakpointManager().findMasterBreakpoint(this);
    if (isMuted) {
      return master == null? AllIcons.Debugger.Db_muted_disabled_method_breakpoint : AllIcons.Debugger.Db_muted_dep_method_breakpoint;
    }
    else {
      return master == null? AllIcons.Debugger.Db_disabled_method_breakpoint : AllIcons.Debugger.Db_dep_method_breakpoint;
    }
  }

  @NotNull
  protected Icon getSetIcon(boolean isMuted) {
    return isMuted? AllIcons.Debugger.Db_muted_method_breakpoint : AllIcons.Debugger.Db_method_breakpoint;
  }

  @NotNull
  protected Icon getInvalidIcon(boolean isMuted) {
    return isMuted? AllIcons.Debugger.Db_muted_invalid_method_breakpoint : AllIcons.Debugger.Db_invalid_method_breakpoint;
  }

  @NotNull
  protected Icon getVerifiedIcon(boolean isMuted) {
    return isMuted? AllIcons.Debugger.Db_muted_verified_method_breakpoint : AllIcons.Debugger.Db_verified_method_breakpoint;
  }

  @NotNull
  protected Icon getVerifiedWarningsIcon(boolean isMuted) {
    return isMuted? AllIcons.Debugger.Db_muted_method_warning_breakpoint : AllIcons.Debugger.Db_method_warning_breakpoint;
  }

  public String getDisplayName() {
    final StringBuilder buffer = StringBuilderSpinAllocator.alloc();
    try {
      if(isValid()) {
        final String className = getClassName();
        final boolean classNameExists = className != null && className.length() > 0;
        if (classNameExists) {
          buffer.append(className);
        }
        if(getMethodName() != null) {
          if (classNameExists) {
            buffer.append(".");
          }
          buffer.append(getMethodName());
        }
      }
      else {
        buffer.append(DebuggerBundle.message("status.breakpoint.invalid"));
      }
      return buffer.toString();
    }
    finally {
      StringBuilderSpinAllocator.dispose(buffer);
    }
  }

  public boolean evaluateCondition(@NotNull EvaluationContextImpl context, @NotNull LocatableEvent event) throws EvaluateException {
    if (!matchesEvent(event, context.getDebugProcess())) {
      return false;
    }
    return super.evaluateCondition(context, event);
  }

  public boolean matchesEvent(@NotNull final LocatableEvent event, final DebugProcessImpl process) throws EvaluateException {
    if (getMethodName() == null || mySignature == null) {
      return false;
    }
    final Method method = event.location().method();
    return method != null && method.name().equals(getMethodName()) && method.signature().equals(mySignature.getName(process));
  }

  @Nullable
  public static MethodBreakpoint create(@NotNull Project project, XBreakpoint xBreakpoint) {
    final MethodBreakpoint breakpoint = new MethodBreakpoint(project, xBreakpoint);
    return (MethodBreakpoint)breakpoint.init();
  }


  //public boolean canMoveTo(final SourcePosition position) {
  //  return super.canMoveTo(position) && PositionUtil.getPsiElementAt(getProject(), PsiMethod.class, position) != null;
  //}

  /**
   * finds FQ method's class name and method's signature
   */
  @Nullable
  private static MethodDescriptor getMethodDescriptor(@NotNull final Project project, @NotNull final PsiFile psiJavaFile, @NotNull final SourcePosition sourcePosition) {
    final PsiDocumentManager docManager = PsiDocumentManager.getInstance(project);
    final Document document = docManager.getDocument(psiJavaFile);
    if(document == null) {
      return null;
    }
    //final int endOffset = document.getLineEndOffset(sourcePosition);
    //final MethodDescriptor descriptor = docManager.commitAndRunReadAction(new Computable<MethodDescriptor>() {
    // conflicts with readAction on initial breakpoints creation
    final MethodDescriptor descriptor = ApplicationManager.getApplication().runReadAction(new Computable<MethodDescriptor>() {
      @Nullable
      public MethodDescriptor compute() {
        //PsiMethod method = DebuggerUtilsEx.findPsiMethod(psiJavaFile, endOffset);
        PsiMethod method = PositionUtil.getPsiElementAt(project, PsiMethod.class, sourcePosition);
        if (method == null) {
          return null;
        }
        final int methodOffset = method.getTextOffset();
        if (methodOffset < 0) {
          return null;
        }
        if (document.getLineNumber(methodOffset) < sourcePosition.getLine()) {
          return null;
        }

        final PsiIdentifier identifier = method.getNameIdentifier();
        int methodNameOffset = identifier != null? identifier.getTextOffset() : methodOffset;
        final MethodDescriptor descriptor =
          new MethodDescriptor();
        descriptor.methodName = JVMNameUtil.getJVMMethodName(method);
        try {
          descriptor.methodSignature = JVMNameUtil.getJVMSignature(method);
          descriptor.isStatic = method.hasModifierProperty(PsiModifier.STATIC);
        }
        catch (IndexNotReadyException ignored) {
          return null;
        }
        descriptor.methodLine = document.getLineNumber(methodNameOffset);
        return descriptor;
      }
    });
    if (descriptor == null || descriptor.methodName == null || descriptor.methodSignature == null) {
      return null;
    }
    return descriptor;
  }

  @Nullable
  static <T extends EventRequest> T findRequest(@NotNull DebugProcessImpl debugProcess, Class<T> requestClass, Requestor requestor) {
    Set<EventRequest> requests = debugProcess.getRequestsManager().findRequests(requestor);
    for (EventRequest eventRequest : requests) {
      if (eventRequest.getClass().equals(requestClass)) {
        return (T)eventRequest;
      }
    }
    return null;
  }

  @Override
  public void readExternal(@NotNull Element breakpointNode) throws InvalidDataException {
    super.readExternal(breakpointNode);
    try {
      getProperties().WATCH_ENTRY = Boolean.valueOf(JDOMExternalizerUtil.readField(breakpointNode, "WATCH_ENTRY"));
    } catch (Exception ignored) {
    }
    try {
      getProperties().WATCH_EXIT = Boolean.valueOf(JDOMExternalizerUtil.readField(breakpointNode, "WATCH_EXIT"));
    } catch (Exception ignored) {
    }
  }

  public boolean isBodyAt(@NotNull Document document, int offset) {
    PsiFile psiFile = PsiDocumentManager.getInstance(myProject).getPsiFile(document);
    if(psiFile instanceof PsiJavaFile) {
      PsiMethod method = DebuggerUtilsEx.findPsiMethod(psiFile, offset);
      return method == getPsiMethod();
    }

    return false;
  }

  private boolean isWatchEntry() {
    return getProperties().WATCH_ENTRY;
  }

  private boolean isWatchExit() {
    return getProperties().WATCH_EXIT;
  }

  @Nullable
  private String getMethodName() {
    return getProperties().myMethodName;
  }

  private void setMethodName(@Nullable String methodName) {
    getProperties().myMethodName = methodName;
  }

  private static final class MethodDescriptor {
    String methodName;
    JVMName methodSignature;
    boolean isStatic;
    int methodLine;
  }
}
