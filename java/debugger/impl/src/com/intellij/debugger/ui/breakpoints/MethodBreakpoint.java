/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.Key;
import com.intellij.psi.*;
import com.intellij.util.StringBuilderSpinAllocator;
import com.intellij.util.text.CharArrayUtil;
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
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Iterator;
import java.util.Set;

public class MethodBreakpoint extends BreakpointWithHighlighter {
  private static final Logger LOG = Logger.getInstance("#com.intellij.debugger.ui.breakpoints.MethodBreakpoint");
  public boolean WATCH_ENTRY = true;
  public boolean WATCH_EXIT  = true;

  private String myMethodName;
  private JVMName mySignature;
  private boolean myIsStatic;

  public static Icon ICON = IconLoader.getIcon("/debugger/db_method_breakpoint.png");
  public static Icon MUTED_ICON = IconLoader.getIcon("/debugger/db_muted_method_breakpoint.png");
  public static Icon DISABLED_ICON = IconLoader.getIcon("/debugger/db_disabled_method_breakpoint.png");
  public static Icon DISABLED_DEP_ICON = IconLoader.getIcon("/debugger/db_dep_method_breakpoint.png");
  public static Icon MUTED_DISABLED_ICON = IconLoader.getIcon("/debugger/db_muted_disabled_method_breakpoint.png");
  public static Icon MUTED_DISABLED_DEP_ICON = IconLoader.getIcon("/debugger/db_muted_dep_method_breakpoint.png");
  private static final Icon ourInvalidIcon = IconLoader.getIcon("/debugger/db_invalid_method_breakpoint.png");
  private static final Icon ourMutedInvalidIcon = IconLoader.getIcon("/debugger/db_muted_invalid_method_breakpoint.png");
  private static final Icon ourVerifiedIcon = IconLoader.getIcon("/debugger/db_verified_method_breakpoint.png");
  private static final Icon ourMutedVerifiedIcon = IconLoader.getIcon("/debugger/db_muted_verified_method_breakpoint.png");
  private static final Icon ourVerifiedWarningIcon = IconLoader.getIcon("/debugger/db_method_warning_breakpoint.png");
  private static final Icon ourMutedVerifiedWarningIcon = IconLoader.getIcon("/debugger/db_muted_method_warning_breakpoint.png");
  public static final @NonNls Key<MethodBreakpoint> CATEGORY = BreakpointCategory.lookup("method_breakpoints");

  protected MethodBreakpoint(Project project) {
    super(project);
  }

  private MethodBreakpoint(Project project, RangeHighlighter highlighter) {
    super(project, highlighter);
  }

  public boolean isStatic() {
    return myIsStatic;
  }

  public Key<MethodBreakpoint> getCategory() {
    return CATEGORY;
  }

  public PsiMethod getPsiMethod() {
    Document document = getDocument();
    if(document == null) return null;
    PsiFile psiFile = PsiDocumentManager.getInstance(myProject).getPsiFile(document);
    if(psiFile instanceof PsiJavaFile) {
      int line = getLineIndex();
      final int offset = CharArrayUtil.shiftForward(document.getCharsSequence(), document.getLineStartOffset(line), " \t");
      return DebuggerUtilsEx.findPsiMethod(psiFile, offset);
    }
    return null;
  }

  public boolean isValid() {
    return super.isValid() && myMethodName != null;
  }

  protected void reload(PsiFile psiFile) {
    myMethodName = null;
    mySignature = null;

    MethodDescriptor descriptor = getMethodDescriptor(myProject, psiFile, getSourcePosition());
    if (descriptor != null) {
      myMethodName = descriptor.methodName;
      mySignature = descriptor.methodSignature;
      myIsStatic = descriptor.isStatic;
    }
    if (myIsStatic) {
      INSTANCE_FILTERS_ENABLED = false;
    }
  }

