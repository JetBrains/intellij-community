// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.java.stubs;

import com.intellij.java.syntax.parser.JavaKeywords;
import com.intellij.lang.ASTNode;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.impl.source.BasicJavaElementType;
import com.intellij.psi.impl.source.tree.java.*;
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
  JavaPackageStatementElementType PACKAGE_STATEMENT = new JavaPackageStatementElementType();

  JavaPackageAccessibilityStatementElementType EXPORTS_STATEMENT =
    new JavaPackageAccessibilityStatementElementType("EXPORTS_STATEMENT", BasicJavaElementType.BASIC_EXPORTS_STATEMENT);
  JavaPackageAccessibilityStatementElementType OPENS_STATEMENT =
    new JavaPackageAccessibilityStatementElementType("OPENS_STATEMENT", BasicJavaElementType.BASIC_OPENS_STATEMENT);

  JavaClassElementType CLASS = new JavaClassElementType("CLASS", BasicJavaElementType.BASIC_CLASS) {
    @Override
    public @NotNull ASTNode createCompositeNode() {
      return new ClassElement(this);
    }
  };

  JavaClassElementType IMPLICIT_CLASS = new JavaClassElementType("IMPLICIT_CLASS", BasicJavaElementType.BASIC_IMPLICIT_CLASS) {
    @Override
    public @NotNull ASTNode createCompositeNode() {
      return new ImplicitClassElement();
    }
  };

  JavaClassElementType ANONYMOUS_CLASS = new JavaClassElementType("ANONYMOUS_CLASS", BasicJavaElementType.BASIC_ANONYMOUS_CLASS) {
    @Override
    public @NotNull ASTNode createCompositeNode() {
      return new AnonymousClassElement();
    }
  };
  JavaClassElementType ENUM_CONSTANT_INITIALIZER =
    new JavaClassElementType("ENUM_CONSTANT_INITIALIZER", BasicJavaElementType.BASIC_ENUM_CONSTANT_INITIALIZER) {
      @Override
      public @NotNull ASTNode createCompositeNode() {
        return new EnumConstantInitializerElement();
      }
    };

  JavaMethodElementType METHOD = new JavaMethodElementType("METHOD", BasicJavaElementType.BASIC_METHOD) {
    @Override
    public @NotNull ASTNode createCompositeNode() {
      return new MethodElement();
    }
  };
  JavaMethodElementType ANNOTATION_METHOD = new JavaMethodElementType("ANNOTATION_METHOD", BasicJavaElementType.BASIC_ANNOTATION_METHOD) {
    @Override
    public @NotNull ASTNode createCompositeNode() {
      return new AnnotationMethodElement();
    }
  };

  JavaFieldStubElementType FIELD = new JavaFieldStubElementType("FIELD", BasicJavaElementType.BASIC_FIELD) {
    @Override
    public @NotNull ASTNode createCompositeNode() {
      return new FieldElement();
    }
  };
  JavaFieldStubElementType ENUM_CONSTANT = new JavaFieldStubElementType("ENUM_CONSTANT", BasicJavaElementType.BASIC_ENUM_CONSTANT) {
    @Override
    public @NotNull ASTNode createCompositeNode() {
      return new EnumConstantElement();
    }
  };

  JavaClassReferenceListElementType EXTENDS_LIST =
    new JavaClassReferenceListElementType("EXTENDS_LIST", BasicJavaElementType.BASIC_EXTENDS_LIST) {
      @Override
      public @NotNull ASTNode createCompositeNode() {
        return new ReferenceListElement(this, JavaTokenType.EXTENDS_KEYWORD, JavaKeywords.EXTENDS);
      }
    };

  JavaClassReferenceListElementType PERMITS_LIST =
    new JavaClassReferenceListElementType("PERMITS_LIST", BasicJavaElementType.BASIC_PERMITS_LIST) {
      @Override
      public @NotNull ASTNode createCompositeNode() {
        return new ReferenceListElement(this, JavaTokenType.PERMITS_KEYWORD, JavaKeywords.PERMITS);
      }
    };
  JavaClassReferenceListElementType IMPLEMENTS_LIST =
    new JavaClassReferenceListElementType("IMPLEMENTS_LIST", BasicJavaElementType.BASIC_IMPLEMENTS_LIST) {
      @Override
      public @NotNull ASTNode createCompositeNode() {
        return new ReferenceListElement(this, JavaTokenType.IMPLEMENTS_KEYWORD, JavaKeywords.IMPLEMENTS);
      }
    };
  JavaClassReferenceListElementType THROWS_LIST =
    new JavaClassReferenceListElementType("THROWS_LIST", BasicJavaElementType.BASIC_THROWS_LIST) {
      @Override
      public @NotNull ASTNode createCompositeNode() {
        return new ReferenceListElement(this, JavaTokenType.THROWS_KEYWORD, JavaKeywords.THROWS);
      }
    };
  JavaClassReferenceListElementType EXTENDS_BOUND_LIST =
    new JavaClassReferenceListElementType("EXTENDS_BOUND_LIST", BasicJavaElementType.BASIC_EXTENDS_BOUND_LIST) {
      @Override
      public @NotNull ASTNode createCompositeNode() {
        return new TypeParameterExtendsBoundsListElement();
      }
    };
  JavaClassReferenceListElementType PROVIDES_WITH_LIST =
    new JavaClassReferenceListElementType("PROVIDES_WITH_LIST", BasicJavaElementType.BASIC_PROVIDES_WITH_LIST) {
      @Override
      public @NotNull ASTNode createCompositeNode() {
        return new ReferenceListElement(this, JavaTokenType.WITH_KEYWORD, JavaKeywords.WITH);
      }
    };

  JavaImportStatementElementType IMPORT_STATEMENT =
    new JavaImportStatementElementType("IMPORT_STATEMENT", BasicJavaElementType.BASIC_IMPORT_STATEMENT) {
      @Override
      public @NotNull ASTNode createCompositeNode() {
        return new ImportStatementElement();
      }
    };
  JavaImportStatementElementType IMPORT_STATIC_STATEMENT =
    new JavaImportStatementElementType("IMPORT_STATIC_STATEMENT", BasicJavaElementType.BASIC_IMPORT_STATIC_STATEMENT) {
      @Override
      public @NotNull ASTNode createCompositeNode() {
        return new ImportStaticStatementElement();
      }
    };
  JavaImportStatementElementType IMPORT_MODULE_STATEMENT =
    new JavaImportStatementElementType("IMPORT_MODULE_STATEMENT", BasicJavaElementType.BASIC_IMPORT_MODULE_STATEMENT) {
      @Override
      public @NotNull ASTNode createCompositeNode() {
        return new ImportModuleStatementElement();
      }
    };
}