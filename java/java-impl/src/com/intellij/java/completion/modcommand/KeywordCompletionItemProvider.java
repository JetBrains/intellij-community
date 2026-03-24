// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.completion.modcommand;

import com.intellij.application.options.CodeStyle;
import com.intellij.codeInsight.BlockUtils;
import com.intellij.codeInsight.ExpectedTypeInfo;
import com.intellij.codeInsight.JavaTailTypes;
import com.intellij.codeInsight.ModNavigatorTailType;
import com.intellij.codeInsight.completion.AllClassesGetter;
import com.intellij.codeInsight.completion.CompletionUtil;
import com.intellij.codeInsight.completion.JavaCompletionContributor;
import com.intellij.codeInsight.completion.JavaKeywordCompletion;
import com.intellij.codeInsight.completion.JavaMemberNameCompletionContributor;
import com.intellij.codeInsight.completion.JavaPatternCompletionUtil;
import com.intellij.codeInsight.completion.JavaPsiClassReferenceElement;
import com.intellij.codeInsight.completion.JavaSmartCompletionContributor;
import com.intellij.codeInsight.completion.ModifierChooser;
import com.intellij.codeInsight.completion.PrefixMatcher;
import com.intellij.codeInsight.completion.ReferenceExpressionCompletionContributor;
import com.intellij.codeInsight.completion.StartOnlyMatcher;
import com.intellij.codeInsight.daemon.impl.quickfix.CreateClassKind;
import com.intellij.codeInsight.lookup.AutoCompletionPolicy;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.java.codeserver.core.JavaPsiSwitchUtil;
import com.intellij.java.syntax.parser.JavaKeywords;
import com.intellij.modcompletion.CommonCompletionItem;
import com.intellij.modcompletion.ModCompletionItem;
import com.intellij.modcompletion.ModCompletionItemPresentation;
import com.intellij.modcompletion.ModCompletionResult;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.util.Conditions;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.MarkupText;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.patterns.ElementPattern;
import com.intellij.patterns.PsiElementPattern;
import com.intellij.patterns.PsiJavaPatterns;
import com.intellij.pom.java.JavaFeature;
import com.intellij.psi.CommonClassNames;
import com.intellij.psi.JavaCodeFragment;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.LambdaUtil;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiAnnotationMethod;
import com.intellij.psi.PsiAnnotationParameterList;
import com.intellij.psi.PsiCaseLabelElement;
import com.intellij.psi.PsiCaseLabelElementList;
import com.intellij.psi.PsiCatchSection;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassInitializer;
import com.intellij.psi.PsiClassLevelDeclarationStatement;
import com.intellij.psi.PsiClassObjectAccessExpression;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiCodeBlock;
import com.intellij.psi.PsiCodeFragment;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiConditionalExpression;
import com.intellij.psi.PsiDeclarationStatement;
import com.intellij.psi.PsiDeconstructionList;
import com.intellij.psi.PsiDefaultCaseLabelElement;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiEnumConstant;
import com.intellij.psi.PsiErrorElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiExpressionCodeFragment;
import com.intellij.psi.PsiExpressionList;
import com.intellij.psi.PsiExpressionStatement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiForStatement;
import com.intellij.psi.PsiIdentifier;
import com.intellij.psi.PsiIfStatement;
import com.intellij.psi.PsiImplicitClass;
import com.intellij.psi.PsiImportList;
import com.intellij.psi.PsiInstanceOfExpression;
import com.intellij.psi.PsiJavaCodeReferenceCodeFragment;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiJavaModule;
import com.intellij.psi.PsiJavaToken;
import com.intellij.psi.PsiLabeledStatement;
import com.intellij.psi.PsiLambdaExpression;
import com.intellij.psi.PsiLiteralExpression;
import com.intellij.psi.PsiLocalVariable;
import com.intellij.psi.PsiLoopStatement;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiMember;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.PsiPackage;
import com.intellij.psi.PsiPackageAccessibilityStatement;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiParameterList;
import com.intellij.psi.PsiParenthesizedExpression;
import com.intellij.psi.PsiPattern;
import com.intellij.psi.PsiPrefixExpression;
import com.intellij.psi.PsiPrimitiveType;
import com.intellij.psi.PsiProvidesStatement;
import com.intellij.psi.PsiRecordComponent;
import com.intellij.psi.PsiRecordHeader;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.PsiReferenceList;
import com.intellij.psi.PsiReferenceParameterList;
import com.intellij.psi.PsiRequiresStatement;
import com.intellij.psi.PsiResolveHelper;
import com.intellij.psi.PsiResourceExpression;
import com.intellij.psi.PsiResourceList;
import com.intellij.psi.PsiStatement;
import com.intellij.psi.PsiSwitchBlock;
import com.intellij.psi.PsiSwitchExpression;
import com.intellij.psi.PsiSwitchLabelStatementBase;
import com.intellij.psi.PsiSwitchLabeledRuleStatement;
import com.intellij.psi.PsiSwitchStatement;
import com.intellij.psi.PsiTryStatement;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiTypeCastExpression;
import com.intellij.psi.PsiTypeCodeFragment;
import com.intellij.psi.PsiTypeElement;
import com.intellij.psi.PsiTypeParameter;
import com.intellij.psi.PsiTypeParameterList;
import com.intellij.psi.PsiTypeTestPattern;
import com.intellij.psi.PsiTypes;
import com.intellij.psi.PsiUnaryExpression;
import com.intellij.psi.PsiVariable;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.templateLanguages.OuterLanguageElement;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.JavaPsiPatternUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.ig.psiutils.ExpressionUtils;
import com.siyeh.ig.psiutils.SealedUtils;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static com.intellij.codeInsight.ModNavigatorTailType.humbleSpaceBeforeWordType;
import static com.intellij.codeInsight.ModNavigatorTailType.insertSpaceType;
import static com.intellij.codeInsight.ModNavigatorTailType.noneType;
import static com.intellij.codeInsight.ModNavigatorTailType.semicolonType;
import static com.intellij.codeInsight.ModNavigatorTailType.spaceType;
import static com.intellij.openapi.util.Conditions.notInstanceOf;
import static com.intellij.openapi.util.text.MarkupText.Kind.GRAYED;
import static com.intellij.openapi.util.text.MarkupText.Kind.NORMAL;
import static com.intellij.openapi.util.text.MarkupText.Kind.STRONG;
import static com.intellij.patterns.PlatformPatterns.psiElement;
import static com.intellij.patterns.PsiJavaPatterns.and;
import static com.intellij.patterns.PsiJavaPatterns.or;
import static com.intellij.patterns.PsiJavaPatterns.psiAnnotation;
import static com.intellij.patterns.PsiJavaPatterns.psiMethod;
import static com.intellij.patterns.PsiJavaPatterns.psiNameValuePair;
import static com.intellij.patterns.PsiJavaPatterns.string;
import static com.intellij.patterns.StandardPatterns.not;
import static com.intellij.psi.SyntaxTraverser.psiApi;

/**
 * A provider for Java keywords completion.
 * <p> 
 * TODO: disable completion chars
 */
@NotNullByDefault
final class KeywordCompletionItemProvider extends JavaModCompletionItemProvider {
  private static final ElementPattern<PsiElement> AFTER_DOT = psiElement().afterLeaf(".");

  private static final ElementPattern<PsiElement> VARIABLE_AFTER_FINAL =
    psiElement().afterLeaf(JavaKeywords.FINAL).inside(PsiDeclarationStatement.class);

  private static final ElementPattern<PsiElement> INSIDE_PARAMETER_LIST =
    psiElement().withParent(
      psiElement(PsiJavaCodeReferenceElement.class).insideStarting(
        psiElement().withTreeParent(
          psiElement(PsiParameterList.class).andNot(psiElement(PsiAnnotationParameterList.class)))));
  private static final ElementPattern<PsiElement> INSIDE_RECORD_HEADER =
    psiElement().withParent(
      psiElement(PsiJavaCodeReferenceElement.class).insideStarting(
        or(
          psiElement().withTreeParent(
            psiElement(PsiRecordComponent.class)),
          psiElement().withTreeParent(
            psiElement(PsiRecordHeader.class)
          )
        )
      ));
  private static final ElementPattern<PsiElement> AFTER_NEW = psiElement().afterLeaf(psiElement().withText(JavaKeywords.NEW));
  private static final ElementPattern<PsiElement> START_SWITCH =
    psiElement().afterLeaf(psiElement().withText("{").withParents(PsiCodeBlock.class, PsiSwitchBlock.class));

  private static final ElementPattern<PsiElement> SUPER_OR_THIS_PATTERN =
    and(JavaSmartCompletionContributor.INSIDE_EXPRESSION,
        not(psiElement().afterLeaf(JavaKeywords.CASE)),
        not(psiElement().afterLeaf(psiElement().withText(".").afterLeaf(JavaKeywords.THIS, JavaKeywords.SUPER))),
        not(psiElement().inside(PsiAnnotation.class)),
        not(START_SWITCH),
        not(JavaMemberNameCompletionContributor.INSIDE_TYPE_PARAMS_PATTERN));

  private static final PsiElementPattern<PsiElement, ?> START_FOR = psiElement().afterLeaf(psiElement().withText("(").afterLeaf("for"));

  @Override
  public void provideItems(CompletionContext context, ModCompletionResult sink) {
    new KeywordAdder(context, sink).provideItems();
  }

