// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.ui.breakpoints;

import com.intellij.debugger.DebuggerBundle;
import com.intellij.debugger.HelpID;
import com.intellij.debugger.SourcePosition;
import com.intellij.debugger.engine.PositionManagerImpl;
import com.intellij.debugger.impl.DebuggerUtilsEx;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.util.SmartList;
import com.intellij.xdebugger.XDebuggerUtil;
import com.intellij.xdebugger.XSourcePosition;
import com.intellij.xdebugger.breakpoints.XBreakpoint;
import com.intellij.xdebugger.breakpoints.XLineBreakpoint;
import com.intellij.xdebugger.breakpoints.ui.XBreakpointGroupingRule;
import com.intellij.xdebugger.impl.XSourcePositionImpl;
import com.intellij.xdebugger.impl.breakpoints.XLineBreakpointImpl;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.java.debugger.breakpoints.properties.JavaBreakpointProperties;
import org.jetbrains.java.debugger.breakpoints.properties.JavaLineBreakpointProperties;

import javax.swing.*;
import java.util.Collections;
import java.util.List;

/**
 * Base class for java line-connected exceptions (line, method, field)
 * @author egor
 */
public class JavaLineBreakpointType extends JavaLineBreakpointTypeBase<JavaLineBreakpointProperties> {
  public JavaLineBreakpointType() {
    super("java-line", DebuggerBundle.message("line.breakpoints.tab.title"));
  }

  protected JavaLineBreakpointType(@NonNls @NotNull String id, @Nls @NotNull String title) {
    super(id, title);
  }

  //@Override
  protected String getHelpID() {
    return HelpID.LINE_BREAKPOINTS;
  }

  //@Override
  public String getDisplayName() {
    return DebuggerBundle.message("line.breakpoints.tab.title");
  }

  @Override
  public List<XBreakpointGroupingRule<XLineBreakpoint<JavaLineBreakpointProperties>, ?>> getGroupingRules() {
    return XDebuggerUtil.getInstance().getGroupingByFileRuleAsList();
  }

  @Nullable
  @Override
  public JavaLineBreakpointProperties createProperties() {
    return new JavaLineBreakpointProperties();
  }

  @NotNull
  @Override
  public JavaLineBreakpointProperties createBreakpointProperties(@NotNull VirtualFile file, int line) {
    return new JavaLineBreakpointProperties();
  }

  @NotNull
  @Override
  public Breakpoint<JavaLineBreakpointProperties> createJavaBreakpoint(Project project, XBreakpoint<JavaLineBreakpointProperties> breakpoint) {
    return new LineBreakpoint<>(project, breakpoint);
  }

  @Override
  public int getPriority() {
    return 100;
  }

  @NotNull
  @Override
  public List<JavaBreakpointVariant> computeVariants(@NotNull Project project, @NotNull XSourcePosition position) {
    SourcePosition pos = DebuggerUtilsEx.toSourcePosition(position, project);
    if (pos == null) {
      return Collections.emptyList();
    }

    List<PsiLambdaExpression> lambdas = DebuggerUtilsEx.collectLambdas(pos, true);
    if (lambdas.isEmpty()) {
      return Collections.emptyList();
    }

    PsiElement startMethod = DebuggerUtilsEx.getContainingMethod(pos);
    //noinspection SuspiciousMethodCalls
    if (lambdas.contains(startMethod) && lambdas.size() == 1) {
      return Collections.emptyList();
    }

    Document document = PsiDocumentManager.getInstance(project).getDocument(pos.getFile());
    if (document == null) {
      return Collections.emptyList();
    }

    List<JavaBreakpointVariant> res = new SmartList<>();

    if (!(startMethod instanceof PsiLambdaExpression)) {
      res.add(new LineJavaBreakpointVariant(position, startMethod, -1)); // base method
    }

    int ordinal = 0;
    for (PsiLambdaExpression lambda : lambdas) { //lambdas
      PsiElement firstElem = DebuggerUtilsEx.getFirstElementOnTheLine(lambda, document, position.getLine());
      XSourcePositionImpl elementPosition = XSourcePositionImpl.createByElement(firstElem);
      if (elementPosition != null) {
        if (lambda == startMethod) {
          res.add(0, new LineJavaBreakpointVariant(elementPosition, lambda, ordinal++));
        }
        else {
          res.add(new LambdaJavaBreakpointVariant(elementPosition, lambda, ordinal++));
        }
      }
    }

    res.add(new JavaBreakpointVariant(position)); //all

    return res;
  }

  public boolean matchesPosition(@NotNull LineBreakpoint<?> breakpoint, @NotNull SourcePosition position) {
    JavaBreakpointProperties properties = breakpoint.getProperties();
    if (properties instanceof JavaLineBreakpointProperties) {
      if (!(breakpoint instanceof RunToCursorBreakpoint) && ((JavaLineBreakpointProperties)properties).getLambdaOrdinal() == null) return true;
      PsiElement containingMethod = getContainingMethod(breakpoint);
      if (containingMethod == null) return false;
      return DebuggerUtilsEx.inTheMethod(position, containingMethod);
    }
    return true;
  }

  @Nullable
  public PsiElement getContainingMethod(@NotNull LineBreakpoint<?> breakpoint) {
    SourcePosition position = breakpoint.getSourcePosition();
    if (position == null) return null;

    JavaBreakpointProperties properties = breakpoint.getProperties();
    if (properties instanceof JavaLineBreakpointProperties && !(breakpoint instanceof RunToCursorBreakpoint)) {
      Integer ordinal = ((JavaLineBreakpointProperties)properties).getLambdaOrdinal();
      if (ordinal > -1) {
        List<PsiLambdaExpression> lambdas = DebuggerUtilsEx.collectLambdas(position, true);
        if (ordinal < lambdas.size()) {
          return lambdas.get(ordinal);
        }
      }
    }
    return DebuggerUtilsEx.getContainingMethod(position);
  }

