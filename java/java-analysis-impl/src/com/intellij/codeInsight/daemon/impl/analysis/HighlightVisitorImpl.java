// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.analysis;

import com.intellij.codeInsight.daemon.JavaErrorBundle;
import com.intellij.codeInsight.daemon.impl.DefaultHighlightUtil;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.HighlightInfoType;
import com.intellij.codeInsight.daemon.impl.HighlightVisitor;
import com.intellij.codeInsight.daemon.impl.quickfix.AdjustFunctionContextFix;
import com.intellij.codeInsight.daemon.impl.quickfix.QuickFixAction;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.QuickFixFactory;
import com.intellij.codeInsight.quickfix.UnresolvedReferenceQuickFixProvider;
import com.intellij.codeInspection.ex.GlobalInspectionContextBase;
import com.intellij.core.JavaPsiBundle;
import com.intellij.java.codeserver.highlighting.JavaErrorCollector;
import com.intellij.java.codeserver.highlighting.errors.JavaCompilationError;
import com.intellij.java.codeserver.highlighting.errors.JavaErrorHighlightType;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.lang.jvm.JvmModifier;
import com.intellij.lang.jvm.JvmModifiersOwner;
import com.intellij.lang.jvm.actions.JvmElementActionFactories;
import com.intellij.lang.jvm.actions.MemberRequestsKt;
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.colors.EditorColorsUtil;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.JavaSdkVersion;
import com.intellij.openapi.projectRoots.JavaVersionService;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.HtmlChunk;
import com.intellij.pom.java.JavaFeature;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.controlFlow.ControlFlowUtil;
import com.intellij.psi.impl.IncompleteModelUtil;
import com.intellij.psi.impl.source.resolve.JavaResolveUtil;
import com.intellij.psi.impl.source.tree.java.PsiReferenceExpressionImpl;
import com.intellij.psi.infos.MethodCandidateInfo;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.ui.ColorUtil;
import com.intellij.ui.NewUI;
import com.intellij.util.ObjectUtils;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.NamedColorUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;

import static com.intellij.java.codeserver.highlighting.errors.JavaErrorKinds.*;
import static com.intellij.util.ObjectUtils.tryCast;
import static java.util.Objects.*;

// java highlighting: problems in java code like unresolved/incompatible symbols/methods etc.
public class HighlightVisitorImpl extends JavaElementVisitor implements HighlightVisitor {
  private final Map<String, String> myTooltipStyles = Map.of(
    JavaCompilationError.JAVA_DISPLAY_INFORMATION, "color: " + ColorUtil.toHtmlColor(NewUI.isEnabled() ? JBUI.CurrentTheme.Editor.Tooltip.FOREGROUND : UIUtil.getToolTipForeground()),
    JavaCompilationError.JAVA_DISPLAY_GRAYED, "color: " + ColorUtil.toHtmlColor(UIUtil.getContextHelpForeground()),
    JavaCompilationError.JAVA_DISPLAY_PARAMETER, "color: " + ColorUtil.toHtmlColor(UIUtil.getContextHelpForeground())+"; background-color: " + ColorUtil.toHtmlColor(
      EditorColorsUtil.getGlobalOrDefaultColorScheme().getAttributes(DefaultLanguageHighlighterColors.INLINE_PARAMETER_HINT).getBackgroundColor()),
    JavaCompilationError.JAVA_DISPLAY_ERROR, "color: " + ColorUtil.toHtmlColor(NamedColorUtil.getErrorForeground()
    ));
  private @NotNull HighlightInfoHolder myHolder;
  private @NotNull LanguageLevel myLanguageLevel;
  private JavaSdkVersion myJavaSdkVersion;

  private @NotNull PsiFile myFile;
  private PsiJavaModule myJavaModule;
  private JavaErrorCollector myCollector;

  private PreviewFeatureUtil.PreviewFeatureVisitor myPreviewFeatureVisitor;

  // map codeBlock->List of PsiReferenceExpression of uninitialized final variables
  private final Map<PsiElement, Collection<PsiReferenceExpression>> myUninitializedVarProblems = new HashMap<>();
  // map codeBlock->List of PsiReferenceExpression of extra initialization of final variable
  private final Map<PsiElement, Collection<ControlFlowUtil.VariableInfo>> myFinalVarProblems = new HashMap<>();

  private final @NotNull Consumer<? super HighlightInfo.Builder> myErrorSink = builder -> add(builder);

  private final Set<PsiClass> myOverrideEquivalentMethodsVisitedClasses = new HashSet<>();
  // stored "clashing signatures" errors for the method (if the key is a PsiModifierList of the method), or the class (if the key is a PsiModifierList of the class)
  private final Map<PsiMember, HighlightInfo.Builder> myOverrideEquivalentMethodsErrors = new HashMap<>();
  private final Function<? super PsiElement, ? extends PsiMethod> mySurroundingConstructor = entry -> findSurroundingConstructor(entry);
  private final Map<PsiElement, PsiMethod> myInsideConstructorOfClassCache = new HashMap<>(); // null value means "cached but no corresponding ctr found"
  private boolean myHasError; // true if myHolder.add() was called with HighlightInfo of >=ERROR severity. On each .visit(PsiElement) call this flag is reset. Useful to determine whether the error was already reported while visiting this PsiElement.

  protected HighlightVisitorImpl() {
  }

