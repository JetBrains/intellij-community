/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
import com.intellij.util.ReflectionUtil;
import com.intellij.util.diff.FlyweightCapableTreeStructure;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Constructor;

public interface JavaElementType {
  @SuppressWarnings("deprecation")
  class JavaCompositeElementType extends IJavaElementType implements ICompositeElementType {
    private final Constructor<? extends ASTNode> myConstructor;

    private JavaCompositeElementType(@NonNls final String debugName, final Class<? extends ASTNode> nodeClass) {
      this(debugName, nodeClass, false);
    }

    private JavaCompositeElementType(@NonNls final String debugName, final Class<? extends ASTNode> nodeClass, final boolean leftBound) {
      super(debugName, leftBound);
      myConstructor = ReflectionUtil.getDefaultConstructor(nodeClass);
    }

    @NotNull
    @Override
    public ASTNode createCompositeNode() {
      return ReflectionUtil.createInstance(myConstructor);
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
  IElementType METHOD_REF_EXPRESSION = JavaStubElementTypes.METHOD_REFERENCE;
  IElementType MODULE = JavaStubElementTypes.MODULE;
  IElementType REQUIRES_STATEMENT = JavaStubElementTypes.REQUIRES_STATEMENT;
  IElementType EXPORTS_STATEMENT = JavaStubElementTypes.EXPORTS_STATEMENT;
  IElementType OPENS_STATEMENT = JavaStubElementTypes.OPENS_STATEMENT;
  IElementType USES_STATEMENT = JavaStubElementTypes.USES_STATEMENT;
  IElementType PROVIDES_STATEMENT = JavaStubElementTypes.PROVIDES_STATEMENT;
  IElementType PROVIDES_WITH_LIST = JavaStubElementTypes.PROVIDES_WITH_LIST;

  IElementType IMPORT_STATIC_REFERENCE = new JavaCompositeElementType("IMPORT_STATIC_REFERENCE", PsiImportStaticReferenceElementImpl.class);
  IElementType TYPE = new JavaCompositeElementType("TYPE", PsiTypeElementImpl.class);
  IElementType DIAMOND_TYPE = new JavaCompositeElementType("DIAMOND_TYPE", PsiDiamondTypeElementImpl.class);
  IElementType REFERENCE_PARAMETER_LIST = new JavaCompositeElementType("REFERENCE_PARAMETER_LIST", PsiReferenceParameterListImpl.class, true);
  IElementType JAVA_CODE_REFERENCE = new JavaCompositeElementType("JAVA_CODE_REFERENCE", PsiJavaCodeReferenceElementImpl.class);
  IElementType PACKAGE_STATEMENT = new JavaCompositeElementType("PACKAGE_STATEMENT", PsiPackageStatementImpl.class);
  IElementType LOCAL_VARIABLE = new JavaCompositeElementType("LOCAL_VARIABLE", PsiLocalVariableImpl.class);
  IElementType REFERENCE_EXPRESSION = new JavaCompositeElementType("REFERENCE_EXPRESSION", PsiReferenceExpressionImpl.class);
  IElementType THIS_EXPRESSION = new JavaCompositeElementType("THIS_EXPRESSION", PsiThisExpressionImpl.class);
  IElementType SUPER_EXPRESSION = new JavaCompositeElementType("SUPER_EXPRESSION", PsiSuperExpressionImpl.class);
  IElementType PARENTH_EXPRESSION = new JavaCompositeElementType("PARENTH_EXPRESSION", PsiParenthesizedExpressionImpl.class);
  IElementType METHOD_CALL_EXPRESSION = new JavaCompositeElementType("METHOD_CALL_EXPRESSION", PsiMethodCallExpressionImpl.class);
  IElementType TYPE_CAST_EXPRESSION = new JavaCompositeElementType("TYPE_CAST_EXPRESSION", PsiTypeCastExpressionImpl.class);
  IElementType PREFIX_EXPRESSION = new JavaCompositeElementType("PREFIX_EXPRESSION", PsiPrefixExpressionImpl.class);
  IElementType POSTFIX_EXPRESSION = new JavaCompositeElementType("POSTFIX_EXPRESSION", PsiPostfixExpressionImpl.class);
  IElementType BINARY_EXPRESSION = new JavaCompositeElementType("BINARY_EXPRESSION", PsiBinaryExpressionImpl.class);
  IElementType POLYADIC_EXPRESSION = new JavaCompositeElementType("POLYADIC_EXPRESSION", PsiPolyadicExpressionImpl.class);
  IElementType CONDITIONAL_EXPRESSION = new JavaCompositeElementType("CONDITIONAL_EXPRESSION", PsiConditionalExpressionImpl.class);
  IElementType ASSIGNMENT_EXPRESSION = new JavaCompositeElementType("ASSIGNMENT_EXPRESSION", PsiAssignmentExpressionImpl.class);
  IElementType NEW_EXPRESSION = new JavaCompositeElementType("NEW_EXPRESSION", PsiNewExpressionImpl.class);
  IElementType ARRAY_ACCESS_EXPRESSION = new JavaCompositeElementType("ARRAY_ACCESS_EXPRESSION", PsiArrayAccessExpressionImpl.class);
  IElementType ARRAY_INITIALIZER_EXPRESSION = new JavaCompositeElementType("ARRAY_INITIALIZER_EXPRESSION", PsiArrayInitializerExpressionImpl.class);
  IElementType INSTANCE_OF_EXPRESSION = new JavaCompositeElementType("INSTANCE_OF_EXPRESSION", PsiInstanceOfExpressionImpl.class);
  IElementType CLASS_OBJECT_ACCESS_EXPRESSION = new JavaCompositeElementType("CLASS_OBJECT_ACCESS_EXPRESSION", PsiClassObjectAccessExpressionImpl.class);
  IElementType EMPTY_EXPRESSION = new JavaCompositeElementType("EMPTY_EXPRESSION", PsiEmptyExpressionImpl.class, true);
  IElementType EXPRESSION_LIST = new JavaCompositeElementType("EXPRESSION_LIST", PsiExpressionListImpl.class, true);
  IElementType EMPTY_STATEMENT = new JavaCompositeElementType("EMPTY_STATEMENT", PsiEmptyStatementImpl.class);
  IElementType BLOCK_STATEMENT = new JavaCompositeElementType("BLOCK_STATEMENT", PsiBlockStatementImpl.class);
  IElementType EXPRESSION_STATEMENT = new JavaCompositeElementType("EXPRESSION_STATEMENT", PsiExpressionStatementImpl.class);
  IElementType EXPRESSION_LIST_STATEMENT = new JavaCompositeElementType("EXPRESSION_LIST_STATEMENT", PsiExpressionListStatementImpl.class);
  IElementType DECLARATION_STATEMENT = new JavaCompositeElementType("DECLARATION_STATEMENT", PsiDeclarationStatementImpl.class);
  IElementType IF_STATEMENT = new JavaCompositeElementType("IF_STATEMENT", PsiIfStatementImpl.class);
  IElementType WHILE_STATEMENT = new JavaCompositeElementType("WHILE_STATEMENT", PsiWhileStatementImpl.class);
  IElementType FOR_STATEMENT = new JavaCompositeElementType("FOR_STATEMENT", PsiForStatementImpl.class);
  IElementType FOREACH_STATEMENT = new JavaCompositeElementType("FOREACH_STATEMENT", PsiForeachStatementImpl.class);
  IElementType DO_WHILE_STATEMENT = new JavaCompositeElementType("DO_WHILE_STATEMENT", PsiDoWhileStatementImpl.class);
  IElementType SWITCH_STATEMENT = new JavaCompositeElementType("SWITCH_STATEMENT", PsiSwitchStatementImpl.class);
  IElementType SWITCH_LABEL_STATEMENT = new JavaCompositeElementType("SWITCH_LABEL_STATEMENT", PsiSwitchLabelStatementImpl.class);
  IElementType BREAK_STATEMENT = new JavaCompositeElementType("BREAK_STATEMENT", PsiBreakStatementImpl.class);
  IElementType CONTINUE_STATEMENT = new JavaCompositeElementType("CONTINUE_STATEMENT", PsiContinueStatementImpl.class);
  IElementType RETURN_STATEMENT = new JavaCompositeElementType("RETURN_STATEMENT", PsiReturnStatementImpl.class);
  IElementType THROW_STATEMENT = new JavaCompositeElementType("THROW_STATEMENT", PsiThrowStatementImpl.class);
  IElementType SYNCHRONIZED_STATEMENT = new JavaCompositeElementType("SYNCHRONIZED_STATEMENT", PsiSynchronizedStatementImpl.class);
  IElementType TRY_STATEMENT = new JavaCompositeElementType("TRY_STATEMENT", PsiTryStatementImpl.class);
  IElementType RESOURCE_LIST = new JavaCompositeElementType("RESOURCE_LIST", PsiResourceListImpl.class);
  IElementType RESOURCE_VARIABLE = new JavaCompositeElementType("RESOURCE_VARIABLE", PsiResourceVariableImpl.class);
  IElementType RESOURCE_EXPRESSION = new JavaCompositeElementType("RESOURCE_EXPRESSION", PsiResourceExpressionImpl.class);
  IElementType CATCH_SECTION = new JavaCompositeElementType("CATCH_SECTION", PsiCatchSectionImpl.class);
  IElementType LABELED_STATEMENT = new JavaCompositeElementType("LABELED_STATEMENT", PsiLabeledStatementImpl.class);
  IElementType ASSERT_STATEMENT = new JavaCompositeElementType("ASSERT_STATEMENT", PsiAssertStatementImpl.class);
  IElementType ANNOTATION_ARRAY_INITIALIZER = new JavaCompositeElementType("ANNOTATION_ARRAY_INITIALIZER", PsiArrayInitializerMemberValueImpl.class);
  IElementType RECEIVER_PARAMETER = new JavaCompositeElementType("RECEIVER", PsiReceiverParameterImpl.class);
  IElementType MODULE_REFERENCE = new JavaCompositeElementType("MODULE_REFERENCE", PsiJavaModuleReferenceElementImpl.class);

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
    public ASTNode parseContents(final ASTNode chameleon) {
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

  IElementType STATEMENTS = new ICodeFragmentElementType("STATEMENTS", JavaLanguage.INSTANCE) {
    private final JavaParserUtil.ParserWrapper myParser = new JavaParserUtil.ParserWrapper() {
      @Override
      public void parse(final PsiBuilder builder) {
        JavaParser.INSTANCE.getStatementParser().parseStatements(builder);
      }
    };

    @Nullable
    @Override
    public ASTNode parseContents(final ASTNode chameleon) {
      return JavaParserUtil.parseFragment(chameleon, myParser);
    }
  };

  IElementType EXPRESSION_TEXT = new ICodeFragmentElementType("EXPRESSION_TEXT", JavaLanguage.INSTANCE) {
    private final JavaParserUtil.ParserWrapper myParser = new JavaParserUtil.ParserWrapper() {
      @Override
      public void parse(final PsiBuilder builder) {
        JavaParser.INSTANCE.getExpressionParser().parse(builder);
      }
    };

    @Nullable
    @Override
    public ASTNode parseContents(final ASTNode chameleon) {
      return JavaParserUtil.parseFragment(chameleon, myParser);
    }
  };

  IElementType REFERENCE_TEXT = new ICodeFragmentElementType("REFERENCE_TEXT", JavaLanguage.INSTANCE) {
    private final JavaParserUtil.ParserWrapper myParser = new JavaParserUtil.ParserWrapper() {
      @Override
      public void parse(final PsiBuilder builder) {
        JavaParser.INSTANCE.getReferenceParser().parseJavaCodeReference(builder, false, true, false, false);
      }
    };

    @Nullable
    @Override
    public ASTNode parseContents(final ASTNode chameleon) {
      return JavaParserUtil.parseFragment(chameleon, myParser);
    }
  };

  IElementType TYPE_WITH_DISJUNCTIONS_TEXT = new TypeTextElementType("TYPE_WITH_DISJUNCTIONS_TEXT", ReferenceParser.DISJUNCTIONS);
  IElementType TYPE_WITH_CONJUNCTIONS_TEXT = new TypeTextElementType("TYPE_WITH_CONJUNCTIONS_TEXT", ReferenceParser.CONJUNCTIONS);

  class TypeTextElementType extends ICodeFragmentElementType {
    private final int myFlags;

    public TypeTextElementType(@NonNls String debugName, int flags) {
      super(debugName, JavaLanguage.INSTANCE);
      myFlags = flags;
    }

    private final JavaParserUtil.ParserWrapper myParser = new JavaParserUtil.ParserWrapper() {
      @Override
      public void parse(final PsiBuilder builder) {
        JavaParser.INSTANCE.getReferenceParser().parseType(builder, ReferenceParser.EAT_LAST_DOT |
                                                                    ReferenceParser.ELLIPSIS |
                                                                    ReferenceParser.WILDCARD |
                                                                    myFlags);
      }
    };

    @Nullable
    @Override
    public ASTNode parseContents(final ASTNode chameleon) {
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
    public ASTNode parseContents(final ASTNode chameleon) {
      assert chameleon instanceof JavaDummyElement : chameleon;
      final JavaDummyElement dummyElement = (JavaDummyElement)chameleon;
      return JavaParserUtil.parseFragment(chameleon, dummyElement.getParser(), dummyElement.consumeAll(), dummyElement.getLanguageLevel());
    }
  }
  IElementType DUMMY_ELEMENT = new JavaDummyElementType();
}