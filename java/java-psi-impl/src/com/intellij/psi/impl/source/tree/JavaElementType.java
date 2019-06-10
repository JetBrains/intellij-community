// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.source.tree;

import com.intellij.lang.*;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.lang.java.JavaParserDefinition;
import com.intellij.lang.java.parser.JavaParser;
import com.intellij.lang.java.parser.JavaParserUtil;
import com.intellij.lang.java.parser.ReferenceParser;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.project.Project;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.impl.java.stubs.JavaStubElementTypes;
import com.intellij.psi.impl.source.*;
import com.intellij.psi.impl.source.tree.java.*;
import com.intellij.psi.tree.*;
import com.intellij.psi.tree.java.IJavaElementType;
import com.intellij.util.diff.FlyweightCapableTreeStructure;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;

public interface JavaElementType {
  class JavaCompositeElementType extends IJavaElementType implements ICompositeElementType {
    private final Supplier<? extends ASTNode> myConstructor;

    private JavaCompositeElementType(@NonNls final String debugName, @NotNull Supplier<? extends ASTNode> constructor) {
      this(debugName, constructor, false);
    }

    private JavaCompositeElementType(@NonNls final String debugName, Supplier<? extends ASTNode> constructor, final boolean leftBound) {
      super(debugName, leftBound);
      myConstructor = constructor;
    }

    @NotNull
    @Override
    public ASTNode createCompositeNode() {
      return myConstructor.get();
    }
  }

  IElementType CLASS = JavaStubElementTypes.CLASS;
  IElementType ANONYMOUS_CLASS = JavaStubElementTypes.ANONYMOUS_CLASS;
  IElementType ENUM_CONSTANT_INITIALIZER = JavaStubElementTypes.ENUM_CONSTANT_INITIALIZER;
  IElementType TYPE_PARAMETER_LIST = JavaStubElementTypes.TYPE_PARAMETER_LIST;
  IElementType TYPE_PARAMETER = JavaStubElementTypes.TYPE_PARAMETER;
  IElementType IMPORT_LIST = JavaStubElementTypes.IMPORT_LIST;
  IElementType IMPORT_STATEMENT = JavaStubElementTypes.IMPORT_STATEMENT;
  IElementType IMPORT_STATIC_STATEMENT = JavaStubElementTypes.IMPORT_STATIC_STATEMENT;
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