  @Contract(pure = true)
  private boolean hasErrorResults() {
    return myHasError;
  }

  private @Contract(pure = true) @NotNull Project getProject() {
    return myHolder.getProject();
  }

  // element -> a constructor inside which this element is contained
  private PsiMethod findSurroundingConstructor(@NotNull PsiElement entry) {
    PsiMethod result = null;
    PsiElement element;
    for (element = entry; element != null && !(element instanceof PsiFile); element = element.getParent()) {
      result = myInsideConstructorOfClassCache.get(element);
      if (result != null || myInsideConstructorOfClassCache.containsKey(element)) {
        break;
      }
      if (element instanceof PsiMethod method && method.isConstructor()) {
        result = method;
        break;
      }
    }
    for (PsiElement e = entry; e != null && !(e instanceof PsiFile); e = e.getParent()) {
      myInsideConstructorOfClassCache.put(e, result);
      if (e == element) break;
    }
    return result;
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
      myUninitializedVarProblems.clear();
      myFinalVarProblems.clear();
      myJavaModule = null;
      myFile = null;
      myHolder = null;
      myCollector = null;
      myPreviewFeatureVisitor = null;
      myOverrideEquivalentMethodsVisitedClasses.clear();
      myOverrideEquivalentMethodsErrors.clear();
      myInsideConstructorOfClassCache.clear();
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
    myJavaSdkVersion = ObjectUtils
      .notNull(JavaVersionService.getInstance().getJavaSdkVersion(file), JavaSdkVersion.fromLanguageLevel(myLanguageLevel));
    myJavaModule = JavaFeature.MODULES.isSufficient(myLanguageLevel) ? JavaModuleGraphUtil.findDescriptorByElement(file) : null;
    myPreviewFeatureVisitor = myLanguageLevel.isPreview() ? null : new PreviewFeatureUtil.PreviewFeatureVisitor(myLanguageLevel, myErrorSink);
    JavaErrorFixProvider errorFixProvider = JavaErrorFixProvider.getInstance();
    myCollector = new JavaErrorCollector(myFile, myJavaModule, error -> reportError(error, errorFixProvider));
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
    errorFixProvider.processFixes(error, fix -> info.registerFix(fix.asIntention(), null, null, null, null));
    error.psiForKind(EXPRESSION_EXPECTED, REFERENCE_UNRESOLVED, REFERENCE_AMBIGUOUS)
      .or(() -> error.psiForKind(TYPE_UNKNOWN_CLASS).map(PsiTypeElement::getInnermostComponentReferenceElement))
      .or(() -> error.psiForKind(CALL_AMBIGUOUS_NO_MATCH, CALL_UNRESOLVED).map(PsiMethodCallExpression::getMethodExpression))
      .ifPresent(ref -> UnresolvedReferenceQuickFixProvider.registerUnresolvedReferenceLazyQuickFixes(ref, info));
    add(info);
  }

  @Override
  public void visitElement(@NotNull PsiElement element) {
    myCollector.processElement(element);
    if (!(myFile instanceof ServerPageFile)) {
      add(DefaultHighlightUtil.checkUnicodeBadCharacter(element));
    }
  }