  private static class KeywordAdder {
    private final CompletionContext myContext;
    private final ModCompletionResult mySink;
    private final PsiElement myPosition;
    private final @Nullable PsiElement myPrevLeaf;
    private final PsiFile myFile;
    private final PrefixMatcher myKeywordMatcher;

    KeywordAdder(CompletionContext context, ModCompletionResult sink) {
      myContext = context;
      mySink = sink;
      myPosition = context.element();
      myFile = context.getOriginalFile();
      myPrevLeaf = PsiTreeUtil.prevCodeLeaf(myPosition);
      myKeywordMatcher = new StartOnlyMatcher(context.matcher());
    }

    void provideItems() {
      if (!myContext.isSmart()) {
        addKeywords();
      }
      addEnumCases();
      addEnhancedCases();
    }

    private void addKeywords() {
      if (!canAddKeywords()) return;
      if (processAnnotationAttributes()) return;
      if (PsiJavaModule.MODULE_INFO_FILE.equals(myFile.getName()) && PsiUtil.isAvailable(JavaFeature.MODULES, myFile)) {
        addModuleKeywords();
        return;
      }

      if (addWildcardExtendsSuper()) {
        return;
      }
      addFinal();
      addWhen();
      boolean statementPosition = isStatementPosition(myPosition);
      if (statementPosition) {

        if (START_SWITCH.accepts(myPosition)) {
          return;
        }

        addBreakContinue();
        addStatementKeywords();

        if (myPrevLeaf != null &&
            myPrevLeaf.textMatches("}") &&
            myPrevLeaf.getParent() instanceof PsiCodeBlock &&
            myPrevLeaf.getParent().getParent() instanceof PsiTryStatement &&
            myPrevLeaf.getParent().getNextSibling() instanceof PsiErrorElement) {
          return;
        }
      }
      else {
        PsiSwitchLabeledRuleStatement rule = findEnclosingSwitchRule(myPosition);
        if (rule != null) {
          addSwitchRuleKeywords(rule);
        }
      }

      addThisSuper();
      addExpressionKeywords(statementPosition);
      addFileHeaderKeywords();
      addInstanceof();
      addClassKeywords();
      addMethodHeaderKeywords();
      addPrimitiveTypes();
      addVar();
      addClassLiteral();
      addExtendsImplements();
      addCaseNullToSwitch();
    }

    private boolean processAnnotationAttributes() {
      PsiAnnotation annotation = JavaCompletionContributor.findAnnotationWhoseAttributeIsCompleted(myPosition);
      if (annotation != null) {
        if (myPosition instanceof PsiIdentifier) {
            PsiClass annoClass = annotation.resolveAnnotationType();
            if (annoClass != null) {
              addPrimitiveTypes();
            }
        }
        return true;
      }
      return false;
    }

    private void addCaseNullToSwitch() {
      if (!isInsideCaseLabel()) return;

      final PsiSwitchBlock switchBlock = PsiTreeUtil.getParentOfType(myPosition, PsiSwitchBlock.class, false, PsiMember.class);
      if (switchBlock == null) return;

      final PsiType selectorType = getSelectorType(switchBlock);
      if (selectorType instanceof PsiPrimitiveType) return;

      addKeyword(createKeyword(JavaKeywords.NULL));
    }

    private void addKeyword(ModCompletionItem item) {
      if (myKeywordMatcher.prefixMatches(item.mainLookupString())) {
        mySink.accept(item);
      }
    }

    private boolean isInsideCaseLabel() {
      if (!PsiUtil.isAvailable(JavaFeature.PATTERNS_IN_SWITCH, myPosition)) return false;
      return PsiJavaPatterns.psiElement().withSuperParent(2, PsiCaseLabelElementList.class).accepts(myPosition);
    }
    
    private void addExtendsImplements() {
      if (myPrevLeaf == null ||
          !(myPrevLeaf instanceof PsiIdentifier || myPrevLeaf.textMatches(">") || myPrevLeaf.textMatches(")"))) {
        return;
      }

      PsiClass psiClass = null;
      PsiElement prevParent = myPrevLeaf.getParent();
      if (myPrevLeaf instanceof PsiIdentifier && prevParent instanceof PsiClass) {
        psiClass = (PsiClass)prevParent;
      }
      else {
        PsiReferenceList referenceList = PsiTreeUtil.getParentOfType(myPrevLeaf, PsiReferenceList.class);
        if (referenceList != null && referenceList.getParent() instanceof PsiClass) {
          psiClass = (PsiClass)referenceList.getParent();
        }
        else if ((prevParent instanceof PsiTypeParameterList || prevParent instanceof PsiRecordHeader)
                 && prevParent.getParent() instanceof PsiClass) {
          psiClass = (PsiClass)prevParent.getParent();
        }
      }

      if (psiClass != null) {
        if (!psiClass.isEnum() && !psiClass.isRecord()) {
          addKeyword(createKeyword(JavaKeywords.EXTENDS, humbleSpaceBeforeWordType()));
          if (PsiUtil.isAvailable(JavaFeature.SEALED_CLASSES, psiClass)) {
            PsiModifierList modifiers = psiClass.getModifierList();
            if (myContext.invocationCount() > 1 ||
                (modifiers != null &&
                 !modifiers.hasExplicitModifier(PsiModifier.FINAL) &&
                 !modifiers.hasExplicitModifier(PsiModifier.NON_SEALED))) {
              CommonCompletionItem permits =
                createKeyword(JavaKeywords.PERMITS, humbleSpaceBeforeWordType());
              if (modifiers != null && !modifiers.hasExplicitModifier(PsiModifier.SEALED)) {
                permits = permits
                  .withAdditionalUpdater((start, updater) -> {
                    PsiClass aClass = PsiTreeUtil.findElementOfClassAtOffset(updater.getPsiFile(), start, PsiClass.class, false);
                    if (aClass != null) {
                      PsiModifierList modifierList = aClass.getModifierList();
                      if (modifierList != null) {
                        modifierList.setModifierProperty(PsiModifier.SEALED, true);
                      }
                    }
                  });
              }
              addKeyword(permits);
            }
          }
        }
        if (!psiClass.isInterface() && !(psiClass instanceof PsiTypeParameter)) {
          addKeyword(createKeyword(JavaKeywords.IMPLEMENTS, humbleSpaceBeforeWordType()));
        }
      }
    }
    
    private void addClassLiteral() {
      if (JavaKeywordCompletion.isAfterTypeDot(myPosition)) {
        addKeyword(createKeyword(JavaKeywords.CLASS));
      }
    }

    private void addVar() {
      if (isVarAllowed()) {
        addKeyword(createKeyword(JavaKeywords.VAR, humbleSpaceBeforeWordType()));
      }
    }

    private boolean isVarAllowed() {
      if (PsiUtil.isAvailable(JavaFeature.VAR_LAMBDA_PARAMETER, myPosition) && isLambdaParameterType(myPosition)) {
        return true;
      }

      if (!PsiUtil.isAvailable(JavaFeature.LVTI, myPosition)) return false;

      if (isAtCatchOrResourceVariableStart(myPosition) && PsiTreeUtil.getParentOfType(myPosition, PsiCatchSection.class) == null) {
        return true;
      }

      return isVariableTypePosition(myPosition) &&
             PsiTreeUtil.getParentOfType(myPosition, PsiCodeBlock.class, true, PsiMember.class, PsiLambdaExpression.class) != null;
    }

