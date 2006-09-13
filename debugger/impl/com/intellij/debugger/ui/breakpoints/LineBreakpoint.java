/*
 * Class LineBreakpoint
 * @author Jeka
 */
package com.intellij.debugger.ui.breakpoints;

import com.intellij.debugger.DebuggerBundle;
import com.intellij.debugger.DebuggerManagerEx;
import com.intellij.debugger.SourcePosition;
import com.intellij.debugger.engine.DebugProcessImpl;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.intellij.debugger.impl.DebuggerUtilsEx;
import com.intellij.debugger.impl.PositionUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.jsp.JspFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.ui.classFilter.ClassFilter;
import com.sun.jdi.*;
import com.sun.jdi.event.LocatableEvent;
import com.sun.jdi.request.BreakpointRequest;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.List;

public class LineBreakpoint extends BreakpointWithHighlighter {
  private static final Logger LOG = Logger.getInstance("#com.intellij.debugger.ui.breakpoints.LineBreakpoint");

  // icons
  public static Icon ICON = IconLoader.getIcon("/debugger/db_set_breakpoint.png");
  public static final Icon DISABLED_ICON = IconLoader.getIcon("/debugger/db_disabled_breakpoint.png");
  public static final Icon DISABLED_DEP_ICON = IconLoader.getIcon("/debugger/db_dep_line_breakpoint.png");
  private static Icon ourInvalidIcon = IconLoader.getIcon("/debugger/db_invalid_breakpoint.png");
  private static Icon ourVerifiedIcon = IconLoader.getIcon("/debugger/db_verified_breakpoint.png");

  private String myMethodName;
  public static final @NonNls String CATEGORY = "line_breakpoints";

  protected LineBreakpoint(Project project) {
    super(project);
  }

  protected LineBreakpoint(Project project, RangeHighlighter highlighter) {
    super(project, highlighter);
  }

  protected Icon getDisabledIcon() {
    final Breakpoint master = DebuggerManagerEx.getInstanceEx(myProject).getBreakpointManager().findMasterBreakpoint(this);
    return master == null? DISABLED_ICON : DISABLED_DEP_ICON;
  }

  protected Icon getSetIcon() {
    return ICON;
  }

  protected Icon getInvalidIcon() {
    return ourInvalidIcon;
  }

  protected Icon getVerifiedIcon() {
    return ourVerifiedIcon;
  }

  public String getCategory() {
    return CATEGORY;
  }

  protected void reload(PsiFile file) {
    super.reload(file);
    myMethodName = LineBreakpoint.findMethodName(file, getHighlighter().getStartOffset());
  }

