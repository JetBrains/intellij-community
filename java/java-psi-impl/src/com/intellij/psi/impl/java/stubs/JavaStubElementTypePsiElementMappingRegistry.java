// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.java.stubs;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;
import com.intellij.psi.PsiElement;
import com.intellij.psi.impl.source.*;
import com.intellij.psi.impl.source.tree.java.*;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

@Service(Service.Level.APP)
public final class JavaStubElementTypePsiElementMappingRegistry {
  private final Map<IElementType, Function<ASTNode, PsiElement>> myFactories = new HashMap<>();

  private JavaStubElementTypePsiElementMappingRegistry() {
    registerFactory(JavaStubElementTypes.CLASS, node -> new PsiClassImpl(node));
    registerFactory(JavaStubElementTypes.IMPLICIT_CLASS, node -> new PsiImplicitClassImpl(node));
    registerFactory(JavaStubElementTypes.ANONYMOUS_CLASS, node -> new PsiAnonymousClassImpl(node));
    registerFactory(JavaStubElementTypes.ENUM_CONSTANT_INITIALIZER, node -> new PsiEnumConstantInitializerImpl(node));
    registerFactory(JavaStubElementTypes.ANNOTATION, node -> new PsiAnnotationImpl(node));
    registerFactory(JavaStubElementTypes.ANNOTATION_PARAMETER_LIST, node -> new PsiAnnotationParamListImpl(node));
    registerFactory(JavaStubElementTypes.MODIFIER_LIST, node -> new PsiModifierListImpl(node));
    registerFactory(JavaStubElementTypes.PARAMETER, node -> new PsiParameterImpl(node));
    registerFactory(JavaStubElementTypes.PARAMETER_LIST, node -> new PsiParameterListImpl(node));
    registerFactory(JavaStubElementTypes.TYPE_PARAMETER, node -> new PsiTypeParameterImpl(node));
    registerFactory(JavaStubElementTypes.TYPE_PARAMETER_LIST, node -> new PsiTypeParameterListImpl(node));
    registerFactory(JavaStubElementTypes.METHOD, node -> new PsiMethodImpl(node));
    registerFactory(JavaStubElementTypes.ANNOTATION_METHOD, node -> new PsiAnnotationMethodImpl(node));
    registerFactory(JavaStubElementTypes.FIELD, node -> new PsiFieldImpl(node));
    registerFactory(JavaStubElementTypes.ENUM_CONSTANT, node -> new PsiEnumConstantImpl(node));
    registerFactory(JavaStubElementTypes.CLASS_INITIALIZER, node -> new PsiClassInitializerImpl(node));
    registerFactory(JavaStubElementTypes.IMPORT_LIST, node -> new PsiImportListImpl(node));
    registerFactory(JavaStubElementTypes.IMPORT_STATEMENT, node -> new PsiImportStatementImpl(node));
    registerFactory(JavaStubElementTypes.IMPORT_STATIC_STATEMENT, node -> new PsiImportStaticStatementImpl(node));
    registerFactory(JavaStubElementTypes.IMPORT_MODULE_STATEMENT, node -> new PsiImportModuleStatementImpl(node));
    registerFactory(JavaStubElementTypes.EXTENDS_LIST, node -> new PsiReferenceListImpl(node));
    registerFactory(JavaStubElementTypes.IMPLEMENTS_LIST, node -> new PsiReferenceListImpl(node));
    registerFactory(JavaStubElementTypes.THROWS_LIST, node -> new PsiReferenceListImpl(node));
    registerFactory(JavaStubElementTypes.EXTENDS_BOUND_LIST, node -> new PsiReferenceListImpl(node));
    registerFactory(JavaStubElementTypes.PERMITS_LIST, node -> new PsiReferenceListImpl(node));
    registerFactory(JavaStubElementTypes.PROVIDES_WITH_LIST, node -> new PsiReferenceListImpl(node));
    registerFactory(JavaStubElementTypes.LITERAL_EXPRESSION, node -> new PsiLiteralExpressionImpl(node));
    registerFactory(JavaStubElementTypes.LAMBDA_EXPRESSION, node -> new PsiLambdaExpressionImpl(node));
    registerFactory(JavaStubElementTypes.METHOD_REF_EXPRESSION, node -> new PsiMethodReferenceExpressionImpl(node));
    registerFactory(JavaStubElementTypes.MODULE, node -> new PsiJavaModuleImpl(node));
    registerFactory(JavaStubElementTypes.REQUIRES_STATEMENT, node -> new PsiRequiresStatementImpl(node));
    registerFactory(JavaStubElementTypes.EXPORTS_STATEMENT, node -> new PsiPackageAccessibilityStatementImpl(node));
    registerFactory(JavaStubElementTypes.OPENS_STATEMENT, node -> new PsiPackageAccessibilityStatementImpl(node));
    registerFactory(JavaStubElementTypes.USES_STATEMENT, node -> new PsiUsesStatementImpl(node));
    registerFactory(JavaStubElementTypes.PROVIDES_STATEMENT, node -> new PsiProvidesStatementImpl(node));
    registerFactory(JavaStubElementTypes.PACKAGE_STATEMENT, node -> new PsiPackageStatementImpl(node));
    registerFactory(JavaStubElementTypes.RECORD_COMPONENT, node -> new PsiRecordComponentImpl(node));
    registerFactory(JavaStubElementTypes.RECORD_HEADER, node -> new PsiRecordHeaderImpl(node));
    registerFactory(JavaStubElementTypes.NAME_VALUE_PAIR, node -> new PsiNameValuePairImpl(node));
  }

  public static JavaStubElementTypePsiElementMappingRegistry getInstance() {
    return ApplicationManager.getApplication().getService( JavaStubElementTypePsiElementMappingRegistry.class);
  }

  private void registerFactory(@NotNull IElementType type, @NotNull Function<ASTNode, PsiElement> factory) {
    myFactories.put(type, factory);
  }

  @Nullable
  public PsiElement createPsi(@NotNull ASTNode node) {
    Function<ASTNode, PsiElement> factory = myFactories.get(node.getElementType());
    return factory != null ? factory.apply(node) : node.getPsi();
  }
}
