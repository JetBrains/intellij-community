/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

/*
 * Class LineBreakpoint
 * @author Jeka
 */
package com.intellij.debugger.ui.breakpoints;

import com.intellij.debugger.DebuggerBundle;
import com.intellij.debugger.DebuggerManagerEx;
import com.intellij.debugger.SourcePosition;
import com.intellij.debugger.engine.ContextUtil;
import com.intellij.debugger.engine.DebugProcessImpl;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.intellij.debugger.impl.DebuggerUtilsEx;
import com.intellij.debugger.jdi.StackFrameProxyImpl;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.jsp.JspFile;
import com.intellij.psi.search.EverythingGlobalScope;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.StringBuilderSpinAllocator;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.xdebugger.XDebuggerUtil;
import com.intellij.xdebugger.XSourcePosition;
import com.intellij.xdebugger.breakpoints.XBreakpoint;
import com.intellij.xdebugger.breakpoints.XBreakpointType;
import com.sun.jdi.*;
import com.sun.jdi.event.LocatableEvent;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.java.debugger.breakpoints.properties.JavaBreakpointProperties;
import org.jetbrains.jps.model.java.JavaModuleSourceRootTypes;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.regex.Pattern;

public class LineBreakpoint<P extends JavaBreakpointProperties> extends BreakpointWithHighlighter<P> {
  private static final Logger LOG = Logger.getInstance("#com.intellij.debugger.ui.breakpoints.LineBreakpoint");

  public static final @NonNls Key<LineBreakpoint> CATEGORY = BreakpointCategory.lookup("line_breakpoints");

  protected LineBreakpoint(Project project, XBreakpoint xBreakpoint) {
    super(project, xBreakpoint);
  }

  @Override
  protected Icon getDisabledIcon(boolean isMuted) {
    if (DebuggerManagerEx.getInstanceEx(myProject).getBreakpointManager().findMasterBreakpoint(this) != null) {
      return isMuted ? AllIcons.Debugger.Db_muted_dep_line_breakpoint : AllIcons.Debugger.Db_dep_line_breakpoint;
    }
    return null;
  }

  @Override
  protected Icon getInvalidIcon(boolean isMuted) {
    return isMuted? AllIcons.Debugger.Db_muted_invalid_breakpoint : AllIcons.Debugger.Db_invalid_breakpoint;
  }

  @Override
  protected Icon getVerifiedIcon(boolean isMuted) {
    if (isRemoveAfterHit()) {
      return isMuted ? AllIcons.Debugger.Db_muted_temporary_breakpoint : AllIcons.Debugger.Db_temporary_breakpoint;
    }
    return isMuted? AllIcons.Debugger.Db_muted_verified_breakpoint : AllIcons.Debugger.Db_verified_breakpoint;
  }

  @Override
  protected Icon getVerifiedWarningsIcon(boolean isMuted) {
    return isMuted? AllIcons.Debugger.Db_muted_verified_warning_breakpoint : AllIcons.Debugger.Db_verified_warning_breakpoint;
  }

  @Override
  public Key<LineBreakpoint> getCategory() {
    return CATEGORY;
  }

  @Override
  protected void createOrWaitPrepare(DebugProcessImpl debugProcess, String classToBeLoaded) {
    if (isInScopeOf(debugProcess, classToBeLoaded)) {
      super.createOrWaitPrepare(debugProcess, classToBeLoaded);
    }
  }