    private void addPrimitiveTypes() {
      if (AFTER_DOT.accepts(myPosition) ||
          psiElement().inside(psiAnnotation()).accepts(myPosition) && !expectsClassLiteral(myPosition)) {
        return;
      }

      if (JavaPatternCompletionUtil.insideDeconstructionList(myPosition)) {
        suggestPrimitivesInsideDeconstructionListPattern();
        return;
      }

      if (JavaKeywordCompletion.afterInstanceofForType(myPosition)) {
        PsiInstanceOfExpression instanceOfExpression = PsiTreeUtil.getParentOfType(myPosition, PsiInstanceOfExpression.class);
        if (instanceOfExpression != null) {
          suggestPrimitiveTypesForPattern(instanceOfExpression.getOperand().getType());
        }
        return;
      }

      if (JavaKeywordCompletion.afterCaseForType(myPosition)) {
        PsiSwitchBlock switchBlock = PsiTreeUtil.getParentOfType(myPosition, PsiSwitchBlock.class);
        if (switchBlock != null && switchBlock.getExpression() != null) {
          suggestPrimitiveTypesForPattern(switchBlock.getExpression().getType());
        }
        if (switchBlock != null && switchBlock.getExpression() != null) {
          PsiType type = switchBlock.getExpression().getType();
          if (PsiTypes.booleanType().equals(PsiPrimitiveType.getOptionallyUnboxedType(type))) {
            Set<String> branches = JavaPsiSwitchUtil.getSwitchBranches(switchBlock).stream()
              .map(branch -> branch instanceof PsiExpression expression ? ExpressionUtils.computeConstantExpression(expression) : null)
              .filter(constant -> constant instanceof Boolean)
              .map(branch -> branch.toString())
              .collect(Collectors.toSet());
            ModNavigatorTailType tailType = JavaTailTypes.forSwitchLabel(switchBlock);
            for (String keyword : List.of(JavaKeywords.TRUE, JavaKeywords.FALSE)) {
              if(branches.contains(keyword)) continue;
              addKeyword(createKeyword(keyword, tailType));
            }
          }
        }
        return;
      }

      boolean afterNew = AFTER_NEW.accepts(myPosition) &&
                         !psiElement().afterLeaf(psiElement().afterLeaf(".")).accepts(myPosition);
      if (afterNew) {
        Set<PsiType> expected = ContainerUtil.map2Set(JavaSmartCompletionContributor.getExpectedTypes(myPosition, false),
                                                      ExpectedTypeInfo::getDefaultType);
        boolean addAll = expected.isEmpty() || ContainerUtil.exists(expected, t ->
          t.equalsToText(CommonClassNames.JAVA_LANG_OBJECT) || t.equalsToText(CommonClassNames.JAVA_IO_SERIALIZABLE));
        for (String primitiveType : PsiTypes.primitiveTypeNames()) {
          PsiType array = Objects.requireNonNull(PsiTypes.primitiveTypeByName(primitiveType)).createArrayType();
          if (addAll || expected.contains(array)) {
            addKeyword(PsiTypeCompletionItem.create(array));
          }
        }
        return;
      }

      boolean inCast = psiElement()
        .afterLeaf(psiElement().withText("(").withParent(PsiJavaPatterns.psiElement(PsiParenthesizedExpression.class, PsiTypeCastExpression.class)))
        .accepts(myPosition);

      boolean typeFragment = myPosition.getContainingFile() instanceof PsiTypeCodeFragment && PsiTreeUtil.prevVisibleLeaf(myPosition) == null;
      boolean declaration = isDeclarationStart(myPosition);
      boolean expressionPosition = isExpressionPosition(myPosition);
      boolean inGenerics = PsiTreeUtil.getParentOfType(myPosition, PsiReferenceParameterList.class) != null;
      if ((isVariableTypePosition(myPosition) ||
           inGenerics ||
           inCast ||
           declaration ||
           typeFragment ||
           expressionPosition) && primitivesAreExpected(myPosition)) {
        for (String primitiveType : PsiTypes.primitiveTypeNames()) {
          addKeyword(createKeyword(primitiveType).withObject(Objects.requireNonNull(PsiTypes.primitiveTypeByName(primitiveType))));
        }
        if (expressionPosition) {
          addKeyword(createKeyword(JavaKeywords.VOID).withObject(PsiTypes.voidType()));
        }
      }
      if (declaration) {
        addKeyword(
          createKeyword(JavaKeywords.VOID, humbleSpaceBeforeWordType()).withObject(PsiTypes.voidType()));
      }
      else if (typeFragment && ((PsiTypeCodeFragment)myPosition.getContainingFile()).isVoidValid()) {
        addKeyword(createKeyword(JavaKeywords.VOID).withObject(PsiTypes.voidType()));
      }
    }

    private void addMethodHeaderKeywords() {
      if (psiElement().withText(")").withParents(PsiParameterList.class, PsiMethod.class).accepts(myPrevLeaf)) {
        assert myPrevLeaf != null;
        if (myPrevLeaf.getParent().getParent() instanceof PsiAnnotationMethod) {
          addKeyword(createKeyword(JavaKeywords.DEFAULT, humbleSpaceBeforeWordType()));
        }
        else {
          addKeyword(createKeyword(JavaKeywords.THROWS, humbleSpaceBeforeWordType()));
        }
      }
    }

    void suggestPrimitivesInsideDeconstructionListPattern() {
      PsiRecordComponent component = JavaPatternCompletionUtil.getRecordComponentForDeconstructionComponent(myPosition);
      if (component == null) return;
      PsiType type = component.getType();
      if (PsiUtil.isAvailable(JavaFeature.PRIMITIVE_TYPES_IN_PATTERNS, myPosition)) {
        suggestPrimitiveTypesForPattern(type);
        return;
      }
      if (type instanceof PsiPrimitiveType) {
        addKeyword(createKeyword(type.getPresentableText(), spaceType()).withObject(type));
      }
    }

    void suggestPrimitiveTypesForPattern(@Nullable PsiType fromType) {
      if (fromType == null) return;
      if (!PsiUtil.isAvailable(JavaFeature.PRIMITIVE_TYPES_IN_PATTERNS, myPosition)) return;
      for (PsiType primitiveType : PsiTypes.primitiveTypes()) {
        if (TypeConversionUtil.areTypesConvertible(fromType, primitiveType)) {
          addKeyword(createKeyword(primitiveType.getPresentableText(), spaceType())
                          .withObject(primitiveType));
        }
      }
    }

    private void addClassKeywords() {
      if (isSuitableForClass(myPosition)) {
        for (String s : ModifierChooser.getKeywords(myPosition)) {
          addKeyword(createKeyword(s, humbleSpaceBeforeWordType()));
        }

        if (psiElement().insideStarting(PsiJavaPatterns.psiElement(PsiLocalVariable.class, PsiExpressionStatement.class)).accepts(myPosition)) {
          addKeyword(createKeyword(JavaKeywords.CLASS, humbleSpaceBeforeWordType()));
          @NlsSafe String abstractClass = "abstract class";
          addKeyword(new CommonCompletionItem(abstractClass)
                          .withPresentation(MarkupText.plainText(abstractClass).highlightAll(MarkupText.Kind.STRONG))
                          .withTail(humbleSpaceBeforeWordType()));
          if (PsiUtil.isAvailable(JavaFeature.RECORDS, myPosition)) {
            addKeyword(createKeyword(JavaKeywords.RECORD, humbleSpaceBeforeWordType()));
          }
          if (PsiUtil.isAvailable(JavaFeature.LOCAL_ENUMS, myPosition)) {
            addKeyword(createKeyword(JavaKeywords.ENUM, humbleSpaceBeforeWordType()));
          }
          if (PsiUtil.isAvailable(JavaFeature.LOCAL_INTERFACES, myPosition)) {
            addKeyword(createKeyword(JavaKeywords.INTERFACE, humbleSpaceBeforeWordType()));
          }
        }
        if (PsiTreeUtil.getParentOfType(myPosition, PsiExpression.class, true, PsiMember.class) == null &&
            PsiTreeUtil.getParentOfType(myPosition, PsiCodeBlock.class, true, PsiMember.class) == null) {
          List<String> keywords = new ArrayList<>();
          keywords.add(JavaKeywords.CLASS);
          keywords.add(JavaKeywords.INTERFACE);
          if (PsiUtil.isAvailable(JavaFeature.RECORDS, myPosition)) {
            keywords.add(JavaKeywords.RECORD);
          }
          if (PsiUtil.isAvailable(JavaFeature.ENUMS, myPosition)) {
            keywords.add(JavaKeywords.ENUM);
          }
          String className = recommendClassName();
          for (String keyword : keywords) {
            if (className == null) {
              addKeyword(createKeyword(keyword, humbleSpaceBeforeWordType()));
            }
            else {
              addKeyword(createTypeDeclaration(keyword, className));
            }
          }
        }
      }

      if (psiElement().withText("@").andNot(psiElement().inside(PsiParameterList.class)).andNot(psiElement().inside(psiNameValuePair()))
        .accepts(myPrevLeaf)) {
        addKeyword(createKeyword(JavaKeywords.INTERFACE, humbleSpaceBeforeWordType()));
      }
    }

    private CommonCompletionItem createTypeDeclaration(@NlsSafe String keyword, @NlsSafe String className) {
      PsiElement nextElement = PsiTreeUtil.skipWhitespacesAndCommentsForward(PsiTreeUtil.nextLeaf(myPosition));
      IElementType nextToken;
      if (nextElement instanceof PsiJavaToken token) {
        nextToken = token.getTokenType();
      }
      else {
        if (nextElement instanceof PsiParameterList l && l.getFirstChild() instanceof PsiJavaToken t) {
          nextToken = t.getTokenType();
        }
        else if (nextElement instanceof PsiCodeBlock b && b.getFirstChild() instanceof PsiJavaToken t) {
          nextToken = t.getTokenType();
        }
        else {
          nextToken = null;
        }
      }
      return new CommonCompletionItem(keyword + " " + className)
        .withPresentation(
          new ModCompletionItemPresentation(MarkupText.builder().append(keyword, STRONG).append(" " + className, NORMAL).build())
            .withMainIcon(() -> CreateClassKind.valueOf(keyword.toUpperCase(Locale.ROOT)).getKindIcon()))
        .withAdditionalUpdater((start, updater) -> {
          Document document = updater.getDocument();
          int offset = updater.getCaretOffset();
          String suffix = " ";
          if (keyword.equals(JavaKeywords.RECORD)) {
            if (JavaTokenType.LPARENTH.equals(nextToken)) {
              suffix = "";
            }
            else if (JavaTokenType.LBRACE.equals(nextToken)) {
              suffix = "() ";
            }
            else {
              suffix = "() {\n}";
            }
          }
          else if (!JavaTokenType.LBRACE.equals(nextToken)) {
            suffix = " {\n}";
          }
          if (offset < document.getTextLength() && document.getCharsSequence().charAt(offset) == ' ') {
            suffix = suffix.trim();
          }
          document.insertString(offset, suffix);
          updater.moveCaretTo(offset + 1);
        });
    }

    private @Nullable String recommendClassName() {
      if (myPrevLeaf == null) return null;
      if (!myPrevLeaf.textMatches(JavaKeywords.PUBLIC) || !(myPrevLeaf.getParent() instanceof PsiModifierList)) return null;

      if (nextIsIdentifier(myPosition)) return null;

      PsiJavaFile file = getFileForDeclaration(myPrevLeaf);
      if (file == null) return null;
      String name = file.getName();
      if (!StringUtil.endsWithIgnoreCase(name, JavaFileType.DOT_DEFAULT_EXTENSION)) return null;
      String candidate = name.substring(0, name.length() - JavaFileType.DOT_DEFAULT_EXTENSION.length());
      if (StringUtil.isJavaIdentifier(candidate)
          && !ContainerUtil.exists(file.getClasses(), c -> !(c instanceof PsiImplicitClass) && candidate.equals(c.getName()))) {
        return candidate;
      }
      return null;
    }

