// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.analysis;

import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.HighlightInfoType;
import com.intellij.codeInsight.daemon.impl.HighlightVisitor;
import com.intellij.codeInsight.intention.CommonIntentionAction;
import com.intellij.codeInsight.quickfix.UnresolvedReferenceQuickFixProvider;
import com.intellij.codeInspection.ex.GlobalInspectionContextBase;
import com.intellij.java.codeserver.core.JavaPsiModuleUtil;
import com.intellij.java.codeserver.highlighting.JavaErrorCollector;
import com.intellij.java.codeserver.highlighting.errors.JavaCompilationError;
import com.intellij.java.codeserver.highlighting.errors.JavaErrorHighlightType;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.colors.EditorColorsUtil;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.HtmlChunk;
import com.intellij.pom.java.JavaFeature;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.resolve.JavaResolveUtil;
import com.intellij.psi.impl.source.tree.java.PsiReferenceExpressionImpl;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.ui.ColorUtil;
import com.intellij.ui.NewUI;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.NamedColorUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.Map;
import java.util.function.Consumer;

import static com.intellij.java.codeserver.highlighting.errors.JavaErrorKinds.*;

// java highlighting: problems in java code like unresolved/incompatible symbols/methods etc.
public class HighlightVisitorImpl extends JavaElementVisitor implements HighlightVisitor {
  private final Map<String, String> myTooltipStyles = initTooltipStyles();

  private @NotNull HighlightInfoHolder myHolder;
  private @NotNull LanguageLevel myLanguageLevel;

  private @NotNull PsiFile myFile;
  private PsiJavaModule myJavaModule;
  private JavaErrorCollector myCollector;

  private final @NotNull Consumer<? super HighlightInfo.Builder> myErrorSink = builder -> add(builder);

  private boolean myHasError; // true if myHolder.add() was called with HighlightInfo of >=ERROR severity. On each .visit(PsiElement) call this flag is reset. Useful to determine whether the error was already reported while visiting this PsiElement.

  protected HighlightVisitorImpl() {
  }

  private static @NotNull Map<String, String> initTooltipStyles() {
    Color parameterBgColor = EditorColorsUtil.getGlobalOrDefaultColorScheme()
      .getAttributes(DefaultLanguageHighlighterColors.INLINE_PARAMETER_HINT).getBackgroundColor();
    String parameterBgStyle = parameterBgColor == null ? "" : "; background-color: " + ColorUtil.toHtmlColor(parameterBgColor);
    return Map.of(
      JavaCompilationError.JAVA_DISPLAY_INFORMATION,
      "color: " + ColorUtil.toHtmlColor(NewUI.isEnabled() ? JBUI.CurrentTheme.Editor.Tooltip.FOREGROUND : UIUtil.getToolTipForeground()),
      JavaCompilationError.JAVA_DISPLAY_GRAYED,
      "color: " + ColorUtil.toHtmlColor(UIUtil.getContextHelpForeground()),
      JavaCompilationError.JAVA_DISPLAY_PARAMETER,
      "color: " + ColorUtil.toHtmlColor(UIUtil.getContextHelpForeground()) + parameterBgStyle,
      JavaCompilationError.JAVA_DISPLAY_ERROR,
      "color: " + ColorUtil.toHtmlColor(NamedColorUtil.getErrorForeground()));
  }

  @Contract(pure = true)
  private boolean hasErrorResults() {
    return myHasError;
  }

  /**
   * @deprecated use {@link #HighlightVisitorImpl()}
   */
  @Deprecated(forRemoval = true)
  protected HighlightVisitorImpl(@NotNull PsiResolveHelper psiResolveHelper) {
  }

  @Override
  @SuppressWarnings("MethodDoesntCallSuperMethod")
  public @NotNull HighlightVisitorImpl clone() {
    return new HighlightVisitorImpl();
  }

  @Override
  public boolean suitableForFile(@NotNull PsiFile file) {
    HighlightingLevelManager highlightingLevelManager = HighlightingLevelManager.getInstance(file.getProject());
    if (highlightingLevelManager.runEssentialHighlightingOnly(file)) {
      return false;
    }

    // both PsiJavaFile and PsiCodeFragment must match
    return file instanceof PsiImportHolder && !InjectedLanguageManager.getInstance(file.getProject()).isInjectedFragment(file);
  }

  @Override
  public void visit(@NotNull PsiElement element) {
    myHasError = false;
    element.accept(this);
  }