  @Override
  protected void createRequestForPreparedClass(final DebugProcessImpl debugProcess, final ReferenceType classType) {
    if (!isInScopeOf(debugProcess, classType.name())) {
      if (LOG.isDebugEnabled()) {
        LOG.debug(classType.name() + " is out of debug-process scope, breakpoint request won't be created for line " + getLineIndex());
      }
      return;
    }
    try {
      List<Location> locations = debugProcess.getPositionManager().locationsOfLine(classType, getSourcePosition());
      if (!locations.isEmpty()) {
        for (Location loc : locations) {
          if (LOG.isDebugEnabled()) {
            LOG.debug("Found location [codeIndex=" + loc.codeIndex() +"] for reference type " + classType.name() + " at line " + getLineIndex() + "; isObsolete: " + (debugProcess.getVirtualMachineProxy().versionHigher("1.4") && loc.method().isObsolete()));
          }
          if (!acceptLocation(debugProcess, classType, loc)) {
            continue;
          }
          createLocationBreakpointRequest(this, loc, debugProcess);
          if (LOG.isDebugEnabled()) {
            LOG.debug("Created breakpoint request for reference type " + classType.name() + " at line " + getLineIndex() + "; codeIndex=" + loc.codeIndex());
          }
        }
      }
      else if (DebuggerUtilsEx.allLineLocations(classType) == null) {
        // there's no line info in this class
        debugProcess.getRequestsManager()
          .setInvalid(this, DebuggerBundle.message("error.invalid.breakpoint.no.line.info", classType.name()));
        if (LOG.isDebugEnabled()) {
          LOG.debug("No line number info in " + classType.name());
        }
      }
      else {
        // there's no executable code in this class
        debugProcess.getRequestsManager().setInvalid(this, DebuggerBundle.message(
          "error.invalid.breakpoint.no.executable.code", (getLineIndex() + 1), classType.name())
        );
        if (LOG.isDebugEnabled()) {
          LOG.debug("No locations of type " + classType.name() + " found at line " + getLineIndex());
        }
      }
    }
    catch (ClassNotPreparedException ex) {
      if (LOG.isDebugEnabled()) {
        LOG.debug("ClassNotPreparedException: " + ex.getMessage());
      }
      // there's a chance to add a breakpoint when the class is prepared
    }
    catch (ObjectCollectedException ex) {
      if (LOG.isDebugEnabled()) {
        LOG.debug("ObjectCollectedException: " + ex.getMessage());
      }
      // there's a chance to add a breakpoint when the class is prepared
    }
    catch (InvalidLineNumberException ex) {
      if (LOG.isDebugEnabled()) {
        LOG.debug("InvalidLineNumberException: " + ex.getMessage());
      }
      debugProcess.getRequestsManager().setInvalid(this, DebuggerBundle.message("error.invalid.breakpoint.bad.line.number"));
    }
    catch(Exception ex) {
      LOG.info(ex);
    }
    updateUI();
  }

  private static final Pattern ourAnonymousPattern = Pattern.compile(".*\\$\\d*$");
  private static boolean isAnonymousClass(ReferenceType classType) {
    if (classType instanceof ClassType) {
      return ourAnonymousPattern.matcher(classType.name()).matches();
    }
    return false;
  }

  protected boolean acceptLocation(final DebugProcessImpl debugProcess, ReferenceType classType, final Location loc) {
    Method method = loc.method();
    // Some frameworks may create synthetic methods with lines mapped to user code, see IDEA-143852
    // if (DebuggerUtils.isSynthetic(method)) { return false; }
    if (isAnonymousClass(classType)) {
      if ((method.isConstructor() && loc.codeIndex() == 0) || method.isBridge()) return false;
    }
    SourcePosition position = debugProcess.getPositionManager().getSourcePosition(loc);
    if (position == null) return false;

    return ReadAction.compute(() -> {
      JavaLineBreakpointType type = getXBreakpointType();
      if (type == null) return true;
      return type.matchesPosition(this, position);
    });
  }

  @Nullable
  protected JavaLineBreakpointType getXBreakpointType() {
    XBreakpointType<?, P> type = myXBreakpoint.getType();
    // Nashorn breakpoints do not contain JavaLineBreakpointType
    if (type instanceof JavaLineBreakpointType) {
      return (JavaLineBreakpointType)type;
    }
    return null;
  }

  private boolean isInScopeOf(DebugProcessImpl debugProcess, String className) {
    final SourcePosition position = getSourcePosition();
    if (position != null) {
      final VirtualFile breakpointFile = position.getFile().getVirtualFile();
      final ProjectFileIndex fileIndex = ProjectRootManager.getInstance(myProject).getFileIndex();
      if (breakpointFile != null && fileIndex.isUnderSourceRootOfType(breakpointFile, JavaModuleSourceRootTypes.SOURCES)) {
        if (debugProcess.getSearchScope().contains(breakpointFile)) {
          return true;
        }
        // apply filtering to breakpoints from content sources only, not for sources attached to libraries
        final Collection<VirtualFile> candidates = findClassCandidatesInSourceContent(className, debugProcess.getSearchScope(), fileIndex);
        if (LOG.isDebugEnabled()) {
          LOG.debug("Found "+ (candidates == null? "null" : candidates.size()) + " candidate containing files for class " + className);
        }
        if (candidates == null) {
          // If no candidates are found in scope then assume that class is loaded dynamically and allow breakpoint
          return true;
        }

        // breakpointFile is not in scope here and there are some candidates in scope
        //for (VirtualFile classFile : candidates) {
        //  if (LOG.isDebugEnabled()) {
        //    LOG.debug("Breakpoint file: " + breakpointFile.getPath()+ "; candidate file: " + classFile.getPath());
        //  }
        //  if (breakpointFile.equals(classFile)) {
        //    return true;
        //  }
        //}
        if (LOG.isDebugEnabled()) {
          final GlobalSearchScope scope = debugProcess.getSearchScope();
          final boolean contains = scope.contains(breakpointFile);
          List<VirtualFile> files = ContainerUtil.map(
            JavaPsiFacade.getInstance(myProject).findClasses(className, scope),
            aClass -> aClass.getContainingFile().getVirtualFile());
          List<VirtualFile> allFiles = ContainerUtil.map(
            JavaPsiFacade.getInstance(myProject).findClasses(className, new EverythingGlobalScope(myProject)),
            aClass -> aClass.getContainingFile().getVirtualFile());
          final VirtualFile contentRoot = fileIndex.getContentRootForFile(breakpointFile);
          final Module module = fileIndex.getModuleForFile(breakpointFile);

          LOG.debug("Did not find '" +
                    className + "' in " + scope +
                    "; contains=" + contains +
                    "; contentRoot=" + contentRoot +
                    "; module = " + module +
                    "; all files in index are: " + files+
                    "; all possible files are: " + allFiles
          );
        }

        return false;
      }
    }
    return true;
  }

