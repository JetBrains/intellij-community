// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.ui.breakpoints;

import com.intellij.debugger.HelpID;
import com.intellij.debugger.JavaDebuggerBundle;
import com.intellij.debugger.SourcePosition;
import com.intellij.debugger.engine.PositionManagerImpl;
import com.intellij.debugger.impl.DebuggerUtilsEx;
import com.intellij.debugger.jdi.MethodBytecodeUtil;
import com.intellij.facet.FacetManager;
import com.intellij.icons.AllIcons;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.tree.LeafElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.DocumentUtil;
import com.intellij.util.Processor;
import com.intellij.util.SlowOperations;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.xdebugger.XDebuggerUtil;
import com.intellij.xdebugger.XSourcePosition;
import com.intellij.xdebugger.breakpoints.XBreakpoint;
import com.intellij.xdebugger.breakpoints.XLineBreakpoint;
import com.intellij.xdebugger.breakpoints.XLineBreakpointType;
import com.intellij.xdebugger.breakpoints.ui.XBreakpointCustomPropertiesPanel;
import com.intellij.xdebugger.breakpoints.ui.XBreakpointGroupingRule;
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
import java.util.Objects;
import java.util.stream.Stream;

/**
 * Base class for java line-connected breakpoints (line, method, field)
 *
 * @author egor
 */
public class JavaLineBreakpointType extends JavaLineBreakpointTypeBase<JavaLineBreakpointProperties> {
  private static final Logger LOG = Logger.getInstance(JavaLineBreakpointType.class);

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
  protected @Nls @NotNull String getGeneralDescription(XLineBreakpointType<JavaLineBreakpointProperties>.XLineBreakpointVariant variant) {
    return JavaLineBreakpointProperties.getGeneralDescription(variant.createProperties());
  }

  @Override
  public @Nls String getGeneralDescription(XLineBreakpoint<JavaLineBreakpointProperties> breakpoint) {
    return JavaLineBreakpointProperties.getGeneralDescription(breakpoint.getProperties());
  }

  @Override
  public List<XBreakpointGroupingRule<XLineBreakpoint<JavaLineBreakpointProperties>, ?>> getGroupingRules() {
    return XDebuggerUtil.getInstance().getGroupingByFileRuleAsList();
  }

  @Override
  public @Nullable JavaLineBreakpointProperties createProperties() {
    return new JavaLineBreakpointProperties();
  }

  @Override
  public @NotNull JavaLineBreakpointProperties createBreakpointProperties(@NotNull VirtualFile file, int line) {
    return new JavaLineBreakpointProperties();
  }

  @Override
  public @NotNull Breakpoint<JavaLineBreakpointProperties> createJavaBreakpoint(Project project, XBreakpoint<JavaLineBreakpointProperties> breakpoint) {
    return new LineBreakpoint<>(project, breakpoint);
  }

  @Override
  public int getPriority() {
    return 100;
  }