    private static boolean nextIsIdentifier(PsiElement position) {
      PsiElement nextLeaf = PsiTreeUtil.nextLeaf(position);
      if (nextLeaf == null) return false;
      PsiElement parent = nextLeaf.getParent();
      if (!(parent instanceof PsiJavaCodeReferenceElement)) return false;
      PsiElement grandParent = parent.getParent();
      if (!(grandParent instanceof PsiTypeElement)) return false;
      return PsiTreeUtil.skipWhitespacesAndCommentsForward(grandParent) instanceof PsiIdentifier;
    }

    private void addInstanceof() {
      if (JavaKeywordCompletion.isInstanceofPlace(myPosition)) {
        addKeyword(createKeyword(JavaKeywords.INSTANCEOF).withAdditionalUpdater((startOffset, updater, insertionContext) -> {
          Document document = updater.getDocument();
          int offset = updater.getCaretOffset();
          ModNavigatorTailType tailType = humbleSpaceBeforeWordType();
          tailType.processTail(updater, offset);
          PsiDocumentManager.getInstance(updater.getProject()).commitDocument(document);
          PsiFile file = updater.getPsiFile();
          PsiInstanceOfExpression expr = 
            PsiTreeUtil.findElementOfClassAtOffset(file, startOffset, PsiInstanceOfExpression.class, false);
          if (expr != null) {
            PsiExpression operand = expr.getOperand();
            if (operand instanceof PsiPrefixExpression prefixExpression &&
                prefixExpression.getOperationTokenType().equals(JavaTokenType.EXCL)) {
              PsiExpression negated = prefixExpression.getOperand();
              if (negated != null) {
                String space = CodeStyle.getLanguageSettings(file).SPACE_WITHIN_PARENTHESES ? " " : "";
                int startParens = negated.getTextRange().getStartOffset();
                document.insertString(startParens, "(" + space);
                int endParens = updater.getCaretOffset();
                document.insertString(endParens, space + ")");
                updater.registerTabOut(TextRange.create(startParens + 1, endParens + space.length()), endParens + space.length() + 1);
              }
            }
            else if ('!' == insertionContext.insertionCharacter()) {
              // TODO: suppress ! typing
              String space = CodeStyle.getLanguageSettings(file).SPACE_WITHIN_PARENTHESES ? " " : "";
              document.insertString(expr.getTextRange().getStartOffset(), "!(" + space);
              document.insertString(updater.getCaretOffset(), space + ")");
            }
          }
        }));
      }
    }

    private void addFileHeaderKeywords() {
      PsiFile file = myPosition.getContainingFile();
      assert file != null;

      if (!(file instanceof PsiExpressionCodeFragment) &&
          !(file instanceof PsiJavaCodeReferenceCodeFragment) &&
          !(file instanceof PsiTypeCodeFragment)) {
        PsiMember parentMember = PsiTreeUtil.getParentOfType(myPosition, PsiMember.class);
        boolean bogusDeclarationInImplicitClass =
          parentMember instanceof PsiField field &&
          field.getParent() instanceof PsiImplicitClass implicitClass &&
          StreamEx.of(implicitClass.getChildren()).select(PsiMember.class).findFirst().orElse(null) == field;
        if (myPrevLeaf == null ||
            bogusDeclarationInImplicitClass && file instanceof PsiJavaFile javaFile && javaFile.getPackageStatement() == null &&
            javaFile.getImportList() != null && javaFile.getImportList().getAllImportStatements().length == 0) {
          addKeyword(createKeyword(JavaKeywords.PACKAGE, humbleSpaceBeforeWordType()));
          addKeyword(createKeyword(JavaKeywords.IMPORT, humbleSpaceBeforeWordType()));
        }
        else if (psiElement().inside(psiAnnotation().withParents(PsiModifierList.class, PsiFile.class)).accepts(myPrevLeaf)
                 && PsiPackage.PACKAGE_INFO_FILE.equals(file.getName())) {
          addKeyword(createKeyword(JavaKeywords.PACKAGE, humbleSpaceBeforeWordType()));
        }
        else if (isEndOfBlock(myPosition) && (parentMember == null || bogusDeclarationInImplicitClass)) {
          addKeyword(createKeyword(JavaKeywords.IMPORT, humbleSpaceBeforeWordType()));
        }
      }

      if (PsiUtil.isAvailable(JavaFeature.STATIC_IMPORTS, file) && myPrevLeaf != null && myPrevLeaf.textMatches(JavaKeywords.IMPORT)) {
        addKeyword(createKeyword(JavaKeywords.STATIC, humbleSpaceBeforeWordType()));
      }

      if (PsiUtil.isAvailable(JavaFeature.MODULE_IMPORT_DECLARATIONS, file) &&
          myPrevLeaf != null &&
          myPrevLeaf.textMatches(JavaKeywords.IMPORT)) {
        addKeyword(createKeyword(JavaKeywords.MODULE, humbleSpaceBeforeWordType()));
      }
    }

    private void addExpressionKeywords(boolean statementPosition) {
      if (isExpressionPosition(myPosition)) {
        PsiElement parent = myPosition.getParent();
        PsiElement grandParent = parent == null ? null : parent.getParent();
        boolean allowExprKeywords = !(grandParent instanceof PsiExpressionStatement) && !(grandParent instanceof PsiUnaryExpression);
        if (PsiTreeUtil.getParentOfType(myPosition, PsiAnnotation.class) == null) {
          if (!statementPosition) {
            addKeyword(createKeyword(JavaKeywords.NEW, insertSpaceType()));
            if (PsiUtil.isAvailable(JavaFeature.ENHANCED_SWITCH, myPosition)) {
              addKeyword(createKeyword(JavaKeywords.SWITCH, JavaTailTypes.SWITCH_LPARENTH));
            }
          }
          if (allowExprKeywords) {
            addKeyword(createKeyword(JavaKeywords.NULL));
          }
        }
        if (allowExprKeywords && mayExpectBoolean()) {
          addKeyword(createKeyword(JavaKeywords.TRUE));
          addKeyword(createKeyword(JavaKeywords.FALSE));
        }
      }

      if (isQualifiedNewContext()) {
        addKeyword(createKeyword(JavaKeywords.NEW));
      }
    }

    private void addThisSuper() {
      if (SUPER_OR_THIS_PATTERN.accepts(myPosition)) {
        boolean afterDot = AFTER_DOT.accepts(myPosition);
        boolean insideQualifierClass = isInsideQualifierClass();
        boolean insideInheritorClass = PsiUtil.isAvailable(JavaFeature.EXTENSION_METHODS, myPosition) && isInsideInheritorClass();
        if (!afterDot || insideQualifierClass || insideInheritorClass) {
          if (!afterDot || insideQualifierClass) {
            addKeyword(createKeyword(JavaKeywords.THIS));
          }

          CommonCompletionItem superItem = createKeyword(JavaKeywords.SUPER);
          if (psiElement().afterLeaf(psiElement().withText("{").withSuperParent(2, psiMethod().constructor(true))).accepts(myPosition)) {
            PsiMethod method = PsiTreeUtil.getParentOfType(myPosition, PsiMethod.class, false, PsiClass.class);
            assert method != null;
            boolean hasParams = superConstructorHasParameters(method);
            addKeyword(superItem.withAdditionalUpdater((startOffset, updater) -> {
              int offset = updater.getCaretOffset();
              Document document = updater.getDocument();
              // TODO: support '(' insertion character
              document.insertString(offset, "();");
              if (hasParams) {
                updater.moveCaretTo(offset + 1);
                updater.registerTabOut(TextRange.create(offset + 1, offset + 1), offset + 3);
              }
              else {
                updater.moveCaretTo(offset + 3);
              }
              PsiDocumentManager.getInstance(updater.getProject()).commitDocument(document);
              CodeStyleManager.getInstance(updater.getProject()).reformatText(updater.getPsiFile(), offset, offset + 3);
            }));
            return;
          }

          addKeyword(superItem);
        }
      }
    }

    private boolean isInsideInheritorClass() {
      if (myPosition.getParent() instanceof PsiJavaCodeReferenceElement) {
        final PsiElement qualifier = ((PsiJavaCodeReferenceElement)myPosition.getParent()).getQualifier();
        if (qualifier instanceof PsiJavaCodeReferenceElement) {
          final PsiElement qualifierClass = ((PsiJavaCodeReferenceElement)qualifier).resolve();
          if (qualifierClass instanceof PsiClass && ((PsiClass)qualifierClass).isInterface()) {
            PsiElement parent = myPosition;
            while ((parent = PsiTreeUtil.getParentOfType(parent, PsiClass.class, true)) != null) {
              if (PsiUtil.getEnclosingStaticElement(myPosition, (PsiClass)parent) == null &&
                  ((PsiClass)parent).isInheritor((PsiClass)qualifierClass, true)) {
                return true;
              }
            }
          }
        }
      }
      return false;
    }