  public static @Nullable JavaResolveResult resolveJavaReference(@NotNull PsiReference reference) {
    return reference instanceof PsiJavaReference psiJavaReference ? psiJavaReference.advancedResolve(false) : null;
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
    add(checkFeature(expression, JavaFeature.LAMBDA_EXPRESSIONS));
    PsiElement parent = PsiUtil.skipParenthesizedExprUp(expression.getParent());
    if (toReportFunctionalExpressionProblemOnParent(parent)) return;
    if (!hasErrorResults() && !LambdaUtil.isValidLambdaContext(parent)) {
      add(HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(expression)
            .descriptionAndTooltip(JavaErrorBundle.message("lambda.expression.not.expected")));
    }

    if (!hasErrorResults()) add(LambdaHighlightingUtil.checkConsistentParameterDeclaration(expression));

    PsiType functionalInterfaceType = null;
    if (!hasErrorResults()) {
      functionalInterfaceType = expression.getFunctionalInterfaceType();
      if (functionalInterfaceType != null) {
        add(HighlightClassUtil.checkExtendsSealedClass(expression, functionalInterfaceType));
        if (!hasErrorResults()) {
          String notFunctionalMessage = LambdaHighlightingUtil.checkInterfaceFunctional(expression, functionalInterfaceType);
          if (notFunctionalMessage != null) {
            add(HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(expression)
                  .descriptionAndTooltip(notFunctionalMessage));
          }
          else {
            add(LambdaHighlightingUtil.checkFunctionalInterfaceTypeAccessible(myFile.getProject(), expression, functionalInterfaceType));
          }
        }
      }
      else if (LambdaUtil.getFunctionalInterfaceType(expression, true) != null) {
        add(HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(expression).descriptionAndTooltip(
          JavaErrorBundle.message("cannot.infer.functional.interface.type")));
      }
    }

    if (!hasErrorResults() && functionalInterfaceType != null) {
      PsiCallExpression callExpression = parent instanceof PsiExpressionList && parent.getParent() instanceof PsiCallExpression ?
                                         (PsiCallExpression)parent.getParent() : null;
      MethodCandidateInfo parentCallResolveResult =
        callExpression != null ? tryCast(callExpression.resolveMethodGenerics(), MethodCandidateInfo.class) : null;
      String parentInferenceErrorMessage = parentCallResolveResult != null ? parentCallResolveResult.getInferenceErrorMessage() : null;
      PsiType returnType = LambdaUtil.getFunctionalInterfaceReturnType(functionalInterfaceType);
      Map<PsiElement, @Nls String> returnErrors = null;
      Set<PsiTypeParameter> parentTypeParameters =
        parentCallResolveResult == null ? Set.of() : Set.of(parentCallResolveResult.getElement().getTypeParameters());
      // If return type of the lambda was not fully inferred and lambda parameters don't mention the same type,
      // it means that lambda is not responsible for inference failure and blaming it would be unreasonable.
      boolean skipReturnCompatibility = parentCallResolveResult != null &&
                                        PsiTypesUtil.mentionsTypeParameters(returnType, parentTypeParameters)
                                        && !LambdaHighlightingUtil.lambdaParametersMentionTypeParameter(functionalInterfaceType, parentTypeParameters);
      if (!skipReturnCompatibility) {
        returnErrors = LambdaUtil.checkReturnTypeCompatible(expression, returnType);
      }
      if (parentInferenceErrorMessage != null && (returnErrors == null || !returnErrors.containsValue(parentInferenceErrorMessage))) {
        if (returnErrors == null) return;
        HighlightInfo.Builder info =
          HighlightMethodUtil.createIncompatibleTypeHighlightInfo(callExpression,
                                                                  parentCallResolveResult, expression);
        if (info != null) {
          for (PsiElement errorElement : returnErrors.keySet()) {
            IntentionAction action = AdjustFunctionContextFix.createFix(errorElement);
            if (action != null) {
              info.registerFix(action, null, null, null, null);
            }
          }
          add(info);
        }
      }
      else if (returnErrors != null && !PsiTreeUtil.hasErrorElements(expression)) {
        for (Map.Entry<PsiElement, @Nls String> entry : returnErrors.entrySet()) {
          PsiElement element = entry.getKey();
          HighlightInfo.Builder info = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
            .range(element)
            .descriptionAndTooltip(entry.getValue());
          IntentionAction action = AdjustFunctionContextFix.createFix(element);
          if (action != null) {
            info.registerFix(action, null, null, null, null);
          }
          if (element instanceof PsiExpression expr) {
            HighlightFixUtil.registerLambdaReturnTypeFixes(HighlightUtil.asConsumer(info), expression, expr);
          }
          add(info);
        }
      }
    }

    if (!hasErrorResults() && functionalInterfaceType != null) {
      PsiClassType.ClassResolveResult resolveResult = PsiUtil.resolveGenericsClassInType(functionalInterfaceType);
      PsiMethod interfaceMethod = LambdaUtil.getFunctionalInterfaceMethod(resolveResult);
      if (interfaceMethod != null) {
        PsiParameter[] parameters = interfaceMethod.getParameterList().getParameters();
        add(LambdaHighlightingUtil.checkParametersCompatible(expression, parameters,
                                                             LambdaUtil.getSubstitutor(interfaceMethod, resolveResult)));
      }
    }

    if (!hasErrorResults()) {
      PsiElement body = expression.getBody();
      if (body instanceof PsiCodeBlock block) {
        add(HighlightControlFlowUtil.checkUnreachableStatement(block));
      }
    }
  }

  @Override
  public void visitClass(@NotNull PsiClass aClass) {
    super.visitClass(aClass);
    if (aClass instanceof PsiSyntheticClass) return;
    if (!hasErrorResults()) GenericsHighlightUtil.checkTypeParameterOverrideEquivalentMethods(aClass, myLanguageLevel, myErrorSink, myOverrideEquivalentMethodsVisitedClasses, myOverrideEquivalentMethodsErrors);
  }

  @Override
  public void visitClassInitializer(@NotNull PsiClassInitializer initializer) {
    super.visitClassInitializer(initializer);
    if (!hasErrorResults()) add(HighlightControlFlowUtil.checkInitializerCompleteNormally(initializer));
    if (!hasErrorResults()) add(HighlightControlFlowUtil.checkUnreachableStatement(initializer.getBody()));
  }

  @Override
  public void visitJavaToken(@NotNull PsiJavaToken token) {
    super.visitJavaToken(token);

    IElementType type = token.getTokenType();
    if (!hasErrorResults() && type == JavaTokenType.TEXT_BLOCK_LITERAL) {
      add(checkFeature(token, JavaFeature.TEXT_BLOCKS));
    }

    if (!hasErrorResults() && type == JavaTokenType.RBRACE && token.getParent() instanceof PsiCodeBlock) {
      PsiElement gParent = token.getParent().getParent();
      PsiCodeBlock codeBlock;
      PsiType returnType;
      if (gParent instanceof PsiMethod method) {
        codeBlock = method.getBody();
        returnType = method.getReturnType();
      }
      else if (gParent instanceof PsiLambdaExpression lambdaExpression) {
        PsiElement body = lambdaExpression.getBody();
        if (!(body instanceof PsiCodeBlock)) return;
        codeBlock = (PsiCodeBlock)body;
        returnType = LambdaUtil.getFunctionalInterfaceReturnType(lambdaExpression);
      }
      else {
        return;
      }
      add(HighlightControlFlowUtil.checkMissingReturnStatement(codeBlock, returnType));
    }

    if (!hasErrorResults()) {
      add(HighlightUtil.checkExtraSemicolonBetweenImportStatements(token, type, myLanguageLevel));
    }
  }

