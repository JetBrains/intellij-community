// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.source.tree;

import com.intellij.java.syntax.element.JavaSyntaxTokenType;
import com.intellij.java.syntax.lexer.JavaLexer;
import com.intellij.java.syntax.parser.JavaParser;
import com.intellij.java.syntax.parser.ReferenceParser;
import com.intellij.lang.ASTNode;
import com.intellij.lang.Language;
import com.intellij.lang.LighterASTNode;
import com.intellij.lang.LighterLazyParseableNode;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.lang.java.parser.JavaParserUtil;
import com.intellij.lang.java.parser.PsiSyntaxBuilderWithLanguageLevel;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.platform.syntax.lexer.Lexer;
import com.intellij.platform.syntax.lexer.TokenList;
import com.intellij.platform.syntax.parser.SyntaxTreeBuilder;
import com.intellij.platform.syntax.psi.ParsingDiagnostics;
import com.intellij.platform.syntax.psi.PsiSyntaxBuilder;
import com.intellij.platform.syntax.util.parser.SyntaxBuilderUtil;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.java.stubs.JavaStubElementTypes;
import com.intellij.psi.impl.source.JavaDummyElement;
import com.intellij.psi.impl.source.PsiDiamondTypeElementImpl;
import com.intellij.psi.impl.source.PsiImportStaticReferenceElementImpl;
import com.intellij.psi.impl.source.PsiJavaCodeReferenceElementImpl;
import com.intellij.psi.impl.source.PsiJavaModuleReferenceElementImpl;
import com.intellij.psi.impl.source.PsiReceiverParameterImpl;
import com.intellij.psi.impl.source.PsiTypeElementImpl;
import com.intellij.psi.impl.source.tree.java.PsiArrayAccessExpressionImpl;
import com.intellij.psi.impl.source.tree.java.PsiArrayInitializerExpressionImpl;
import com.intellij.psi.impl.source.tree.java.PsiArrayInitializerMemberValueImpl;
import com.intellij.psi.impl.source.tree.java.PsiAssertStatementImpl;
import com.intellij.psi.impl.source.tree.java.PsiAssignmentExpressionImpl;
import com.intellij.psi.impl.source.tree.java.PsiBinaryExpressionImpl;
import com.intellij.psi.impl.source.tree.java.PsiBlockStatementImpl;
import com.intellij.psi.impl.source.tree.java.PsiBreakStatementImpl;
import com.intellij.psi.impl.source.tree.java.PsiCaseLabelElementListImpl;
import com.intellij.psi.impl.source.tree.java.PsiCatchSectionImpl;
import com.intellij.psi.impl.source.tree.java.PsiClassObjectAccessExpressionImpl;
import com.intellij.psi.impl.source.tree.java.PsiCodeBlockImpl;
import com.intellij.psi.impl.source.tree.java.PsiConditionalExpressionImpl;
import com.intellij.psi.impl.source.tree.java.PsiContinueStatementImpl;
import com.intellij.psi.impl.source.tree.java.PsiDeclarationStatementImpl;
import com.intellij.psi.impl.source.tree.java.PsiDeconstructionListImpl;
import com.intellij.psi.impl.source.tree.java.PsiDeconstructionPatternImpl;
import com.intellij.psi.impl.source.tree.java.PsiDeconstructionPatternVariableImpl;
import com.intellij.psi.impl.source.tree.java.PsiDefaultLabelElementImpl;
import com.intellij.psi.impl.source.tree.java.PsiDoWhileStatementImpl;
import com.intellij.psi.impl.source.tree.java.PsiEmptyExpressionImpl;
import com.intellij.psi.impl.source.tree.java.PsiEmptyStatementImpl;
import com.intellij.psi.impl.source.tree.java.PsiExpressionListImpl;
import com.intellij.psi.impl.source.tree.java.PsiExpressionListStatementImpl;
import com.intellij.psi.impl.source.tree.java.PsiExpressionStatementImpl;
import com.intellij.psi.impl.source.tree.java.PsiForStatementImpl;
import com.intellij.psi.impl.source.tree.java.PsiForeachPatternStatementImpl;
import com.intellij.psi.impl.source.tree.java.PsiForeachStatementImpl;
import com.intellij.psi.impl.source.tree.java.PsiIfStatementImpl;
import com.intellij.psi.impl.source.tree.java.PsiInstanceOfExpressionImpl;
import com.intellij.psi.impl.source.tree.java.PsiLabeledStatementImpl;
import com.intellij.psi.impl.source.tree.java.PsiLocalVariableImpl;
import com.intellij.psi.impl.source.tree.java.PsiMethodCallExpressionImpl;
import com.intellij.psi.impl.source.tree.java.PsiNewExpressionImpl;
import com.intellij.psi.impl.source.tree.java.PsiParenthesizedExpressionImpl;
import com.intellij.psi.impl.source.tree.java.PsiPatternVariableImpl;
import com.intellij.psi.impl.source.tree.java.PsiPolyadicExpressionImpl;
import com.intellij.psi.impl.source.tree.java.PsiPostfixExpressionImpl;
import com.intellij.psi.impl.source.tree.java.PsiPrefixExpressionImpl;
import com.intellij.psi.impl.source.tree.java.PsiReferenceExpressionImpl;
import com.intellij.psi.impl.source.tree.java.PsiReferenceParameterListImpl;
import com.intellij.psi.impl.source.tree.java.PsiResourceExpressionImpl;
import com.intellij.psi.impl.source.tree.java.PsiResourceListImpl;
import com.intellij.psi.impl.source.tree.java.PsiResourceVariableImpl;
import com.intellij.psi.impl.source.tree.java.PsiReturnStatementImpl;
import com.intellij.psi.impl.source.tree.java.PsiSuperExpressionImpl;
import com.intellij.psi.impl.source.tree.java.PsiSwitchExpressionImpl;
import com.intellij.psi.impl.source.tree.java.PsiSwitchLabelStatementImpl;
import com.intellij.psi.impl.source.tree.java.PsiSwitchLabeledRuleStatementImpl;
import com.intellij.psi.impl.source.tree.java.PsiSwitchStatementImpl;
import com.intellij.psi.impl.source.tree.java.PsiSynchronizedStatementImpl;
import com.intellij.psi.impl.source.tree.java.PsiTemplateExpressionImpl;
import com.intellij.psi.impl.source.tree.java.PsiTemplateImpl;
import com.intellij.psi.impl.source.tree.java.PsiThisExpressionImpl;
import com.intellij.psi.impl.source.tree.java.PsiThrowStatementImpl;
import com.intellij.psi.impl.source.tree.java.PsiTryStatementImpl;
import com.intellij.psi.impl.source.tree.java.PsiTypeCastExpressionImpl;
import com.intellij.psi.impl.source.tree.java.PsiTypeTestPatternImpl;
import com.intellij.psi.impl.source.tree.java.PsiUnnamedPatternImpl;
import com.intellij.psi.impl.source.tree.java.PsiWhileStatementImpl;
import com.intellij.psi.impl.source.tree.java.PsiYieldStatementImpl;
import com.intellij.psi.tree.ICompositeElementType;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.IErrorCounterReparseableElementType;
import com.intellij.psi.tree.ILazyParseableElementType;
import com.intellij.psi.tree.ILightLazyParseableElementType;
import com.intellij.psi.tree.java.IJavaElementType;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.diff.FlyweightCapableTreeStructure;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Function;
import java.util.function.Supplier;