  @Nullable
  private Collection<VirtualFile> findClassCandidatesInSourceContent(final String className, final GlobalSearchScope scope, final ProjectFileIndex fileIndex) {
    final int dollarIndex = className.indexOf("$");
    final String topLevelClassName = dollarIndex >= 0? className.substring(0, dollarIndex) : className;
    return ReadAction.compute(() -> {
      final PsiClass[] classes = JavaPsiFacade.getInstance(myProject).findClasses(topLevelClassName, scope);
      if (LOG.isDebugEnabled()) {
        LOG.debug("Found "+ classes.length + " classes " + topLevelClassName + " in scope "+scope);
      }
      if (classes.length == 0) {
        return null;
      }
      final List<VirtualFile> list = new ArrayList<>(classes.length);
      for (PsiClass aClass : classes) {
        final PsiFile psiFile = aClass.getContainingFile();

        if (LOG.isDebugEnabled()) {
          final StringBuilder msg = new StringBuilder();
          msg.append("Checking class ").append(aClass.getQualifiedName());
          msg.append("\n\t").append("PsiFile=").append(psiFile);
          if (psiFile != null) {
            final VirtualFile vFile = psiFile.getVirtualFile();
            msg.append("\n\t").append("VirtualFile=").append(vFile);
            if (vFile != null) {
              msg.append("\n\t").append("isInSourceContent=").append(fileIndex.isUnderSourceRootOfType(vFile, JavaModuleSourceRootTypes.SOURCES));
            }
          }
          LOG.debug(msg.toString());
        }

        if (psiFile == null) {
          return null;
        }
        final VirtualFile vFile = psiFile.getVirtualFile();
        if (vFile == null || !fileIndex.isUnderSourceRootOfType(vFile, JavaModuleSourceRootTypes.SOURCES)) {
          return null; // this will switch off the check if at least one class is from libraries
        }
        list.add(vFile);
      }
      return list;
    });
  }

  @Override
  protected String calculateEventClass(EvaluationContextImpl context, LocatableEvent event) throws EvaluateException {
    String className = null;
    final ObjectReference thisObject = (ObjectReference)context.computeThisObject();
    if (thisObject != null) {
      className = thisObject.referenceType().name();
    }
    else {
      final StackFrameProxyImpl frame = context.getFrameProxy();
      if (frame != null) {
        className = frame.location().declaringType().name();
      }
    }
    return className;
  }

  @Override
  public String getShortName() {
    return getDisplayInfoInternal(false, 30);
  }

  @Override
  public String getDisplayName() {
    return getDisplayInfoInternal(true, -1);
  }