  @Override
  public @NotNull List<JavaBreakpointVariant> computeVariants(@NotNull Project project, @NotNull XSourcePosition position) {
    PsiFile file = DebuggerUtilsEx.getPsiFile(position, project);
    if (file == null) return Collections.emptyList();

    SourcePosition pos = SourcePosition.createFromLine(file, position.getLine());

    Document document = file.getViewProvider().getDocument();
    if (document == null) {
      return Collections.emptyList();
    }

    List<PsiLambdaExpression> lambdas = DebuggerUtilsEx.collectLambdas(pos, true);
    PsiElement condRet = canStopOnConditionalReturn(file)
                         ? findSingleConditionalReturn(project, document, position.getLine())
                         : null;

    // Start method is either outer method/lambda or lambda starting at the beginning of the line.
    PsiElement startMethod = DebuggerUtilsEx.getContainingMethod(pos);

    PsiElement outerMethod = startMethod != null && startMethod.getTextOffset() >= pos.getOffset()
                             ? DebuggerUtilsEx.getContainingMethod(startMethod.getParent())
                             : startMethod;

    // In this case, startMethod lambda is not treated like a real lambda variant, but it's just containing block for us.
    boolean startMethodIsOuterLambda = startMethod == outerMethod && lambdas.contains(startMethod);

    if ((lambdas.isEmpty() || (startMethodIsOuterLambda && lambdas.size() == 1)) && condRet == null) {
      return Collections.emptyList();
    }

    List<JavaBreakpointVariant> res = new SmartList<>();

    boolean mainMethodAdded = false;
    int lambdaCount = 0;
    if (!(startMethod instanceof PsiLambdaExpression)) {
      res.add(new LineJavaBreakpointVariant(position, startMethod, JavaLineBreakpointProperties.NO_LAMBDA));
      mainMethodAdded = true;
    }

    {
      int ordinal = 0;
      for (PsiLambdaExpression lambda : lambdas) { //lambdas
        PsiElement firstElem = DebuggerUtilsEx.getFirstElementOnTheLine(lambda, document, position.getLine());
        XSourcePositionImpl elementPosition = XSourcePositionImpl.createByElement(firstElem);
        if (elementPosition != null) {
          PsiElement body = lambda.getBody();
          LOG.assertTrue(body != null, "if we got an element, there must be a body");
          if (startMethodIsOuterLambda && lambda == startMethod) {
            res.add(0, new LineJavaBreakpointVariant(elementPosition, body, ordinal));
            mainMethodAdded = true;
          }
          else if (lambda != outerMethod) {
            lambdaCount++;
            res.add(new LambdaJavaBreakpointVariant(elementPosition, body, ordinal));
          }
          ordinal++;
        }
      }
    }

    if (lambdaCount >= 2 || (mainMethodAdded && lambdaCount == 1)) {
      res.add(new JavaBreakpointVariant(position, lambdaCount)); //all
    }

    if (condRet != null) {
      PsiElement method = DebuggerUtilsEx.getContainingMethod(condRet);
      int ordinal = lambdas.indexOf(method);
      res.add(new ConditionalReturnJavaBreakpointVariant(position, condRet, ordinal)); //conditional return
    }

    return res;
  }

  /**
   * @param pos specifies the line in the file where {@code return} is looked for
   */
  public static @Nullable PsiElement findSingleConditionalReturn(@Nullable SourcePosition pos) {
    if (pos == null) return null;
    return findSingleConditionalReturn(pos.getFile(), pos.getLine());
  }

  public static @Nullable PsiElement findSingleConditionalReturn(@NotNull PsiFile file, int line) {
    // Decompiled binary classfiles have no one-liners, so all these checks are pointless for them.
    // This check is expected to fix problems like IDEA-339925.
    if (file.getFileType().isBinary()) return null;

    Project project = file.getProject();
    return ReadAction.compute(() -> {
      Document document = file.getViewProvider().getDocument();
      return document != null ? findSingleConditionalReturn(project, document, line) : null;
    });
  }

  protected static @Nullable PsiElement findSingleConditionalReturn(@NotNull Project project, @NotNull Document document, int line) {
    if (!DocumentUtil.isValidLine(line, document)) return null;

    class RetFinder implements Processor<PsiElement> {
      // Our ultimate goal is to find the single return statement which is executed conditionally to break on it.
      // However, in most cases it's enough to just check that return is not the first on the line
      // (code like `workHard(); return result();` is quite rare,
      // also note that putting breakpoint on such return would not lead to catastrophic circumstances).
      // We ignore multiple returns because it's not easy to map them in source code to multiple return instructions in bytecode.

      boolean somethingBeforeReturn = false;
      @Nullable PsiElement singleReturn = null;

      @Override
      public boolean process(PsiElement element) {
        if (isReturnKeyword(element)) {
          if (singleReturn != null) {
            singleReturn = null;
            return false;
          }

          if (!somethingBeforeReturn) {
            assert singleReturn == null;
            return false;
          }

          singleReturn = element;
        }

        if (!(element instanceof PsiWhiteSpace || element instanceof PsiComment)) {
          somethingBeforeReturn = true;
        }

        return true;
      }
    }
    RetFinder finder = new RetFinder();
    XDebuggerUtil.getInstance().iterateLine(project, document, line, finder);
    return finder.singleReturn;
  }

  public static boolean isReturnKeyword(@NotNull PsiElement element) {
    // Don't check for PsiKeyword to cover many languages at once.
    return element instanceof LeafElement && element.getText().equals("return");
  }

  public static boolean canStopOnConditionalReturn(@NotNull PsiFile file) {
    try (var ignore = SlowOperations.knownIssue("IDEA-331623, EA-903915")) {
      // We haven't implemented Dalvik bytecode parsing yet.
      Module module = ModuleUtilCore.findModuleForFile(file);
      return module == null ||
             !ContainerUtil.exists(FacetManager.getInstance(module).getAllFacets(), f -> f.getName().equals("Android"));
    }
  }