  protected void createRequestForPreparedClass(final DebugProcessImpl debugProcess, final ReferenceType classType) {
    ApplicationManager.getApplication().runReadAction(new Runnable() {
      public void run() {
        try {
          List<Location> locs = debugProcess.getPositionManager().locationsOfLine(classType, getSourcePosition());
          Location location = locs.size() > 0 ? locs.get(0) : null;
          if (location != null) {
            if (LOG.isDebugEnabled()) {
              LOG.debug("Found location for reference type " + classType.name() + " at line " + getLineIndex() + "; isObsolete: " + (debugProcess.getVirtualMachineProxy().versionHigher("1.4") && location.method().isObsolete()));
            }
            BreakpointRequest request = debugProcess.getRequestsManager().createBreakpointRequest(LineBreakpoint.this, location);
            debugProcess.getRequestsManager().enableRequest(request);
            if (LOG.isDebugEnabled()) {
              LOG.debug("Created breakpoint request for reference type " + classType.name() + " at line " + getLineIndex());
            }
          }
          else {
            // there's no executable code in this class
            debugProcess.getRequestsManager().setInvalid(LineBreakpoint.this, DebuggerBundle.message(
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
          debugProcess.getRequestsManager().setInvalid(LineBreakpoint.this, DebuggerBundle.message("error.invalid.breakpoint.bad.line.number"));
        }
        catch (InternalException ex) {
          LOG.info(ex);
        }
        catch(Exception ex) {
          LOG.info(ex);
        }
        updateUI();
      }
    });
  }

  public boolean evaluateCondition(EvaluationContextImpl context, LocatableEvent event) throws EvaluateException {
    if(CLASS_FILTERS_ENABLED){
      Value value = context.getThisObject();
      ObjectReference thisObject = (ObjectReference)value;
      if(thisObject == null) {
        return false;
      }
      String name = DebuggerUtilsEx.getQualifiedClassName(thisObject.referenceType().name(), getProject());
      if(name == null) {
        return false;
      }
      ClassFilter [] filters = getClassFilters();
      boolean matches = false;
      for (int i = 0; i < filters.length; i++) {
        ClassFilter classFilter = filters[i];
        if(classFilter.isEnabled() && classFilter.matches(name)) {
          matches = true;
          break;
        }
      }
      if(!matches) {
        return false;
      }

      ClassFilter [] ifilters = getClassExclusionFilters();
      for (int i = 0; i < ifilters.length; i++) {
        ClassFilter classFilter = ifilters[i];
        if(classFilter.isEnabled() && classFilter.matches(name)) {
          return false;
        }
      }
    }
    return super.evaluateCondition(context, event);
  }

  public String toString() {
    return getDescription();
  }


  public String getDisplayName() {
    final int lineNumber = (getHighlighter().getDocument().getLineNumber(getHighlighter().getStartOffset()) + 1);
    if(isValid()) {
      final String className = getClassName();
      final boolean hasClassInfo = className != null && className.length() > 0;
      final boolean hasMethodInfo = myMethodName != null && myMethodName.length() > 0;
      if (hasClassInfo || hasMethodInfo) {
        final StringBuffer info = new StringBuffer();
        if (hasClassInfo) {
          info.append(className);
        }
        if(hasMethodInfo) {
          if (hasClassInfo) {
            info.append(".");
          }
          info.append(myMethodName);
        }
        return DebuggerBundle.message("line.breakpoint.display.name.with.class.or.method", lineNumber, info.toString());
      }
      return DebuggerBundle.message("line.breakpoint.display.name", lineNumber);
    }
    return DebuggerBundle.message("status.breakpoint.invalid");
  }

  private static @Nullable String findMethodName(final PsiFile file, final int offset) {
    if (file instanceof JspFile) {
      return null;
    }
    if (file instanceof PsiJavaFile) {
      return ApplicationManager.getApplication().runReadAction(new Computable<String>() {
        public String compute() {
          final PsiMethod method = DebuggerUtilsEx.findPsiMethod(file, offset);
          return method != null? method.getName() + "()" : null;
        }
      });
    }
    return null;
  }

  public String getEventMessage(LocatableEvent event) {
    return DebuggerBundle.message("status.line.breakpoint.reached", event.location().declaringType().name(), getLineIndex() + 1);
  }

  public PsiElement getEvaluationElement() {
    return PositionUtil.getContextElement(getSourcePosition());
  }

  protected static LineBreakpoint create(Project project, Document document, int lineIndex) {
    VirtualFile virtualFile = FileDocumentManager.getInstance().getFile(document);
    if (virtualFile == null) return null;

    LineBreakpoint breakpoint = new LineBreakpoint(project, createHighlighter(project, document, lineIndex));
    return (LineBreakpoint)breakpoint.init();
  }

  public boolean canMoveTo(SourcePosition position) {
    if (!canAddLineBreakpoint(myProject, getDocument(), position.getLine())) {
      return false;
    }
    return super.canMoveTo(position);
  }

  public static boolean canAddLineBreakpoint(Project project, final Document document, final int lineIndex) {
    if (lineIndex < 0 || lineIndex >= document.getLineCount()) {
      return false;
    }
    final BreakpointWithHighlighter breakpointAtLine = DebuggerManagerEx.getInstanceEx(project).getBreakpointManager().findBreakpoint(
      document,
      document.getLineStartOffset(lineIndex)
    );
    if (breakpointAtLine != null && CATEGORY.equals(breakpointAtLine.getCategory())) {
      // there already exists a line breakpoint at this line
      return false;
    }
    final boolean[] canAdd = new boolean[]{false};

    PsiDocumentManager.getInstance(project).commitDocument(document);

    DebuggerUtilsEx.iterateLine(project, document, lineIndex, new DebuggerUtilsEx.ElementVisitor() {
      public boolean acceptElement(PsiElement element) {
        if ((element instanceof PsiWhiteSpace) || (PsiTreeUtil.getParentOfType(element, PsiComment.class, false) != null)) {
          return false;
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
        return true;
      }
    });

    return canAdd[0];
  }

  public @Nullable String getMethodName() {
    return myMethodName;
  }
}