/**
 * @see com.intellij.java.syntax.element.JavaSyntaxElementType
 */
public interface JavaElementType {
  class JavaCompositeElementType extends IJavaElementType implements ICompositeElementType {
    private final Supplier<? extends ASTNode> myConstructor;

    public JavaCompositeElementType(@NonNls @NotNull String debugName,
                                    @NotNull Supplier<? extends ASTNode> constructor) {
      this(debugName, constructor, false);
    }

    public JavaCompositeElementType(@NonNls @NotNull String debugName,
                                    @NotNull Supplier<? extends ASTNode> constructor,
                                    boolean leftBound) {
      super(debugName, leftBound);
      myConstructor = constructor;
    }

    @Override
    public @NotNull ASTNode createCompositeNode() {
      return myConstructor.get();
    }
  }

  IElementType CLASS = JavaStubElementTypes.CLASS;
  IElementType IMPLICIT_CLASS = JavaStubElementTypes.IMPLICIT_CLASS;
  IElementType ANONYMOUS_CLASS = JavaStubElementTypes.ANONYMOUS_CLASS;
  IElementType ENUM_CONSTANT_INITIALIZER = JavaStubElementTypes.ENUM_CONSTANT_INITIALIZER;
  IElementType TYPE_PARAMETER_LIST = JavaStubElementTypes.TYPE_PARAMETER_LIST;
  IElementType TYPE_PARAMETER = JavaStubElementTypes.TYPE_PARAMETER;
  IElementType IMPORT_LIST = JavaStubElementTypes.IMPORT_LIST;
  IElementType IMPORT_STATEMENT = JavaStubElementTypes.IMPORT_STATEMENT;
  IElementType IMPORT_STATIC_STATEMENT = JavaStubElementTypes.IMPORT_STATIC_STATEMENT;
  IElementType IMPORT_MODULE_STATEMENT = JavaStubElementTypes.IMPORT_MODULE_STATEMENT;
  IElementType MODIFIER_LIST = JavaStubElementTypes.MODIFIER_LIST;
  IElementType ANNOTATION = JavaStubElementTypes.ANNOTATION;
  IElementType NAME_VALUE_PAIR = JavaStubElementTypes.NAME_VALUE_PAIR;
  IElementType LITERAL_EXPRESSION = JavaStubElementTypes.LITERAL_EXPRESSION;
  IElementType ANNOTATION_PARAMETER_LIST = JavaStubElementTypes.ANNOTATION_PARAMETER_LIST;
  IElementType EXTENDS_LIST = JavaStubElementTypes.EXTENDS_LIST;
  IElementType IMPLEMENTS_LIST = JavaStubElementTypes.IMPLEMENTS_LIST;
  IElementType FIELD = JavaStubElementTypes.FIELD;
  IElementType ENUM_CONSTANT = JavaStubElementTypes.ENUM_CONSTANT;
  IElementType METHOD = JavaStubElementTypes.METHOD;
  IElementType ANNOTATION_METHOD = JavaStubElementTypes.ANNOTATION_METHOD;
  IElementType CLASS_INITIALIZER = JavaStubElementTypes.CLASS_INITIALIZER;
  IElementType PARAMETER = JavaStubElementTypes.PARAMETER;
  IElementType PARAMETER_LIST = JavaStubElementTypes.PARAMETER_LIST;
  IElementType EXTENDS_BOUND_LIST = JavaStubElementTypes.EXTENDS_BOUND_LIST;
  IElementType THROWS_LIST = JavaStubElementTypes.THROWS_LIST;
  IElementType LAMBDA_EXPRESSION = JavaStubElementTypes.LAMBDA_EXPRESSION;
  IElementType METHOD_REF_EXPRESSION = JavaStubElementTypes.METHOD_REF_EXPRESSION;
  IElementType MODULE = JavaStubElementTypes.MODULE;
  IElementType REQUIRES_STATEMENT = JavaStubElementTypes.REQUIRES_STATEMENT;
  IElementType EXPORTS_STATEMENT = JavaStubElementTypes.EXPORTS_STATEMENT;
  IElementType OPENS_STATEMENT = JavaStubElementTypes.OPENS_STATEMENT;
  IElementType USES_STATEMENT = JavaStubElementTypes.USES_STATEMENT;
  IElementType PROVIDES_STATEMENT = JavaStubElementTypes.PROVIDES_STATEMENT;
  IElementType PROVIDES_WITH_LIST = JavaStubElementTypes.PROVIDES_WITH_LIST;
  IElementType RECORD_COMPONENT = JavaStubElementTypes.RECORD_COMPONENT;
  IElementType RECORD_HEADER = JavaStubElementTypes.RECORD_HEADER;
  IElementType PERMITS_LIST = JavaStubElementTypes.PERMITS_LIST;
  IElementType PACKAGE_STATEMENT = JavaStubElementTypes.PACKAGE_STATEMENT;