  IElementType IMPORT_STATIC_REFERENCE = new JavaCompositeElementType("IMPORT_STATIC_REFERENCE", PsiImportStaticReferenceElementImpl::new);
  IElementType TYPE = new JavaCompositeElementType("TYPE", PsiTypeElementImpl::new);
  IElementType DIAMOND_TYPE = new JavaCompositeElementType("DIAMOND_TYPE", PsiDiamondTypeElementImpl::new);
  IElementType REFERENCE_PARAMETER_LIST = new JavaCompositeElementType("REFERENCE_PARAMETER_LIST", PsiReferenceParameterListImpl::new, true);
  IElementType JAVA_CODE_REFERENCE = new JavaCompositeElementType("JAVA_CODE_REFERENCE", PsiJavaCodeReferenceElementImpl::new);
  IElementType PACKAGE_STATEMENT = new JavaCompositeElementType("PACKAGE_STATEMENT", PsiPackageStatementImpl::new);
  IElementType LOCAL_VARIABLE = new JavaCompositeElementType("LOCAL_VARIABLE", PsiLocalVariableImpl::new);
  IElementType REFERENCE_EXPRESSION = new JavaCompositeElementType("REFERENCE_EXPRESSION", PsiReferenceExpressionImpl::new);
  IElementType THIS_EXPRESSION = new JavaCompositeElementType("THIS_EXPRESSION", PsiThisExpressionImpl::new);
  IElementType SUPER_EXPRESSION = new JavaCompositeElementType("SUPER_EXPRESSION", PsiSuperExpressionImpl::new);
  IElementType PARENTH_EXPRESSION = new JavaCompositeElementType("PARENTH_EXPRESSION", PsiParenthesizedExpressionImpl::new);
  IElementType METHOD_CALL_EXPRESSION = new JavaCompositeElementType("METHOD_CALL_EXPRESSION", PsiMethodCallExpressionImpl::new);
  IElementType TYPE_CAST_EXPRESSION = new JavaCompositeElementType("TYPE_CAST_EXPRESSION", PsiTypeCastExpressionImpl::new);
  IElementType PREFIX_EXPRESSION = new JavaCompositeElementType("PREFIX_EXPRESSION", PsiPrefixExpressionImpl::new);
  IElementType POSTFIX_EXPRESSION = new JavaCompositeElementType("POSTFIX_EXPRESSION", PsiPostfixExpressionImpl::new);
  IElementType BINARY_EXPRESSION = new JavaCompositeElementType("BINARY_EXPRESSION", PsiBinaryExpressionImpl::new);
  IElementType POLYADIC_EXPRESSION = new JavaCompositeElementType("POLYADIC_EXPRESSION", PsiPolyadicExpressionImpl::new);
  IElementType CONDITIONAL_EXPRESSION = new JavaCompositeElementType("CONDITIONAL_EXPRESSION", PsiConditionalExpressionImpl::new);
  IElementType ASSIGNMENT_EXPRESSION = new JavaCompositeElementType("ASSIGNMENT_EXPRESSION", PsiAssignmentExpressionImpl::new);
  IElementType NEW_EXPRESSION = new JavaCompositeElementType("NEW_EXPRESSION", PsiNewExpressionImpl::new);
  IElementType ARRAY_ACCESS_EXPRESSION = new JavaCompositeElementType("ARRAY_ACCESS_EXPRESSION", PsiArrayAccessExpressionImpl::new);
  IElementType ARRAY_INITIALIZER_EXPRESSION = new JavaCompositeElementType("ARRAY_INITIALIZER_EXPRESSION", PsiArrayInitializerExpressionImpl::new);
  IElementType INSTANCE_OF_EXPRESSION = new JavaCompositeElementType("INSTANCE_OF_EXPRESSION", PsiInstanceOfExpressionImpl::new);
  IElementType CLASS_OBJECT_ACCESS_EXPRESSION = new JavaCompositeElementType("CLASS_OBJECT_ACCESS_EXPRESSION", PsiClassObjectAccessExpressionImpl::new);
  IElementType EMPTY_EXPRESSION = new JavaCompositeElementType("EMPTY_EXPRESSION", PsiEmptyExpressionImpl::new, true);
  IElementType EXPRESSION_LIST = new JavaCompositeElementType("EXPRESSION_LIST", PsiExpressionListImpl::new, true);
  IElementType EMPTY_STATEMENT = new JavaCompositeElementType("EMPTY_STATEMENT", PsiEmptyStatementImpl::new);
  IElementType BLOCK_STATEMENT = new JavaCompositeElementType("BLOCK_STATEMENT", PsiBlockStatementImpl::new);
  IElementType EXPRESSION_STATEMENT = new JavaCompositeElementType("EXPRESSION_STATEMENT", PsiExpressionStatementImpl::new);
  IElementType EXPRESSION_LIST_STATEMENT = new JavaCompositeElementType("EXPRESSION_LIST_STATEMENT", PsiExpressionListStatementImpl::new);
  IElementType DECLARATION_STATEMENT = new JavaCompositeElementType("DECLARATION_STATEMENT", PsiDeclarationStatementImpl::new);
  IElementType IF_STATEMENT = new JavaCompositeElementType("IF_STATEMENT", PsiIfStatementImpl::new);
  IElementType WHILE_STATEMENT = new JavaCompositeElementType("WHILE_STATEMENT", PsiWhileStatementImpl::new);
  IElementType FOR_STATEMENT = new JavaCompositeElementType("FOR_STATEMENT", PsiForStatementImpl::new);
  IElementType FOREACH_STATEMENT = new JavaCompositeElementType("FOREACH_STATEMENT", PsiForeachStatementImpl::new);
  IElementType DO_WHILE_STATEMENT = new JavaCompositeElementType("DO_WHILE_STATEMENT", PsiDoWhileStatementImpl::new);
  IElementType SWITCH_STATEMENT = new JavaCompositeElementType("SWITCH_STATEMENT", PsiSwitchStatementImpl::new);
  IElementType SWITCH_EXPRESSION = new JavaCompositeElementType("SWITCH_EXPRESSION", PsiSwitchExpressionImpl::new);
  IElementType SWITCH_LABEL_STATEMENT = new JavaCompositeElementType("SWITCH_LABEL_STATEMENT", PsiSwitchLabelStatementImpl::new);
  IElementType SWITCH_LABELED_RULE = new JavaCompositeElementType("SWITCH_LABELED_RULE", PsiSwitchLabeledRuleStatementImpl::new);
  IElementType BREAK_STATEMENT = new JavaCompositeElementType("BREAK_STATEMENT", PsiBreakStatementImpl::new);
  IElementType CONTINUE_STATEMENT = new JavaCompositeElementType("CONTINUE_STATEMENT", PsiContinueStatementImpl::new);
  IElementType RETURN_STATEMENT = new JavaCompositeElementType("RETURN_STATEMENT", PsiReturnStatementImpl::new);
  IElementType THROW_STATEMENT = new JavaCompositeElementType("THROW_STATEMENT", PsiThrowStatementImpl::new);
  IElementType SYNCHRONIZED_STATEMENT = new JavaCompositeElementType("SYNCHRONIZED_STATEMENT", PsiSynchronizedStatementImpl::new);
  IElementType TRY_STATEMENT = new JavaCompositeElementType("TRY_STATEMENT", PsiTryStatementImpl::new);
  IElementType RESOURCE_LIST = new JavaCompositeElementType("RESOURCE_LIST", PsiResourceListImpl::new);
  IElementType RESOURCE_VARIABLE = new JavaCompositeElementType("RESOURCE_VARIABLE", PsiResourceVariableImpl::new);
  IElementType RESOURCE_EXPRESSION = new JavaCompositeElementType("RESOURCE_EXPRESSION", PsiResourceExpressionImpl::new);
  IElementType CATCH_SECTION = new JavaCompositeElementType("CATCH_SECTION", PsiCatchSectionImpl::new);
  IElementType LABELED_STATEMENT = new JavaCompositeElementType("LABELED_STATEMENT", PsiLabeledStatementImpl::new);
  IElementType ASSERT_STATEMENT = new JavaCompositeElementType("ASSERT_STATEMENT", PsiAssertStatementImpl::new);
  IElementType ANNOTATION_ARRAY_INITIALIZER = new JavaCompositeElementType("ANNOTATION_ARRAY_INITIALIZER", PsiArrayInitializerMemberValueImpl::new);
  IElementType RECEIVER_PARAMETER = new JavaCompositeElementType("RECEIVER", PsiReceiverParameterImpl::new);
  IElementType MODULE_REFERENCE = new JavaCompositeElementType("MODULE_REFERENCE", PsiJavaModuleReferenceElementImpl::new);