  public boolean matchesPosition(@NotNull LineBreakpoint<?> breakpoint, @NotNull SourcePosition position) {
    JavaBreakpointProperties properties = breakpoint.getProperties();
    if (properties instanceof JavaLineBreakpointProperties) {
      if (!(breakpoint instanceof RunToCursorBreakpoint) && ((JavaLineBreakpointProperties)properties).isAllPositions()) return true;

      // Non-Java JVM languages sometimes use Java breakpoints but they should only use "all positions" variant.
      // Because otherwise the code below is not able to analyze non-Java PSI.
      var language = position.getFile().getLanguage();
      LOG.assertTrue(language.isKindOf(JavaLanguage.INSTANCE), language.getID());

      PsiElement containingMethod = getContainingMethod(breakpoint);
      if (containingMethod == null) return false;
      return DebuggerUtilsEx.inTheMethod(position, containingMethod);
    }
    return true;
  }

  public @Nullable PsiElement getContainingMethod(@NotNull LineBreakpoint<?> breakpoint) {
    SourcePosition position = breakpoint.getSourcePosition();
    if (position == null) return null;

    JavaBreakpointProperties properties = breakpoint.getProperties();
    if (properties instanceof JavaLineBreakpointProperties javaProperties && !(breakpoint instanceof RunToCursorBreakpoint)) {
      if (javaProperties.isInLambda()) {
        Integer ordinal = javaProperties.getLambdaOrdinal();
        assert ordinal != null;
        List<PsiLambdaExpression> lambdas = DebuggerUtilsEx.collectLambdas(position, true);
        if (ordinal < lambdas.size()) {
          return lambdas.get(ordinal);
        }
      }
    }
    return DebuggerUtilsEx.getContainingMethod(position);
  }

