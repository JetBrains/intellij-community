package com.intellij.codeInsight.daemon.impl.analysis;

import com.intellij.codeInsight.daemon.JavaErrorMessages;
import com.intellij.codeInsight.daemon.impl.*;
import com.intellij.codeInsight.daemon.impl.quickfix.QuickFixAction;
import com.intellij.codeInsight.daemon.impl.quickfix.SetupJDKFix;
import com.intellij.lang.Language;
import com.intellij.lang.annotation.Annotation;
import com.intellij.lang.annotation.Annotator;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.lang.jsp.JspxFileViewProvider;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.impl.injected.DocumentRange;
import com.intellij.openapi.fileTypes.SyntaxHighlighter;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.controlFlow.ControlFlowUtil;
import com.intellij.psi.impl.source.javadoc.PsiDocMethodOrFieldRef;
import com.intellij.psi.impl.source.jsp.jspJava.JspClass;
import com.intellij.psi.impl.source.jsp.jspJava.JspExpression;
import com.intellij.psi.impl.source.jsp.jspJava.OuterLanguageElement;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.javadoc.PsiDocTagValue;
import com.intellij.psi.jsp.el.ELExpressionHolder;
import com.intellij.psi.util.MethodSignature;
import com.intellij.psi.util.MethodSignatureBackedByPsiMethod;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlElement;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlText;
import gnu.trove.THashMap;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;
import java.util.Map;

public class HighlightVisitorImpl extends PsiElementVisitor implements HighlightVisitor, ProjectComponent {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.daemon.impl.analysis.HighlightVisitorImpl");

  private final PsiResolveHelper myResolveHelper;

  private HighlightInfoHolder myHolder;

  private RefCountHolder myRefCountHolder;

  private final XmlHighlightVisitor myXmlVisitor;
  // map codeBlock->List of PsiReferenceExpression of uninitailized final variables
  private final Map<PsiElement, Collection<PsiReferenceExpression>> myUninitializedVarProblems = new THashMap<PsiElement, Collection<PsiReferenceExpression>>();
  // map codeBlock->List of PsiReferenceExpression of extra initailization of final variable
  private final Map<PsiElement, Collection<ControlFlowUtil.VariableInfo>> myFinalVarProblems = new THashMap<PsiElement, Collection<ControlFlowUtil.VariableInfo>>();
  private final Map<PsiParameter, Boolean> myParameterIsReassigned = new THashMap<PsiParameter, Boolean>();

  private final Map<String, Pair<PsiImportStatement,PsiClass>> mySingleImportedClasses = new THashMap<String, Pair<PsiImportStatement,PsiClass>>();
  private final Map<String, Pair<PsiImportStaticReferenceElement,PsiField>> mySingleImportedFields = new THashMap<String, Pair<PsiImportStaticReferenceElement,PsiField>>();
  private final Map<MethodSignature, Pair<PsiImportStaticReferenceElement,PsiMethod>> mySingleImportedMethods = new THashMap<MethodSignature, Pair<PsiImportStaticReferenceElement, PsiMethod>>();
  private final AnnotationHolderImpl myAnnotationHolder = new AnnotationHolderImpl();

  @NotNull
  public String getComponentName() {
    return "HighlightVisitorImpl";
  }

  public void initComponent() {
  }

  public void disposeComponent() {}

  public void projectOpened() {}

  public void projectClosed() {}

  public HighlightVisitorImpl(PsiManager manager) {
    this(new XmlHighlightVisitor(),manager.getResolveHelper());
  }

  private HighlightVisitorImpl(XmlHighlightVisitor xmlHighlightVisitor, PsiResolveHelper resolveHelper) {
    myResolveHelper = resolveHelper;
    myXmlVisitor = xmlHighlightVisitor;
  }

  public HighlightVisitorImpl clone() {
    return new HighlightVisitorImpl(myXmlVisitor, myResolveHelper);
  }

  public boolean suitableForFile(PsiFile file) {
    return true;
  }

  public void visit(PsiElement element, HighlightInfoHolder holder) {
    if (!holder.isWritable()) {
      throw new UnsupportedOperationException();
    }
    myHolder = holder;

    if (LOG.isDebugEnabled()) {
      LOG.assertTrue(element.isValid());
    }
    element.accept(this);
  }

  public void init() {
    myUninitializedVarProblems.clear();
    myFinalVarProblems.clear();
    mySingleImportedClasses.clear();
    mySingleImportedFields.clear();
    mySingleImportedMethods.clear();
    myParameterIsReassigned.clear();
  }

  public void setRefCountHolder(RefCountHolder refCountHolder) {
    myRefCountHolder = refCountHolder;
  }

  public void visitElement(PsiElement element) {
    Language lang = element.getLanguage();
    boolean hasAnnotators = highlightInjectedPsi(element);
    List<Annotator> annotators = lang.getAnnotators();

    if (annotators.size() > 0) {
      //noinspection ForLoopReplaceableByForEach
      for (int i = 0; i < annotators.size(); i++) {
        annotators.get(i).annotate(element, myAnnotationHolder);
      }
      hasAnnotators = true;
    }
    else if (element instanceof OuterLanguageElement) {
      myXmlVisitor.visitJspElement((OuterLanguageElement)element);
    }

    if (hasAnnotators) {
      convertAnnotationsToHighlightInfos();
    }
  }