  @Override
  public void visitExpression(@NotNull PsiExpression expression) {
    super.visitExpression(expression);

    PsiElement parent = expression.getParent();
    // Method expression of the call should not be especially processed
    if (parent instanceof PsiMethodCallExpression) return;
    PsiType type = expression.getType();

    if (!hasErrorResults()) add(HighlightControlFlowUtil.checkCannotWriteToFinal(expression, myFile));
    if (!hasErrorResults()) add(HighlightUtil.checkConditionalExpressionBranchTypesMatch(expression, type));
  }

  @Override
  public void visitField(@NotNull PsiField field) {
    super.visitField(field);
    if (!hasErrorResults()) add(HighlightControlFlowUtil.checkFinalFieldInitialized(field));
  }

  @Override
  public void visitImportStaticStatement(@NotNull PsiImportStaticStatement statement) {
    visitElement(statement);
    if (!hasErrorResults()) PreviewFeatureUtil.checkPreviewFeature(statement, myPreviewFeatureVisitor);
  }

  @Override
  public void visitIdentifier(@NotNull PsiIdentifier identifier) {
    PsiElement parent = identifier.getParent();
    if (parent instanceof PsiVariable variable) {
      add(HighlightUtil.checkVariableAlreadyDefined(variable));
    }
    else if (parent instanceof PsiClass aClass) {
      if (!hasErrorResults() && JavaFeature.EXTENSION_METHODS.isSufficient(myLanguageLevel)) {
        add(GenericsHighlightUtil.checkUnrelatedDefaultMethods(aClass, identifier));
      }
    }

    super.visitIdentifier(identifier);
  }

  @Override
  public void visitImportStatement(@NotNull PsiImportStatement statement) {
    super.visitImportStatement(statement);
    if (!hasErrorResults()) {
      PreviewFeatureUtil.checkPreviewFeature(statement, myPreviewFeatureVisitor);
    }
  }

  @Override
  public void visitImportStaticReferenceElement(@NotNull PsiImportStaticReferenceElement ref) {
    super.visitImportStaticReferenceElement(ref);
    JavaResolveResult[] results = ref.multiResolve(false);
    if (!hasErrorResults() && results.length == 1) {
      add(HighlightUtil.checkReference(ref, results[0], myFile, myLanguageLevel));
    }
  }

  @Override
  public void visitImportModuleStatement(@NotNull PsiImportModuleStatement statement) {
    super.visitImportModuleStatement(statement);
    if (!hasErrorResults()) add(checkFeature(statement, JavaFeature.MODULE_IMPORT_DECLARATIONS));
    if (!hasErrorResults()) add(ModuleHighlightUtil.checkModuleReference(statement));
  }

  @Override
  public void visitKeyword(@NotNull PsiKeyword keyword) {
    super.visitKeyword(keyword);
    PsiElement parent = keyword.getParent();
    String text = keyword.getText();
    if (parent instanceof PsiModifierList psiModifierList) {
      PsiElement pParent = psiModifierList.getParent();
      if (PsiModifier.ABSTRACT.equals(text) && pParent instanceof PsiMethod psiMethod) {
        if (!hasErrorResults()) {
          add(HighlightMethodUtil.checkAbstractMethodInConcreteClass(psiMethod, keyword));
        }
      }
      else if (pParent instanceof PsiEnumConstant) {
        String description = JavaErrorBundle.message("modifiers.for.enum.constants");
        add(HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(keyword).descriptionAndTooltip(description));
      }
    }
  }

  @Override
  public void visitMethod(@NotNull PsiMethod method) {
    super.visitMethod(method);
    if (!hasErrorResults()) add(HighlightMethodUtil.checkConstructorInImplicitClass(method));
    if (!hasErrorResults()) add(HighlightControlFlowUtil.checkUnreachableStatement(method.getBody()));
    if (!hasErrorResults()) add(HighlightMethodUtil.checkConstructorHandleSuperClassExceptions(method));
  }

  @Override
  public void visitMethodCallExpression(@NotNull PsiMethodCallExpression expression) {
    visitExpression(expression);
    if (!hasErrorResults()) {
      PreviewFeatureUtil.checkPreviewFeature(expression, myPreviewFeatureVisitor);
    }
  }

  @Override
  public void visitModifierList(@NotNull PsiModifierList list) {
    super.visitModifierList(list);
    PsiElement parent = list.getParent();
    if (parent instanceof PsiMethod method) {
      PsiClass aClass = method.getContainingClass();
      if (!hasErrorResults() && aClass != null) {
        GenericsHighlightUtil.computeOverrideEquivalentMethodErrors(aClass, myOverrideEquivalentMethodsVisitedClasses, myOverrideEquivalentMethodsErrors);
        myErrorSink.accept(myOverrideEquivalentMethodsErrors.get(method));
      }
    }
    else if (parent instanceof PsiClass aClass) {
      if (!hasErrorResults()) {
        GenericsHighlightUtil.computeOverrideEquivalentMethodErrors(aClass, myOverrideEquivalentMethodsVisitedClasses, myOverrideEquivalentMethodsErrors);
        myErrorSink.accept(myOverrideEquivalentMethodsErrors.get(aClass));
      }
    }
  }