  IElementType IMPORT_STATIC_REFERENCE =
    new JavaCompositeElementType("IMPORT_STATIC_REFERENCE", () -> new PsiImportStaticReferenceElementImpl());
  IElementType TYPE = new JavaCompositeElementType("TYPE", () -> new PsiTypeElementImpl());
  IElementType DIAMOND_TYPE =
    new JavaCompositeElementType("DIAMOND_TYPE", () -> new PsiDiamondTypeElementImpl());
  IElementType REFERENCE_PARAMETER_LIST =
    new JavaCompositeElementType("REFERENCE_PARAMETER_LIST", () -> new PsiReferenceParameterListImpl(), true);
  IElementType JAVA_CODE_REFERENCE = new JavaCompositeElementType("JAVA_CODE_REFERENCE", () -> new PsiJavaCodeReferenceElementImpl());
  IElementType LOCAL_VARIABLE = new JavaCompositeElementType("LOCAL_VARIABLE", () -> new PsiLocalVariableImpl());
  IElementType REFERENCE_EXPRESSION = new JavaCompositeElementType("REFERENCE_EXPRESSION", () -> new PsiReferenceExpressionImpl());
  IElementType THIS_EXPRESSION = new JavaCompositeElementType("THIS_EXPRESSION", () -> new PsiThisExpressionImpl());
  IElementType SUPER_EXPRESSION = new JavaCompositeElementType("SUPER_EXPRESSION", () -> new PsiSuperExpressionImpl());
  IElementType PARENTH_EXPRESSION = new JavaCompositeElementType("PARENTH_EXPRESSION", () -> new PsiParenthesizedExpressionImpl());
  IElementType METHOD_CALL_EXPRESSION = new JavaCompositeElementType("METHOD_CALL_EXPRESSION", () -> new PsiMethodCallExpressionImpl());
  IElementType TYPE_CAST_EXPRESSION = new JavaCompositeElementType("TYPE_CAST_EXPRESSION", () -> new PsiTypeCastExpressionImpl());
  IElementType PREFIX_EXPRESSION = new JavaCompositeElementType("PREFIX_EXPRESSION", () -> new PsiPrefixExpressionImpl());
  IElementType POSTFIX_EXPRESSION = new JavaCompositeElementType("POSTFIX_EXPRESSION", () -> new PsiPostfixExpressionImpl());
  IElementType BINARY_EXPRESSION = new JavaCompositeElementType("BINARY_EXPRESSION", () -> new PsiBinaryExpressionImpl());
  IElementType POLYADIC_EXPRESSION = new JavaCompositeElementType("POLYADIC_EXPRESSION", () -> new PsiPolyadicExpressionImpl());
  IElementType CONDITIONAL_EXPRESSION = new JavaCompositeElementType("CONDITIONAL_EXPRESSION", () -> new PsiConditionalExpressionImpl());
  IElementType ASSIGNMENT_EXPRESSION = new JavaCompositeElementType("ASSIGNMENT_EXPRESSION", () -> new PsiAssignmentExpressionImpl());
  IElementType NEW_EXPRESSION = new JavaCompositeElementType("NEW_EXPRESSION", () -> new PsiNewExpressionImpl());
  IElementType ARRAY_ACCESS_EXPRESSION = new JavaCompositeElementType("ARRAY_ACCESS_EXPRESSION", () -> new PsiArrayAccessExpressionImpl());
  IElementType ARRAY_INITIALIZER_EXPRESSION =
    new JavaCompositeElementType("ARRAY_INITIALIZER_EXPRESSION", () -> new PsiArrayInitializerExpressionImpl());
  IElementType INSTANCE_OF_EXPRESSION = new JavaCompositeElementType("INSTANCE_OF_EXPRESSION", () -> new PsiInstanceOfExpressionImpl());
  IElementType CLASS_OBJECT_ACCESS_EXPRESSION =
    new JavaCompositeElementType("CLASS_OBJECT_ACCESS_EXPRESSION", () -> new PsiClassObjectAccessExpressionImpl());
  IElementType TEMPLATE_EXPRESSION = new JavaCompositeElementType("TEMPLATE_EXPRESSION", () -> new PsiTemplateExpressionImpl());
  IElementType TEMPLATE = new JavaCompositeElementType("TEMPLATE", () -> new PsiTemplateImpl());
  IElementType EMPTY_EXPRESSION = new JavaCompositeElementType("EMPTY_EXPRESSION", () -> new PsiEmptyExpressionImpl(), true);
  IElementType EXPRESSION_LIST = new JavaCompositeElementType("EXPRESSION_LIST", () -> new PsiExpressionListImpl(), true);
  IElementType EMPTY_STATEMENT = new JavaCompositeElementType("EMPTY_STATEMENT", () -> new PsiEmptyStatementImpl());
  IElementType BLOCK_STATEMENT = new JavaCompositeElementType("BLOCK_STATEMENT", () -> new PsiBlockStatementImpl());
  IElementType EXPRESSION_STATEMENT = new JavaCompositeElementType("EXPRESSION_STATEMENT", () -> new PsiExpressionStatementImpl());
  IElementType EXPRESSION_LIST_STATEMENT =
    new JavaCompositeElementType("EXPRESSION_LIST_STATEMENT", () -> new PsiExpressionListStatementImpl());
  IElementType DECLARATION_STATEMENT = new JavaCompositeElementType("DECLARATION_STATEMENT", () -> new PsiDeclarationStatementImpl());
  IElementType IF_STATEMENT = new JavaCompositeElementType("IF_STATEMENT", () -> new PsiIfStatementImpl());
  IElementType WHILE_STATEMENT = new JavaCompositeElementType("WHILE_STATEMENT", () -> new PsiWhileStatementImpl());
  IElementType FOR_STATEMENT = new JavaCompositeElementType("FOR_STATEMENT", () -> new PsiForStatementImpl());
  IElementType FOREACH_STATEMENT = new JavaCompositeElementType("FOREACH_STATEMENT", () -> new PsiForeachStatementImpl());
  IElementType FOREACH_PATTERN_STATEMENT =
    new JavaCompositeElementType("FOREACH_PATTERN_STATEMENT", () -> new PsiForeachPatternStatementImpl());
  IElementType DO_WHILE_STATEMENT = new JavaCompositeElementType("DO_WHILE_STATEMENT", () -> new PsiDoWhileStatementImpl());
  IElementType SWITCH_STATEMENT = new JavaCompositeElementType("SWITCH_STATEMENT", () -> new PsiSwitchStatementImpl());
  IElementType SWITCH_EXPRESSION = new JavaCompositeElementType("SWITCH_EXPRESSION", () -> new PsiSwitchExpressionImpl());
  IElementType SWITCH_LABEL_STATEMENT = new JavaCompositeElementType("SWITCH_LABEL_STATEMENT", () -> new PsiSwitchLabelStatementImpl());
  IElementType SWITCH_LABELED_RULE = new JavaCompositeElementType("SWITCH_LABELED_RULE", () -> new PsiSwitchLabeledRuleStatementImpl());
  IElementType BREAK_STATEMENT = new JavaCompositeElementType("BREAK_STATEMENT", () -> new PsiBreakStatementImpl());
  IElementType YIELD_STATEMENT = new JavaCompositeElementType("YIELD_STATEMENT", () -> new PsiYieldStatementImpl());
  IElementType CONTINUE_STATEMENT = new JavaCompositeElementType("CONTINUE_STATEMENT", () -> new PsiContinueStatementImpl());
  IElementType RETURN_STATEMENT = new JavaCompositeElementType("RETURN_STATEMENT", () -> new PsiReturnStatementImpl());
  IElementType THROW_STATEMENT = new JavaCompositeElementType("THROW_STATEMENT", () -> new PsiThrowStatementImpl());
  IElementType SYNCHRONIZED_STATEMENT = new JavaCompositeElementType("SYNCHRONIZED_STATEMENT", () -> new PsiSynchronizedStatementImpl());
  IElementType TRY_STATEMENT = new JavaCompositeElementType("TRY_STATEMENT", () -> new PsiTryStatementImpl());
  IElementType RESOURCE_LIST = new JavaCompositeElementType("RESOURCE_LIST", () -> new PsiResourceListImpl());
  IElementType RESOURCE_VARIABLE = new JavaCompositeElementType("RESOURCE_VARIABLE", () -> new PsiResourceVariableImpl());
  IElementType RESOURCE_EXPRESSION = new JavaCompositeElementType("RESOURCE_EXPRESSION", () -> new PsiResourceExpressionImpl());
  IElementType CATCH_SECTION = new JavaCompositeElementType("CATCH_SECTION", () -> new PsiCatchSectionImpl());
  IElementType LABELED_STATEMENT = new JavaCompositeElementType("LABELED_STATEMENT", () -> new PsiLabeledStatementImpl());
  IElementType ASSERT_STATEMENT = new JavaCompositeElementType("ASSERT_STATEMENT", () -> new PsiAssertStatementImpl());
  IElementType ANNOTATION_ARRAY_INITIALIZER =
    new JavaCompositeElementType("ANNOTATION_ARRAY_INITIALIZER", () -> new PsiArrayInitializerMemberValueImpl());
  IElementType RECEIVER_PARAMETER = new JavaCompositeElementType("RECEIVER", () -> new PsiReceiverParameterImpl());
  IElementType MODULE_REFERENCE = new JavaCompositeElementType("MODULE_REFERENCE", () -> new PsiJavaModuleReferenceElementImpl());
  IElementType UNNAMED_PATTERN =
    new JavaCompositeElementType("UNNAMED_PATTERN", () -> new PsiUnnamedPatternImpl());
  IElementType TYPE_TEST_PATTERN =
    new JavaCompositeElementType("TYPE_TEST_PATTERN", () -> new PsiTypeTestPatternImpl());
  IElementType PATTERN_VARIABLE =
    new JavaCompositeElementType("PATTERN_VARIABLE", () -> new PsiPatternVariableImpl());
  IElementType DECONSTRUCTION_PATTERN =
    new JavaCompositeElementType("DECONSTRUCTION_PATTERN", () -> new PsiDeconstructionPatternImpl());
  IElementType DECONSTRUCTION_LIST =
    new JavaCompositeElementType("DECONSTRUCTION_LIST", () -> new PsiDeconstructionListImpl());
  IElementType DECONSTRUCTION_PATTERN_VARIABLE =
    new JavaCompositeElementType("DECONSTRUCTION_PATTERN_VARIABLE", () -> new PsiDeconstructionPatternVariableImpl());
  IElementType DEFAULT_CASE_LABEL_ELEMENT =
    new JavaCompositeElementType("DEFAULT_CASE_LABEL_ELEMENT", () -> new PsiDefaultLabelElementImpl());
  IElementType CASE_LABEL_ELEMENT_LIST =
    new JavaCompositeElementType("CASE_LABEL_ELEMENT_LIST", () -> new PsiCaseLabelElementListImpl());