    private boolean isInsideQualifierClass() {
      if (myPosition.getParent() instanceof PsiJavaCodeReferenceElement) {
        final PsiElement qualifier = ((PsiJavaCodeReferenceElement)myPosition.getParent()).getQualifier();
        if (qualifier instanceof PsiJavaCodeReferenceElement) {
          final PsiElement qualifierClass = ((PsiJavaCodeReferenceElement)qualifier).resolve();
          if (qualifierClass instanceof PsiClass) {
            PsiElement parent = myPosition;
            final PsiManager psiManager = myPosition.getManager();
            while ((parent = PsiTreeUtil.getParentOfType(parent, PsiClass.class, true)) != null) {
              if (psiManager.areElementsEquivalent(parent, qualifierClass)) {
                return true;
              }
            }
          }
        }
      }
      return false;
    }

    private void addSwitchRuleKeywords(PsiSwitchLabeledRuleStatement rule) {
      addKeyword(createKeyword(JavaKeywords.THROW, insertSpaceType()));
      addKeyword(wrapRuleIntoBlock(createKeyword(JavaKeywords.ASSERT, insertSpaceType())));
      addKeyword(wrapRuleIntoBlock(createKeyword(JavaKeywords.WHILE, JavaTailTypes.WHILE_LPARENTH)));
      addKeyword(wrapRuleIntoBlock(createKeyword(JavaKeywords.DO, JavaTailTypes.DO_LBRACE)));
      addKeyword(wrapRuleIntoBlock(createKeyword(JavaKeywords.FOR, JavaTailTypes.FOR_LPARENTH)));
      addKeyword(wrapRuleIntoBlock(createKeyword(JavaKeywords.IF, JavaTailTypes.IF_LPARENTH)));
      addKeyword(wrapRuleIntoBlock(createKeyword(JavaKeywords.TRY, JavaTailTypes.TRY_LBRACE)));
      if (rule.getEnclosingSwitchBlock() instanceof PsiSwitchStatement) {
        addKeyword(wrapRuleIntoBlock(createKeyword(JavaKeywords.RETURN, getReturnTail())));
      }
      else {
        addKeyword(wrapRuleIntoBlock(createKeyword(JavaKeywords.YIELD, insertSpaceType())));
      }
    }

    private void addBreakContinue() {
      PsiLoopStatement loop =
        PsiTreeUtil.getParentOfType(myPosition, PsiLoopStatement.class, true, PsiLambdaExpression.class, PsiMember.class);

      ModNavigatorTailType tailType;
      if (psiElement().insideSequence(true, psiElement(PsiLabeledStatement.class),
                                      or(psiElement(PsiFile.class), psiElement(PsiMethod.class),
                                         psiElement(PsiClassInitializer.class))).accepts(myPosition)) {
        tailType = humbleSpaceBeforeWordType();
      }
      else {
        tailType = semicolonType();
      }
      CommonCompletionItem br = createKeyword(JavaKeywords.BREAK, tailType);
      CommonCompletionItem cont = createKeyword(JavaKeywords.CONTINUE, tailType);

      if (loop != null && PsiTreeUtil.isAncestor(loop.getBody(), myPosition, false)) {
        addKeyword(br);
        addKeyword(cont);
      }
      if (psiElement().inside(PsiSwitchStatement.class).accepts(myPosition)) {
        addKeyword(br);
      }
      else if (psiElement().inside(PsiSwitchExpression.class).accepts(myPosition) &&
               PsiUtil.isAvailable(JavaFeature.SWITCH_EXPRESSION, myPosition)) {
        addKeyword(createKeyword(JavaKeywords.YIELD, insertSpaceType()));
      }

      for (PsiLabeledStatement labeled : psiApi().parents(myPosition).takeWhile(notInstanceOf(PsiMember.class))
        .filter(PsiLabeledStatement.class)) {
        @NlsSafe String keyword = JavaKeywords.BREAK + " " + labeled.getName();
        addKeyword(new CommonCompletionItem(keyword)
                        .withObject(JavaPsiFacade.getElementFactory(myFile.getProject()).createKeyword(JavaKeywords.BREAK, myPosition))
                        .withTail(semicolonType())
                        .withPresentation(
                          MarkupText.plainText(JavaKeywords.BREAK + " " + labeled.getName()).highlightAll(MarkupText.Kind.STRONG)));
      }
    }

    private void addWhen() {
      if (!PsiUtil.isAvailable(JavaFeature.PATTERN_GUARDS_AND_RECORD_PATTERNS, myPosition)) {
        return;
      }
      PsiElement element = PsiTreeUtil.skipWhitespacesAndCommentsForward(myPrevLeaf);
      if (element instanceof PsiErrorElement) {
        return;
      }

      element = PsiTreeUtil.skipWhitespacesAndCommentsBackward(PsiTreeUtil.prevLeaf(myPosition));
      if (element instanceof PsiErrorElement) {
        return;
      }

      PsiPattern psiPattern =
        PsiTreeUtil.getParentOfType(myPrevLeaf, PsiPattern.class, true, PsiStatement.class, PsiMember.class, PsiClass.class);
      if (psiPattern == null ||
          (psiPattern instanceof PsiTypeTestPattern testPattern &&
           testPattern.getPatternVariable() != null &&
           testPattern.getPatternVariable().getNameIdentifier() == myPosition)) {
        return;
      }
      PsiElement parentOfPattern = PsiTreeUtil.skipParentsOfType(psiPattern, PsiPattern.class, PsiDeconstructionList.class);
      if (!(parentOfPattern instanceof PsiCaseLabelElementList)) {
        return;
      }
      addKeyword(createKeyword(JavaKeywords.WHEN, insertSpaceType()));
    }

    private boolean addWildcardExtendsSuper() {
      if (JavaMemberNameCompletionContributor.INSIDE_TYPE_PARAMS_PATTERN.accepts(myPosition)) {
        for (String keyword : ContainerUtil.ar(JavaKeywords.EXTENDS, JavaKeywords.SUPER)) {
          if (myKeywordMatcher.isStartMatch(keyword)) {
            addKeyword(createKeyword(keyword, humbleSpaceBeforeWordType()));
          }
        }
        return true;
      }
      return false;
    }
    
    private void addFinal() {
      PsiStatement statement = PsiTreeUtil.getParentOfType(myPosition, PsiExpressionStatement.class, PsiDeclarationStatement.class);
      if (statement != null && statement.getTextRange().getStartOffset() == myPosition.getTextRange().getStartOffset()) {
        if (!psiElement().withSuperParent(2, PsiSwitchBlock.class).afterLeaf("{").accepts(statement)) {
          PsiTryStatement tryStatement = PsiTreeUtil.getParentOfType(myPrevLeaf, PsiTryStatement.class);
          if (tryStatement == null ||
              tryStatement.getCatchSections().length > 0 ||
              tryStatement.getFinallyBlock() != null || tryStatement.getResourceList() != null) {
            CommonCompletionItem finalKeyword =
              createKeyword(JavaKeywords.FINAL, humbleSpaceBeforeWordType());
            if (statement.getParent() instanceof PsiSwitchLabeledRuleStatement) {
              finalKeyword = wrapRuleIntoBlock(finalKeyword);
            }
            addKeyword(finalKeyword);
            return;
          }
        }
      }

      if ((isInsideParameterList(myPosition) || isAtCatchOrResourceVariableStart(myPosition)) &&
          !psiElement().afterLeaf(JavaKeywords.FINAL).accepts(myPosition) &&
          !AFTER_DOT.accepts(myPosition)) {
        addKeyword(createKeyword(JavaKeywords.FINAL, humbleSpaceBeforeWordType()));
      }
    }

    public static boolean isInsideParameterList(PsiElement position) {
      PsiElement prev = PsiTreeUtil.prevVisibleLeaf(position);
      PsiModifierList modifierList = PsiTreeUtil.getParentOfType(prev, PsiModifierList.class);
      if (modifierList != null) {
        if (PsiTreeUtil.isAncestor(modifierList, position, false)) {
          return false;
        }
        PsiElement parent = modifierList.getParent();
        return parent instanceof PsiParameterList || parent instanceof PsiParameter && parent.getParent() instanceof PsiParameterList;
      }
      return INSIDE_PARAMETER_LIST.accepts(position);
    }

    private static boolean isAtCatchOrResourceVariableStart(PsiElement position) {
      PsiElement type = PsiTreeUtil.getParentOfType(position, PsiTypeElement.class);
      if (type != null && type.getTextRange().getStartOffset() == position.getTextRange().getStartOffset()) {
        PsiElement parent = type.getParent();
        if (parent instanceof PsiVariable) parent = parent.getParent();
        return parent instanceof PsiCatchSection || parent instanceof PsiResourceList;
      }
      return psiElement().insideStarting(psiElement(PsiResourceExpression.class)).accepts(position);
    }


    private static CommonCompletionItem wrapRuleIntoBlock(CommonCompletionItem item) {
      return item.withAdditionalUpdater((startOffset, updater) -> {
        PsiStatement statement = PsiTreeUtil.getParentOfType(updater.getPsiFile().findElementAt(startOffset), PsiStatement.class);
        boolean isAfterArrow = false;
        if (statement != null) {
          if (statement.getParent() instanceof PsiCodeBlock) {
            PsiElement prevLeaf = PsiTreeUtil.prevCodeLeaf(statement);
            if (PsiUtil.isJavaToken(prevLeaf, JavaTokenType.ARROW) && prevLeaf.getParent() instanceof PsiSwitchLabeledRuleStatement) {
              isAfterArrow = true;
            }
          }
          else if (statement.getParent() instanceof PsiSwitchLabeledRuleStatement) {
            isAfterArrow = true;
          }
        }
        if (isAfterArrow) {
          int origPos = updater.getCaretOffset();
          int start = statement.getTextRange().getStartOffset();
          PsiStatement updatedStatement = BlockUtils.expandSingleStatementToBlockStatement(statement);
          int updatedStart = updatedStatement.getTextRange().getStartOffset();
          updater.moveCaretTo(origPos + updatedStart - start);
          PsiDocumentManager.getInstance(updater.getProject()).doPostponedOperationsAndUnblockDocument(updater.getDocument());
        }
      });
    }