  @Override
  public void visitNewExpression(@NotNull PsiNewExpression expression) {
    if (!hasErrorResults()) visitExpression(expression);

    if (!hasErrorResults()) {
      PreviewFeatureUtil.checkPreviewFeature(expression, myPreviewFeatureVisitor);
    }
  }

  @Override
  public void visitPackageStatement(@NotNull PsiPackageStatement statement) {
    super.visitPackageStatement(statement);
    if (JavaFeature.MODULES.isSufficient(myLanguageLevel)) {
      if (!hasErrorResults()) add(ModuleHighlightUtil.checkPackageStatement(statement, myFile, myJavaModule));
    }
  }

  @Override
  public void visitRecordComponent(@NotNull PsiRecordComponent recordComponent) {
    super.visitRecordComponent(recordComponent);
    if (!hasErrorResults()) add(HighlightControlFlowUtil.checkRecordComponentInitialized(recordComponent));
  }

  @Override
  public void visitReferenceElement(@NotNull PsiJavaCodeReferenceElement ref) {
    super.visitReferenceElement(ref);
    JavaResolveResult result = ref instanceof PsiExpression ? resolveOptimised(ref, myFile) : doVisitReferenceElement(ref);
    if (result != null) {
      PsiElement resolved = result.getElement();
      if (!hasErrorResults() && resolved instanceof PsiModifierListOwner) {
        PreviewFeatureUtil.checkPreviewFeature(ref, myPreviewFeatureVisitor);
      }
    }
  }

  private JavaResolveResult doVisitReferenceElement(@NotNull PsiJavaCodeReferenceElement ref) {
    JavaResolveResult result = resolveOptimised(ref, myFile);
    if (result == null) return null;

    PsiElement resolved = result.getElement();
    PsiElement parent = ref.getParent();

    add(HighlightUtil.checkReference(ref, result, myFile, myLanguageLevel));

    if ((parent instanceof PsiJavaCodeReferenceElement || ref.isQualified()) &&
        !hasErrorResults() &&
        resolved instanceof PsiTypeParameter) {
      boolean canSelectFromTypeParameter = myJavaSdkVersion.isAtLeast(JavaSdkVersion.JDK_1_7);
      if (canSelectFromTypeParameter) {
        PsiClass containingClass = PsiTreeUtil.getParentOfType(ref, PsiClass.class);
        if (containingClass != null) {
          if (PsiTreeUtil.isAncestor(containingClass.getExtendsList(), ref, false) ||
              PsiTreeUtil.isAncestor(containingClass.getImplementsList(), ref, false)) {
            canSelectFromTypeParameter = false;
          }
        }
      }
      if (!canSelectFromTypeParameter) {
        add(HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).descriptionAndTooltip(
          JavaErrorBundle.message("cannot.select.from.a.type.parameter")).range(ref));
      }
    }

    if (resolved != null && parent instanceof PsiReferenceList referenceList && !hasErrorResults()) {
      add(HighlightUtil.checkElementInReferenceList(ref, referenceList, result));
    }

    if (parent instanceof PsiAnonymousClass psiAnonymousClass && ref.equals(psiAnonymousClass.getBaseClassReference())) {
      GenericsHighlightUtil.computeOverrideEquivalentMethodErrors(psiAnonymousClass, myOverrideEquivalentMethodsVisitedClasses, myOverrideEquivalentMethodsErrors);
      myErrorSink.accept(myOverrideEquivalentMethodsErrors.get(psiAnonymousClass));
    }

    if (parent instanceof PsiNewExpression newExpression &&
        !(resolved instanceof PsiClass) &&
        resolved instanceof PsiNamedElement namedElement &&
        newExpression.getClassOrAnonymousClassReference() == ref) {
      String text = JavaErrorBundle.message("cannot.resolve.symbol", namedElement.getName());
      HighlightInfo.Builder info = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(ref).descriptionAndTooltip(text);
      if (HighlightUtil.isCallToStaticMember(newExpression)) {
        var action = new RemoveNewKeywordFix(newExpression);
        info.registerFix(action, null, null, null, null);
      }
      add(info);
    }

    if (!hasErrorResults()) {
      add(HighlightUtil.checkPackageAndClassConflict(ref, myFile));
    }
    if (!hasErrorResults()) {
      add(HighlightUtil.checkMemberReferencedBeforeConstructorCalled(ref, resolved, mySurroundingConstructor));
    }

    return result;
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

  private JavaResolveResult @Nullable [] resolveOptimised(@NotNull PsiReferenceExpression expression) {
    try {
      if (expression instanceof PsiReferenceExpressionImpl) {
        PsiReferenceExpressionImpl.OurGenericsResolver resolver = PsiReferenceExpressionImpl.OurGenericsResolver.INSTANCE;
        return JavaResolveUtil.resolveWithContainingFile(expression, resolver, true, true, myFile);
      }
      else {
        return expression.multiResolve(true);
      }
    }
    catch (IndexNotReadyException e) {
      return null;
    }
  }