  private boolean highlightInjectedPsi(final PsiElement element) {
    if (!(element instanceof PsiLanguageInjectionHost)) {
      return false;
    }
    PsiLanguageInjectionHost injectionHost = (PsiLanguageInjectionHost)element;
    List<Pair<PsiElement, TextRange>> injected = injectionHost.getInjectedPsi();
    if (injected == null) return false;
    PsiDocumentManager documentManager = PsiDocumentManager.getInstance(element.getProject());
    for (Pair<PsiElement, TextRange> pair : injected) {
      PsiElement injectedPsi = pair.getFirst();
      final DocumentRange documentRange = (DocumentRange)documentManager.getDocument((PsiFile)injectedPsi);

      Language injectedLanguage = injectedPsi.getLanguage();
      VirtualFile virtualFile = element.getContainingFile().getVirtualFile();
      SyntaxHighlighter syntaxHighlighter = injectedLanguage.getSyntaxHighlighter(element.getProject(), virtualFile);
      final Annotator languageAnnotator = injectedLanguage.getAnnotator();
      final SyntaxHighlighterAsAnnotator syntaxAnnotator = new SyntaxHighlighterAsAnnotator(syntaxHighlighter);
      PsiRecursiveElementVisitor visitor = new PsiRecursiveElementVisitor() {
        final AnnotationHolderImpl fixingAnnotationHolder = new AnnotationHolderImpl() {
          protected Annotation createAnnotation(TextRange range, HighlightSeverity severity, String message) {
            if (!documentRange.isEditable(range)) return null; //do not highlight generated header/footer
            TextRange patched = new TextRange(documentRange.injectedToHost(range.getStartOffset()), documentRange.injectedToHost(range.getEndOffset()));
            Annotation annotation = super.createAnnotation(patched, severity, message);
            myAnnotationHolder.add(annotation);
            return annotation;
          }
        };
        public void visitElement(PsiElement element) {
          super.visitElement(element);
          syntaxAnnotator.annotate(element, fixingAnnotationHolder);
          if (languageAnnotator != null) {
            languageAnnotator.annotate(element, fixingAnnotationHolder);
          }
        }

        public void visitErrorElement(PsiErrorElement element) {
          HighlightInfo info = createErrorElementInfo(element);
          HighlightInfo fixed = new HighlightInfo(HighlightInfoType.ERROR, documentRange.injectedToHost(info.startOffset),
                                                  documentRange.injectedToHost(info.endOffset), info.description, info.toolTip);
          myHolder.add(fixed);
        }
      };

      injectedPsi.accept(visitor);
    }
    return true;
  }

  private void convertAnnotationsToHighlightInfos() {
    if (myAnnotationHolder.hasAnnotations()) {
      for (Annotation annotation : myAnnotationHolder) {
        myHolder.add(HighlightUtil.convertToHighlightInfo(annotation));
      }
      myAnnotationHolder.clear();
    }
  }

  public void visitArrayInitializerExpression(PsiArrayInitializerExpression expression) {
    super.visitArrayInitializerExpression(expression);
    if (!myHolder.hasErrorResults()) myHolder.add(HighlightUtil.checkArrayInitializerApplicable(expression));
    if (!(expression.getParent() instanceof PsiNewExpression)) {
      if (!myHolder.hasErrorResults()) myHolder.add(GenericsHighlightUtil.checkGenericArrayCreation(expression, expression.getType()));
    }
  }

  public void visitAssignmentExpression(PsiAssignmentExpression assignment) {
    if (!myHolder.hasErrorResults()) myHolder.add(HighlightUtil.checkAssignmentCompatibleTypes(assignment));
    if (!myHolder.hasErrorResults()) myHolder.add(HighlightUtil.checkAssignmentOperatorApplicable(assignment));
    if (!myHolder.hasErrorResults()) visitExpression(assignment);
  }

  public void visitBinaryExpression(PsiBinaryExpression expression) {
    super.visitBinaryExpression(expression);
    if (!myHolder.hasErrorResults()) myHolder.add(HighlightUtil.checkBinaryOperatorApplicable(expression));
  }

  public void visitBreakStatement(PsiBreakStatement statement) {
    super.visitBreakStatement(statement);
    if (!myHolder.hasErrorResults()) {
      myHolder.add(HighlightUtil.checkLabelDefined(statement.getLabelIdentifier(), statement.findExitedStatement()));
    }
  }

  public void visitClass(PsiClass aClass) {
    super.visitClass(aClass);
    if (aClass instanceof JspClass) return;
    if (!myHolder.hasErrorResults()) myHolder.add(GenericsHighlightUtil.checkInterfaceMultipleInheritance(aClass));
    if (!myHolder.hasErrorResults()) myHolder.add(HighlightClassUtil.checkDuplicateTopLevelClass(aClass));
    if (!myHolder.hasErrorResults()) myHolder.add(GenericsHighlightUtil.checkEnumMustNotBeLocal(aClass));
    if (!myHolder.hasErrorResults()) myHolder.add(HighlightUtil.checkImplicitThisReferenceBeforeSuper(aClass));
  }

  public void visitClassInitializer(PsiClassInitializer initializer) {
    super.visitClassInitializer(initializer);
    if (!myHolder.hasErrorResults()) myHolder.add(HighlightControlFlowUtil.checkInitializerCompleteNormally(initializer));
    if (!myHolder.hasErrorResults()) myHolder.add(HighlightControlFlowUtil.checkUnreachableStatement(initializer.getBody()));
    if (!myHolder.hasErrorResults()) {
      myHolder.add(HighlightClassUtil.checkThingNotAllowedInInterface(initializer, initializer.getContainingClass()));
    }
  }

  public void visitClassObjectAccessExpression(PsiClassObjectAccessExpression expression) {
    super.visitClassObjectAccessExpression(expression);
    if (!myHolder.hasErrorResults()) myHolder.add(GenericsHighlightUtil.checkClassObjectAccessExpression(expression));
  }

  public void visitComment(PsiComment comment) {
    super.visitComment(comment);
    if (!myHolder.hasErrorResults()) myHolder.add(HighlightUtil.checkUnclosedComment(comment));
  }

  public void visitContinueStatement(PsiContinueStatement statement) {
    super.visitContinueStatement(statement);
    if (!myHolder.hasErrorResults()) {
      myHolder.add(HighlightUtil.checkLabelDefined(statement.getLabelIdentifier(), statement.findContinuedStatement()));
    }
  }

  public void visitJavaToken(PsiJavaToken token) {
    super.visitJavaToken(token);
    if (!myHolder.hasErrorResults()
        && token.getTokenType() == JavaTokenType.RBRACE
        && token.getParent() instanceof PsiCodeBlock
        && token.getParent().getParent() instanceof PsiMethod) {
      PsiMethod method = (PsiMethod)token.getParent().getParent();
      myHolder.add(HighlightControlFlowUtil.checkMissingReturnStatement(method));
    }

  }

  public void visitDocComment(PsiDocComment comment) {
    if (!myHolder.hasErrorResults()) myHolder.add(HighlightUtil.checkUnclosedComment(comment));
  }

  public void visitDocTagValue(PsiDocTagValue value) {
    if (value.getReference() != null) {
      PsiReference reference = value.getReference();
      if (reference != null) {
        PsiElement element = reference.resolve();
        if (element instanceof PsiMethod) {
          myHolder.add(HighlightNamesUtil.highlightMethodName((PsiMethod)element, ((PsiDocMethodOrFieldRef)value).getNameElement(), false));
        }
        else if (element instanceof PsiParameter) {
          myHolder.add(HighlightNamesUtil.highlightVariable((PsiVariable)element, value.getNavigationElement()));
        }
      }
    }
  }