  protected void createRequestForPreparedClass(DebugProcessImpl debugProcess, ReferenceType classType) {
    try {
      boolean hasMethod = false;
      for (Iterator iterator = classType.allMethods().iterator(); iterator.hasNext();) {
        Method method = (Method)iterator.next();
        String signature = method.signature();
        String name = method.name();

        if (myMethodName.equals(name) && mySignature.getName(debugProcess).equals(signature)) {
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
      if (WATCH_ENTRY) {
        MethodEntryRequest entryRequest = (MethodEntryRequest)findRequest(debugProcess, MethodEntryRequest.class);
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
      if (WATCH_EXIT) {
        MethodExitRequest exitRequest = (MethodExitRequest)findRequest(debugProcess, MethodExitRequest.class);
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


  public String getEventMessage(LocatableEvent event) {
    final Location location = event.location();
    final String locationQName = location.declaringType().name() + "." + location.method().name();
    String locationFileName = "";
    try {
      locationFileName = location.sourceName();
    }
    catch (AbsentInformationException e) {
      locationFileName = getSourcePosition().getFile().getName();
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

  protected Icon getDisabledIcon(boolean isMuted) {
    final Breakpoint master = DebuggerManagerEx.getInstanceEx(myProject).getBreakpointManager().findMasterBreakpoint(this);
    if (isMuted) {
      return master == null? MUTED_DISABLED_ICON : MUTED_DISABLED_DEP_ICON;
    }
    else {
      return master == null? DISABLED_ICON : DISABLED_DEP_ICON;
    }
  }

  protected Icon getSetIcon(boolean isMuted) {
    return isMuted? MUTED_ICON : ICON;
  }

  protected Icon getInvalidIcon(boolean isMuted) {
    return isMuted? ourMutedInvalidIcon : ourInvalidIcon;
  }

  protected Icon getVerifiedIcon(boolean isMuted) {
    return isMuted? ourMutedVerifiedIcon : ourVerifiedIcon;
  }

  protected Icon getVerifiedWarningsIcon(boolean isMuted) {
    return isMuted? ourMutedVerifiedWarningIcon : ourVerifiedWarningIcon;
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
        if(myMethodName != null) {
          if (classNameExists) {
            buffer.append(".");
          }
          buffer.append(myMethodName);
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

  public boolean evaluateCondition(EvaluationContextImpl context, LocatableEvent event) throws EvaluateException {
    if (!matchesEvent(event, context.getDebugProcess())) {
      return false;
    }
    return super.evaluateCondition(context, event);
  }

  public boolean matchesEvent(final LocatableEvent event, final DebugProcessImpl process) throws EvaluateException {
    if (myMethodName == null || mySignature == null) {
      return false;
    }
    final Method method = event.location().method();
    return method != null && method.name().equals(myMethodName) && method.signature().equals(mySignature.getName(process));
  }

  public static MethodBreakpoint create(Project project, Document document, int lineIndex) {
    final MethodBreakpoint breakpoint = new MethodBreakpoint(project, createHighlighter(project, document, lineIndex));
    return (MethodBreakpoint)breakpoint.init();
  }


  public boolean canMoveTo(final SourcePosition position) {
    return super.canMoveTo(position) && PositionUtil.getPsiElementAt(getProject(), PsiMethod.class, position) != null;
  }

  /**
   * finds FQ method's class name and method's signature
   */
  @Nullable
  private static MethodDescriptor getMethodDescriptor(final Project project, final PsiFile psiJavaFile, final SourcePosition sourcePosition) {
    final PsiDocumentManager docManager = PsiDocumentManager.getInstance(project);
    final Document document = docManager.getDocument(psiJavaFile);
    if(document == null) {
      return null;
    }
    //final int endOffset = document.getLineEndOffset(sourcePosition);
    final MethodDescriptor descriptor = docManager.commitAndRunReadAction(new Computable<MethodDescriptor>() {
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
        //noinspection HardCodedStringLiteral
        descriptor.methodName = method.isConstructor() ? "<init>" : method.getName();
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

  public String toString() {
    return getDescription();
  }

  public boolean isBodyAt(Document document, int offset) {
    PsiFile psiFile = PsiDocumentManager.getInstance(myProject).getPsiFile(document);
    if(psiFile instanceof PsiJavaFile) {
      PsiMethod method = DebuggerUtilsEx.findPsiMethod(psiFile, offset);
      return method == getPsiMethod();
    }

    return false;
  }

  private static final class MethodDescriptor {
    String methodName;
    JVMName methodSignature;
    boolean isStatic;
    int methodLine;
  }
}