  ILazyParseableElementType CODE_BLOCK = new FrontBackICodeBlockElementType(PsiUtil::getLanguageLevel,
                                                                            JavaParserUtil::obtainTokens) {
    @Override
    public ASTNode createNode(final CharSequence text) {
      return new PsiCodeBlockImpl(text);
    }

    @Override
    public @NotNull ASTNode createCompositeNode() {
      return new PsiCodeBlockImpl(null);
    }
  };

  IElementType MEMBERS = new MemberThinCodeFragmentElementType();
  IElementType STATEMENTS = new StatementThinCodeFragmentElementType();
  IElementType EXPRESSION_TEXT = new ExpressionThinCodeFragmentElementType();
  IElementType REFERENCE_TEXT = new ReferenceThinCodeFragmentElementType();
  IElementType TYPE_WITH_DISJUNCTIONS_TEXT = new FrontBackTypeTextElementType("TYPE_WITH_DISJUNCTIONS_TEXT",
                                                                              ReferenceParser.DISJUNCTIONS);
  IElementType TYPE_WITH_CONJUNCTIONS_TEXT = new FrontBackTypeTextElementType("TYPE_WITH_CONJUNCTIONS_TEXT",
                                                                              ReferenceParser.DISJUNCTIONS);
  IElementType DUMMY_ELEMENT = new JavaDummyElementType();