  class ICodeBlockElementType extends IErrorCounterReparseableElementType implements ICompositeElementType, ILightLazyParseableElementType {
    private ICodeBlockElementType() {
      super("CODE_BLOCK", JavaLanguage.INSTANCE);
    }

    @Override
    public ASTNode createNode(final CharSequence text) {
      return new PsiCodeBlockImpl(text);
    }

    @NotNull
    @Override
    public ASTNode createCompositeNode() {
      return new PsiCodeBlockImpl(null);
    }

    @Override
    public ASTNode parseContents(@NotNull final ASTNode chameleon) {
      final PsiBuilder builder = JavaParserUtil.createBuilder(chameleon);
      JavaParser.INSTANCE.getStatementParser().parseCodeBlockDeep(builder, true);
      return builder.getTreeBuilt().getFirstChildNode();
    }

    @Override
    public FlyweightCapableTreeStructure<LighterASTNode> parseContents(final LighterLazyParseableNode chameleon) {
      final PsiBuilder builder = JavaParserUtil.createBuilder(chameleon);
      JavaParser.INSTANCE.getStatementParser().parseCodeBlockDeep(builder, true);
      return builder.getLightTree();
    }

    @Override
    public int getErrorsCount(final CharSequence seq, Language fileLanguage, final Project project) {
      Lexer lexer = JavaParserDefinition.createLexer(LanguageLevel.HIGHEST);
      return PsiBuilderUtil.hasProperBraceBalance(seq, lexer, JavaTokenType.LBRACE, JavaTokenType.RBRACE) ? NO_ERRORS : FATAL_ERROR;
    }
  }
  ILazyParseableElementType CODE_BLOCK = new ICodeBlockElementType();

