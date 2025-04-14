// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.source.tree;

import com.intellij.java.syntax.parser.ReferenceParser;
import com.intellij.lang.ASTNode;
import com.intellij.lang.java.parser.JavaParserUtil;
import com.intellij.psi.impl.java.stubs.JavaStubElementTypes;
import com.intellij.psi.impl.source.*;
import com.intellij.psi.impl.source.tree.java.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.ILazyParseableElementType;
import com.intellij.psi.util.PsiUtil;
import org.jetbrains.annotations.NotNull;

import java.util.function.Supplier;

import static com.intellij.psi.impl.source.BasicJavaElementType.*;

/**
 * @see com.intellij.java.syntax.element.JavaSyntaxElementType
 */
public interface JavaElementType {
  class JavaCompositeElementType extends BasicJavaElementType.JavaCompositeElementType {
    public JavaCompositeElementType(@NotNull String debugName,
                                    @NotNull Supplier<? extends ASTNode> constructor,
                                    @NotNull IElementType parentElementType) {
      super(debugName, constructor, parentElementType);
    }

    public JavaCompositeElementType(@NotNull String debugName,
                                    @NotNull Supplier<? extends ASTNode> constructor,
                                    boolean leftBound,
                                    @NotNull IElementType parentElementType) {
      super(debugName, constructor, leftBound, parentElementType);
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

  IElementType IMPORT_STATIC_REFERENCE =
    new JavaCompositeElementType("IMPORT_STATIC_REFERENCE", () -> new PsiImportStaticReferenceElementImpl(),
                                 BASIC_IMPORT_STATIC_REFERENCE);
  IElementType TYPE = new JavaCompositeElementType("TYPE", () -> new PsiTypeElementImpl(), BASIC_TYPE);
  IElementType DIAMOND_TYPE =
    new JavaCompositeElementType("DIAMOND_TYPE", () -> new PsiDiamondTypeElementImpl(), BASIC_DIAMOND_TYPE);
  IElementType REFERENCE_PARAMETER_LIST =
    new JavaCompositeElementType("REFERENCE_PARAMETER_LIST", () -> new PsiReferenceParameterListImpl(), true,
                                 BASIC_REFERENCE_PARAMETER_LIST);
  IElementType JAVA_CODE_REFERENCE = new JavaCompositeElementType("JAVA_CODE_REFERENCE", () -> new PsiJavaCodeReferenceElementImpl(),
                                                                  BASIC_JAVA_CODE_REFERENCE);
  IElementType PACKAGE_STATEMENT = new JavaCompositeElementType("PACKAGE_STATEMENT", () -> new PsiPackageStatementImpl(),
                                                                BASIC_PACKAGE_STATEMENT);
  IElementType LOCAL_VARIABLE = new JavaCompositeElementType("LOCAL_VARIABLE", () -> new PsiLocalVariableImpl(),
                                                             BASIC_LOCAL_VARIABLE);
  IElementType REFERENCE_EXPRESSION = new JavaCompositeElementType("REFERENCE_EXPRESSION", () -> new PsiReferenceExpressionImpl(),
                                                                   BASIC_REFERENCE_EXPRESSION);
  IElementType THIS_EXPRESSION = new JavaCompositeElementType("THIS_EXPRESSION", () -> new PsiThisExpressionImpl(),
                                                              BASIC_THIS_EXPRESSION);
  IElementType SUPER_EXPRESSION = new JavaCompositeElementType("SUPER_EXPRESSION", () -> new PsiSuperExpressionImpl(),
                                                               BASIC_SUPER_EXPRESSION);
  IElementType PARENTH_EXPRESSION = new JavaCompositeElementType("PARENTH_EXPRESSION", () -> new PsiParenthesizedExpressionImpl(),
                                                                 BASIC_PARENTH_EXPRESSION);
  IElementType METHOD_CALL_EXPRESSION = new JavaCompositeElementType("METHOD_CALL_EXPRESSION", () -> new PsiMethodCallExpressionImpl(),
                                                                     BASIC_METHOD_CALL_EXPRESSION);
  IElementType TYPE_CAST_EXPRESSION = new JavaCompositeElementType("TYPE_CAST_EXPRESSION", () -> new PsiTypeCastExpressionImpl(),
                                                                   BASIC_TYPE_CAST_EXPRESSION);
  IElementType PREFIX_EXPRESSION = new JavaCompositeElementType("PREFIX_EXPRESSION", () -> new PsiPrefixExpressionImpl(),
                                                                BASIC_PREFIX_EXPRESSION);
  IElementType POSTFIX_EXPRESSION = new JavaCompositeElementType("POSTFIX_EXPRESSION", () -> new PsiPostfixExpressionImpl(),
                                                                 BASIC_POSTFIX_EXPRESSION);
  IElementType BINARY_EXPRESSION = new JavaCompositeElementType("BINARY_EXPRESSION", () -> new PsiBinaryExpressionImpl(),
                                                                BASIC_BINARY_EXPRESSION);
  IElementType POLYADIC_EXPRESSION = new JavaCompositeElementType("POLYADIC_EXPRESSION", () -> new PsiPolyadicExpressionImpl(),
                                                                  BASIC_POLYADIC_EXPRESSION);
  IElementType CONDITIONAL_EXPRESSION = new JavaCompositeElementType("CONDITIONAL_EXPRESSION", () -> new PsiConditionalExpressionImpl(),
                                                                     BASIC_CONDITIONAL_EXPRESSION);
  IElementType ASSIGNMENT_EXPRESSION = new JavaCompositeElementType("ASSIGNMENT_EXPRESSION", () -> new PsiAssignmentExpressionImpl(),
                                                                    BASIC_ASSIGNMENT_EXPRESSION);
  IElementType NEW_EXPRESSION = new JavaCompositeElementType("NEW_EXPRESSION", () -> new PsiNewExpressionImpl(),
                                                             BASIC_NEW_EXPRESSION);
  IElementType ARRAY_ACCESS_EXPRESSION = new JavaCompositeElementType("ARRAY_ACCESS_EXPRESSION", () -> new PsiArrayAccessExpressionImpl(),
                                                                      BASIC_ARRAY_ACCESS_EXPRESSION);
  IElementType ARRAY_INITIALIZER_EXPRESSION =
    new JavaCompositeElementType("ARRAY_INITIALIZER_EXPRESSION", () -> new PsiArrayInitializerExpressionImpl(),
                                 BASIC_ARRAY_INITIALIZER_EXPRESSION);
  IElementType INSTANCE_OF_EXPRESSION = new JavaCompositeElementType("INSTANCE_OF_EXPRESSION", () -> new PsiInstanceOfExpressionImpl(),
                                                                     BASIC_INSTANCE_OF_EXPRESSION);
  IElementType CLASS_OBJECT_ACCESS_EXPRESSION =
    new JavaCompositeElementType("CLASS_OBJECT_ACCESS_EXPRESSION", () -> new PsiClassObjectAccessExpressionImpl(),
                                 BASIC_CLASS_OBJECT_ACCESS_EXPRESSION);
  IElementType TEMPLATE_EXPRESSION = new JavaCompositeElementType("TEMPLATE_EXPRESSION", () -> new PsiTemplateExpressionImpl(),
                                                                  BASIC_TEMPLATE_EXPRESSION);
  IElementType TEMPLATE = new JavaCompositeElementType("TEMPLATE", () -> new PsiTemplateImpl(),
                                                       BASIC_TEMPLATE);
  IElementType EMPTY_EXPRESSION = new JavaCompositeElementType("EMPTY_EXPRESSION", () -> new PsiEmptyExpressionImpl(), true,
                                                               BASIC_EMPTY_EXPRESSION);
  IElementType EXPRESSION_LIST = new JavaCompositeElementType("EXPRESSION_LIST", () -> new PsiExpressionListImpl(), true,
                                                              BASIC_EXPRESSION_LIST);
  IElementType EMPTY_STATEMENT = new JavaCompositeElementType("EMPTY_STATEMENT", () -> new PsiEmptyStatementImpl(),
                                                              BASIC_EMPTY_STATEMENT);
  IElementType BLOCK_STATEMENT = new JavaCompositeElementType("BLOCK_STATEMENT", () -> new PsiBlockStatementImpl(),
                                                              BASIC_BLOCK_STATEMENT);
  IElementType EXPRESSION_STATEMENT = new JavaCompositeElementType("EXPRESSION_STATEMENT", () -> new PsiExpressionStatementImpl(),
                                                                   BASIC_EXPRESSION_STATEMENT);
  IElementType EXPRESSION_LIST_STATEMENT =
    new JavaCompositeElementType("EXPRESSION_LIST_STATEMENT", () -> new PsiExpressionListStatementImpl(),
                                 BASIC_EXPRESSION_LIST_STATEMENT);
  IElementType DECLARATION_STATEMENT = new JavaCompositeElementType("DECLARATION_STATEMENT", () -> new PsiDeclarationStatementImpl(),
                                                                    BASIC_DECLARATION_STATEMENT);
  IElementType IF_STATEMENT = new JavaCompositeElementType("IF_STATEMENT", () -> new PsiIfStatementImpl(),
                                                           BASIC_IF_STATEMENT);
  IElementType WHILE_STATEMENT = new JavaCompositeElementType("WHILE_STATEMENT", () -> new PsiWhileStatementImpl(),
                                                              BASIC_WHILE_STATEMENT);
  IElementType FOR_STATEMENT = new JavaCompositeElementType("FOR_STATEMENT", () -> new PsiForStatementImpl(),
                                                            BASIC_FOR_STATEMENT);
  IElementType FOREACH_STATEMENT = new JavaCompositeElementType("FOREACH_STATEMENT", () -> new PsiForeachStatementImpl(),
                                                                BASIC_FOREACH_STATEMENT);
  IElementType FOREACH_PATTERN_STATEMENT =
    new JavaCompositeElementType("FOREACH_PATTERN_STATEMENT", () -> new PsiForeachPatternStatementImpl(),
                                 BASIC_FOREACH_PATTERN_STATEMENT);
  IElementType DO_WHILE_STATEMENT = new JavaCompositeElementType("DO_WHILE_STATEMENT", () -> new PsiDoWhileStatementImpl(),
                                                                 BASIC_DO_WHILE_STATEMENT);
  IElementType SWITCH_STATEMENT = new JavaCompositeElementType("SWITCH_STATEMENT", () -> new PsiSwitchStatementImpl(),
                                                               BASIC_SWITCH_STATEMENT);
  IElementType SWITCH_EXPRESSION = new JavaCompositeElementType("SWITCH_EXPRESSION", () -> new PsiSwitchExpressionImpl(),
                                                                BASIC_SWITCH_EXPRESSION);
  IElementType SWITCH_LABEL_STATEMENT = new JavaCompositeElementType("SWITCH_LABEL_STATEMENT", () -> new PsiSwitchLabelStatementImpl(),
                                                                     BASIC_SWITCH_LABEL_STATEMENT);
  IElementType SWITCH_LABELED_RULE = new JavaCompositeElementType("SWITCH_LABELED_RULE", () -> new PsiSwitchLabeledRuleStatementImpl(),
                                                                  BASIC_SWITCH_LABELED_RULE);
  IElementType BREAK_STATEMENT = new JavaCompositeElementType("BREAK_STATEMENT", () -> new PsiBreakStatementImpl(),
                                                              BASIC_BREAK_STATEMENT);
  IElementType YIELD_STATEMENT = new JavaCompositeElementType("YIELD_STATEMENT", () -> new PsiYieldStatementImpl(),
                                                              BASIC_YIELD_STATEMENT);
  IElementType CONTINUE_STATEMENT = new JavaCompositeElementType("CONTINUE_STATEMENT", () -> new PsiContinueStatementImpl(),
                                                                 BASIC_CONTINUE_STATEMENT);
  IElementType RETURN_STATEMENT = new JavaCompositeElementType("RETURN_STATEMENT", () -> new PsiReturnStatementImpl(),
                                                               BASIC_RETURN_STATEMENT);
  IElementType THROW_STATEMENT = new JavaCompositeElementType("THROW_STATEMENT", () -> new PsiThrowStatementImpl(),
                                                              BASIC_THROW_STATEMENT);
  IElementType SYNCHRONIZED_STATEMENT = new JavaCompositeElementType("SYNCHRONIZED_STATEMENT", () -> new PsiSynchronizedStatementImpl(),
                                                                     BASIC_SYNCHRONIZED_STATEMENT);
  IElementType TRY_STATEMENT = new JavaCompositeElementType("TRY_STATEMENT", () -> new PsiTryStatementImpl(),
                                                            BASIC_TRY_STATEMENT);
  IElementType RESOURCE_LIST = new JavaCompositeElementType("RESOURCE_LIST", () -> new PsiResourceListImpl(),
                                                            BASIC_RESOURCE_LIST);
  IElementType RESOURCE_VARIABLE = new JavaCompositeElementType("RESOURCE_VARIABLE", () -> new PsiResourceVariableImpl(),
                                                                BASIC_RESOURCE_VARIABLE);
  IElementType RESOURCE_EXPRESSION = new JavaCompositeElementType("RESOURCE_EXPRESSION", () -> new PsiResourceExpressionImpl(),
                                                                  BASIC_RESOURCE_EXPRESSION);
  IElementType CATCH_SECTION = new JavaCompositeElementType("CATCH_SECTION", () -> new PsiCatchSectionImpl(),
                                                            BASIC_CATCH_SECTION);
  IElementType LABELED_STATEMENT = new JavaCompositeElementType("LABELED_STATEMENT", () -> new PsiLabeledStatementImpl(),
                                                                BASIC_LABELED_STATEMENT);
  IElementType ASSERT_STATEMENT = new JavaCompositeElementType("ASSERT_STATEMENT", () -> new PsiAssertStatementImpl(),
                                                               BASIC_ASSERT_STATEMENT);
  IElementType ANNOTATION_ARRAY_INITIALIZER =
    new JavaCompositeElementType("ANNOTATION_ARRAY_INITIALIZER", () -> new PsiArrayInitializerMemberValueImpl(),
                                 BASIC_ANNOTATION_ARRAY_INITIALIZER);
  IElementType RECEIVER_PARAMETER = new JavaCompositeElementType("RECEIVER", () -> new PsiReceiverParameterImpl(),
                                                                 BASIC_RECEIVER_PARAMETER);
  IElementType MODULE_REFERENCE = new JavaCompositeElementType("MODULE_REFERENCE", () -> new PsiJavaModuleReferenceElementImpl(),
                                                               BASIC_MODULE_REFERENCE);
  IElementType UNNAMED_PATTERN =
    new JavaCompositeElementType("UNNAMED_PATTERN", () -> new PsiUnnamedPatternImpl(), BASIC_UNNAMED_PATTERN);
  IElementType TYPE_TEST_PATTERN =
    new JavaCompositeElementType("TYPE_TEST_PATTERN", () -> new PsiTypeTestPatternImpl(), BASIC_TYPE_TEST_PATTERN);
  IElementType PATTERN_VARIABLE =
    new JavaCompositeElementType("PATTERN_VARIABLE", () -> new PsiPatternVariableImpl(), BASIC_PATTERN_VARIABLE);
  IElementType DECONSTRUCTION_PATTERN =
    new JavaCompositeElementType("DECONSTRUCTION_PATTERN", () -> new PsiDeconstructionPatternImpl(),
                                 BASIC_DECONSTRUCTION_PATTERN);
  IElementType DECONSTRUCTION_LIST =
    new JavaCompositeElementType("DECONSTRUCTION_LIST", () -> new PsiDeconstructionListImpl(),
                                 BASIC_DECONSTRUCTION_LIST);
  IElementType DECONSTRUCTION_PATTERN_VARIABLE =
    new JavaCompositeElementType("DECONSTRUCTION_PATTERN_VARIABLE", () -> new PsiDeconstructionPatternVariableImpl(),
                                 BASIC_DECONSTRUCTION_PATTERN_VARIABLE);
  IElementType DEFAULT_CASE_LABEL_ELEMENT =
    new JavaCompositeElementType("DEFAULT_CASE_LABEL_ELEMENT", () -> new PsiDefaultLabelElementImpl(),
                                 BASIC_DEFAULT_CASE_LABEL_ELEMENT);
  IElementType CASE_LABEL_ELEMENT_LIST =
    new JavaCompositeElementType("CASE_LABEL_ELEMENT_LIST", () -> new PsiCaseLabelElementListImpl(),
                                 BASIC_CASE_LABEL_ELEMENT_LIST);

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
                                                                              ReferenceParser.DISJUNCTIONS,
                                                                              BASIC_TYPE_WITH_DISJUNCTIONS_TEXT);
  IElementType TYPE_WITH_CONJUNCTIONS_TEXT = new FrontBackTypeTextElementType("TYPE_WITH_CONJUNCTIONS_TEXT",
                                                                              ReferenceParser.DISJUNCTIONS,
                                                                              BASIC_TYPE_WITH_CONJUNCTIONS_TEXT);
  IElementType DUMMY_ELEMENT = new JavaDummyElementType();
}