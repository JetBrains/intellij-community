// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.ExpectedTypeInfo;
import com.intellij.codeInsight.ExpectedTypesProvider;
import com.intellij.codeInsight.intention.impl.TypeExpression;
import com.intellij.codeInsight.template.TemplateBuilder;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.PostprocessReformattingAspect;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

import static com.intellij.codeInsight.ExpectedTypeInfo.*;
import static com.intellij.util.containers.ContainerUtil.map;

public class GuessTypeParameters {

  private static final Logger LOG = Logger.getInstance(GuessTypeParameters.class);

  private final Project myProject;
  private final PsiManager myManager;
  private final JVMElementFactory myFactory;
  private final TemplateBuilder myBuilder;
  private final PsiSubstitutor mySubstitutor;

  public GuessTypeParameters(@NotNull Project project,
                             @NotNull JVMElementFactory factory,
                             @NotNull TemplateBuilder builder,
                             @Nullable PsiSubstitutor substitutor) {
    myProject = project;
    myManager = PsiManager.getInstance(project);
    myFactory = factory;
    myBuilder = builder;
    mySubstitutor = substitutor == null ? PsiSubstitutor.EMPTY : substitutor;
  }

  @NotNull
  public PsiTypeElement setupTypeElement(@NotNull PsiTypeElement typeElement,
                                         ExpectedTypeInfo @NotNull [] infos,
                                         @Nullable PsiElement context,
                                         @NotNull PsiClass targetClass) {
    LOG.assertTrue(typeElement.isValid());
    if (typeElement.isPhysical()) {
      ApplicationManager.getApplication().assertWriteAccessAllowed();
    }

    GlobalSearchScope scope = typeElement.getResolveScope();

    if (infos.length == 1 && mySubstitutor != PsiSubstitutor.EMPTY) {
      ExpectedTypeInfo info = infos[0];

      final PsiType expectedType = info.getType();

      final List<PsiTypeParameter> matchedParameters = matchingTypeParameters(mySubstitutor, expectedType, info.getKind());
      if (!matchedParameters.isEmpty()) {
        final List<PsiType> types = new SmartList<>(map(matchedParameters, it -> myFactory.createType(it)));
        ContainerUtil.addAll(types, ExpectedTypesProvider.processExpectedTypes(infos, new MyTypeVisitor(myManager, scope), myProject));
        myBuilder.replaceElement(typeElement, new TypeExpression(myProject, types));
        return typeElement;
      }

      typeElement = replaceTypeElement(typeElement, info.getType());

      PsiSubstitutor rawingSubstitutor = getRawingSubstitutor(myProject, context, targetClass);
      int substitionResult = hasNullSubstitutions(mySubstitutor)
                             ? SUBSTITUTED_NONE
                             : substituteToTypeParameters(typeElement, expectedType, rawingSubstitutor, true);
      if (substitionResult == SUBSTITUTED_IN_PARAMETERS) {
        PsiJavaCodeReferenceElement refElement = typeElement.getInnermostComponentReferenceElement();
        LOG.assertTrue(refElement != null);
        PsiElement qualifier = refElement.getQualifier();
        if (qualifier != null) {
          // Consider type element `java.util.List<java.lang.String>`.
          // At this point there is a template on `java.lang.String` type element already.
          //
          // We need java.util.List element to put the second template on,
          // but there is no such element, because type element consists of:
          // - qualifier `java.util`
          // - dot
          // - reference name element `List`
          // - reference type parameters `<java.lang.String>`
          //
          // Removing the qualifier also removes the dot, so in the end we get `List<java.lang.String>`
          // and we are safe to put the template on the reference name element `List`.
          //
          // Actual shortening or using of FQNs is then handled by com.intellij.codeInsight.template.impl.ShortenFQNamesProcessor.
          qualifier.delete();
        }
        PsiElement referenceNameElement = refElement.getReferenceNameElement();
        LOG.assertTrue(referenceNameElement != null);
        PsiClassType defaultType = getComponentType(info.getDefaultType());
        LOG.assertTrue(defaultType != null);
        PsiClassType rawDefaultType = defaultType.rawType();
        ExpectedTypeInfo info1 = ExpectedTypesProvider.createInfo(rawDefaultType, TYPE_STRICTLY, rawDefaultType, info.getTailType());
        MyTypeVisitor visitor = new MyTypeVisitor(myManager, scope);
        PsiType[] types = ExpectedTypesProvider.processExpectedTypes(new ExpectedTypeInfo[]{info1}, visitor, myProject);
        myBuilder.replaceElement(referenceNameElement, new TypeExpression(myProject, types));
        return typeElement;
      }
      else if (substitionResult != SUBSTITUTED_NONE) {
        return typeElement;
      }
    }

    PsiType[] types = infos.length == 0
                      ? new PsiType[]{typeElement.getType()}
                      : ExpectedTypesProvider.processExpectedTypes(infos, new MyTypeVisitor(myManager, scope), myProject);
    myBuilder.replaceElement(typeElement, new TypeExpression(myProject, types));
    return typeElement;
  }

  private PsiTypeElement replaceTypeElement(@NotNull PsiTypeElement templateElement, @NotNull PsiType type) {
    PsiTypeElement newTypeElement = JavaPsiFacade.getElementFactory(myProject).createTypeElement(type);
    return PostprocessReformattingAspect.getInstance(myProject).disablePostprocessFormattingInside(
      () -> (PsiTypeElement)templateElement.replace(newTypeElement)
    );
  }