  @Override
  public void visitReferenceExpression(@NotNull PsiReferenceExpression expression) {
    doVisitReferenceElement(expression);

    if (!hasErrorResults()) {
      visitExpression(expression);
      if (hasErrorResults()) return;
    }

    JavaResolveResult[] results = resolveOptimised(expression);
    if (results == null) return;
    JavaResolveResult result = results.length == 1 ? results[0] : JavaResolveResult.EMPTY;

    PsiElement resolved = result.getElement();
    if (resolved instanceof PsiVariable variable && resolved.getContainingFile() == expression.getContainingFile()) {
      boolean isFinal = variable.hasModifierProperty(PsiModifier.FINAL);
      if (isFinal && !variable.hasInitializer() && !(variable instanceof PsiPatternVariable)) {
        if (!hasErrorResults()) {
          add(HighlightControlFlowUtil.checkFinalVariableMightAlreadyHaveBeenAssignedTo(variable, expression, myFinalVarProblems));
        }
      }
      if (!hasErrorResults()) {
        try {
          add(HighlightControlFlowUtil.checkVariableInitializedBeforeUsage(expression, variable, myUninitializedVarProblems, myFile));
        }
        catch (IndexNotReadyException ignored) {
        }
      }
    }

    if (!hasErrorResults()) add(HighlightUtil.checkClassReferenceAfterQualifier(expression, resolved));
    PsiExpression qualifierExpression = expression.getQualifierExpression();
    if (!hasErrorResults() && myJavaModule == null && qualifierExpression != null) {
      add(GenericsHighlightUtil.checkMemberSignatureTypesAccessibility(expression));
    }
    if (!hasErrorResults() && resolved instanceof PsiModifierListOwner) {
      PreviewFeatureUtil.checkPreviewFeature(expression, myPreviewFeatureVisitor);
    }
  }

