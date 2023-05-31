// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.ui.breakpoints;

import com.intellij.debugger.HelpID;
import com.intellij.debugger.JavaDebuggerBundle;
import com.intellij.debugger.SourcePosition;
import com.intellij.debugger.engine.PositionManagerImpl;
import com.intellij.debugger.impl.DebuggerUtilsEx;
import com.intellij.debugger.jdi.MethodBytecodeUtil;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.util.DocumentUtil;
import com.intellij.util.Processor;
import com.intellij.util.SmartList;
import com.intellij.xdebugger.XDebuggerUtil;
import com.intellij.xdebugger.XSourcePosition;
import com.intellij.xdebugger.breakpoints.XBreakpoint;
import com.intellij.xdebugger.breakpoints.XLineBreakpoint;
import com.intellij.xdebugger.breakpoints.ui.XBreakpointCustomPropertiesPanel;
import com.intellij.xdebugger.breakpoints.ui.XBreakpointGroupingRule;
import com.intellij.xdebugger.impl.XDebuggerUtilImpl;
import com.intellij.xdebugger.impl.XSourcePositionImpl;
import com.intellij.xdebugger.impl.breakpoints.XLineBreakpointImpl;
import com.sun.jdi.Location;
import com.sun.jdi.Method;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.java.debugger.breakpoints.properties.JavaBreakpointProperties;
import org.jetbrains.java.debugger.breakpoints.properties.JavaLineBreakpointProperties;
import org.jetbrains.org.objectweb.asm.Label;
import org.jetbrains.org.objectweb.asm.MethodVisitor;
import org.jetbrains.org.objectweb.asm.Opcodes;

import javax.swing.*;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

import static org.jetbrains.java.debugger.breakpoints.properties.JavaLineBreakpointProperties.COND_RET_CODE;

/**
 * Base class for java line-connected breakpoints (line, method, field)
 *
 * @author egor
 */
public class JavaLineBreakpointType extends JavaLineBreakpointTypeBase<JavaLineBreakpointProperties> {
  public JavaLineBreakpointType() {
    super("java-line", JavaDebuggerBundle.message("line.breakpoints.tab.title"));
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
    return JavaDebuggerBundle.message("line.breakpoints.tab.title");
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

    Document document = PsiDocumentManager.getInstance(project).getDocument(pos.getFile());
    if (document == null) {
      return Collections.emptyList();
    }

    PsiElement startMethod = DebuggerUtilsEx.getContainingMethod(pos);
    List<PsiLambdaExpression> lambdas = DebuggerUtilsEx.collectLambdas(pos, true);
    PsiKeyword condRet = findSingleConditionalReturn(project, document, position.getLine());

    if ((lambdas.isEmpty() || (lambdas.contains(startMethod) && lambdas.size() == 1)) && condRet == null) {
      return Collections.emptyList();
    }

    List<JavaBreakpointVariant> res = new SmartList<>();

    boolean baseMethodWasAdded = false;
    boolean anyLambdaWasAdded = false;
    if (!(startMethod instanceof PsiLambdaExpression)) {
      baseMethodWasAdded = true;
      res.add(new LineJavaBreakpointVariant(position, startMethod, -1));
    }

    {
      int ordinal = 0;
      for (PsiLambdaExpression lambda : lambdas) { //lambdas
        PsiElement firstElem = DebuggerUtilsEx.getFirstElementOnTheLine(lambda, document, position.getLine());
        XSourcePositionImpl elementPosition = XSourcePositionImpl.createByElement(firstElem);
        if (elementPosition != null) {
          if (lambda == startMethod) {
            baseMethodWasAdded = true;
            res.add(0, new LineJavaBreakpointVariant(elementPosition, lambda, ordinal++));
          }
          else {
            anyLambdaWasAdded = true;
            res.add(new LambdaJavaBreakpointVariant(elementPosition, lambda, ordinal++));
          }
        }
      }
    }
    assert baseMethodWasAdded;

    if (anyLambdaWasAdded) {
      res.add(new JavaBreakpointVariant(position)); //all
    }

    if (condRet != null) {
      PsiElement method = DebuggerUtilsEx.getContainingMethod(condRet);
      int ordinal = method instanceof PsiLambdaExpression
                    ? Math.toIntExact(lambdas.stream().takeWhile(l -> l != method).count())
                    : -1;
      res.add(new ConditionalReturnJavaBreakpointVariant(position, condRet, ordinal)); //conditional return
    }

    assert anyLambdaWasAdded || condRet != null;

    return res;
  }