  protected static Stream<Location> collectInlineConditionalReturnLocations(@NotNull Method method, int lineNumber) {
    assert lineNumber > 0;
    class Visitor extends MethodVisitor implements MethodBytecodeUtil.InstructionOffsetReader {
      final SmartList<Integer> returnOffsets = new SmartList<>();
      private int bytecodeOffset = -1;
      private boolean lineMatched;
      private boolean lastAddedReturnIsLastInstruction;

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
        lastAddedReturnIsLastInstruction = false;
        if (lineMatched && Opcodes.IRETURN <= opcode && opcode <= Opcodes.RETURN) {
          assert bytecodeOffset >= 0;
          returnOffsets.add(bytecodeOffset);
          lastAddedReturnIsLastInstruction = true;
        }
      }
    }
    Visitor visitor = new Visitor();
    MethodBytecodeUtil.visit(method, visitor, true);
    if (visitor.lastAddedReturnIsLastInstruction && visitor.returnOffsets.size() >= 2) {
      // Return at the end of the method is likely to be implicitly generated,
      // it is not the conditional return we were looking for, drop it.
      visitor.returnOffsets.remove(visitor.returnOffsets.size() - 1);
    }
    return visitor.returnOffsets.stream().map(offs -> method.locationOfCodeIndex(offs));
  }

  @Override
  public boolean variantAndBreakpointMatch(@NotNull XLineBreakpoint<JavaLineBreakpointProperties> breakpoint,
                                           @NotNull XLineBreakpointType<JavaLineBreakpointProperties>.XLineBreakpointVariant variant) {
    var props = breakpoint.getProperties();
    if (variant instanceof ExactJavaBreakpointVariant exactJavaVariant) {
      return Objects.equals(props.getEncodedInlinePosition(), exactJavaVariant.myEncodedInlinePosition) ||
             // Coverage for the case:
             // 1. User sets a breakpoint on a simple line (single println). "All positions" breakpoint is set (historical and compatibility reasons).
             // 2. User adds lambda to the line.
             // 3. Just for the simplicity of UI we ignore correctness and match this "all positions" breakpoint with a "line" breakpoint variant.
             props.isAllPositions() && JavaLineBreakpointProperties.isLinePosition(exactJavaVariant.myEncodedInlinePosition);
    }
    else {
      // variant is a default line breakpoint variant or explicit "all" variant
      return props.isLinePosition();
    }
  }

  public class JavaBreakpointVariant extends XLineBreakpointAllVariant {
    private final int lambdaCount;
    protected final Integer myEncodedInlinePosition;

    public JavaBreakpointVariant(@NotNull XSourcePosition position, int lambdaCount, Integer myEncodedInlinePosition) {
      super(position);
      this.lambdaCount = lambdaCount;
      this.myEncodedInlinePosition = myEncodedInlinePosition;
    }

    /**
     * Create exact variant (i.e. line or exact lambda).
     */
    public JavaBreakpointVariant(@NotNull XSourcePosition position, Integer encodedInlinePosition) {
      this(position, -1, encodedInlinePosition);
    }

    /**
     * Create "Line and Lambdas" variant.
     */
    public JavaBreakpointVariant(@NotNull XSourcePosition position, int lambdaCount) {
      this(position, lambdaCount, null);
    }

    public JavaBreakpointVariant(@NotNull XSourcePosition position) {
      this(position, -1);
    }

    @Override
    public @NotNull String getText() {
      return lambdaCount >= 0
             ? JavaDebuggerBundle.message("breakpoint.variant.text.line.and.lambda", lambdaCount)
             : JavaDebuggerBundle.message("breakpoint.variant.text.line.and.lambda.uknown.count");
    }

    @Override
    public @Nullable JavaLineBreakpointProperties createProperties() {
      JavaLineBreakpointProperties properties = super.createProperties();
      assert properties != null;
      properties.setEncodedInlinePosition(myEncodedInlinePosition);
      return properties;
    }
  }

  public class ExactJavaBreakpointVariant extends JavaBreakpointVariant {
    private final PsiElement myElement;

    public ExactJavaBreakpointVariant(@NotNull XSourcePosition position, @Nullable PsiElement element, Integer encodedInlinePosition) {
      super(position, encodedInlinePosition);
      myElement = element;
    }

    @Override
    public Icon getIcon() {
      return myElement != null ? myElement.getIcon(0) : AllIcons.Debugger.Db_set_breakpoint;
    }

    @Override
    public @NotNull String getText() {
      return myElement != null
             ? StringUtil.shortenTextWithEllipsis(ReadAction.compute(() -> myElement.getText()), 100, 0)
             : JavaDebuggerBundle.message("breakpoint.variant.text.line");
    }

    @Override
    public TextRange getHighlightRange() {
      if (myElement == null || JavaLineBreakpointProperties.isLinePosition(myEncodedInlinePosition)) {
        return null;
      }
      TextRange textRange = getTextRangeWithoutTrailingComments(myElement);
      return DebuggerUtilsEx.getHighlightingRangeInsideLine(textRange, myElement.getContainingFile(), mySourcePosition.getLine());
    }

    @Override
    public boolean isMultiVariant() {
      return false;
    }
  }

  public class LineJavaBreakpointVariant extends ExactJavaBreakpointVariant {
    public LineJavaBreakpointVariant(@NotNull XSourcePosition position, @Nullable PsiElement method, int lambdaOrdinal) {
      super(position, method, JavaLineBreakpointProperties.encodeInlinePosition(lambdaOrdinal, false));
    }

    @Override
    public @NotNull String getText() {
      return JavaDebuggerBundle.message("breakpoint.variant.text.line");
    }

    @Override
    public Icon getIcon() {
      return AllIcons.Debugger.Db_set_breakpoint;
    }

    @Override
    public int getPriority(@NotNull Project project) {
      var basePriority = super.getPriority(project);

      PsiFile file = DebuggerUtilsEx.getPsiFile(mySourcePosition, project);
      if (file == null) return basePriority;

      SourcePosition pos = SourcePosition.createFromLine(file, mySourcePosition.getLine());
      var firstLineElement = pos.getElementAt();
      if (firstLineElement == null) return basePriority;

      if (isLowPriority(firstLineElement)) {
        // Thus, we are raising the priority of other variants (i.e., lambda argument).
        return basePriority - 50;
      }

      return basePriority;
    }

    protected boolean isLowPriority(PsiElement firstLineElement) {
      // Dot at the beginning is the sign of a multi-line statement, lower the line breakpoint priority.
      return firstLineElement instanceof PsiJavaToken dot && dot.getTokenType() == JavaTokenType.DOT;
    }
  }

  public class LambdaJavaBreakpointVariant extends ExactJavaBreakpointVariant {
    public LambdaJavaBreakpointVariant(@NotNull XSourcePosition position, @NotNull PsiElement element, int lambdaOrdinal) {
      super(position, element, JavaLineBreakpointProperties.encodeInlinePosition(lambdaOrdinal, false));
    }

    @Override
    public Icon getIcon() {
      return AllIcons.Debugger.LambdaBreakpoint;
    }
  }

  public class ConditionalReturnJavaBreakpointVariant extends ExactJavaBreakpointVariant {
    public ConditionalReturnJavaBreakpointVariant(@NotNull XSourcePosition position, PsiElement element, int lambdaOrdinal) {
      super(position, element, JavaLineBreakpointProperties.encodeInlinePosition(lambdaOrdinal, true));
    }

    @Override
    public Icon getIcon() {
      return AllIcons.Debugger.Db_set_breakpoint;
    }
  }

  @Override
  public @Nullable TextRange getHighlightRange(XLineBreakpoint<JavaLineBreakpointProperties> breakpoint) {
    PsiElement highlightedElement = null;
    Integer lambdaOrdinal = getLambdaOrdinal(breakpoint);
    if (lambdaOrdinal != null) {
      Breakpoint<?> javaBreakpoint = BreakpointManager.getJavaBreakpoint(breakpoint);
      if (javaBreakpoint instanceof LineBreakpoint<?> lineBreakpoint) {
        assert breakpoint.getProperties() != null;
        if (breakpoint.getProperties().isConditionalReturn()) {
          highlightedElement = findSingleConditionalReturn(lineBreakpoint.getSourcePosition());
        }
        else {
          highlightedElement = getContainingMethod(lineBreakpoint);
          if (highlightedElement instanceof PsiLambdaExpression lambda) {
            highlightedElement = lambda.getBody();
          }
        }
      }
    }
    if (highlightedElement != null) {
      PsiFile file = highlightedElement.getContainingFile();
      int line = breakpoint.getLine();
      return DebuggerUtilsEx.getHighlightingRangeInsideLine(highlightedElement.getTextRange(), file, line);
    }
    return null;
  }

  @Override
  public XSourcePosition getSourcePosition(@NotNull XBreakpoint<JavaLineBreakpointProperties> breakpoint) {
    JavaLineBreakpointProperties properties = breakpoint.getProperties();
    if (properties == null) return null;

    boolean condRet = properties.isConditionalReturn();
    boolean isLambda = properties.isInLambda();
    if (!condRet && !isLambda) return null;

    return ReadAction.compute(() -> {
      SourcePosition linePosition = createLineSourcePosition((XLineBreakpointImpl)breakpoint);
      if (linePosition != null) {
        PsiElement theReturn = condRet ? findSingleConditionalReturn(linePosition) : null;
        if (theReturn != null) {
          return XSourcePositionImpl.createByElement(theReturn);
        }
        else if (isLambda) {
          Integer lambdaOrdinal = properties.getLambdaOrdinal();
          assert lambdaOrdinal != null;
          return DebuggerUtilsEx.toXSourcePosition(new PositionManagerImpl.JavaSourcePosition(linePosition, lambdaOrdinal));
        }
      }
      return null;
    });
  }

  private static @Nullable Integer getLambdaOrdinal(XBreakpoint<JavaLineBreakpointProperties> breakpoint) {
    JavaLineBreakpointProperties properties = breakpoint.getProperties();
    return properties != null ? properties.getLambdaOrdinal() : null;
  }

  private static @Nullable SourcePosition createLineSourcePosition(XLineBreakpointImpl breakpoint) {
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
      if (DumbService.isDumb(project)) { // always allow line breakpoints in dumb mode
        return true;
      }

      if (element instanceof PsiField) {
        PsiExpression initializer = ((PsiField)element).getInitializer();
        if (initializer != null && !PsiTypes.nullType().equals(initializer.getType())) {
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

  @Override
  public @Nullable XBreakpointCustomPropertiesPanel<XLineBreakpoint<JavaLineBreakpointProperties>> createCustomPropertiesPanel(@NotNull Project project) {
    if (Registry.is("debugger.call.tracing")) {
      return new CallTracingPropertiesPanel(project);
    }
    return null;
  }

  private static boolean isWhiteSpaceOrComment(@NotNull PsiElement psiElement) {
    return psiElement instanceof PsiWhiteSpace || PsiTreeUtil.getParentOfType(psiElement, PsiComment.class, false) != null;
  }

  public static TextRange getTextRangeWithoutTrailingComments(@NotNull PsiElement psiElement) {
    @NotNull PsiElement lastChild = psiElement;
    @Nullable PsiElement prevChild = psiElement.getLastChild();
    while (prevChild != null && isWhiteSpaceOrComment(prevChild)) {
      lastChild = prevChild;
      prevChild = prevChild.getPrevSibling();
    }
    return new TextRange(psiElement.getTextRange().getStartOffset(), lastChild.getTextRange().getEndOffset());
  }
}