  private static PsiSubstitutor getRawingSubstitutor(Project project, PsiElement context, PsiClass targetClass) {
    if (context == null || targetClass == null) return PsiSubstitutor.EMPTY;

    PsiTypeParameterListOwner currContext = PsiTreeUtil.getParentOfType(context, PsiTypeParameterListOwner.class);
    PsiManager manager = context.getManager();
    PsiSubstitutor substitutor = PsiSubstitutor.EMPTY;
    while (currContext != null && !manager.areElementsEquivalent(currContext, targetClass)) {
      PsiTypeParameter[] typeParameters = currContext.getTypeParameters();
      substitutor = JavaPsiFacade.getElementFactory(project).createRawSubstitutor(substitutor, typeParameters);
      currContext = currContext.getContainingClass();
    }

    return substitutor;
  }

  @Nullable
  private static PsiClassType getComponentType (PsiType type) {
    type = type.getDeepComponentType();
    if (type instanceof PsiClassType) return (PsiClassType)type;

    return null;
  }

  private static final int SUBSTITUTED_NONE = 0;
  private static final int SUBSTITUTED_IN_REF = 1;
  private static final int SUBSTITUTED_IN_PARAMETERS = 2;

  private int substituteToTypeParameters(PsiTypeElement typeElement,
                                         PsiType expectedType,
                                         PsiSubstitutor rawingSubstitutor,
                                         boolean toplevel) {
    final List<PsiTypeParameter> matchedParameters = matchingTypeParameters(mySubstitutor, expectedType, TYPE_STRICTLY);
    if (!matchedParameters.isEmpty()) {
      List<PsiType> types = new SmartList<>(map(matchedParameters, it -> myFactory.createType(it)));

      PsiType substituted = rawingSubstitutor.substitute(expectedType);
      if (!CommonClassNames.JAVA_LANG_OBJECT.equals(substituted.getCanonicalText()) && (toplevel || substituted.equals(expectedType))) {
        types.add(substituted);
      }

      myBuilder.replaceElement(typeElement, new TypeExpression(myProject, types));
      return toplevel ? SUBSTITUTED_IN_REF : SUBSTITUTED_IN_PARAMETERS;
    }

    final PsiTypeElement[] innerTypeElements = typeArguments(typeElement);
    if (innerTypeElements == null) return SUBSTITUTED_NONE;

    final PsiType[] expectedTypeArguments = typeArguments(expectedType);
    if (expectedTypeArguments == null) return SUBSTITUTED_NONE;

    boolean substituted = false;
    for (int i = 0; i < innerTypeElements.length; i++) {
      substituted |= substituteToTypeParameters(innerTypeElements[i], expectedTypeArguments[i],
                                                rawingSubstitutor, false) != SUBSTITUTED_NONE;
    }
    return substituted ? SUBSTITUTED_IN_PARAMETERS : SUBSTITUTED_NONE;
  }

  private static PsiTypeElement @Nullable [] typeArguments(@NotNull PsiTypeElement typeElement) {
    // Foo<String, Bar>[][][] -> Foo<String, Bar>
    // Foo<String, Bar> -> Foo<String, Bar>
    final PsiJavaCodeReferenceElement unwrappedRef = typeElement.getInnermostComponentReferenceElement();
    if (unwrappedRef == null) return null;

    // Foo<String, Bar> -> <String, Bar>
    final PsiReferenceParameterList typeArgumentList = unwrappedRef.getParameterList();
    if (typeArgumentList == null) return null;

    return typeArgumentList.getTypeParameterElements();
  }

  private static PsiType @Nullable [] typeArguments(@NotNull PsiType type) {
    PsiClassType unwrappedType = getComponentType(type);
    return unwrappedType == null ? null : unwrappedType.getParameters();
  }

  public static class MyTypeVisitor extends PsiTypeVisitor<PsiType> {
    private final GlobalSearchScope myResolveScope;
    private final PsiManager myManager;

    public MyTypeVisitor(PsiManager manager, GlobalSearchScope resolveScope) {
      myManager = manager;
      myResolveScope = resolveScope;
    }

    @Override
    public PsiType visitType(@NotNull PsiType type) {
      if (type.equals(PsiTypes.nullType())) return PsiType.getJavaLangObject(myManager, myResolveScope);
      return type;
    }

    @Override
    public PsiType visitCapturedWildcardType(@NotNull PsiCapturedWildcardType capturedWildcardType) {
      return capturedWildcardType.getUpperBound().accept(this);
    }
  }

  /**
   * @return list of type parameters which match expected type after substitution
   */
  @NotNull
  private static List<PsiTypeParameter> matchingTypeParameters(@NotNull PsiSubstitutor substitutor,
                                                               @NotNull PsiType expectedType,
                                                               @Type int kind) {
    final List<PsiTypeParameter> result = new SmartList<>();
    for (Map.Entry<PsiTypeParameter, PsiType> entry : substitutor.getSubstitutionMap().entrySet()) {
      final PsiType typeArgument = entry.getValue();
      if (typeArgument != null && matches(typeArgument, expectedType, kind)) {
        result.add(entry.getKey());
      }
    }
    return result;
  }

  private static boolean matches(@NotNull PsiType type, @NotNull PsiType expectedType, @Type int kind) {
    return switch (kind) {
      case TYPE_STRICTLY -> type.equals(expectedType);
      case TYPE_OR_SUBTYPE -> expectedType.isAssignableFrom(type);
      case TYPE_OR_SUPERTYPE -> type.isAssignableFrom(expectedType);
      default -> false;
    };
  }

  private static boolean hasNullSubstitutions(@NotNull PsiSubstitutor substitutor) {
    return substitutor.getSubstitutionMap().containsValue(null);
  }
}