    private void addModuleKeywords() {
      PsiElement context =
        PsiTreeUtil.skipParentsOfType(myPosition, PsiErrorElement.class, PsiJavaCodeReferenceElement.class, PsiTypeElement.class);
      PsiElement prevLeaf = PsiTreeUtil.skipWhitespacesAndCommentsBackward(myPosition.getParent());

      if (context instanceof PsiField && context.getParent() instanceof PsiImplicitClass) {
        addKeyword(createKeyword(JavaKeywords.MODULE, humbleSpaceBeforeWordType()));
        if (prevLeaf == null) {
          addKeyword(createKeyword(JavaKeywords.IMPORT, humbleSpaceBeforeWordType()));
        }
      }

      if (context instanceof PsiJavaFile && !(prevLeaf instanceof PsiJavaModule) || context instanceof PsiImportList) {
        if (prevLeaf == null || PsiUtil.isJavaToken(prevLeaf, JavaTokenType.SEMICOLON)) {
          addKeyword(createKeyword(JavaKeywords.IMPORT, humbleSpaceBeforeWordType()));
        }
        addKeyword(createKeyword(JavaKeywords.MODULE, humbleSpaceBeforeWordType()));
        if (prevLeaf == null || !prevLeaf.textMatches(JavaKeywords.OPEN)) {
          addKeyword(createKeyword(JavaKeywords.OPEN, humbleSpaceBeforeWordType()));
        }
      }
      else if (context instanceof PsiJavaModule) {
        if (prevLeaf instanceof PsiPackageAccessibilityStatement && !prevLeaf.textMatches(";")) {
          addKeyword(createKeyword(JavaKeywords.TO, humbleSpaceBeforeWordType()));
        }
        else if (!PsiUtil.isJavaToken(prevLeaf, JavaTokenType.MODULE_KEYWORD)) {
          addKeyword(createKeyword(JavaKeywords.REQUIRES, humbleSpaceBeforeWordType()));
          addKeyword(createKeyword(JavaKeywords.EXPORTS, humbleSpaceBeforeWordType()));
          addKeyword(createKeyword(JavaKeywords.OPENS, humbleSpaceBeforeWordType()));
          addKeyword(createKeyword(JavaKeywords.USES, humbleSpaceBeforeWordType()));
          addKeyword(createKeyword(JavaKeywords.PROVIDES, humbleSpaceBeforeWordType()));
        }
      }
      else if (context instanceof PsiRequiresStatement && prevLeaf != null) {
        if (!prevLeaf.textMatches(JavaKeywords.TRANSITIVE)) {
          addKeyword(createKeyword(JavaKeywords.TRANSITIVE, humbleSpaceBeforeWordType()));
        }
        if (!prevLeaf.textMatches(JavaKeywords.STATIC)) {
          addKeyword(createKeyword(JavaKeywords.STATIC, humbleSpaceBeforeWordType()));
        }
      }
      else if (context instanceof PsiProvidesStatement && prevLeaf instanceof PsiJavaCodeReferenceElement) {
        addKeyword(createKeyword(JavaKeywords.WITH, humbleSpaceBeforeWordType()));
      }
    }

    private void addStatementKeywords() {
      if (psiElement()
        .withText("}")
        .withParent(psiElement(PsiCodeBlock.class).withParent(or(psiElement(PsiTryStatement.class), psiElement(PsiCatchSection.class))))
        .accepts(myPrevLeaf)) {
        addKeyword(createKeyword(JavaKeywords.CATCH, JavaTailTypes.CATCH_LPARENTH));
        addKeyword(createKeyword(JavaKeywords.FINALLY, JavaTailTypes.FINALLY_LBRACE));
        if (myPrevLeaf != null && myPrevLeaf.getParent().getNextSibling() instanceof PsiErrorElement) {
          return;
        }
      }
      addKeyword(createKeyword(JavaKeywords.SWITCH, JavaTailTypes.SWITCH_LPARENTH));
      addKeyword(createKeyword(JavaKeywords.WHILE, JavaTailTypes.WHILE_LPARENTH));
      addKeyword(createKeyword(JavaKeywords.DO, JavaTailTypes.DO_LBRACE));
      addKeyword(createKeyword(JavaKeywords.FOR, JavaTailTypes.FOR_LPARENTH));
      addKeyword(createKeyword(JavaKeywords.IF, JavaTailTypes.IF_LPARENTH));
      addKeyword(createKeyword(JavaKeywords.TRY, JavaTailTypes.TRY_LBRACE));
      addKeyword(createKeyword(JavaKeywords.SYNCHRONIZED, JavaTailTypes.SYNCHRONIZED_LPARENTH));
      addKeyword(createKeyword(JavaKeywords.THROW, insertSpaceType()));
      addKeyword(createKeyword(JavaKeywords.NEW, insertSpaceType()));
      if (PsiUtil.isAvailable(JavaFeature.ASSERTIONS, myPosition)) {
        addKeyword(createKeyword(JavaKeywords.ASSERT, insertSpaceType()));
      }
      if (!(PsiTreeUtil.getParentOfType(myPosition, PsiSwitchExpression.class, PsiLambdaExpression.class)
              instanceof PsiSwitchExpression)) {
        addKeyword(createKeyword(JavaKeywords.RETURN, getReturnTail()));
      }
      if (psiElement().withText(";").withSuperParent(2, PsiIfStatement.class).accepts(myPrevLeaf) ||
          psiElement().withText("}").withSuperParent(3, PsiIfStatement.class).accepts(myPrevLeaf)) {
        CommonCompletionItem elseKeyword = createKeyword(JavaKeywords.ELSE, humbleSpaceBeforeWordType());
        CharSequence text = myPosition.getContainingFile().getFileDocument().getCharsSequence();
        int offset = myContext.getOffset();
        while (text.length() > offset && Character.isWhitespace(text.charAt(offset))) {
          offset++;
        }
        if (text.length() > offset + JavaKeywords.ELSE.length() &&
            text.subSequence(offset, offset + JavaKeywords.ELSE.length()).toString().equals(JavaKeywords.ELSE) &&
            Character.isWhitespace(text.charAt(offset + JavaKeywords.ELSE.length()))) {
          elseKeyword = elseKeyword.withPriority(-1);
        }
        addKeyword(elseKeyword);
      }
    }

    void addEnumCases() {
      PsiSwitchBlock switchBlock = getSwitchFromLabelPosition();
      PsiExpression expression = switchBlock == null ? null : switchBlock.getExpression();
      PsiClass switchType = expression == null ? null : PsiUtil.resolveClassInClassTypeOnly(expression.getType());
      if (switchType == null || !switchType.isEnum()) return;

      Set<PsiField> used = ReferenceExpressionCompletionContributor.findConstantsUsedInSwitch(switchBlock);
      ModNavigatorTailType tailType = JavaTailTypes.forSwitchLabel(switchBlock);
      for (PsiField field : switchType.getAllFields()) {
        String name = field.getName();
        if (!(field instanceof PsiEnumConstant) || used.contains(CompletionUtil.getOriginalOrSelf(field))) {
          continue;
        }
        @NlsSafe String prefix = "case ";
        CommonCompletionItem caseConst =
          new CommonCompletionItem(prefix + name)
            .addLookupString(name)
            .adjustIndent()
            .withTail(tailType)
            .withObject(field)
            .withPresentation(MarkupText.builder().append(prefix, STRONG).append(name, GRAYED).build())
            .withPriority(prioritizeForRule(switchBlock));
        addKeyword(caseConst);
      }
    }

    void addEnhancedCases() {
      if (!canAddKeywords()) return;

      boolean statementPosition = isStatementPosition(myPosition);
      if (statementPosition) {
        addCaseDefault();

        addPatternMatchingInSwitchCases();
      }
      PsiElement parent = myPosition.getParent();
      if (parent != null && parent.getParent() instanceof PsiCaseLabelElementList) {
        addCaseAfterNullDefault();
      }
    }

    private void addCaseAfterNullDefault() {
      if (!PsiUtil.isAvailable(JavaFeature.PATTERNS_IN_SWITCH, myPosition)) return;
      PsiCaseLabelElementList labels = PsiTreeUtil.getParentOfType(myPosition, PsiCaseLabelElementList.class);
      if (labels == null || labels.getElementCount() != 2 ||
          !(labels.getElements()[0] instanceof PsiLiteralExpression literalExpression &&
            ExpressionUtils.isNullLiteral(literalExpression))) {
        return;
      }

      PsiSwitchBlock switchBlock = PsiTreeUtil.getParentOfType(labels, PsiSwitchBlock.class);
      if (switchBlock == null) return;
      List<PsiSwitchLabelStatementBase> allBranches =
        PsiTreeUtil.getChildrenOfTypeAsList(switchBlock.getBody(), PsiSwitchLabelStatementBase.class);
      if (allBranches.isEmpty() || allBranches.getLast().getCaseLabelElementList() != labels) {
        return;
      }
      if (JavaPsiSwitchUtil.findDefaultElement(switchBlock) != null) {
        return;
      }

      ModCompletionItem defaultCaseRule = createKeyword(JavaKeywords.DEFAULT, JavaTailTypes.forSwitchLabel(switchBlock))
        .adjustIndent()
        .withPriority(prioritizeForRule(switchBlock));
      addKeyword(defaultCaseRule);
    }