  private String getDisplayInfoInternal(boolean showPackageInfo, int totalTextLength) {
    if(isValid()) {
      final int lineNumber = myXBreakpoint.getSourcePosition().getLine() + 1;
      String className = getClassName();
      final boolean hasClassInfo = className != null && className.length() > 0;
      final String methodName = getMethodName();
      final String displayName = methodName != null? methodName + "()" : null;
      final boolean hasMethodInfo = displayName != null && displayName.length() > 0;
      if (hasClassInfo || hasMethodInfo) {
        final StringBuilder info = StringBuilderSpinAllocator.alloc();
        try {
          boolean isFile = myXBreakpoint.getSourcePosition().getFile().getName().equals(className);
          String packageName = null;
          if (hasClassInfo) {
            final int dotIndex = className.lastIndexOf(".");
            if (dotIndex >= 0 && !isFile) {
              packageName = className.substring(0, dotIndex);
              className = className.substring(dotIndex + 1);
            }

            if (totalTextLength != -1) {
              if (className.length() + (hasMethodInfo ? displayName.length() : 0) > totalTextLength + 3) {
                int offset = totalTextLength - (hasMethodInfo ? displayName.length() : 0);
                if (offset > 0 && offset < className.length()) {
                  className = className.substring(className.length() - offset);
                  info.append("...");
                }
              }
            }

            info.append(className);
          }
          if(hasMethodInfo) {
            if (isFile) {
              info.append(":");
            }
            else if (hasClassInfo) {
              info.append(".");
            }
            info.append(displayName);
          }
          if (showPackageInfo && packageName != null) {
            info.append(" (").append(packageName).append(")");
          }
          return DebuggerBundle.message("line.breakpoint.display.name.with.class.or.method", lineNumber, info.toString());
        }
        finally {
          StringBuilderSpinAllocator.dispose(info);
        }
      }
      return DebuggerBundle.message("line.breakpoint.display.name", lineNumber);
    }
    return DebuggerBundle.message("status.breakpoint.invalid");
  }

  @Nullable
  private static String findOwnerMethod(final PsiFile file, final int offset) {
    if (offset < 0 || file instanceof JspFile) {
      return null;
    }
    if (file instanceof PsiClassOwner) {
      return ReadAction.compute(() -> {
        PsiMethod method = DebuggerUtilsEx.findPsiMethod(file, offset);
        return method != null? method.getName() : null;
      });
    }
    return null;
  }

  @Override
  public String getEventMessage(LocatableEvent event) {
    final Location location = event.location();
    String sourceName = DebuggerUtilsEx.getSourceName(location, e -> getFileName());

    return DebuggerBundle.message(
      "status.line.breakpoint.reached",
      DebuggerUtilsEx.getLocationMethodQName(location),
      sourceName,
      getLineIndex() + 1
    );
  }

  @Override
  public PsiElement getEvaluationElement() {
    return ContextUtil.getContextElement(getSourcePosition());
  }

  public static LineBreakpoint create(@NotNull Project project, XBreakpoint xBreakpoint) {
    LineBreakpoint breakpoint = new LineBreakpoint(project, xBreakpoint);
    return (LineBreakpoint)breakpoint.init();
  }

  //@Override
  //public boolean canMoveTo(SourcePosition position) {
  //  if (!super.canMoveTo(position)) {
  //    return false;
  //  }
  //  final Document document = PsiDocumentManager.getInstance(getProject()).getDocument(position.getFile());
  //  return canAddLineBreakpoint(myProject, document, position.getLine());
  //}

  public static boolean canAddLineBreakpoint(Project project, final Document document, final int lineIndex) {
    if (lineIndex < 0 || lineIndex >= document.getLineCount()) {
      return false;
    }
    final BreakpointManager breakpointManager = DebuggerManagerEx.getInstanceEx(project).getBreakpointManager();
    final LineBreakpoint breakpointAtLine = breakpointManager.findBreakpoint( document, document.getLineStartOffset(lineIndex), CATEGORY);
    if (breakpointAtLine != null) {
      // there already exists a line breakpoint at this line
      return false;
    }
    PsiDocumentManager.getInstance(project).commitDocument(document);

    final boolean[] canAdd = new boolean[]{false};
    XDebuggerUtil.getInstance().iterateLine(project, document, lineIndex, element -> {
      if ((element instanceof PsiWhiteSpace) || (PsiTreeUtil.getParentOfType(element, PsiComment.class, false) != null)) {
        return true;
      }
      PsiElement child = element;
      while(element != null) {

        final int offset = element.getTextOffset();
        if (offset >= 0) {
          if (document.getLineNumber(offset) != lineIndex) {
            break;
          }
        }
        child = element;
        element = element.getParent();
      }

      if(child instanceof PsiMethod && child.getTextRange().getEndOffset() >= document.getLineEndOffset(lineIndex)) {
        PsiCodeBlock body = ((PsiMethod)child).getBody();
        if(body == null) {
          canAdd[0] = false;
        }
        else {
          PsiStatement[] statements = body.getStatements();
          canAdd[0] = statements.length > 0 && document.getLineNumber(statements[0].getTextOffset()) == lineIndex;
        }
      }
      else {
        canAdd[0] = true;
      }
      return false;
    });

    return canAdd[0];
  }

  @Nullable
  public String getMethodName() {
    XSourcePosition position = myXBreakpoint.getSourcePosition();
    if (position != null) {
      int offset = position.getOffset();
      return findOwnerMethod(DebuggerUtilsEx.getPsiFile(myXBreakpoint.getSourcePosition(), myProject), offset);
    }
    return null;
  }
}
