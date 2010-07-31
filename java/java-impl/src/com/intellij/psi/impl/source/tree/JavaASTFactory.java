/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

/*
 * @author max
 */
package com.intellij.psi.impl.source.tree;

import com.intellij.lang.ASTFactory;
import com.intellij.lang.java.JavaParserDefinition;
import com.intellij.psi.PlainTextTokenTypes;
import com.intellij.psi.impl.source.*;
import com.intellij.psi.impl.source.javadoc.*;
import com.intellij.psi.impl.source.tree.java.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.IFileElementType;
import com.intellij.psi.tree.ILazyParseableElementType;
import com.intellij.psi.tree.java.IJavaDocElementType;
import com.intellij.psi.tree.java.IJavaElementType;

public class JavaASTFactory extends ASTFactory implements Constants {

  @Override
  public LazyParseableElement createLazy(ILazyParseableElementType type, CharSequence text) {
    if (type == JavaParserDefinition.JAVA_FILE) {
      return new JavaFileElement(text);
    }
    else if (type == PlainTextTokenTypes.PLAIN_TEXT_FILE) {
      return new PlainTextFileElement(text);
    }
    else if (type == CODE_FRAGMENT) {
      return new CodeFragmentElement(text);
    }
    else if (type == DUMMY_HOLDER) {
      return new DummyHolderElement(text);
    }
    else if(type instanceof IFileElementType) {
      return new FileElement(type, text);
    }
    else if (type == JavaDocElementType.DOC_COMMENT) {
      return new PsiDocCommentImpl(text);
    }

    return null;
  }