    private void addCaseDefault() {
      PsiSwitchBlock switchBlock = getSwitchFromLabelPosition();
      if (switchBlock == null) return;
      PsiElement defaultElement = JavaPsiSwitchUtil.findDefaultElement(switchBlock);
      if (defaultElement != null && defaultElement.getTextRange().getStartOffset() < myPosition.getTextRange().getStartOffset()) return;
      addKeyword(createKeyword(JavaKeywords.CASE, insertSpaceType()));
      if (defaultElement != null) {
        return;
      }
      ModCompletionItem defaultCaseRule = createKeyword(JavaKeywords.DEFAULT, JavaTailTypes.forSwitchLabel(switchBlock))
        .adjustIndent()
        .withPriority(prioritizeForRule(switchBlock));
      addKeyword(defaultCaseRule);
    }

    private void addPatternMatchingInSwitchCases() {
      if (!PsiUtil.isAvailable(JavaFeature.PATTERNS_IN_SWITCH, myPosition)) return;

      PsiSwitchBlock switchBlock = getSwitchFromLabelPosition();
      if (switchBlock == null) return;

      final PsiType selectorType = getSelectorType(switchBlock);
      if (selectorType == null || selectorType instanceof PsiPrimitiveType) return;

      PsiElement defaultElement = JavaPsiSwitchUtil.findDefaultElement(switchBlock);
      if (defaultElement != null && defaultElement.getTextRange().getStartOffset() < myPosition.getTextRange().getStartOffset()) return;

      final ModNavigatorTailType caseRuleTail = JavaTailTypes.forSwitchLabel(switchBlock);
      Set<String> containedLabels = getSwitchCoveredLabels(switchBlock);
      if (!containedLabels.contains(JavaKeywords.NULL)) {
        addKeyword(createCaseRule(JavaKeywords.NULL, caseRuleTail, switchBlock));
        if (!containedLabels.contains(JavaKeywords.DEFAULT)) {
          addKeyword(createCaseRule(JavaKeywords.NULL + ", " + JavaKeywords.DEFAULT, caseRuleTail, switchBlock));
        }
      }
      addSealedHierarchyCases(selectorType, containedLabels);
    }

    private static ModCompletionItem createCaseRule(@NlsSafe String caseRuleName,
                                                    ModNavigatorTailType tailType,
                                                    @Nullable PsiSwitchBlock switchBlock) {
      @NlsSafe String prefix = "case ";

      return new CommonCompletionItem(prefix + caseRuleName)
        .withPresentation(MarkupText.builder()
                            .append(prefix, STRONG)
                            .append(caseRuleName).build())
        .withTail(tailType)
        .addLookupString(caseRuleName)
        .adjustIndent()
        .withPriority(prioritizeForRule(switchBlock));
    }

    private static double prioritizeForRule(@Nullable PsiSwitchBlock switchBlock) {
      if (switchBlock == null) {
        return 0;
      }
      PsiCodeBlock body = switchBlock.getBody();
      if (body == null) {
        return 0;
      }
      PsiStatement[] statements = body.getStatements();
      if (statements.length == 0) {
        return 0;
      }
      if (statements[0] instanceof PsiSwitchLabeledRuleStatement) {
        return -1;
      }
      return 0;
    }

    private Set<String> getSwitchCoveredLabels(@Nullable PsiSwitchBlock block) {
      HashSet<String> labels = new HashSet<>();
      if (block == null) {
        return labels;
      }
      PsiCodeBlock body = block.getBody();
      if (body == null) {
        return labels;
      }
      int offset = myPosition.getTextRange().getStartOffset();
      for (PsiStatement statement : body.getStatements()) {
        if (!(statement instanceof PsiSwitchLabelStatementBase labelStatement)) continue;
        if (labelStatement.isDefaultCase()) {
          labels.add(JavaKeywords.DEFAULT);
          continue;
        }
        if (labelStatement.getGuardExpression() != null) {
          continue;
        }
        PsiCaseLabelElementList list = labelStatement.getCaseLabelElementList();
        if (list == null) {
          continue;
        }
        for (PsiCaseLabelElement element : list.getElements()) {
          if (element instanceof PsiExpression expr &&
              ExpressionUtils.isNullLiteral(expr)) {
            labels.add(JavaKeywords.NULL);
            continue;
          }
          if (element instanceof PsiDefaultCaseLabelElement) {
            labels.add(JavaKeywords.DEFAULT);
          }
          if (element.getTextRange().getStartOffset() >= offset) {
            break;
          }
          PsiType patternType = JavaPsiPatternUtil.getPatternType(element);
          if (patternType != null && JavaPsiPatternUtil.isUnconditionalForType(element, patternType)) {
            PsiClass psiClass = PsiUtil.resolveClassInClassTypeOnly(patternType);
            if (psiClass == null) continue;
            String qualifiedName = psiClass.getQualifiedName();
            if (qualifiedName == null) continue;
            labels.add(qualifiedName);
          }
        }
      }
      return labels;
    }

    @Contract(pure = true)
    private static @Nullable PsiType getSelectorType(PsiSwitchBlock switchBlock) {

      final PsiExpression selector = switchBlock.getExpression();
      if (selector == null) return null;

      return selector.getType();
    }

    private @Nullable PsiSwitchBlock getSwitchFromLabelPosition() {
      PsiStatement statement = PsiTreeUtil.getParentOfType(myPosition, PsiStatement.class, false, PsiMember.class);
      if (statement == null || statement.getTextRange().getStartOffset() != myPosition.getTextRange().getStartOffset()) {
        return null;
      }

      if (!(statement instanceof PsiSwitchLabelStatementBase) && statement.getParent() instanceof PsiCodeBlock) {
        return ObjectUtils.tryCast(statement.getParent().getParent(), PsiSwitchBlock.class);
      }
      return null;
    }

    private void addSealedHierarchyCases(PsiType type, Set<String> containedLabels) {
      final PsiResolveHelper resolver = JavaPsiFacade.getInstance(myPosition.getProject()).getResolveHelper();
      PsiClass aClass = resolver.resolveReferencedClass(type.getCanonicalText(), null);
      if (aClass == null) {
        aClass = PsiUtil.resolveClassInClassTypeOnly(type);
      }
      if (aClass == null || aClass.isEnum() || !aClass.hasModifierProperty(PsiModifier.SEALED)) return;

      for (PsiClass inheritor : SealedUtils.findSameFileInheritorsClasses(aClass)) {
        //we don't check hierarchy here, because it is time-consuming
        if (containedLabels.contains(inheritor.getQualifiedName())) {
          continue;
        }

        final JavaPsiClassReferenceElement item = AllClassesGetter.createLookupItem(inheritor, AllClassesGetter.TRY_SHORTENING);
        item.setForcedPresentableName("case " + inheritor.getName());
        addKeyword(new ClassReferenceCompletionItem(inheritor).withPresentableName("case " + inheritor.getName()));
      }
    }

    private ModNavigatorTailType getReturnTail() {
      PsiElement scope = myPosition;
      while (true) {
        if (scope instanceof PsiFile || scope instanceof PsiClassInitializer) {
          return noneType();
        }

        if (scope instanceof PsiMethod method) {
          if (method.isConstructor() || PsiTypes.voidType().equals(method.getReturnType())) {
            return semicolonType();
          }

          return humbleSpaceBeforeWordType();
        }
        if (scope instanceof PsiLambdaExpression lambda) {
          final PsiType returnType = LambdaUtil.getFunctionalInterfaceReturnType(lambda);
          if (PsiTypes.voidType().equals(returnType)) {
            return semicolonType();
          }
          return humbleSpaceBeforeWordType();
        }
        scope = scope.getParent();
      }
    }

    private boolean canAddKeywords() {
      if (PsiTreeUtil.getNonStrictParentOfType(myPosition, PsiLiteralExpression.class, PsiComment.class) != null) {
        return false;
      }
      if (psiElement().afterLeaf("::").accepts(myPosition)) {
        return false;
      }
      return true;
    }

    private boolean isQualifiedNewContext() {
      if (myPosition.getParent() instanceof PsiReferenceExpression) {
        PsiExpression qualifier = ((PsiReferenceExpression)myPosition.getParent()).getQualifierExpression();
        PsiClass qualifierClass = PsiUtil.resolveClassInClassTypeOnly(qualifier == null ? null : qualifier.getType());
        return qualifierClass != null &&
               ContainerUtil.exists(qualifierClass.getAllInnerClasses(), inner -> canBeCreatedInQualifiedNew(qualifierClass, inner));
      }
      return false;
    }

    private boolean canBeCreatedInQualifiedNew(PsiClass outer, PsiClass inner) {
      PsiMethod[] constructors = inner.getConstructors();
      return !inner.hasModifierProperty(PsiModifier.STATIC) &&
             PsiUtil.isAccessible(inner, myPosition, outer) &&
             (constructors.length == 0 || ContainerUtil.exists(constructors, c -> PsiUtil.isAccessible(c, myPosition, outer)));
    }

    private boolean mayExpectBoolean() {
      for (ExpectedTypeInfo info : JavaSmartCompletionContributor.getExpectedTypes(myPosition, myContext.isSmart())) {
        PsiType type = info.getType();
        if (type instanceof PsiClassType || PsiTypes.booleanType().equals(type)) return true;
      }
      return false;
    }