  public void visitErrorElement(PsiErrorElement element) {
    if(filterJspErrors(element)) return;

    if (PsiTreeUtil.getParentOfType(element, PsiDocComment.class) != null) return;

    HighlightInfo info = createErrorElementInfo(element);
    myHolder.add(info);
  }

  private HighlightInfo createErrorElementInfo(final PsiErrorElement element) {
    TextRange range = element.getTextRange();
    if (range.getLength() > 0) {
      final HighlightInfo highlightInfo = HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR, range, element.getErrorDescription());
      if (PsiTreeUtil.getParentOfType(element, XmlTag.class) != null) {
        myXmlVisitor.registerXmlErrorQuickFix(element,highlightInfo);
      }
      return highlightInfo;
    }
    int offset = range.getStartOffset();
    PsiFile containingFile = element.getContainingFile();
    int fileLength = containingFile.getTextLength();
    PsiElement elementAtOffset = containingFile.findElementAt(offset);
    String text = elementAtOffset == null ? null : elementAtOffset.getText();
    HighlightInfo info;
    if (offset < fileLength && text != null && !StringUtil.startsWithChar(text, '\n') && !StringUtil.startsWithChar(text, '\r')) {
      int start = offset;
      PsiElement prevElement = containingFile.findElementAt(offset - 1);
      if (offset > 0 && prevElement != null && prevElement.getText().equals("(") && StringUtil.startsWithChar(text, ')')) {
        start = offset - 1;
      }
      int end = offset + 1;
      info = HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR, start, end, element.getErrorDescription());
      info.navigationShift = offset - start;
    }
    else {
      int start;
      int end;
      if (offset > 0) {
        start = offset - 1;
        end = offset;
      }
      else {
        start = offset;
        end = offset < fileLength ? offset + 1 : offset;
      }
      info = HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR, start, end, element.getErrorDescription());
      info.isAfterEndOfLine = true;
    }
    return info;
  }

  private static boolean filterJspErrors(final PsiErrorElement element) {
    PsiElement nextSibling = element.getNextSibling();

    if (nextSibling == null) {
      final PsiFile containingFile = element.getContainingFile();
      if (PsiUtil.isInJspFile(containingFile)) {
        final JspxFileViewProvider viewProvider = (JspxFileViewProvider)containingFile.getViewProvider();
        nextSibling = viewProvider.findElementAt(element.getTextOffset() + 1, viewProvider.getTemplateDataLanguage());
      }
    }

    while (nextSibling instanceof PsiWhiteSpace) {
      nextSibling = nextSibling.getNextSibling();
    }

    final PsiElement psiElement = nextSibling == null ? null : PsiTreeUtil.findCommonParent(nextSibling, element);
    if ((nextSibling instanceof OuterLanguageElement ||
         nextSibling instanceof JspExpression ||
         nextSibling instanceof ELExpressionHolder
        ) && psiElement != null && !(psiElement instanceof PsiFile) // error is not inside jsp text
       ) {
      return true;
    }

    final XmlAttributeValue parentOfType = PsiTreeUtil.getParentOfType(element, XmlAttributeValue.class);
    if(parentOfType != null && parentOfType.getUserData(XmlHighlightVisitor.DO_NOT_VALIDATE_KEY) != null) {
      return true;
    }

    final PsiElement prevLeaf = PsiTreeUtil.prevLeaf(element, true);
    if (prevLeaf instanceof OuterLanguageElement &&
        prevLeaf.getTextRange().getEndOffset() != element.getContainingFile().getTextLength()
       ) {
      return true;
    }

    return element.getParent().getUserData(XmlHighlightVisitor.DO_NOT_VALIDATE_KEY) != null;

  }

  public void visitEnumConstant(PsiEnumConstant enumConstant) {
    super.visitEnumConstant(enumConstant);
    if (!myHolder.hasErrorResults()) myHolder.add(GenericsHighlightUtil.checkEnumConstantForConstructorProblems(enumConstant));
    if (!myHolder.hasErrorResults()) registerConstructorCall(enumConstant);
  }

  public void visitEnumConstantInitializer(PsiEnumConstantInitializer enumConstantInitializer) {
    super.visitEnumConstantInitializer(enumConstantInitializer);
    if (!myHolder.hasErrorResults()) myHolder.add(HighlightClassUtil.checkClassMustBeAbstract(enumConstantInitializer));
  }

  public void visitExpression(PsiExpression expression) {
    super.visitExpression(expression);
    if (myHolder.add(HighlightUtil.checkMustBeBoolean(expression))) return;
    if (expression instanceof PsiArrayAccessExpression
        && ((PsiArrayAccessExpression)expression).getIndexExpression() != null) {
      myHolder.add(HighlightUtil.checkValidArrayAccessExpression(((PsiArrayAccessExpression)expression).getArrayExpression(),
                                                                 ((PsiArrayAccessExpression)expression).getIndexExpression()));
    }
    else if (expression.getParent() instanceof PsiNewExpression
             && ((PsiNewExpression)expression.getParent()).getQualifier() != expression
             && ((PsiNewExpression)expression.getParent()).getArrayInitializer() != expression) {
      // like in 'new String["s"]'
      myHolder.add(HighlightUtil.checkValidArrayAccessExpression(null, expression));
    }
    if (!myHolder.hasErrorResults()) myHolder.add(HighlightControlFlowUtil.checkCannotWriteToFinal(expression));
    if (!myHolder.hasErrorResults()) myHolder.add(HighlightUtil.checkVariableExpected(expression));
    if (!myHolder.hasErrorResults()) myHolder.add(HighlightUtil.checkArrayInitalizerCompatibleTypes(expression));
    if (!myHolder.hasErrorResults()) myHolder.add(HighlightUtil.checkTernaryOperatorConditionIsBoolean(expression));
    if (!myHolder.hasErrorResults()) myHolder.add(HighlightUtil.checkAssertOperatorTypes(expression));
    if (!myHolder.hasErrorResults()) myHolder.add(HighlightUtil.checkSynchronizedExpressionType(expression));
    if (!myHolder.hasErrorResults()) myHolder.add(HighlightUtil.checkConditionalExpressionBranchTypesMatch(expression));
    if (!myHolder.hasErrorResults()) myHolder.add(HighlightUtil.checkConstantExpressionOverflow(expression));
    if (!myHolder.hasErrorResults()
        && expression.getParent() instanceof PsiThrowStatement
        && ((PsiThrowStatement)expression.getParent()).getException() == expression) {
      PsiType type = expression.getType();
      myHolder.add(HighlightUtil.checkMustBeThrowable(type, expression, true));
    }

    if (!myHolder.hasErrorResults()) {
      myHolder.add(AnnotationsHighlightUtil.checkConstantExpression(expression));
    }
  }

  public void visitField(PsiField field) {
    super.visitField(field);
    if (!myHolder.hasErrorResults()) myHolder.add(HighlightControlFlowUtil.checkFinalFieldInitialized(field));
  }

  public void visitImportStaticStatement(PsiImportStaticStatement statement) {
    if (PsiUtil.getLanguageLevel(statement).compareTo(LanguageLevel.JDK_1_5) < 0) {
      myHolder.add(HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR,
                                                     statement.getFirstChild(),
                                                     JavaErrorMessages.message("static.imports.prior.15")));
    }
  }

  public void visitForeachStatement(PsiForeachStatement statement) {
    if (PsiUtil.getLanguageLevel(statement).compareTo(LanguageLevel.JDK_1_5) < 0) {
      myHolder.add(HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR,
                                                     statement.getFirstChild(),
                                                     JavaErrorMessages.message("foreach.prior.15")));
    }
  }

  public void visitIdentifier(PsiIdentifier identifier) {
    PsiElement parent = identifier.getParent();
    if (parent instanceof PsiVariable) {
      myHolder.add(HighlightUtil.checkVariableAlreadyDefined((PsiVariable)parent));
    }
    else if (parent instanceof PsiClass) {
      myHolder.add(HighlightClassUtil.checkClassAlreadyImported((PsiClass)parent, identifier));
      myHolder.add(HighlightClassUtil.checkExternalizableHasPublicNoArgsConstructor((PsiClass)parent, identifier));
      if (!(parent instanceof PsiAnonymousClass)) {
        myHolder.add(HighlightNamesUtil.highlightClassName((PsiClass)parent, ((PsiClass)parent).getNameIdentifier()));
      }
    }
    else if (parent instanceof PsiMethod) {
      PsiMethod method = (PsiMethod)parent;
      if (method.isConstructor()) {
        myHolder.add(HighlightMethodUtil.checkConstructorName(method));
      }
    }
  }

  public void visitImportStatement(PsiImportStatement statement) {
    if (!myHolder.hasErrorResults()) myHolder.add(HighlightUtil.checkSingleImportClassConflict(statement, mySingleImportedClasses));
  }


  public void visitInstanceOfExpression(PsiInstanceOfExpression expression) {
    super.visitInstanceOfExpression(expression);
    if (!myHolder.hasErrorResults()) myHolder.add(HighlightUtil.checkInstanceOfApplicable(expression));
    if (!myHolder.hasErrorResults()) myHolder.add(GenericsHighlightUtil.checkInstanceOfGenericType(expression));
  }

  public void visitKeyword(PsiKeyword keyword) {
    super.visitKeyword(keyword);
    PsiElement parent = keyword.getParent();
    if (parent instanceof PsiModifierList) {
      PsiModifierList psiModifierList = (PsiModifierList)parent;
      if (!myHolder.hasErrorResults()) myHolder.add(HighlightUtil.checkNotAllowedModifier(keyword, psiModifierList));
      if (!myHolder.hasErrorResults()) myHolder.add(HighlightUtil.checkIllegalModifierCombination(keyword, psiModifierList));
      if (!myHolder.hasErrorResults()) myHolder.add(HighlightClassUtil.checkPublicClassInRightFile(keyword, psiModifierList));
      if (PsiModifier.ABSTRACT.equals(keyword.getText()) && psiModifierList.getParent() instanceof PsiMethod) {
        if (!myHolder.hasErrorResults()) {
          myHolder.add(HighlightMethodUtil.checkAbstractMethodInConcreteClass((PsiMethod)psiModifierList.getParent(), keyword));
        }
      }
    }
    else if (keyword.getText().equals(PsiKeyword.CONTINUE) && parent instanceof PsiContinueStatement) {
      PsiContinueStatement statement = (PsiContinueStatement)parent;
      if (!myHolder.hasErrorResults()) myHolder.add(HighlightUtil.checkContinueOutsideLoop(statement));
    }
    else if (keyword.getText().equals(PsiKeyword.BREAK) && parent instanceof PsiBreakStatement) {
      PsiBreakStatement statement = (PsiBreakStatement)parent;
      if (!myHolder.hasErrorResults()) myHolder.add(HighlightUtil.checkBreakOutsideLoop(statement));
    }
    else if (PsiKeyword.INTERFACE.equals(keyword.getText()) && parent instanceof PsiClass) {
      if (!myHolder.hasErrorResults()) myHolder.add(HighlightClassUtil.checkInterfaceCannotBeLocal((PsiClass)parent));
    }
    if (!myHolder.hasErrorResults()) myHolder.add(HighlightClassUtil.checkStaticDeclarationInInnerClass(keyword));
    if (!myHolder.hasErrorResults()) myHolder.add(HighlightUtil.checkIllegalVoidType(keyword));

    if (PsiTreeUtil.getParentOfType(keyword, PsiDocTagValue.class) != null) {
      myHolder.add(HighlightInfo.createHighlightInfo(HighlightInfoType.JAVA_KEYWORD, keyword, null));
    }
  }

  public void visitLabeledStatement(PsiLabeledStatement statement) {
    super.visitLabeledStatement(statement);
    if (!myHolder.hasErrorResults()) myHolder.add(HighlightUtil.checkLabelWithoutStatement(statement));
    if (!myHolder.hasErrorResults()) myHolder.add(HighlightUtil.checkLabelAlreadyInUse(statement));
  }

  public void visitLiteralExpression(PsiLiteralExpression expression) {
    super.visitLiteralExpression(expression);
    if (myHolder.hasErrorResults()) return;
    myHolder.add(HighlightUtil.checkLiteralExpressionParsingError(expression));
  }

  public void visitMethod(PsiMethod method) {
    super.visitMethod(method);
    if (!myHolder.hasErrorResults()) myHolder.add(HighlightControlFlowUtil.checkUnreachableStatement(method.getBody()));
    if (!myHolder.hasErrorResults()) myHolder.add(HighlightMethodUtil.checkConstructorHandleSuperClassExceptions(method));
    if (!myHolder.hasErrorResults()) myHolder.add(HighlightMethodUtil.checkMethodSameNameAsConstructor(method));
    if (!myHolder.hasErrorResults()) myHolder.add(HighlightMethodUtil.checkRecursiveConstructorInvocation(method));
    if (!myHolder.hasErrorResults()) myHolder.add(GenericsHighlightUtil.checkOverrideAnnotation(method));
    if (!myHolder.hasErrorResults() && method.isConstructor()) {
      myHolder.add(HighlightClassUtil.checkThingNotAllowedInInterface(method, method.getContainingClass()));
    }
    if (!myHolder.hasErrorResults()) myHolder.add(HighlightNamesUtil.highlightMethodName(method, method.getNameIdentifier(), true));
  }

  private void highlightMethodOrClassName(PsiJavaCodeReferenceElement element) {
    PsiElement parent = element.getParent();
    if (parent instanceof PsiReferenceExpression || parent instanceof PsiJavaCodeReferenceElement) {
      return;
    }
    if (parent instanceof PsiMethodCallExpression) {
      PsiMethod method = ((PsiMethodCallExpression)parent).resolveMethod();
      PsiElement methodNameElement = element.getReferenceNameElement();
      if (method != null && methodNameElement != null) {
        myHolder.add(HighlightNamesUtil.highlightMethodName(method, methodNameElement, false));
        myHolder.add(HighlightNamesUtil.highlightClassNameInQualifier(element));
      }
    }
    else if (parent instanceof PsiConstructorCall) {
      PsiMethod method = ((PsiConstructorCall)parent).resolveConstructor();
      if (method == null) {
        PsiElement resolved = element.resolve();
        if (resolved instanceof PsiClass) {
          myHolder.add(HighlightNamesUtil.highlightClassName((PsiClass)resolved, element));
        }
      }
      else {
        myHolder.add(HighlightNamesUtil.highlightMethodName(method, element, false));
      }
    }
    else if (parent instanceof PsiImportStatement && ((PsiImportStatement)parent).isOnDemand()) {
      // highlight on demand import as class
      myHolder.add(HighlightNamesUtil.highlightClassName(null, element));
    }
    else {
      PsiElement resolved = element.resolve();
      if (resolved instanceof PsiClass) {
        myHolder.add(HighlightNamesUtil.highlightClassName((PsiClass)resolved, element));
      }
    }
  }

  public void visitMethodCallExpression(PsiMethodCallExpression expression) {
    if (!myHolder.hasErrorResults()) myHolder.add(GenericsHighlightUtil.checkEnumSuperConstructorCall(expression));
    if (!myHolder.hasErrorResults()) myHolder.add(HighlightClassUtil.checkSuperQualifierType(expression));
    if (!myHolder.hasErrorResults()) visitExpression(expression);
  }

  public void visitModifierList(PsiModifierList list) {
    super.visitModifierList(list);
    PsiElement parent = list.getParent();
    if (!myHolder.hasErrorResults() && parent instanceof PsiMethod) {
      myHolder.add(HighlightMethodUtil.checkMethodCanHaveBody((PsiMethod)parent));
    }
    if (parent instanceof PsiMethod) {
      PsiMethod method = (PsiMethod)parent;
      MethodSignatureBackedByPsiMethod methodSignature = MethodSignatureBackedByPsiMethod.create(method, PsiSubstitutor.EMPTY);
      if (!method.isConstructor()) {
        List<MethodSignatureBackedByPsiMethod> superMethodSignatures = method.findSuperMethodSignaturesIncludingStatic(true);
        List<MethodSignatureBackedByPsiMethod> superMethodCandidateSignatures = method.findSuperMethodSignaturesIncludingStatic(false);
        if (!myHolder.hasErrorResults()) myHolder.add(HighlightMethodUtil.checkMethodWeakerPrivileges(methodSignature, superMethodCandidateSignatures, true));
        if (!myHolder.hasErrorResults()) myHolder.add(HighlightMethodUtil.checkMethodIncompatibleReturnType(methodSignature, superMethodSignatures, true));
        if (!myHolder.hasErrorResults()) myHolder.add(HighlightMethodUtil.checkMethodIncompatibleThrows(methodSignature, superMethodSignatures, true, method.getContainingClass()));
        if (!myHolder.hasErrorResults()) myHolder.add(HighlightMethodUtil.checkMethodOverridesFinal(methodSignature, superMethodSignatures));
//        if (!myHolder.hasErrorResults()) myHolder.add(DeprecationInspection.checkMethodOverridesDeprecated(methodSignature, superMethodSignatures, mySettings));
        if (!myHolder.hasErrorResults()) myHolder.add(GenericsHighlightUtil.checkUncheckedOverriding(method, superMethodSignatures));
      }
      PsiClass aClass = method.getContainingClass();
      if (!myHolder.hasErrorResults()) myHolder.add(HighlightMethodUtil.checkMethodMustHaveBody(method, aClass));
      if (!myHolder.hasErrorResults()) myHolder.add(HighlightMethodUtil.checkDuplicateMethod(aClass, method));
      if (!myHolder.hasErrorResults()) myHolder.add(HighlightMethodUtil.checkConstructorCallsBaseClassConstructor(method, myRefCountHolder, myResolveHelper));
      if (!myHolder.hasErrorResults()) myHolder.add(HighlightMethodUtil.checkStaticMethodOverride(method));
    }
    else if (parent instanceof PsiClass) {
      PsiClass aClass = (PsiClass)parent;
      if (!myHolder.hasErrorResults()) myHolder.add(HighlightClassUtil.checkDuplicateNestedClass(aClass));
      if (!myHolder.hasErrorResults()) myHolder.add(HighlightClassUtil.checkClassMustBeAbstract(aClass));
      if (!myHolder.hasErrorResults()) {
        myHolder.add(HighlightClassUtil.checkClassDoesNotCallSuperConstructorOrHandleExceptions(aClass, myRefCountHolder, myResolveHelper));
      }
      if (!myHolder.hasErrorResults()) myHolder.add(HighlightMethodUtil.checkOverrideEquivalentInheritedMethods(aClass));
      if (!myHolder.hasErrorResults()) myHolder.addAll(GenericsHighlightUtil.checkOverrideEquivalentMethods(aClass));
      if (!myHolder.hasErrorResults()) myHolder.add(HighlightClassUtil.checkCyclicInheritance(aClass));
    }
    else if (parent instanceof PsiEnumConstant) {
      if (!myHolder.hasErrorResults()) myHolder.addAll(GenericsHighlightUtil.checkEnumConstantModifierList(list));
    }

    if (!myHolder.hasErrorResults()) {
      Collection<HighlightInfo> duplicateResults = AnnotationsHighlightUtil.checkDuplicatedAnnotations(list);
      for (HighlightInfo duplicateResult : duplicateResults) {
        myHolder.add(duplicateResult);
      }
    }
  }

  public void visitAnnotation(PsiAnnotation annotation) {
    super.visitAnnotation(annotation);
    myHolder.add(AnnotationsHighlightUtil.checkApplicability(annotation));
    if (!myHolder.hasErrorResults()) myHolder.add(AnnotationsHighlightUtil.checkAnnotationType(annotation));
    if (!myHolder.hasErrorResults()) myHolder.add(AnnotationsHighlightUtil.checkMissingAttributes(annotation));
    if (!myHolder.hasErrorResults()) myHolder.add(AnnotationsHighlightUtil.checkTargetAnnotationDuplicates(annotation));
  }

  public void visitNewExpression(PsiNewExpression expression) {
    myHolder.add(HighlightUtil.checkUnhandledExceptions(expression, null));
    if (!myHolder.hasErrorResults()) myHolder.add(HighlightClassUtil.checkAnonymousInheritFinal(expression));
    if (!myHolder.hasErrorResults()) myHolder.add(HighlightClassUtil.checkQualifiedNewOfStaticClass(expression));
    if (!myHolder.hasErrorResults()) myHolder.add(HighlightClassUtil.checkCreateInnerClassFromStaticContext(expression));
    if (!myHolder.hasErrorResults()) myHolder.add(GenericsHighlightUtil.checkTypeParameterInstantiation(expression));
    if (!myHolder.hasErrorResults()) myHolder.add(HighlightMethodUtil.checkNewExpression(expression));
    if (!myHolder.hasErrorResults()) myHolder.add(GenericsHighlightUtil.checkEnumInstantiation(expression));
    if (!myHolder.hasErrorResults()) myHolder.add(GenericsHighlightUtil.checkGenericArrayCreation(expression, expression.getType()));
    if (!myHolder.hasErrorResults()) registerConstructorCall(expression);

    if (!myHolder.hasErrorResults()) visitExpression(expression);
  }

  public void visitPackageStatement(PsiPackageStatement statement) {
    super.visitPackageStatement(statement);
    myHolder.add(AnnotationsHighlightUtil.checkPackageAnnotationContainingFile(statement));
  }

  public void visitParameter(PsiParameter parameter) {
    super.visitParameter(parameter);
    if (!myHolder.hasErrorResults()) myHolder.add(HighlightUtil.checkExceptionThrownInTry(parameter));
    if (!myHolder.hasErrorResults()) myHolder.add(HighlightUtil.checkCatchParameterIsThrowable(parameter));
    if (!myHolder.hasErrorResults()) myHolder.add(GenericsHighlightUtil.checkCatchParameterIsClass(parameter));
    if (!myHolder.hasErrorResults()) myHolder.add(GenericsHighlightUtil.checkVarArgParameterIsLast(parameter));
    if (!myHolder.hasErrorResults() && parameter.getParent() instanceof PsiForeachStatement) {
      myHolder.add(GenericsHighlightUtil.checkForeachLoopParameterType((PsiForeachStatement)parameter.getParent()));
    }
  }

  public void visitPostfixExpression(PsiPostfixExpression expression) {
    super.visitPostfixExpression(expression);
    if (!myHolder.hasErrorResults()) {
      myHolder.add(HighlightUtil.checkUnaryOperatorApplicable(expression.getOperationSign(), expression.getOperand()));
    }
  }

  public void visitPrefixExpression(PsiPrefixExpression expression) {
    super.visitPrefixExpression(expression);
    if (!myHolder.hasErrorResults()) {
      myHolder.add(HighlightUtil.checkUnaryOperatorApplicable(expression.getOperationSign(), expression.getOperand()));
    }
  }

  public void visitImportStaticReferenceElement(PsiImportStaticReferenceElement ref) {
    String refName = ref.getReferenceName();
    JavaResolveResult[] results = ref.multiResolve(false);

    if (results.length == 0) {
      String description = JavaErrorMessages.message("cannot.resolve.symbol", refName);
      HighlightInfo info = HighlightInfo.createHighlightInfo(HighlightInfoType.WRONG_REF, ref.getReferenceNameElement(), description);
      myHolder.add(info);
      QuickFixAction.registerQuickFixAction(info, SetupJDKFix.getInstnace());
    }
    else {
      PsiManager manager = ref.getManager();
      for (JavaResolveResult result : results) {
        PsiElement element = result.getElement();
        if (!(element instanceof PsiModifierListOwner) || !((PsiModifierListOwner)element).hasModifierProperty(PsiModifier.STATIC)) {
          continue;
        }
        @NonNls String messageKey = null;
        if (element instanceof PsiClass) {
          Pair<PsiImportStatement, PsiClass> imported = mySingleImportedClasses.get(refName);
          PsiClass aClass = imported == null ? null : imported.getSecond();
          PsiImportStatement statement = PsiTreeUtil.getParentOfType(ref, PsiImportStatement.class);

          if (aClass != null && !manager.areElementsEquivalent(aClass, element) && !manager.areElementsEquivalent(statement, imported.getFirst())) {
            messageKey = "class.is.already.defined.in.single.type.import";
          }
          mySingleImportedClasses.put(refName, Pair.create(statement, (PsiClass)element));
        }
        else if (element instanceof PsiField) {
          Pair<PsiImportStaticReferenceElement, PsiField> imported = mySingleImportedFields.get(refName);
          PsiField field = imported == null ? null : imported.getSecond();

          if (field != null && !manager.areElementsEquivalent(field, element) && !manager.areElementsEquivalent(ref, imported.getFirst())) {
            messageKey = "field.is.already.defined.in.single.type.import";
          }
          mySingleImportedFields.put(refName, Pair.create(ref, (PsiField)element));
        }
        else if (element instanceof PsiMethod) {
          MethodSignature signature = ((PsiMethod)element).getSignature(PsiSubstitutor.EMPTY);
          Pair<PsiImportStaticReferenceElement, PsiMethod> imported = mySingleImportedMethods.get(signature);
          PsiMethod method = imported == null ? null : imported.getSecond();
          if (method != null && !manager.areElementsEquivalent(method, element) && !manager.areElementsEquivalent(imported.getFirst(), ref)) {
            messageKey = "method.is.already.defined.in.single.type.import";
          }
          mySingleImportedMethods.put(signature, Pair.create(ref, (PsiMethod)element));
        }

        if (messageKey != null) {
          String description = JavaErrorMessages.message(messageKey, refName);
          myHolder.add(HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR, ref, description));
        }
      }
    }
  }

  private void registerConstructorCall(PsiConstructorCall constructorCall) {
    JavaResolveResult resolveResult = constructorCall.resolveMethodGenerics();
    final PsiElement resolved = resolveResult.getElement();
    if (myRefCountHolder != null && resolved instanceof PsiNamedElement) {
      myRefCountHolder.registerLocallyReferenced((PsiNamedElement)resolved);
    }
  }

  public void visitReferenceElement(PsiJavaCodeReferenceElement ref) {
    JavaResolveResult result = ref.advancedResolve(true);
    PsiElement resolved = result.getElement();
    PsiElement parent = ref.getParent();
    if (myRefCountHolder != null) {
      myRefCountHolder.registerReference(ref, result);
    }

    myHolder.add(HighlightUtil.checkReference(ref, result, resolved));
    if (!myHolder.hasErrorResults()) myHolder.add(HighlightClassUtil.checkAbstractInstantiation(ref));
    if (!myHolder.hasErrorResults()) myHolder.add(HighlightClassUtil.checkExtendsDuplicate(ref, resolved));
    if (!myHolder.hasErrorResults()) myHolder.add(HighlightUtil.checkExceptionAlreadyCaught(ref, resolved));
    /*if (!myHolder.hasErrorResults()) {
      myHolder.add(DeprecationInspection.checkDeprecated(resolved, ref.getReferenceNameElement(), DaemonCodeAnalyzerSettings.getInstance()));
    }*/
    //if (!myHolder.hasErrorResults()) myHolder.add(HighlightMethodUtil.checkExceptionsNeverThrown(ref));
    if (!myHolder.hasErrorResults()) myHolder.add(HighlightClassUtil.checkClassExtendsForeignInnerClass(ref, resolved));
    if (!myHolder.hasErrorResults()) {
      myHolder.add(GenericsHighlightUtil.checkParameterizedReferenceTypeArguments(resolved, ref, result.getSubstitutor()));
    }

    if (resolved instanceof PsiClass && parent instanceof PsiReferenceList) {
      myHolder.add(HighlightUtil.checkReferenceList(ref, (PsiReferenceList)parent, result));
    }

    if (!myHolder.hasErrorResults()) {
      if (resolved instanceof PsiVariable) {
        PsiVariable variable = (PsiVariable)resolved;
        if (!variable.hasModifierProperty(PsiModifier.FINAL) && HighlightControlFlowUtil.isReassigned(variable, myFinalVarProblems, myParameterIsReassigned)) {
          myHolder.add(HighlightNamesUtil.highlightReassignedVariable(variable, ref));
        }
        else {
          myHolder.add(HighlightNamesUtil.highlightVariable(variable, ref.getReferenceNameElement()));
        }
        myHolder.add(HighlightNamesUtil.highlightClassNameInQualifier(ref));
      }
      else {
        highlightMethodOrClassName(ref);
      }
    }
  }

  public void visitAnnotationMethod(PsiAnnotationMethod method) {
    PsiType returnType = method.getReturnType();
    PsiAnnotationMemberValue value = method.getDefaultValue();
    if (returnType != null && value != null) {
      myHolder.add(AnnotationsHighlightUtil.checkMemberValueType(value, returnType));
    }

    myHolder.add(AnnotationsHighlightUtil.checkValidAnnotationType(method.getReturnTypeElement()));
    myHolder.add(AnnotationsHighlightUtil.checkCyclicMemberType(method.getReturnTypeElement(), method.getContainingClass()));
  }

  public void visitNameValuePair(PsiNameValuePair pair) {
    myHolder.add(AnnotationsHighlightUtil.checkNameValuePair(pair));
    if (!myHolder.hasErrorResults()) {
      PsiIdentifier nameId = pair.getNameIdentifier();
      if (nameId != null) myHolder.add(HighlightInfo.createHighlightInfo(HighlightInfoType.ANNOTATION_ATTRIBUTE_NAME, nameId, null));
    }
  }

  public void visitAnnotationArrayInitializer(PsiArrayInitializerMemberValue initializer) {
    PsiMethod method = null;
    PsiElement parent = initializer.getParent();
    if (parent instanceof PsiNameValuePair) {
      method = (PsiMethod)parent.getReference().resolve();
    }
    else if (parent instanceof PsiAnnotationMethod) {
      method = (PsiMethod)parent;
    }
    if (method != null) {
      PsiType type = method.getReturnType();
      if (type instanceof PsiArrayType) {
        type = ((PsiArrayType)type).getComponentType();
        PsiAnnotationMemberValue[] initializers = initializer.getInitializers();
        for (PsiAnnotationMemberValue initializer1 : initializers) {
          myHolder.add(AnnotationsHighlightUtil.checkMemberValueType(initializer1, type));
        }
      }
    }
  }

  public void visitReferenceExpression(PsiReferenceExpression expression) {
    visitReferenceElement(expression);
    if (!myHolder.hasErrorResults()) {
      visitExpression(expression);
    }
    JavaResolveResult result = expression.advancedResolve(false);
    PsiElement resolved = result.getElement();
    if (resolved instanceof PsiVariable && resolved.getContainingFile() == expression.getContainingFile()) {
      if (!myHolder.hasErrorResults()) {
        myHolder.add(HighlightControlFlowUtil.checkVariableInitializedBeforeUsage(expression, resolved, myUninitializedVarProblems));
      }
      PsiVariable variable = (PsiVariable)resolved;
      boolean isFinal = variable.hasModifierProperty(PsiModifier.FINAL);
      if (isFinal && !variable.hasInitializer()) {
        if (!myHolder.hasErrorResults()) {
          myHolder.add(HighlightControlFlowUtil.checkFinalVariableMightAlreadyHaveBeenAssignedTo(variable, expression, myFinalVarProblems));
        }
        if (!myHolder.hasErrorResults()) myHolder.add(HighlightControlFlowUtil.checkFinalVariableInitalizedInLoop(expression, resolved));
      }
    }
    else if (expression.getParent() instanceof PsiMethodCallExpression) {
      myHolder.add(HighlightMethodUtil.checkMethodCall((PsiMethodCallExpression)expression.getParent(), myResolveHelper));
    }
    if (!myHolder.hasErrorResults()) myHolder.add(HighlightUtil.checkExpressionRequired(expression));
    if (!myHolder.hasErrorResults() && resolved instanceof PsiField) {
      myHolder.add(HighlightUtil.checkIllegalForwardReferenceToField(expression, (PsiField)resolved));
    }
    if (!myHolder.hasErrorResults()) myHolder.add(HighlightMethodUtil.checkConstructorCallMustBeFirstStatement(expression));
    if (!myHolder.hasErrorResults()) myHolder.add(GenericsHighlightUtil.checkAccessStaticFieldFromEnumConstructor(expression, result));
    if (!myHolder.hasErrorResults()) myHolder.add(HighlightUtil.checkClassReferenceAfterQualifier(expression, resolved));
  }

  public void visitReferenceList(PsiReferenceList list) {
    if (list.getFirstChild() == null) return;
    PsiElement parent = list.getParent();
    if (!(parent instanceof PsiTypeParameter)) {
      myHolder.add(AnnotationsHighlightUtil.checkAnnotationDeclaration(parent, list));
      if (!myHolder.hasErrorResults()) myHolder.add(HighlightClassUtil.checkImplementsAllowed(list));
      if (!myHolder.hasErrorResults()) myHolder.add(HighlightClassUtil.checkClassExtendsOnlyOneClass(list));
    }
  }


  public void visitReferenceParameterList(PsiReferenceParameterList list) {
    myHolder.add(GenericsHighlightUtil.checkParametersOnRaw(list));
  }

  public void visitParameterList(PsiParameterList list) {
    if (list.getParent() instanceof PsiAnnotationMethod && list.getParameters().length > 0) {
      myHolder.add(HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR,
                                                     list,
                                                     JavaErrorMessages.message("annotation.interface.members.may.not.have.parameters")));
    }
  }

  public void visitTypeParameterList(PsiTypeParameterList list) {
    myHolder.add(GenericsHighlightUtil.checkTypeParametersList(list));
  }

  public void visitReturnStatement(PsiReturnStatement statement) {
    myHolder.add(HighlightUtil.checkReturnStatementType(statement));
  }

  public void visitStatement(PsiStatement statement) {
    super.visitStatement(statement);
    if (!myHolder.hasErrorResults()) myHolder.add(HighlightUtil.checkNotAStatement(statement));
    if (!myHolder.hasErrorResults()) myHolder.add(HighlightUtil.checkStatementPrependedWithCaseInsideSwitch(statement));
  }

  public void visitSuperExpression(PsiSuperExpression expr) {
    myHolder.add(HighlightUtil.checkThisOrSuperExpressionInIllegalContext(expr, expr.getQualifier()));
    if (!myHolder.hasErrorResults()) myHolder.add(HighlightMethodUtil.checkAbstractMethodDirectCall(expr));
    if (!myHolder.hasErrorResults()) visitExpression(expr);
  }

  public void visitSwitchLabelStatement(PsiSwitchLabelStatement statement) {
    super.visitSwitchLabelStatement(statement);
    if (!myHolder.hasErrorResults()) myHolder.add(HighlightUtil.checkCaseStatement(statement));
  }

  public void visitSwitchStatement(PsiSwitchStatement statement) {
    super.visitSwitchStatement(statement);
    if (!myHolder.hasErrorResults()) myHolder.add(HighlightUtil.checkSwitchSelectorType(statement));
  }

  public void visitThisExpression(PsiThisExpression expr) {
    myHolder.add(HighlightUtil.checkThisOrSuperExpressionInIllegalContext(expr, expr.getQualifier()));
    if (!myHolder.hasErrorResults()) myHolder.add(HighlightUtil.checkMemberReferencedBeforeConstructorCalled(expr));
    if (!myHolder.hasErrorResults()) {
      visitExpression(expr);
    }
  }

  public void visitThrowStatement(PsiThrowStatement statement) {
    myHolder.add(HighlightUtil.checkUnhandledExceptions(statement, null));
    if (!myHolder.hasErrorResults()) visitStatement(statement);
  }

  public void visitTypeElement(PsiTypeElement type) {
    if (!myHolder.hasErrorResults()) myHolder.add(HighlightUtil.checkIllegalType(type));
    if (!myHolder.hasErrorResults()) myHolder.add(GenericsHighlightUtil.checkReferenceTypeUsedAsTypeArgument(type));
    if (!myHolder.hasErrorResults()) myHolder.add(GenericsHighlightUtil.checkWildcardUsage(type));
  }

  public void visitTypeCastExpression(PsiTypeCastExpression typeCast) {
    super.visitTypeCastExpression(typeCast);
    if (!myHolder.hasErrorResults()) myHolder.add(HighlightUtil.checkInconvertibleTypeCast(typeCast));
    if (!myHolder.hasErrorResults()) myHolder.add(GenericsHighlightUtil.checkUncheckedTypeCast(typeCast));
  }

  public void visitVariable(PsiVariable variable) {
    super.visitVariable(variable);
    if (!myHolder.hasErrorResults()) myHolder.add(HighlightUtil.checkVariableInitializerType(variable));

    if (HighlightControlFlowUtil.isReassigned(variable, myFinalVarProblems, myParameterIsReassigned)) {
      myHolder.add(HighlightNamesUtil.highlightReassignedVariable(variable, variable.getNameIdentifier()));
    }
    else {
      myHolder.add(HighlightNamesUtil.highlightVariable(variable, variable.getNameIdentifier()));
    }
  }

  public void visitXmlElement(XmlElement element) {
    myXmlVisitor.setRefCountHolder(myRefCountHolder);
    element.accept(myXmlVisitor);

    List<HighlightInfo> result = myXmlVisitor.getResult();
    myHolder.addAll(result);
    myXmlVisitor.clearResult();
  }

  public void visitXmlText(XmlText text) {
    super.visitXmlText(text);
    visitElement(text);
  }

  public void visitXmlAttributeValue(XmlAttributeValue value) {
    super.visitXmlAttributeValue(value);
    visitElement(value);
  }
}
