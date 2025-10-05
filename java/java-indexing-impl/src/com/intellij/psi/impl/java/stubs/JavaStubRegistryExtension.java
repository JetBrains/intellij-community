// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.java.stubs;

import com.intellij.lang.java.JavaParserDefinition;
import com.intellij.psi.impl.java.stubs.factories.*;
import com.intellij.psi.impl.java.stubs.serializers.*;
import com.intellij.psi.stubs.StubRegistry;
import com.intellij.psi.stubs.StubRegistryExtension;
import org.jetbrains.annotations.NotNull;

public class JavaStubRegistryExtension implements StubRegistryExtension {
  @Override
  public void register(@NotNull StubRegistry registry) {
    JavaStubElementTypePsiElementMappingRegistry.getInstance();
    
    registry.registerStubSerializer(JavaParserDefinition.JAVA_FILE, new JavaFileSerializer());

    registry.registerStubSerializer(JavaStubElementTypes.LITERAL_EXPRESSION, new JavaLiteralExpressionStubSerializer(JavaStubElementTypes.LITERAL_EXPRESSION));
    registry.registerLightStubFactory(JavaStubElementTypes.LITERAL_EXPRESSION, new JavaLiteralExpressionStubFactory());

    registry.registerStubSerializer(JavaStubElementTypes.MODIFIER_LIST, new JavaModifierListSerializer(JavaStubElementTypes.MODIFIER_LIST));
    registry.registerLightStubFactory(JavaStubElementTypes.MODIFIER_LIST, new JavaModifierListStubFactory());

    registry.registerStubSerializer(JavaStubElementTypes.ANNOTATION, new JavaAnnotationStubSerializer(JavaStubElementTypes.ANNOTATION));
    registry.registerLightStubFactory(JavaStubElementTypes.ANNOTATION, new JavaAnnotationStubFactory());

    registry.registerStubSerializer(JavaStubElementTypes.ANNOTATION_PARAMETER_LIST, new JavaAnnotationParameterListStubSerializer(JavaStubElementTypes.ANNOTATION_PARAMETER_LIST));
    registry.registerLightStubFactory(JavaStubElementTypes.ANNOTATION_PARAMETER_LIST, new JavaAnnotationParameterListStubFactory());

    registry.registerStubSerializer(JavaStubElementTypes.NAME_VALUE_PAIR, new JavaNameValuePairStubSerializer(JavaStubElementTypes.NAME_VALUE_PAIR));
    registry.registerLightStubFactory(JavaStubElementTypes.NAME_VALUE_PAIR, new JavaNameValuePairStubFactory());

    registry.registerStubSerializer(JavaStubElementTypes.LAMBDA_EXPRESSION, new LambdaExpressionStubSerializer(JavaStubElementTypes.LAMBDA_EXPRESSION));
    registry.registerLightStubFactory(JavaStubElementTypes.LAMBDA_EXPRESSION, new LambdaExpressionStubFactory());

    registry.registerStubSerializer(JavaStubElementTypes.METHOD_REF_EXPRESSION, new MethodReferenceStubSerializer(JavaStubElementTypes.METHOD_REF_EXPRESSION));
    registry.registerLightStubFactory(JavaStubElementTypes.METHOD_REF_EXPRESSION, new MethodReferenceStubFactory());

    registry.registerStubSerializer(JavaStubElementTypes.PARAMETER_LIST, new JavaParameterListStubSerializer(JavaStubElementTypes.PARAMETER_LIST));
    registry.registerLightStubFactory(JavaStubElementTypes.PARAMETER_LIST, new JavaParameterListStubFactory());

    registry.registerStubSerializer(JavaStubElementTypes.PARAMETER, new JavaParameterStubSerializer(JavaStubElementTypes.PARAMETER));
    registry.registerLightStubFactory(JavaStubElementTypes.PARAMETER, new JavaParameterStubFactory());

    registry.registerStubSerializer(JavaStubElementTypes.TYPE_PARAMETER, new JavaTypeParameterStubSerializer(JavaStubElementTypes.TYPE_PARAMETER));
    registry.registerLightStubFactory(JavaStubElementTypes.TYPE_PARAMETER, new JavaTypeParameterStubFactory());

    registry.registerStubSerializer(JavaStubElementTypes.TYPE_PARAMETER_LIST, new JavaTypeParameterListStubSerializer(JavaStubElementTypes.TYPE_PARAMETER_LIST));
    registry.registerLightStubFactory(JavaStubElementTypes.TYPE_PARAMETER_LIST, new JavaTypeParameterListStubFactory());

    registry.registerStubSerializer(JavaStubElementTypes.CLASS_INITIALIZER, new JavaClassInitializerStubSerializer(JavaStubElementTypes.CLASS_INITIALIZER));
    registry.registerLightStubFactory(JavaStubElementTypes.CLASS_INITIALIZER, new JavaClassInitializerStubFactory());

    registry.registerStubSerializer(JavaStubElementTypes.IMPORT_LIST, new JavaImportListStubSerializer(JavaStubElementTypes.IMPORT_LIST));
    registry.registerLightStubFactory(JavaStubElementTypes.IMPORT_LIST, new JavaImportListStubFactory());

    registry.registerStubSerializer(JavaStubElementTypes.MODULE, new JavaModuleStubSerializer(JavaStubElementTypes.MODULE));
    registry.registerLightStubFactory(JavaStubElementTypes.MODULE, new JavaModuleStubFactory());

    registry.registerStubSerializer(JavaStubElementTypes.REQUIRES_STATEMENT, new JavaRequiresStatementStubSerializer(JavaStubElementTypes.REQUIRES_STATEMENT));
    registry.registerLightStubFactory(JavaStubElementTypes.REQUIRES_STATEMENT, new JavaRequiresStatementStubFactory());

    registry.registerStubSerializer(JavaStubElementTypes.USES_STATEMENT, new JavaUsesStatementStubSerializer(JavaStubElementTypes.USES_STATEMENT));
    registry.registerLightStubFactory(JavaStubElementTypes.USES_STATEMENT, new JavaUsesStatementStubFactory());

    registry.registerStubSerializer(JavaStubElementTypes.PROVIDES_STATEMENT, new JavaProvidesStatementStubSerializer(JavaStubElementTypes.PROVIDES_STATEMENT));
    registry.registerLightStubFactory(JavaStubElementTypes.PROVIDES_STATEMENT, new JavaProvidesStatementStubFactory());

    registry.registerStubSerializer(JavaStubElementTypes.RECORD_COMPONENT, new JavaRecordComponentStubSerializer(JavaStubElementTypes.RECORD_COMPONENT));
    registry.registerLightStubFactory(JavaStubElementTypes.RECORD_COMPONENT, new JavaRecordComponentStubFactory());

    registry.registerStubSerializer(JavaStubElementTypes.RECORD_HEADER, new JavaRecordHeaderStubSerializer(JavaStubElementTypes.RECORD_HEADER));
    registry.registerLightStubFactory(JavaStubElementTypes.RECORD_HEADER, new JavaRecordHeaderStubFactory());

    registry.registerStubSerializer(JavaStubElementTypes.PACKAGE_STATEMENT, new JavaPackageStatementStubSerializer(JavaStubElementTypes.PACKAGE_STATEMENT));
    registry.registerLightStubFactory(JavaStubElementTypes.PACKAGE_STATEMENT, new JavaPackageStatementStubFactory());

    registry.registerStubSerializer(JavaStubElementTypes.EXPORTS_STATEMENT, new JavaPackageAccessibilityStatementStubSerializer(JavaStubElementTypes.EXPORTS_STATEMENT));
    registry.registerLightStubFactory(JavaStubElementTypes.EXPORTS_STATEMENT, new JavaPackageAccessibilityStatementStubFactory());

    registry.registerStubSerializer(JavaStubElementTypes.OPENS_STATEMENT, new JavaPackageAccessibilityStatementStubSerializer(JavaStubElementTypes.OPENS_STATEMENT));
    registry.registerLightStubFactory(JavaStubElementTypes.OPENS_STATEMENT, new JavaPackageAccessibilityStatementStubFactory());

    registry.registerStubSerializer(JavaStubElementTypes.CLASS, new JavaClassStubSerializer(JavaStubElementTypes.CLASS));
    registry.registerLightStubFactory(JavaStubElementTypes.CLASS, new JavaClassStubFactory());

    registry.registerStubSerializer(JavaStubElementTypes.IMPLICIT_CLASS, new JavaClassStubSerializer(JavaStubElementTypes.IMPLICIT_CLASS));
    registry.registerLightStubFactory(JavaStubElementTypes.IMPLICIT_CLASS, new JavaClassStubFactory());

    registry.registerStubSerializer(JavaStubElementTypes.ANONYMOUS_CLASS, new JavaClassStubSerializer(JavaStubElementTypes.ANONYMOUS_CLASS));
    registry.registerLightStubFactory(JavaStubElementTypes.ANONYMOUS_CLASS, new JavaClassStubFactory());

    registry.registerStubSerializer(JavaStubElementTypes.ENUM_CONSTANT_INITIALIZER, new JavaClassStubSerializer(JavaStubElementTypes.ENUM_CONSTANT_INITIALIZER));
    registry.registerLightStubFactory(JavaStubElementTypes.ENUM_CONSTANT_INITIALIZER, new JavaClassStubFactory());

    registry.registerStubSerializer(JavaStubElementTypes.METHOD, new JavaMethodStubSerializer(JavaStubElementTypes.METHOD));
    registry.registerLightStubFactory(JavaStubElementTypes.METHOD, new JavaMethodStubFactory());

    registry.registerStubSerializer(JavaStubElementTypes.ANNOTATION_METHOD, new JavaMethodStubSerializer(JavaStubElementTypes.ANNOTATION_METHOD));
    registry.registerLightStubFactory(JavaStubElementTypes.ANNOTATION_METHOD, new JavaMethodStubFactory());

    registry.registerStubSerializer(JavaStubElementTypes.FIELD, new JavaFieldStubSerializer(JavaStubElementTypes.FIELD));
    registry.registerLightStubFactory(JavaStubElementTypes.FIELD, new JavaFieldStubFactory());

    registry.registerStubSerializer(JavaStubElementTypes.ENUM_CONSTANT, new JavaFieldStubSerializer(JavaStubElementTypes.ENUM_CONSTANT));
    registry.registerLightStubFactory(JavaStubElementTypes.ENUM_CONSTANT, new JavaFieldStubFactory());

    registry.registerStubSerializer(JavaStubElementTypes.EXTENDS_LIST, new JavaClassReferenceListStubSerializer(JavaStubElementTypes.EXTENDS_LIST));
    registry.registerLightStubFactory(JavaStubElementTypes.EXTENDS_LIST, new JavaClassReferenceListStubFactory());

    registry.registerStubSerializer(JavaStubElementTypes.PERMITS_LIST, new JavaClassReferenceListStubSerializer(JavaStubElementTypes.PERMITS_LIST));
    registry.registerLightStubFactory(JavaStubElementTypes.PERMITS_LIST, new JavaClassReferenceListStubFactory());

    registry.registerStubSerializer(JavaStubElementTypes.IMPLEMENTS_LIST, new JavaClassReferenceListStubSerializer(JavaStubElementTypes.IMPLEMENTS_LIST));
    registry.registerLightStubFactory(JavaStubElementTypes.IMPLEMENTS_LIST, new JavaClassReferenceListStubFactory());

    registry.registerStubSerializer(JavaStubElementTypes.THROWS_LIST, new JavaClassReferenceListStubSerializer(JavaStubElementTypes.THROWS_LIST));
    registry.registerLightStubFactory(JavaStubElementTypes.THROWS_LIST, new JavaClassReferenceListStubFactory());

    registry.registerStubSerializer(JavaStubElementTypes.EXTENDS_BOUND_LIST, new JavaClassReferenceListStubSerializer(JavaStubElementTypes.EXTENDS_BOUND_LIST));
    registry.registerLightStubFactory(JavaStubElementTypes.EXTENDS_BOUND_LIST, new JavaClassReferenceListStubFactory());

    registry.registerStubSerializer(JavaStubElementTypes.PROVIDES_WITH_LIST, new JavaClassReferenceListStubSerializer(JavaStubElementTypes.PROVIDES_WITH_LIST));
    registry.registerLightStubFactory(JavaStubElementTypes.PROVIDES_WITH_LIST, new JavaClassReferenceListStubFactory());

    registry.registerStubSerializer(JavaStubElementTypes.IMPORT_STATEMENT, new JavaImportStatementStubSerializer(JavaStubElementTypes.IMPORT_STATEMENT));
    registry.registerLightStubFactory(JavaStubElementTypes.IMPORT_STATEMENT, new JavaImportStatementStubFactory());

    registry.registerStubSerializer(JavaStubElementTypes.IMPORT_STATIC_STATEMENT, new JavaImportStatementStubSerializer(JavaStubElementTypes.IMPORT_STATIC_STATEMENT));
    registry.registerLightStubFactory(JavaStubElementTypes.IMPORT_STATIC_STATEMENT, new JavaImportStatementStubFactory());

    registry.registerStubSerializer(JavaStubElementTypes.IMPORT_MODULE_STATEMENT, new JavaImportStatementStubSerializer(JavaStubElementTypes.IMPORT_MODULE_STATEMENT));
    registry.registerLightStubFactory(JavaStubElementTypes.IMPORT_MODULE_STATEMENT, new JavaImportStatementStubFactory());
  }
}