  IElementType MEMBERS = new ICodeFragmentElementType("MEMBERS", JavaLanguage.INSTANCE) {
    private final JavaParserUtil.ParserWrapper myParser =
      builder -> JavaParser.INSTANCE.getDeclarationParser().parseClassBodyDeclarations(builder, false);

    @Nullable
    @Override
    public ASTNode parseContents(@NotNull ASTNode chameleon) {
      return JavaParserUtil.parseFragment(chameleon, myParser);
    }
  };

  IElementType STATEMENTS = new ICodeFragmentElementType("STATEMENTS", JavaLanguage.INSTANCE) {
    private final JavaParserUtil.ParserWrapper myParser = builder -> JavaParser.INSTANCE.getStatementParser().parseStatements(builder);

    @Nullable
    @Override
    public ASTNode parseContents(@NotNull final ASTNode chameleon) {
      return JavaParserUtil.parseFragment(chameleon, myParser);
    }
  };

  IElementType EXPRESSION_TEXT = new ICodeFragmentElementType("EXPRESSION_TEXT", JavaLanguage.INSTANCE) {
    private final JavaParserUtil.ParserWrapper myParser = builder -> JavaParser.INSTANCE.getExpressionParser().parse(builder);

    @Nullable
    @Override
    public ASTNode parseContents(@NotNull final ASTNode chameleon) {
      return JavaParserUtil.parseFragment(chameleon, myParser);
    }
  };

  IElementType REFERENCE_TEXT = new ICodeFragmentElementType("REFERENCE_TEXT", JavaLanguage.INSTANCE) {
    private final JavaParserUtil.ParserWrapper myParser =
      builder -> JavaParser.INSTANCE.getReferenceParser().parseJavaCodeReference(builder, false, true, false, false);

    @Nullable
    @Override
    public ASTNode parseContents(@NotNull final ASTNode chameleon) {
      return JavaParserUtil.parseFragment(chameleon, myParser);
    }
  };

  IElementType TYPE_WITH_DISJUNCTIONS_TEXT = new TypeTextElementType("TYPE_WITH_DISJUNCTIONS_TEXT", ReferenceParser.DISJUNCTIONS);
  IElementType TYPE_WITH_CONJUNCTIONS_TEXT = new TypeTextElementType("TYPE_WITH_CONJUNCTIONS_TEXT", ReferenceParser.CONJUNCTIONS);

  class TypeTextElementType extends ICodeFragmentElementType {
    private final int myFlags;

    TypeTextElementType(@NonNls String debugName, int flags) {
      super(debugName, JavaLanguage.INSTANCE);
      myFlags = flags;
    }

    private final JavaParserUtil.ParserWrapper myParser = new JavaParserUtil.ParserWrapper() {
      @Override
      public void parse(final PsiBuilder builder) {
        int flags = ReferenceParser.EAT_LAST_DOT | ReferenceParser.ELLIPSIS | ReferenceParser.WILDCARD | myFlags;
        JavaParser.INSTANCE.getReferenceParser().parseType(builder, flags);
      }
    };

    @Nullable
    @Override
    public ASTNode parseContents(@NotNull final ASTNode chameleon) {
      return JavaParserUtil.parseFragment(chameleon, myParser);
    }
  }

  class JavaDummyElementType extends ILazyParseableElementType implements ICompositeElementType {
    private JavaDummyElementType() {
      super("DUMMY_ELEMENT", JavaLanguage.INSTANCE);
    }

    @NotNull
    @Override
    public ASTNode createCompositeNode() {
      return new CompositePsiElement(this) { };
    }

    @Nullable
    @Override
    public ASTNode parseContents(@NotNull final ASTNode chameleon) {
      assert chameleon instanceof JavaDummyElement : chameleon;
      final JavaDummyElement dummyElement = (JavaDummyElement)chameleon;
      return JavaParserUtil.parseFragment(chameleon, dummyElement.getParser(), dummyElement.consumeAll(), dummyElement.getLanguageLevel());
    }
  }
  IElementType DUMMY_ELEMENT = new JavaDummyElementType();
}