  @Override
  public boolean analyze(@NotNull PsiFile file, boolean updateWholeFile, @NotNull HighlightInfoHolder holder, @NotNull Runnable highlight) {
    try {
      prepare(holder, file);
      if (updateWholeFile) {
        GlobalInspectionContextBase.assertUnderDaemonProgress();
        Project project = file.getProject();
        Document document = PsiDocumentManager.getInstance(project).getDocument(file);
        highlight.run();
        ProgressManager.checkCanceled();
        if (document != null) {
          new UnusedImportsVisitor(file, document).collectHighlights(holder);
        }
      }
      else {
        highlight.run();
      }
    }
    finally {
      myJavaModule = null;
      myFile = null;
      myHolder = null;
      myCollector = null;
    }

    return true;
  }

  protected void prepareToRunAsInspection(@NotNull HighlightInfoHolder holder) {
    prepare(holder, holder.getContextFile());
  }

  private void prepare(@NotNull HighlightInfoHolder holder, @NotNull PsiFile file) {
    myHolder = holder;
    myFile = file;
    myLanguageLevel = PsiUtil.getLanguageLevel(file);
    myJavaModule = JavaFeature.MODULES.isSufficient(myLanguageLevel) ? JavaPsiModuleUtil.findDescriptorByElement(file) : null;
    JavaErrorFixProvider errorFixProvider = JavaErrorFixProvider.getInstance();
    myCollector = new JavaErrorCollector(myFile, error -> reportError(error, errorFixProvider));
  }

  private void reportError(JavaCompilationError<?, ?> error, JavaErrorFixProvider errorFixProvider) {
    JavaErrorHighlightType javaHighlightType = error.highlightType();
    HighlightInfoType type = switch (javaHighlightType) {
      case ERROR, FILE_LEVEL_ERROR -> HighlightInfoType.ERROR;
      case UNHANDLED_EXCEPTION -> HighlightInfoType.UNHANDLED_EXCEPTION;
      case WRONG_REF -> HighlightInfoType.WRONG_REF;
      case PENDING_REF -> HighlightInfoType.PENDING_REFERENCE;
    };
    TextRange range = error.range();
    HtmlChunk tooltip = error.tooltip();
    HighlightInfo.Builder info = HighlightInfo.newHighlightInfo(type);
    if (tooltip.isEmpty()) {
      info.descriptionAndTooltip(error.description().toString());
    } else {
      info.description(error.description().toString()).escapedToolTip(
        tooltip.applyStyles(myTooltipStyles).toString());
    }
    if (javaHighlightType == JavaErrorHighlightType.FILE_LEVEL_ERROR) {
      info.fileLevelAnnotation();
    }
    PsiElement anchor = error.anchor();
    if (range != null) {
      info.range(anchor, range);
      if (range.getLength() == 0) {
        int offset = range.getStartOffset() + anchor.getTextRange().getStartOffset();
        CharSequence sequence = myFile.getFileDocument().getCharsSequence();
        if (offset >= sequence.length() || sequence.charAt(offset) == '\n') {
          info.endOfLine();
        }
      }
    } else {
      info.range(anchor);
    }
    Consumer<@NotNull CommonIntentionAction> consumer = fix -> info.registerFix(fix.asIntention(), null, null, null, null);
    errorFixProvider.processFixes(error, consumer);
    ErrorFixExtensionPoint.registerFixes(consumer, error.psi(), error.kind().key());
    error.psiForKind(EXPRESSION_EXPECTED, REFERENCE_UNRESOLVED, REFERENCE_AMBIGUOUS)
      .or(() -> error.psiForKind(TYPE_UNKNOWN_CLASS).map(PsiTypeElement::getInnermostComponentReferenceElement))
      .or(() -> error.psiForKind(CALL_AMBIGUOUS_NO_MATCH, CALL_UNRESOLVED).map(PsiMethodCallExpression::getMethodExpression))
      .ifPresent(ref -> UnresolvedReferenceQuickFixProvider.registerUnresolvedReferenceLazyQuickFixes(ref, info));
    add(info);
  }

  @Override
  public void visitElement(@NotNull PsiElement element) {
    myCollector.processElement(element);
  }

  private boolean add(@Nullable HighlightInfo.Builder builder) {
    if (builder != null) {
      HighlightInfo info = builder.create();
      if (info != null && info.getSeverity().compareTo(HighlightSeverity.ERROR) >= 0) {
        myHasError = true;
      }
      return myHolder.add(info);
    }
    return false;
  }

  @Override
  public void visitLambdaExpression(@NotNull PsiLambdaExpression expression) {
    visitElement(expression);
    PsiElement parent = PsiUtil.skipParenthesizedExprUp(expression.getParent());
    if (hasErrorResults() || toReportFunctionalExpressionProblemOnParent(parent)) return;

    if (!hasErrorResults()) {
      PsiType functionalInterfaceType = expression.getFunctionalInterfaceType();
      if (functionalInterfaceType != null) {
        add(LambdaHighlightingUtil.checkFunctionalInterfaceTypeAccessible(myFile.getProject(), expression, functionalInterfaceType));
      }
    }
  }

