// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.java.stubs;

import com.intellij.lang.ASTNode;
import com.intellij.lang.java.JavaParserDefinition;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.PsiKeyword;
import com.intellij.psi.impl.source.tree.java.*;
import com.intellij.psi.tree.IStubFileElementType;
import org.jetbrains.annotations.NotNull;

public interface JavaStubElementTypes {
  JavaModifierListElementType MODIFIER_LIST = new JavaModifierListElementType();
  JavaAnnotationElementType ANNOTATION = new JavaAnnotationElementType();
  JavaAnnotationParameterListType ANNOTATION_PARAMETER_LIST = new JavaAnnotationParameterListType();
  JavaNameValuePairType NAME_VALUE_PAIR = new JavaNameValuePairType();
  JavaLiteralExpressionElementType LITERAL_EXPRESSION = new JavaLiteralExpressionElementType();
  LambdaExpressionElementType LAMBDA_EXPRESSION = new LambdaExpressionElementType();
  MethodReferenceElementType METHOD_REF_EXPRESSION = new MethodReferenceElementType();
  JavaParameterListElementType PARAMETER_LIST = new JavaParameterListElementType();
  JavaParameterElementType PARAMETER = new JavaParameterElementType();
  JavaTypeParameterElementType TYPE_PARAMETER = new JavaTypeParameterElementType();
  JavaTypeParameterListElementType TYPE_PARAMETER_LIST = new JavaTypeParameterListElementType();
  JavaClassInitializerElementType CLASS_INITIALIZER = new JavaClassInitializerElementType();
  JavaImportListElementType IMPORT_LIST = new JavaImportListElementType();
  JavaModuleElementType MODULE = new JavaModuleElementType();
  JavaRequiresStatementElementType REQUIRES_STATEMENT = new JavaRequiresStatementElementType();
  JavaUsesStatementElementType USES_STATEMENT = new JavaUsesStatementElementType();
  JavaProvidesStatementElementType PROVIDES_STATEMENT = new JavaProvidesStatementElementType();
  JavaRecordComponentElementType RECORD_COMPONENT = new JavaRecordComponentElementType();
  JavaRecordHeaderElementType RECORD_HEADER = new JavaRecordHeaderElementType();

  JavaPackageAccessibilityStatementElementType EXPORTS_STATEMENT = new JavaPackageAccessibilityStatementElementType("EXPORTS_STATEMENT");
  JavaPackageAccessibilityStatementElementType OPENS_STATEMENT = new JavaPackageAccessibilityStatementElementType("OPENS_STATEMENT");

  JavaClassElementType CLASS = new JavaClassElementType("CLASS") {
    @NotNull
    @Override
    public ASTNode createCompositeNode() {
      return new ClassElement(this);
    }
  };
  JavaClassElementType ANONYMOUS_CLASS = new JavaClassElementType("ANONYMOUS_CLASS") {
    @NotNull
    @Override
    public ASTNode createCompositeNode() {
      return new AnonymousClassElement();
    }
  };
  JavaClassElementType ENUM_CONSTANT_INITIALIZER = new JavaClassElementType("ENUM_CONSTANT_INITIALIZER") {
    @NotNull
    @Override
    public ASTNode createCompositeNode() {
      return new EnumConstantInitializerElement();
    }
  };

  JavaMethodElementType METHOD = new JavaMethodElementType("METHOD") {
    @NotNull
    @Override
    public ASTNode createCompositeNode() {
      return new MethodElement();
    }
  };
  JavaMethodElementType ANNOTATION_METHOD = new JavaMethodElementType("ANNOTATION_METHOD") {
    @NotNull
    @Override
    public ASTNode createCompositeNode() {
      return new AnnotationMethodElement();
    }
  };

  JavaFieldStubElementType FIELD = new JavaFieldStubElementType("FIELD") {
    @NotNull
    @Override
    public ASTNode createCompositeNode() {
      return new FieldElement();
    }
  };
  JavaFieldStubElementType ENUM_CONSTANT = new JavaFieldStubElementType("ENUM_CONSTANT") {
    @NotNull
    @Override
    public ASTNode createCompositeNode() {
      return new EnumConstantElement();
    }
  };

  JavaClassReferenceListElementType EXTENDS_LIST = new JavaClassReferenceListElementType("EXTENDS_LIST") {
    @NotNull
    @Override
    public ASTNode createCompositeNode() {
      return new ReferenceListElement(this, JavaTokenType.EXTENDS_KEYWORD, PsiKeyword.EXTENDS);
    }
  };

  JavaClassReferenceListElementType PERMITS_LIST = new JavaClassReferenceListElementType("PERMITS_LIST") {
    @NotNull
    @Override
    public ASTNode createCompositeNode() {
      return new ReferenceListElement(this, JavaTokenType.PERMITS_KEYWORD, PsiKeyword.PERMITS);
    }
  };
  JavaClassReferenceListElementType IMPLEMENTS_LIST = new JavaClassReferenceListElementType("IMPLEMENTS_LIST") {
    @NotNull
    @Override
    public ASTNode createCompositeNode() {
      return new ReferenceListElement(this, JavaTokenType.IMPLEMENTS_KEYWORD, PsiKeyword.IMPLEMENTS);
    }
  };
  JavaClassReferenceListElementType THROWS_LIST = new JavaClassReferenceListElementType("THROWS_LIST") {
    @NotNull
    @Override
    public ASTNode createCompositeNode() {
      return new ReferenceListElement(this, JavaTokenType.THROWS_KEYWORD, PsiKeyword.THROWS);
    }
  };
  JavaClassReferenceListElementType EXTENDS_BOUND_LIST = new JavaClassReferenceListElementType("EXTENDS_BOUND_LIST") {
    @NotNull
    @Override
    public ASTNode createCompositeNode() {
      return new TypeParameterExtendsBoundsListElement();
    }
  };
  JavaClassReferenceListElementType PROVIDES_WITH_LIST = new JavaClassReferenceListElementType("PROVIDES_WITH_LIST") {
    @NotNull
    @Override
    public ASTNode createCompositeNode() {
      return new ReferenceListElement(this, JavaTokenType.WITH_KEYWORD, PsiKeyword.WITH);
    }
  };

  JavaImportStatementElementType IMPORT_STATEMENT = new JavaImportStatementElementType("IMPORT_STATEMENT") {
    @NotNull
    @Override
    public ASTNode createCompositeNode() {
      return new ImportStatementElement();
    }
  };
  JavaImportStatementElementType IMPORT_STATIC_STATEMENT = new JavaImportStatementElementType("IMPORT_STATIC_STATEMENT") {
    @NotNull
    @Override
    public ASTNode createCompositeNode() {
      return new ImportStaticStatementElement();
    }
  };

  /**
   * @deprecated use {@link JavaParserDefinition#JAVA_FILE}
   */
  @Deprecated
  IStubFileElementType JAVA_FILE = JavaParserDefinition.JAVA_FILE;
}