  @ApiStatus.Internal
  final class JavaDummyElementType extends ILazyParseableElementType implements ICompositeElementType {

    public JavaDummyElementType() {
      super("DUMMY_ELEMENT", JavaLanguage.INSTANCE);
    }

    @Override
    public @NotNull ASTNode createCompositeNode() {
      return new CompositePsiElement(this) {
      };
    }

    @Override
    public @Nullable ASTNode parseContents(final @NotNull ASTNode chameleon) {
      assert chameleon instanceof JavaDummyElement : chameleon;
      final JavaDummyElement dummyElement = (JavaDummyElement)chameleon;
      return JavaParserUtil.parseFragment(chameleon, dummyElement.getParser(), dummyElement.consumeAll(),
                                               dummyElement.getLanguageLevel()
      );
    }
  }


  abstract class FrontBackICodeBlockElementType extends IErrorCounterReparseableElementType
    implements ICompositeElementType, ILightLazyParseableElementType {
    private final Function<PsiElement, LanguageLevel> languageLevelFunction;
    private final Function<PsiFile, TokenList> psiAsLexer;

    public FrontBackICodeBlockElementType(@NotNull Function<PsiElement, LanguageLevel> function,
                                          @NotNull Function<PsiFile, TokenList> lexer) {
      super("CODE_BLOCK", JavaLanguage.INSTANCE);
      languageLevelFunction = function;
      psiAsLexer = lexer;
    }

    @Override
    public ASTNode parseContents(final @NotNull ASTNode chameleon) {
      PsiSyntaxBuilderWithLanguageLevel
        builderAndLevel = JavaParserUtil.createSyntaxBuilder(chameleon, languageLevelFunction, psiAsLexer);
      PsiSyntaxBuilder psiSyntaxBuilder = builderAndLevel.getBuilder();
      LanguageLevel level = builderAndLevel.getLanguageLevel();
      long startTime = System.nanoTime();
      SyntaxTreeBuilder builder = psiSyntaxBuilder.getSyntaxTreeBuilder();
      new JavaParser(level).getStatementParser().parseCodeBlockDeep(builder, true);
      ASTNode node = psiSyntaxBuilder.getTreeBuilt().getFirstChildNode();
      ParsingDiagnostics.registerParse(builder, getLanguage(), System.nanoTime() - startTime);
      return node;
    }

    @Override
    public @NotNull FlyweightCapableTreeStructure<LighterASTNode> parseContents(final @NotNull LighterLazyParseableNode chameleon) {
      PsiSyntaxBuilderWithLanguageLevel builderAndLevel = JavaParserUtil.createSyntaxBuilder(chameleon, languageLevelFunction);
      PsiSyntaxBuilder psiSyntaxBuilder = builderAndLevel.getBuilder();
      LanguageLevel level = builderAndLevel.getLanguageLevel();
      long startTime = System.nanoTime();
      SyntaxTreeBuilder builder = psiSyntaxBuilder.getSyntaxTreeBuilder();
      new JavaParser(level).getStatementParser().parseCodeBlockDeep(builder, true);
      FlyweightCapableTreeStructure<LighterASTNode> tree = psiSyntaxBuilder.getLightTree();
      ParsingDiagnostics.registerParse(builder, getLanguage(), System.nanoTime() - startTime);
      return tree;
    }

    @Override
    public int getErrorsCount(final CharSequence seq, Language fileLanguage, final Project project) {
      Lexer lexer = new JavaLexer(LanguageLevel.HIGHEST);
      boolean hasProperBraceBalance = SyntaxBuilderUtil.hasProperBraceBalance(
        seq, lexer, JavaSyntaxTokenType.LBRACE, JavaSyntaxTokenType.RBRACE,
        () -> ProgressManager.checkCanceled()
      );
      return hasProperBraceBalance ? NO_ERRORS : FATAL_ERROR;
    }

    @Override
    public boolean reuseCollapsedTokens() {
      return true;
    }
  }