  @Override
  public void visitIdentifier(@NotNull PsiIdentifier identifier) {
    PsiElement parent = identifier.getParent();
    if (parent instanceof PsiClass aClass) {
      if (!hasErrorResults() && JavaFeature.EXTENSION_METHODS.isSufficient(myLanguageLevel)) {
        add(GenericsHighlightUtil.checkUnrelatedDefaultMethods(aClass, identifier));
      }
    }

    super.visitIdentifier(identifier);
  }

  @Override
  public void visitImportStaticReferenceElement(@NotNull PsiImportStaticReferenceElement ref) {
    super.visitImportStaticReferenceElement(ref);
    JavaResolveResult[] results = ref.multiResolve(false);
    if (!hasErrorResults() && results.length == 1) {
      add(HighlightUtil.checkReference(ref, results[0]));
    }
  }

  @Override
  public void visitImportModuleStatement(@NotNull PsiImportModuleStatement statement) {
    super.visitImportModuleStatement(statement);
    if (!hasErrorResults()) add(ModuleHighlightUtil.checkModuleReferenceAccess(statement));
  }

  static @Nullable JavaResolveResult resolveOptimised(@NotNull PsiJavaCodeReferenceElement ref, @NotNull PsiFile containingFile) {
    try {
      if (ref instanceof PsiReferenceExpressionImpl) {
        PsiReferenceExpressionImpl.OurGenericsResolver resolver = PsiReferenceExpressionImpl.OurGenericsResolver.INSTANCE;
        JavaResolveResult[] results = JavaResolveUtil.resolveWithContainingFile(ref, resolver, true, true, containingFile);
        return results.length == 1 ? results[0] : JavaResolveResult.EMPTY;
      }
      return ref.advancedResolve(true);
    }
    catch (IndexNotReadyException e) {
      return null;
    }
  }

  @Override
  public void visitReferenceExpression(@NotNull PsiReferenceExpression expression) {
    JavaResolveResult result = resolveOptimised(expression, myFile);
    if (result != null) {
      add(HighlightUtil.checkReference(expression, result));
    }

    if (!hasErrorResults()) {
      visitElement(expression);
      if (hasErrorResults()) return;
    }

    PsiExpression qualifierExpression = expression.getQualifierExpression();
    if (!hasErrorResults() && myJavaModule == null && qualifierExpression != null) {
      add(GenericsHighlightUtil.checkMemberSignatureTypesAccessibility(expression));
    }
  }

  @Override
  public void visitMethodReferenceExpression(@NotNull PsiMethodReferenceExpression expression) {
    visitElement(expression);
    PsiElement parent = PsiUtil.skipParenthesizedExprUp(expression.getParent());
    if (toReportFunctionalExpressionProblemOnParent(parent)) return;
    PsiType functionalInterfaceType = expression.getFunctionalInterfaceType();
    if (functionalInterfaceType != null) {
      if (!hasErrorResults() && PsiTypesUtil.allTypeParametersResolved(expression, functionalInterfaceType)) {
        add(LambdaHighlightingUtil.checkFunctionalInterfaceTypeAccessible(myFile.getProject(), expression, functionalInterfaceType));
      }
    }
  }

  /**
   * @return true for {@code functional_expression;} or {@code var l = functional_expression;}
   */
  private static boolean toReportFunctionalExpressionProblemOnParent(@Nullable PsiElement parent) {
    if (parent instanceof PsiLocalVariable variable) {
      return variable.getTypeElement().isInferredType();
    }
    return parent instanceof PsiExpressionStatement && !(parent.getParent() instanceof PsiSwitchLabeledRuleStatement);
  }

  @Override
  public void visitSwitchStatement(@NotNull PsiSwitchStatement statement) {
    super.visitSwitchStatement(statement);
    checkSwitchBlock(statement);
  }

  @Override
  public void visitSwitchExpression(@NotNull PsiSwitchExpression expression) {
    super.visitSwitchExpression(expression);
    checkSwitchBlock(expression);
  }

  private void checkSwitchBlock(@NotNull PsiSwitchBlock switchBlock) {
    SwitchBlockHighlightingModel model = SwitchBlockHighlightingModel.createInstance(myLanguageLevel, switchBlock, myFile);
    if (model == null) return;
    if (!hasErrorResults()) model.checkSwitchLabelValues(myErrorSink);
  }

  @Override
  public void visitReferenceElement(@NotNull PsiJavaCodeReferenceElement ref) {
    super.visitReferenceElement(ref);
    if (!(ref instanceof PsiExpression)) {
      JavaResolveResult result = resolveOptimised(ref, myFile);
      if (result != null) {
        add(HighlightUtil.checkReference(ref, result));
      }
    }
  }
}