  @Override
  public void visitMethodReferenceExpression(@NotNull PsiMethodReferenceExpression expression) {
    visitElement(expression);
    PsiElement parent = PsiUtil.skipParenthesizedExprUp(expression.getParent());
    if (toReportFunctionalExpressionProblemOnParent(parent)) return;
    PsiType functionalInterfaceType = expression.getFunctionalInterfaceType();
    if (functionalInterfaceType != null && !PsiTypesUtil.allTypeParametersResolved(expression, functionalInterfaceType)) return;

    JavaResolveResult result;
    JavaResolveResult[] results;
    try {
      results = expression.multiResolve(true);
      result = results.length == 1 ? results[0] : JavaResolveResult.EMPTY;
    }
    catch (IndexNotReadyException e) {
      return;
    }
    PsiElement method = result.getElement();
    if (method instanceof PsiJvmMember && !result.isAccessible()) {
      String accessProblem = HighlightUtil.accessProblemDescription(expression, method, result);
      HighlightInfo.Builder info =
        HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(expression).descriptionAndTooltip(accessProblem);
      HighlightFixUtil.registerAccessQuickFixAction(HighlightUtil.asConsumer(info), (PsiJvmMember)method, expression, result.getCurrentFileResolveScope());
      add(info);
    }

    if (!LambdaUtil.isValidLambdaContext(parent)) {
      String description = JavaErrorBundle.message("method.reference.expression.is.not.expected");
      add(HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(expression).descriptionAndTooltip(description));
    }

    if (!hasErrorResults()) {
      PsiElement referenceNameElement = expression.getReferenceNameElement();
      if (referenceNameElement instanceof PsiKeyword) {
        if (!PsiMethodReferenceUtil.isValidQualifier(expression)) {
          PsiElement qualifier = expression.getQualifier();
          if (qualifier != null) {
            boolean pending = qualifier instanceof PsiJavaCodeReferenceElement ref &&
                              IncompleteModelUtil.isIncompleteModel(expression) &&
                              IncompleteModelUtil.canBePendingReference(ref);
            if (!pending) {
              String description = JavaErrorBundle.message("cannot.find.class", qualifier.getText());
              add(HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(qualifier).descriptionAndTooltip(description));
            }
          }
        }
      }
    }

    if (functionalInterfaceType != null) {
      if (!hasErrorResults()) {
        add(HighlightClassUtil.checkExtendsSealedClass(expression, functionalInterfaceType));
      }
      if (!hasErrorResults()) {
        boolean isFunctional = LambdaUtil.isFunctionalType(functionalInterfaceType);
        if (!isFunctional && !(IncompleteModelUtil.isIncompleteModel(expression) &&
                               IncompleteModelUtil.isUnresolvedClassType(functionalInterfaceType))) {
          String description =
            JavaErrorBundle.message("not.a.functional.interface", functionalInterfaceType.getPresentableText());
          add(HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(expression).descriptionAndTooltip(description));
        }
      }
      if (!hasErrorResults()) {
        add(LambdaHighlightingUtil.checkFunctionalInterfaceTypeAccessible(myFile.getProject(), expression, functionalInterfaceType));
      }
      if (!hasErrorResults()) {
        String errorMessage = PsiMethodReferenceHighlightingUtil.checkMethodReferenceContext(expression);
        if (errorMessage != null) {
          HighlightInfo.Builder info =
            HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(expression).descriptionAndTooltip(errorMessage);
          if (method instanceof PsiMethod psiMethod &&
              !psiMethod.isConstructor() &&
              !psiMethod.hasModifierProperty(PsiModifier.ABSTRACT)) {
            boolean shouldHave = !psiMethod.hasModifierProperty(PsiModifier.STATIC);
            QuickFixAction.registerQuickFixActions(info, null, JvmElementActionFactories.createModifierActions(
              (JvmModifiersOwner)method, MemberRequestsKt.modifierRequest(JvmModifier.STATIC, shouldHave)));
          }
          add(info);
        }
      }
    }

    if (!hasErrorResults()) {
      PsiElement qualifier = expression.getQualifier();
      if (qualifier instanceof PsiTypeElement typeElement) {
        PsiType psiType = typeElement.getType();
        String wildcardMessage = checkTypeArguments(typeElement, psiType);
        if (wildcardMessage != null) {
          add(HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(qualifier).descriptionAndTooltip(wildcardMessage));
        }
      }
    }

    if (!hasErrorResults()) {
      add(PsiMethodReferenceHighlightingUtil.checkRawConstructorReference(expression));
    }

    if (!hasErrorResults()) {
      boolean resolvedButNonApplicable = results.length == 1 && results[0] instanceof MethodCandidateInfo methodInfo &&
                                         !methodInfo.isApplicable() &&
                                         functionalInterfaceType != null;
      if (results.length != 1 || resolvedButNonApplicable) {
        String description = null;
        if (results.length == 1) {
          description = ((MethodCandidateInfo)results[0]).getInferenceErrorMessage();
        }
        if (expression.isConstructor()) {
          PsiClass containingClass = PsiMethodReferenceUtil.getQualifierResolveResult(expression).getContainingClass();

          if (containingClass != null && 
              containingClass.isPhysical() &&
              description == null) {
            description = JavaErrorBundle.message("cannot.resolve.constructor", containingClass.getName());
          }
        }
        else if (description == null) {
          if (results.length > 1) {
            if (IncompleteModelUtil.isIncompleteModel(expression) &&
                IncompleteModelUtil.isUnresolvedClassType(functionalInterfaceType)) {
              return;
            }
            String t1 = HighlightUtil.format(requireNonNull(results[0].getElement()));
            String t2 = HighlightUtil.format(requireNonNull(results[1].getElement()));
            description = JavaErrorBundle.message("ambiguous.reference", expression.getReferenceName(), t1, t2);
          }
          else {
            if (IncompleteModelUtil.isIncompleteModel(expression) && IncompleteModelUtil.canBePendingReference(expression)) {
              PsiElement referenceNameElement = expression.getReferenceNameElement();
              if (referenceNameElement != null) {
                add(HighlightUtil.getPendingReferenceHighlightInfo(referenceNameElement));
              }
              return;
            }
            
            if (!(resolvedButNonApplicable && HighlightMethodUtil.hasSurroundingInferenceError(expression))) {
              description = JavaErrorBundle.message("cannot.resolve.method", expression.getReferenceName());
            }
          }
        }

        if (description != null) {
          PsiElement referenceNameElement = ObjectUtils.notNull(expression.getReferenceNameElement(), expression);
          HighlightInfoType type = results.length == 0 ? HighlightInfoType.WRONG_REF : HighlightInfoType.ERROR;
          HighlightInfo.Builder highlightInfo =
            HighlightInfo.newHighlightInfo(type).descriptionAndTooltip(description).range(referenceNameElement);
          TextRange fixRange = HighlightMethodUtil.getFixRange(referenceNameElement);
          IntentionAction action = QuickFixFactory.getInstance().createCreateMethodFromUsageFix(expression);
          highlightInfo.registerFix(action, null, null, fixRange, null);
          add(highlightInfo);
        }
      }
    }

    if (!hasErrorResults()) {
      String badReturnTypeMessage = PsiMethodReferenceUtil.checkReturnType(expression, result, functionalInterfaceType);
      if (badReturnTypeMessage != null) {
        HighlightInfo.Builder info =
          HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(expression).descriptionAndTooltip(badReturnTypeMessage);
        IntentionAction action = AdjustFunctionContextFix.createFix(expression);
        if (action != null) {
          info.registerFix(action, null, null, null, null);
        }
        add(info);
      }
    }
    if (!hasErrorResults() && method instanceof PsiModifierListOwner) {
      PreviewFeatureUtil.checkPreviewFeature(expression, myPreviewFeatureVisitor);
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
  public void visitReferenceList(@NotNull PsiReferenceList list) {
    super.visitReferenceList(list);
    if (list.getFirstChild() == null) return;
    PsiElement parent = list.getParent();
    if (!(parent instanceof PsiTypeParameter)) {
      if (!hasErrorResults()) HighlightClassUtil.checkPermitsList(list, myErrorSink);
    }
  }

  @Override
  public void visitSwitchStatement(@NotNull PsiSwitchStatement statement) {
    super.visitSwitchStatement(statement);
    checkSwitchBlock(statement);
  }

  @Override
  public void visitSwitchExpression(@NotNull PsiSwitchExpression expression) {
    super.visitSwitchExpression(expression);
    if (!hasErrorResults()) add(checkFeature(expression, JavaFeature.SWITCH_EXPRESSION));
    checkSwitchBlock(expression);
    if (!hasErrorResults()) HighlightUtil.checkSwitchExpressionReturnTypeCompatible(expression, myErrorSink);
    if (!hasErrorResults()) HighlightUtil.checkSwitchExpressionHasResult(expression, myErrorSink);
  }

  private void checkSwitchBlock(@NotNull PsiSwitchBlock switchBlock) {
    SwitchBlockHighlightingModel model = SwitchBlockHighlightingModel.createInstance(myLanguageLevel, switchBlock, myFile);
    if (model == null) return;
    if (!hasErrorResults()) model.checkSwitchBlockStatements(myErrorSink);
    if (!hasErrorResults()) model.checkSwitchSelectorType(myErrorSink);
    if (!hasErrorResults()) model.checkSwitchLabelValues(myErrorSink);
  }

  @Override
  public void visitThisExpression(@NotNull PsiThisExpression expr) {
    if (!(expr.getParent() instanceof PsiReceiverParameter)) {
      add(HighlightUtil.checkMemberReferencedBeforeConstructorCalled(expr, null, mySurroundingConstructor));
      if (!hasErrorResults()) visitExpression(expr);
    }
  }

  @Override
  public void visitSuperExpression(@NotNull PsiSuperExpression expression) {
    add(HighlightUtil.checkMemberReferencedBeforeConstructorCalled(expression, null, mySurroundingConstructor));
    if (!hasErrorResults()) visitExpression(expression);
  }

  @Override
  public void visitResourceExpression(@NotNull PsiResourceExpression resource) {
    super.visitResourceExpression(resource);
    if (!hasErrorResults()) add(HighlightUtil.checkResourceVariableIsFinal(resource));
  }

  @Override
  public void visitTypeElement(@NotNull PsiTypeElement type) {
    super.visitTypeElement(type);
    if (!hasErrorResults()) {
      PreviewFeatureUtil.checkPreviewFeature(type, myPreviewFeatureVisitor);
    }
  }

  @Override
  public void visitModule(@NotNull PsiJavaModule module) {
    super.visitModule(module);
    if (!hasErrorResults()) add(checkFeature(module, JavaFeature.MODULES));
    if (!hasErrorResults()) add(ModuleHighlightUtil.checkFileName(module, myFile));
    if (!hasErrorResults()) add(ModuleHighlightUtil.checkFileDuplicates(module, myFile));
    if (!hasErrorResults()) ModuleHighlightUtil.checkDuplicateStatements(module, myErrorSink);
    if (!hasErrorResults()) add(ModuleHighlightUtil.checkClashingReads(module));
    if (!hasErrorResults()) ModuleHighlightUtil.checkUnusedServices(module, myFile, myErrorSink);
    if (!hasErrorResults()) add(ModuleHighlightUtil.checkFileLocation(module, myFile));
  }

  @Override
  public void visitModuleStatement(@NotNull PsiStatement statement) {
    super.visitModuleStatement(statement);
    if (!hasErrorResults()) {
      PreviewFeatureUtil.checkPreviewFeature(statement, myPreviewFeatureVisitor);
    }
  }

  @Override
  public void visitRequiresStatement(@NotNull PsiRequiresStatement statement) {
    super.visitRequiresStatement(statement);
    if (JavaFeature.MODULES.isSufficient(myLanguageLevel)) {
      if (!hasErrorResults()) add(ModuleHighlightUtil.checkModuleReference(statement));
      if (!hasErrorResults() && myLanguageLevel.isAtLeast(LanguageLevel.JDK_10)) {
        ModuleHighlightUtil.checkModifiers(statement, myErrorSink);
      }
    }
  }

  @Override
  public void visitPackageAccessibilityStatement(@NotNull PsiPackageAccessibilityStatement statement) {
    super.visitPackageAccessibilityStatement(statement);
    if (JavaFeature.MODULES.isSufficient(myLanguageLevel)) {
      if (!hasErrorResults()) add(ModuleHighlightUtil.checkHostModuleStrength(statement));
      if (!hasErrorResults()) add(ModuleHighlightUtil.checkPackageReference(statement, myFile));
      if (!hasErrorResults()) ModuleHighlightUtil.checkPackageAccessTargets(statement, myErrorSink);
    }
  }

  @Override
  public void visitUsesStatement(@NotNull PsiUsesStatement statement) {
    super.visitUsesStatement(statement);
    if (JavaFeature.MODULES.isSufficient(myLanguageLevel)) {
      if (!hasErrorResults()) add(ModuleHighlightUtil.checkServiceReference(statement.getClassReference()));
    }
  }

  @Override
  public void visitProvidesStatement(@NotNull PsiProvidesStatement statement) {
    super.visitProvidesStatement(statement);
    if (JavaFeature.MODULES.isSufficient(myLanguageLevel)) {
      if (!hasErrorResults()) ModuleHighlightUtil.checkServiceImplementations(statement, myFile, myErrorSink);
    }
  }

  private @Nullable HighlightInfo.Builder checkFeature(@NotNull PsiElement element, @NotNull JavaFeature feature) {
    return HighlightUtil.checkFeature(element, feature, myLanguageLevel, myFile);
  }

  private static @NlsContexts.DetailedDescription String checkTypeArguments(PsiTypeElement qualifier, PsiType psiType) {
    if (psiType instanceof PsiClassType) {
      final PsiJavaCodeReferenceElement referenceElement = qualifier.getInnermostComponentReferenceElement();
      if (referenceElement != null) {
        PsiType[] typeParameters = referenceElement.getTypeParameters();
        for (PsiType typeParameter : typeParameters) {
          if (typeParameter instanceof PsiWildcardType) {
            return JavaPsiBundle.message("error.message.wildcard.not.expected");
          }
        }
      }
    }
    return null;
  }
}