  class FrontBackTypeTextElementType extends ICodeFragmentElementType {
    private final int myFlags;

    public FrontBackTypeTextElementType(@NonNls String debugName, int flags) {
      super(debugName, JavaLanguage.INSTANCE);
      myFlags = flags;
    }

    private final JavaParserUtil.ParserWrapper myParser = new JavaParserUtil.ParserWrapper() {
      @Override
      public void parse(@NotNull SyntaxTreeBuilder builder, @NotNull LanguageLevel languageLevel) {
        int flags = ReferenceParser.EAT_LAST_DOT | ReferenceParser.ELLIPSIS | ReferenceParser.WILDCARD | myFlags;
        new JavaParser(languageLevel).getReferenceParser().parseType(builder, flags);
      }
    };

    @Override
    public @Nullable ASTNode parseContents(final @NotNull ASTNode chameleon) {
      return JavaParserUtil.parseFragmentWithHighestLanguageLevel(chameleon, myParser, JavaLexer::new);
    }
  }

  class StatementThinCodeFragmentElementType extends JavaElementType.ThinCodeFragmentElementType {
    public StatementThinCodeFragmentElementType() {
      super("STATEMENTS", (builder, languageLevel) -> {
        new JavaParser(languageLevel).getStatementParser().parseStatements(builder);
      });
    }
  }