  public CompositeElement createComposite(final IElementType type) {
    //TODO: Replace whole method with type.createPsiElement();
    if (type == TYPE_PARAMETER_LIST) {
      return new TypeParameterListElement();
    }
    else if (type == TYPE_PARAMETER) {
      return new TypeParameterElement();
    }
    else if (type == EXTENDS_BOUND_LIST) {
      return new TypeParameterExtendsBoundsListElement();
    }
    else if (type == ERROR_ELEMENT) {
      return new PsiErrorElementImpl();
    }
    else if (type == DOC_TAG) {
      return new PsiDocTagImpl();
    }
    else if (type == DOC_TAG_VALUE_TOKEN) {
      return new PsiDocTagValueImpl();
    }
    else if (type == DOC_METHOD_OR_FIELD_REF) {
      return new PsiDocMethodOrFieldRef();
    }
    else if (type == DOC_PARAMETER_REF) {
      return new PsiDocParamRef();
    }
    else if (type == DOC_INLINE_TAG) {
      return new PsiInlineDocTagImpl();
    }
    else if (type == CLASS) {
      return new ClassElement(type);
    }
    else if (type == ANONYMOUS_CLASS) {
      return new AnonymousClassElement();
    }
    else if (type == ENUM_CONSTANT_INITIALIZER) {
      return new EnumConstantInitializerElement();
    }
    else if (type == FIELD) {
      return new FieldElement();
    }
    else if (type == ENUM_CONSTANT) {
      return new EnumConstantElement();
    }
    else if (type == METHOD) {
      return new MethodElement();
    }
    else if (type == LOCAL_VARIABLE) {
      return new PsiLocalVariableImpl();
    }
    else if (type == PARAMETER) {
      return new ParameterElement();
    }
    else if (type == PARAMETER_LIST) {
      return new ParameterListElement();
    }
    else if (type == CLASS_INITIALIZER) {
      return new ClassInitializerElement();
    }
    else if (type == PACKAGE_STATEMENT) {
      return new PsiPackageStatementImpl();
    }
    else if (type == IMPORT_LIST) {
      return new ImportListElement();
    }
    else if (type == IMPORT_STATEMENT) {
      return new ImportStatementElement();
    }
    else if (type == IMPORT_STATIC_STATEMENT) {
      return new ImportStaticStatementElement();
    }
    else if (type == IMPORT_STATIC_REFERENCE) {
      return new PsiImportStaticReferenceElementImpl();
    }
    else if (type == JAVA_CODE_REFERENCE) {
      return new PsiJavaCodeReferenceElementImpl();
    }
    else if (type == REFERENCE_PARAMETER_LIST) {
      return new PsiReferenceParameterListImpl();
    }
    else if (type == TYPE) {
      return new PsiTypeElementImpl();
    }
    else if (type == MODIFIER_LIST) {
      return new ModifierListElement();
    }
    else if (type == EXTENDS_LIST) {
      return new ExtendsListElement();
    }
    else if (type == IMPLEMENTS_LIST) {
      return new ImplementsListElement();
    }
    else if (type == THROWS_LIST) {
      return new PsiThrowsListImpl();
    }
    else if (type == EXPRESSION_LIST) {
      return new PsiExpressionListImpl();
    }
    else if (type == REFERENCE_EXPRESSION) {
      return new PsiReferenceExpressionImpl();
    }
    else if (type == LITERAL_EXPRESSION) {
      return new PsiLiteralExpressionImpl();
    }
    else if (type == THIS_EXPRESSION) {
      return new PsiThisExpressionImpl();
    }
    else if (type == SUPER_EXPRESSION) {
      return new PsiSuperExpressionImpl();
    }
    else if (type == PARENTH_EXPRESSION) {
      return new PsiParenthesizedExpressionImpl();
    }
    else if (type == METHOD_CALL_EXPRESSION) {
      return new PsiMethodCallExpressionImpl();
    }
    else if (type == TYPE_CAST_EXPRESSION) {
      return new PsiTypeCastExpressionImpl();
    }
    else if (type == PREFIX_EXPRESSION) {
      return new PsiPrefixExpressionImpl();
    }
    else if (type == POSTFIX_EXPRESSION) {
      return new PsiPostfixExpressionImpl();
    }
    else if (type == BINARY_EXPRESSION) {
      return new PsiBinaryExpressionImpl();
    }
    else if (type == CONDITIONAL_EXPRESSION) {
      return new PsiConditionalExpressionImpl();
    }
    else if (type == ASSIGNMENT_EXPRESSION) {
      return new PsiAssignmentExpressionImpl();
    }
    else if (type == NEW_EXPRESSION) {
      return new PsiNewExpressionImpl();
    }
    else if (type == ARRAY_ACCESS_EXPRESSION) {
      return new PsiArrayAccessExpressionImpl();
    }
    else if (type == ARRAY_INITIALIZER_EXPRESSION) {
      return new PsiArrayInitializerExpressionImpl();
    }
    else if (type == INSTANCE_OF_EXPRESSION) {
      return new PsiInstanceOfExpressionImpl();
    }
    else if (type == CLASS_OBJECT_ACCESS_EXPRESSION) {
      return new PsiClassObjectAccessExpressionImpl();
    }
    else if (type == EMPTY_EXPRESSION) {
      return new PsiEmptyExpressionImpl();
    }
    else if (type == EMPTY_STATEMENT) {
      return new PsiEmptyStatementImpl();
    }
    else if (type == BLOCK_STATEMENT) {
      return new PsiBlockStatementImpl();
    }
    else if (type == EXPRESSION_STATEMENT) {
      return new PsiExpressionStatementImpl();
    }
    else if (type == EXPRESSION_LIST_STATEMENT) {
      return new PsiExpressionListStatementImpl();
    }
    else if (type == DECLARATION_STATEMENT) {
      return new PsiDeclarationStatementImpl();
    }
    else if (type == IF_STATEMENT) {
      return new PsiIfStatementImpl();
    }
    else if (type == WHILE_STATEMENT) {
      return new PsiWhileStatementImpl();
    }
    else if (type == FOR_STATEMENT) {
      return new PsiForStatementImpl();
    }
    else if (type == FOREACH_STATEMENT) {
      return new PsiForeachStatementImpl();
    }
    else if (type == DO_WHILE_STATEMENT) {
      return new PsiDoWhileStatementImpl();
    }
    else if (type == SWITCH_STATEMENT) {
      return new PsiSwitchStatementImpl();
    }
    else if (type == SWITCH_LABEL_STATEMENT) {
      return new PsiSwitchLabelStatementImpl();
    }
    else if (type == BREAK_STATEMENT) {
      return new PsiBreakStatementImpl();
    }
    else if (type == CONTINUE_STATEMENT) {
      return new PsiContinueStatementImpl();
    }
    else if (type == RETURN_STATEMENT) {
      return new PsiReturnStatementImpl();
    }
    else if (type == THROW_STATEMENT) {
      return new PsiThrowStatementImpl();
    }
    else if (type == SYNCHRONIZED_STATEMENT) {
      return new PsiSynchronizedStatementImpl();
    }
    else if (type == ASSERT_STATEMENT) {
      return new PsiAssertStatementImpl();
    }
    else if (type == TRY_STATEMENT) {
      return new PsiTryStatementImpl();
    }
    else if (type == LABELED_STATEMENT) {
      return new PsiLabeledStatementImpl();
    }
    else if (type == CATCH_SECTION) {
      return new PsiCatchSectionImpl();
    }
    else if (type == ANNOTATION_METHOD) {
      return new AnnotationMethodElement();
    }
    else if (type == ANNOTATION) {
      return new AnnotationElement();
    }
    else if (type == ANNOTATION_ARRAY_INITIALIZER) {
      return new PsiArrayInitializerMemberValueImpl();
    }
    else if (type == NAME_VALUE_PAIR) {
      return new PsiNameValuePairImpl();
    }
    else if (type == ANNOTATION_PARAMETER_LIST) {
      return new PsiAnnotationParameterListImpl();
    }
    else if (type == METHOD_RECEIVER) {
      return new PsiMethodReceiverImpl();
    }
    else if (type == CODE_BLOCK) {
      // deep code block parsing
      return new PsiCodeBlockImpl(null);
    }

    return new CompositePsiElement(type){};
  }

  public LeafElement createLeaf(final IElementType type, CharSequence text) {
    if (type == C_STYLE_COMMENT || type == END_OF_LINE_COMMENT) {
      return new PsiCommentImpl(type, text);
    }
    else if (type == IDENTIFIER) {
      return new PsiIdentifierImpl(text);
    }
    else if (KEYWORD_BIT_SET.contains(type)) {
      return new PsiKeywordImpl(type, text);
    }
    else if (type instanceof IJavaElementType) {
      return new PsiJavaTokenImpl(type, text);
    }
    else if (type instanceof IJavaDocElementType) {
      return new PsiDocTokenImpl(type, text);
    }

    return new LeafPsiElement(type, text);
  }
}