    @Contract(pure = true)
    private CommonCompletionItem createKeyword(@NlsSafe String keyword) {
      return createKeyword(keyword, noneType());
    }

    @Contract(pure = true)
    private CommonCompletionItem createKeyword(@NlsSafe String keyword, ModNavigatorTailType tailType) {
      return new CommonCompletionItem(keyword)
        .withObject(JavaPsiFacade.getElementFactory(myFile.getProject()).createKeyword(keyword, myPosition))
        .withPresentation(MarkupText.builder().append(keyword, STRONG).build())
        .withAutoCompletionPolicy(AutoCompletionPolicy.GIVE_CHANCE_TO_OVERWRITE)
        .withTail(tailType);
    }
  }

  private static boolean isStatementPosition(PsiElement position) {
    if (psiElement()
      .withSuperParent(2, PsiConditionalExpression.class)
      .andNot(psiElement().insideStarting(psiElement(PsiConditionalExpression.class)))
      .accepts(position)) {
      return false;
    }

    if (isEndOfBlock(position) &&
        PsiTreeUtil.getParentOfType(position, PsiCodeBlock.class, true, PsiMember.class) != null) {
      return !isForLoopMachinery(position);
    }

    if (psiElement().withParents(PsiReferenceExpression.class, PsiExpressionStatement.class, PsiIfStatement.class).andNot(
      psiElement().afterLeaf(".")).accepts(position)) {
      PsiElement stmt = position.getParent().getParent();
      PsiIfStatement ifStatement = (PsiIfStatement)stmt.getParent();
      return ifStatement.getElseBranch() == stmt || ifStatement.getThenBranch() == stmt;
    }

    return false;
  }

  private static boolean isEndOfBlock(PsiElement element) {
    PsiElement prev = PsiTreeUtil.prevCodeLeaf(element);
    if (prev == null) {
      PsiFile file = element.getContainingFile();
      return !(file instanceof PsiCodeFragment) || isStatementCodeFragment(file);
    }

    if (psiElement().inside(psiAnnotation()).accepts(prev)) return false;

    if (prev instanceof OuterLanguageElement) return true;
    if (psiElement().withText(string().oneOf("{", "}", ";", ":", "else")).accepts(prev)) return true;
    if (prev.textMatches(")")) {
      PsiElement parent = prev.getParent();
      if (parent instanceof PsiParameterList) {
        return PsiTreeUtil.getParentOfType(PsiTreeUtil.prevVisibleLeaf(element), PsiDocComment.class) != null;
      }

      return !(parent instanceof PsiExpressionList || parent instanceof PsiTypeCastExpression
               || parent instanceof PsiRecordHeader);
    }

    return false;
  }

  private static boolean isStatementCodeFragment(PsiFile file) {
    return file instanceof JavaCodeFragment &&
           !(file instanceof PsiExpressionCodeFragment ||
             file instanceof PsiJavaCodeReferenceCodeFragment ||
             file instanceof PsiTypeCodeFragment);
  }

  private static boolean isForLoopMachinery(PsiElement myPosition) {
    PsiStatement statement = PsiTreeUtil.getParentOfType(myPosition, PsiStatement.class);
    if (statement == null) return false;

    return statement instanceof PsiForStatement ||
           statement.getParent() instanceof PsiForStatement && statement != ((PsiForStatement)statement.getParent()).getBody();
  }

  private static @Nullable PsiSwitchLabeledRuleStatement findEnclosingSwitchRule(PsiElement position) {
    PsiElement parent = position.getParent();
    return parent.getParent() instanceof PsiExpressionStatement stmt &&
           stmt.getParent() instanceof PsiSwitchLabeledRuleStatement rule ? rule : null;
  }

  private static boolean superConstructorHasParameters(PsiMethod method) {
    final PsiClass psiClass = method.getContainingClass();
    if (psiClass == null) {
      return false;
    }

    final PsiClass superClass = psiClass.getSuperClass();
    if (superClass != null) {
      for (final PsiMethod psiMethod : superClass.getConstructors()) {
        final PsiResolveHelper resolveHelper = JavaPsiFacade.getInstance(method.getProject()).getResolveHelper();
        if (resolveHelper.isAccessible(psiMethod, method, null) && !psiMethod.getParameterList().isEmpty()) {
          return true;
        }
      }
    }
    return false;
  }

  private static boolean isExpressionPosition(PsiElement position) {
    if (psiElement().insideStarting(psiElement(PsiClassObjectAccessExpression.class)).accepts(position)) return true;

    PsiElement parent = position.getParent();
    if (!(parent instanceof PsiReferenceExpression) ||
        ((PsiReferenceExpression)parent).isQualified() ||
        JavaCompletionContributor.IN_SWITCH_LABEL.accepts(position)) {
      return false;
    }
    if (parent.getParent() instanceof PsiExpressionStatement) {
      PsiElement previous = PsiTreeUtil.skipWhitespacesBackward(parent.getParent());
      return previous == null || previous.getLastChild() == null ||
             !(PsiTreeUtil.getDeepestLast(previous.getLastChild()) instanceof PsiErrorElement);
    }
    return true;
  }

  private static @Nullable PsiJavaFile getFileForDeclaration(PsiElement elementBeforeName) {
    PsiElement parent = elementBeforeName.getParent();
    if (parent == null) return null;
    PsiElement grandParent = parent.getParent();
    if (grandParent == null) return null;
    if (grandParent instanceof PsiJavaFile f) {
      return f;
    }
    PsiElement grandGrandParent = grandParent.getParent();
    if (grandGrandParent == null) return null;
    return ObjectUtils.tryCast(grandGrandParent.getParent(), PsiJavaFile.class);
  }
  
  private static boolean isSuitableForClass(PsiElement position) {
    if (psiElement().afterLeaf("@").accepts(position) ||
        PsiTreeUtil.getNonStrictParentOfType(position, PsiLiteralExpression.class, PsiComment.class, PsiExpressionCodeFragment.class) !=
        null) {
      return false;
    }

    PsiElement prev = PsiTreeUtil.prevCodeLeaf(position);
    if (prev == null) {
      return true;
    }
    if (psiElement().withoutText(".").inside(
      psiElement(PsiModifierList.class).withParent(
        not(psiElement(PsiParameter.class)).andNot(psiElement(PsiParameterList.class)))).accepts(prev) &&
        (!psiElement().inside(PsiAnnotationParameterList.class).accepts(prev) || prev.textMatches(")"))) {
      return true;
    }

    if (psiElement().withParents(PsiErrorElement.class, PsiFile.class).accepts(position)) {
      return true;
    }

    return isEndOfBlock(position);
  }

  private static boolean expectsClassLiteral(PsiElement position) {
    return ContainerUtil.find(JavaSmartCompletionContributor.getExpectedTypes(position, false),
                              info -> InheritanceUtil.isInheritor(info.getType(), CommonClassNames.JAVA_LANG_CLASS)) != null;
  }

  private static boolean isDeclarationStart(PsiElement position) {
    if (PsiJavaPatterns.psiElement().afterLeaf("@", ".").accepts(position)) return false;

    PsiElement parent = position.getParent();
    if (parent instanceof PsiJavaCodeReferenceElement && parent.getParent() instanceof PsiTypeElement) {
      PsiElement typeHolder = psiApi().parents(parent.getParent()).skipWhile(Conditions.instanceOf(PsiTypeElement.class)).first();
      return typeHolder instanceof PsiMember || typeHolder instanceof PsiClassLevelDeclarationStatement ||
             (typeHolder instanceof PsiJavaFile javaFile &&
              PsiUtil.isAvailable(JavaFeature.IMPLICIT_CLASSES, position) &&
              javaFile.getPackageStatement() == null);
    }

    return false;
  }

  private static boolean isVariableTypePosition(PsiElement position) {
    PsiElement parent = position.getParent();
    if (parent instanceof PsiJavaCodeReferenceElement && parent.getParent() instanceof PsiTypeElement &&
        parent.getParent().getParent() instanceof PsiDeclarationStatement) {
      return true;
    }
    return START_FOR.accepts(position) ||
           JavaKeywordCompletion.isInsideParameterList(position) ||
           INSIDE_RECORD_HEADER.accepts(position) ||
           VARIABLE_AFTER_FINAL.accepts(position) ||
           isStatementPosition(position);
  }

  private static boolean primitivesAreExpected(@Nullable PsiElement position) {
    if (position == null) return false;
    PsiElement parent = position.getParent();
    //example: stream.map(i-> i <caret>)
    if (parent.getParent() instanceof PsiExpressionList) {
      PsiElement previous = PsiTreeUtil.prevVisibleLeaf(parent);
      if (previous != null) {
        PsiExpression expression = PsiTreeUtil.getParentOfType(previous, PsiExpression.class, true);
        if (expression != null && !PsiTreeUtil.isAncestor(expression, parent, true)) {
          return false;
        }
      }
    }
    return true;
  }

  private static boolean isLambdaParameterType(PsiElement position) {
    PsiParameterList paramList = PsiTreeUtil.getParentOfType(position, PsiParameterList.class);
    if (paramList != null && paramList.getParent() instanceof PsiLambdaExpression) {
      PsiParameter param = PsiTreeUtil.getParentOfType(position, PsiParameter.class);
      PsiTypeElement type = param == null ? null : param.getTypeElement();
      return type == null || PsiTreeUtil.isAncestor(type, position, false);
    }
    return false;
  }
}