  class MemberThinCodeFragmentElementType extends JavaElementType.ThinCodeFragmentElementType {
    public MemberThinCodeFragmentElementType() {
      super("MEMBERS", (builder, languageLevel) -> {
        new JavaParser(languageLevel).getDeclarationParser().parseClassBodyDeclarations(builder, false);
      });
    }
  }

  class ExpressionThinCodeFragmentElementType extends JavaElementType.ThinCodeFragmentElementType {
    public ExpressionThinCodeFragmentElementType() {
      super("EXPRESSION_TEXT", (builder, languageLevel) -> {
        new JavaParser(languageLevel).getExpressionParser().parse(builder);
      });
    }
  }

  class ReferenceThinCodeFragmentElementType extends JavaElementType.ThinCodeFragmentElementType {
    public ReferenceThinCodeFragmentElementType() {
      super("REFERENCE_TEXT", (builder, languageLevel) -> {
        new JavaParser(languageLevel).getReferenceParser().parseJavaCodeReference(builder, false, true, false, false);
      });
    }
  }

  class ThinCodeFragmentElementType extends ICodeFragmentElementType {
    private final JavaParserUtil.ParserWrapper myParser;

    private ThinCodeFragmentElementType(@NotNull String debugName,
                                        @NotNull JavaParserUtil.ParserWrapper parser) {
      super(debugName, JavaLanguage.INSTANCE);
      myParser = parser;
    }

    @Override
    public @Nullable ASTNode parseContents(final @NotNull ASTNode chameleon) {
      return JavaParserUtil.parseFragmentWithHighestLanguageLevel(chameleon, myParser, JavaLexer::new);
    }
  }
}