  public class JavaBreakpointVariant extends XLineBreakpointAllVariant {
    public JavaBreakpointVariant(@NotNull XSourcePosition position) {
      super(position);
    }
  }

  public class ExactJavaBreakpointVariant extends JavaBreakpointVariant {
    private final PsiElement myElement;
    private final Integer myLambdaOrdinal;

    public ExactJavaBreakpointVariant(@NotNull XSourcePosition position, @Nullable PsiElement element, Integer lambdaOrdinal) {
      super(position);
      myElement = element;
      myLambdaOrdinal = lambdaOrdinal;
    }

    @Override
    public Icon getIcon() {
      return myElement != null ? myElement.getIcon(0) : AllIcons.Debugger.Db_set_breakpoint;
    }

    @NotNull
    @Override
    public String getText() {
      return myElement != null ? StringUtil.shortenTextWithEllipsis(myElement.getText(), 100, 0) : "Line";
    }

    @Override
    public TextRange getHighlightRange() {
      if (myElement != null) {
        return DebuggerUtilsEx.intersectWithLine(myElement.getTextRange(), myElement.getContainingFile(), mySourcePosition.getLine());
      }
      return null;
    }

    @NotNull
    @Override
    public JavaLineBreakpointProperties createProperties() {
      JavaLineBreakpointProperties properties = super.createProperties();
      assert properties != null;
      properties.setLambdaOrdinal(myLambdaOrdinal);
      return properties;
    }
  }

  public class LineJavaBreakpointVariant extends ExactJavaBreakpointVariant {
    public LineJavaBreakpointVariant(@NotNull XSourcePosition position, @Nullable PsiElement element, Integer lambdaOrdinal) {
      super(position, element, lambdaOrdinal);
    }

    @NotNull
    @Override
    public String getText() {
      return "Line";
    }

    @Override
    public Icon getIcon() {
      return AllIcons.Debugger.Db_set_breakpoint;
    }
  }

  public class LambdaJavaBreakpointVariant extends ExactJavaBreakpointVariant {
    public LambdaJavaBreakpointVariant(@NotNull XSourcePosition position, @NotNull PsiElement element, Integer lambdaOrdinal) {
      super(position, element, lambdaOrdinal);
    }

    @Override
    public Icon getIcon() {
      return AllIcons.Debugger.LambdaBreakpoint;
    }
  }

  @Nullable
  @Override
  public TextRange getHighlightRange(XLineBreakpoint<JavaLineBreakpointProperties> breakpoint) {
    Integer ordinal = getLambdaOrdinal(breakpoint);
    if (ordinal != null) {
      Breakpoint javaBreakpoint = BreakpointManager.getJavaBreakpoint(breakpoint);
      if (javaBreakpoint instanceof LineBreakpoint) {
        PsiElement method = getContainingMethod((LineBreakpoint)javaBreakpoint);
        if (method != null) {
          return DebuggerUtilsEx.intersectWithLine(method.getTextRange(), method.getContainingFile(), breakpoint.getLine());
        }
      }
    }
    return null;
  }

  @Override
  public XSourcePosition getSourcePosition(@NotNull XBreakpoint<JavaLineBreakpointProperties> breakpoint) {
    Integer ordinal = getLambdaOrdinal(breakpoint);
    if (ordinal != null && ordinal > -1) {
      SourcePosition linePosition = createLineSourcePosition((XLineBreakpointImpl)breakpoint);
      if (linePosition != null) {
        return DebuggerUtilsEx.toXSourcePosition(new PositionManagerImpl.JavaSourcePosition(linePosition, ordinal));
      }
    }
    return null;
  }

  @Nullable
  private static Integer getLambdaOrdinal(XBreakpoint<JavaLineBreakpointProperties> breakpoint) {
    JavaLineBreakpointProperties properties = breakpoint.getProperties();
    return properties != null ? properties.getLambdaOrdinal() : null;
  }

  @Nullable
  private static SourcePosition createLineSourcePosition(XLineBreakpointImpl breakpoint) {
    VirtualFile file = breakpoint.getFile();
    if (file != null) {
      PsiFile psiFile = PsiManager.getInstance(breakpoint.getProject()).findFile(file);
      if (psiFile != null) {
        return SourcePosition.createFromLine(psiFile, breakpoint.getLine());
      }
    }
    return null;
  }

  @Override
  public boolean canBeHitInOtherPlaces() {
    return true; // line breakpoints could be hit in other versions of the same classes
  }

  @Override
  public boolean canPutAt(@NotNull VirtualFile file, int line, @NotNull Project project) {
    return canPutAtElement(file, line, project, (element, document) -> {
      if (element instanceof PsiField) {
        PsiExpression initializer = ((PsiField)element).getInitializer();
        if (initializer != null && !PsiType.NULL.equals(initializer.getType())) {
          Object value = JavaPsiFacade.getInstance(project).getConstantEvaluationHelper().computeConstantExpression(initializer);
          return value == null;
        }
        return false;
      }
      else if (element instanceof PsiMethod) {
        PsiCodeBlock body = ((PsiMethod)element).getBody();
        if (body != null) {
          PsiStatement[] statements = body.getStatements();
          if (statements.length > 0 && document.getLineNumber(statements[0].getTextOffset()) == line) {
            return true;
          }
        }
        return false;
      }
      return true;
    });
  }
}