  public static @Nullable PsiKeyword findSingleConditionalReturn(@Nullable SourcePosition pos) {
    if (pos == null) return null;
    Project project = pos.getFile().getProject();
    Document document = PsiDocumentManager.getInstance(project).getDocument(pos.getFile());
    if (document == null) return null;
    return findSingleConditionalReturn(project, document, pos.getLine());
  }

  public static @Nullable PsiKeyword findSingleConditionalReturn(@NotNull Project project, @NotNull Document document, int line) {
    if (!DocumentUtil.isValidLine(line, document)) return null;
    TextRange curLineRange = DocumentUtil.getLineTextRange(document, line);
    class CondRetFinder implements Processor<PsiElement> {
      @Nullable PsiKeyword conditionalReturn = null;

      @Override
      public boolean process(PsiElement element) {
        if (element instanceof PsiKeyword retKeyword && element.getText().equals(PsiKeyword.RETURN)) {
          if (conditionalReturn != null) {
            // it's not easy to map multiple returns in source code to multiple returns in bytecode
            conditionalReturn = null;
            return false;
          }

          PsiElement cur = element;
          while (cur != null && cur.getTextOffset() >= curLineRange.getStartOffset()) {
            PsiElement parent = cur.getParent();
            if (parent instanceof PsiIfStatement ifStmt &&
                     (cur == ifStmt.getThenBranch() || cur == ifStmt.getElseBranch())) {
              conditionalReturn = retKeyword;
            }

            cur = parent;
          }
        }

        return true;
      }
    }
    CondRetFinder finder = new CondRetFinder();
    new XDebuggerUtilImpl().iterateLine(project, document, line, finder);
    return finder.conditionalReturn;
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

  public static Stream<Location> collectInlineReturnLocations(@NotNull Method method, int lineNumber) {
    assert lineNumber > 0;
    class Visitor extends MethodVisitor implements MethodBytecodeUtil.InstructionOffsetReader {
      final SmartList<Integer> returnOffsets = new SmartList<>();
      private int bytecodeOffset = -1;
      private boolean lineMatched;

      protected Visitor() {
        super(Opcodes.API_VERSION);
      }

      @Override
      public void readBytecodeInstructionOffset(int offset) {
        bytecodeOffset = offset;
      }

      @Override
      public void visitLineNumber(int line, Label start) {
        lineMatched = line == lineNumber;
      }

      @Override
      public void visitInsn(int opcode) {
        if (lineMatched && Opcodes.IRETURN <= opcode && opcode <= Opcodes.RETURN) {
          assert bytecodeOffset >= 0;
          returnOffsets.add(bytecodeOffset);
        }
      }
    }
    Visitor visitor = new Visitor();
    MethodBytecodeUtil.visit(method, visitor, true);
    return visitor.returnOffsets.stream().map(offs -> method.locationOfCodeIndex(offs));
  }

  public class JavaBreakpointVariant extends XLineBreakpointAllVariant {
    public JavaBreakpointVariant(@NotNull XSourcePosition position) {
      super(position);
    }
  }

  public class ExactJavaBreakpointVariant extends JavaBreakpointVariant {
    private final PsiElement myElement;
    private final Integer myEncodedInlinePosition;

    public ExactJavaBreakpointVariant(@NotNull XSourcePosition position, @Nullable PsiElement element, Integer encodedInlinePosition) {
      super(position);
      myElement = element;
      myEncodedInlinePosition = encodedInlinePosition;
    }

    @Override
    public Icon getIcon() {
      return myElement != null ? myElement.getIcon(0) : AllIcons.Debugger.Db_set_breakpoint;
    }

    @NotNull
    @Override
    public String getText() {
      return myElement != null
             ? StringUtil.shortenTextWithEllipsis(ReadAction.compute(() -> myElement.getText()), 100, 0)
             : JavaDebuggerBundle.message("breakpoint.variant.text.line");
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
      properties.setEncodedInlinePosition(myEncodedInlinePosition);
      return properties;
    }
  }

  public class LineJavaBreakpointVariant extends ExactJavaBreakpointVariant {
    public LineJavaBreakpointVariant(@NotNull XSourcePosition position, @Nullable PsiElement element, int lambdaOrdinal) {
      super(position, element, lambdaOrdinal);
    }

    @NotNull
    @Override
    public String getText() {
      return JavaDebuggerBundle.message("breakpoint.variant.text.line");
    }

    @Override
    public Icon getIcon() {
      return AllIcons.Debugger.Db_set_breakpoint;
    }
  }

  public class LambdaJavaBreakpointVariant extends ExactJavaBreakpointVariant {
    public LambdaJavaBreakpointVariant(@NotNull XSourcePosition position, @NotNull PsiElement element, int lambdaOrdinal) {
      super(position, element, lambdaOrdinal);
    }

    @Override
    public Icon getIcon() {
      return AllIcons.Debugger.LambdaBreakpoint;
    }
  }

  public class ConditionalReturnJavaBreakpointVariant extends ExactJavaBreakpointVariant {
    public ConditionalReturnJavaBreakpointVariant(@NotNull XSourcePosition position, PsiKeyword element, int lambdaOrdinal) {
      super(position, element, COND_RET_CODE - lambdaOrdinal - 1);
    }

    @Override
    public Icon getIcon() {
      return AllIcons.Debugger.Db_set_breakpoint;
    }
  }

  @Nullable
  @Override
  public TextRange getHighlightRange(XLineBreakpoint<JavaLineBreakpointProperties> breakpoint) {
    PsiElement highlightedElement = null;
    Integer ordinal = getLambdaOrdinal(breakpoint);
    if (ordinal != null) {
      Breakpoint<?> javaBreakpoint = BreakpointManager.getJavaBreakpoint(breakpoint);
      if (javaBreakpoint instanceof LineBreakpoint<?> lineBreakpoint) {
        assert breakpoint.getProperties() != null;
        if (breakpoint.getProperties().isConditionalReturn()) {
          highlightedElement = findSingleConditionalReturn(lineBreakpoint.getSourcePosition());
        }
        else {
          highlightedElement = getContainingMethod(lineBreakpoint);
        }
      }
    }
    return highlightedElement != null
           ? DebuggerUtilsEx.intersectWithLine(highlightedElement.getTextRange(), highlightedElement.getContainingFile(), breakpoint.getLine())
           : null;
  }

  @Override
  public XSourcePosition getSourcePosition(@NotNull XBreakpoint<JavaLineBreakpointProperties> breakpoint) {
    Integer ordinal = getLambdaOrdinal(breakpoint);
    if (ordinal != null && ordinal > -1) {
      return ReadAction.compute(() -> {
        SourcePosition linePosition = createLineSourcePosition((XLineBreakpointImpl)breakpoint);
        if (linePosition != null) {
          return DebuggerUtilsEx.toXSourcePosition(new PositionManagerImpl.JavaSourcePosition(linePosition, ordinal));
        }
        return null;
      });
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
        if (initializer != null && !PsiTypes.nullType().equals(initializer.getType())) {
          if (DumbService.isDumb(project)) {
            return true;
          }
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

  @Nullable
  @Override
  public XBreakpointCustomPropertiesPanel<XLineBreakpoint<JavaLineBreakpointProperties>> createCustomPropertiesPanel(@NotNull Project project) {
    if (Registry.is("debugger.call.tracing")) {
      return new CallTracingPropertiesPanel(project);
    }
    return null;
  }
}
