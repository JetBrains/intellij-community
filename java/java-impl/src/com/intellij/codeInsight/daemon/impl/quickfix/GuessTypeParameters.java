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
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.ExpectedTypeInfo;
import com.intellij.codeInsight.ExpectedTypeInfo.*;
import com.intellij.codeInsight.ExpectedTypesProvider;
import com.intellij.codeInsight.intention.impl.TypeExpression;
import com.intellij.codeInsight.template.TemplateBuilder;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.intellij.codeInsight.ExpectedTypeInfo.*;
import static com.intellij.util.containers.ContainerUtil.map;

/**
 * @author ven
  */
public class GuessTypeParameters {

  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.daemon.impl.quickfix.GuessTypeParameters");

  private final Project myProject;
  private final JVMElementFactory myFactory;

  public GuessTypeParameters(@NotNull Project project, @NotNull JVMElementFactory factory) {
    myProject = project;
    myFactory = factory;
  }

  public void setupTypeElement (PsiTypeElement typeElement, ExpectedTypeInfo[] infos, PsiSubstitutor substitutor,
                                TemplateBuilder builder, @Nullable PsiElement context, PsiClass targetClass) {
    LOG.assertTrue(typeElement.isValid());
    ApplicationManager.getApplication().assertWriteAccessAllowed();

    PsiManager manager = typeElement.getManager();
    GlobalSearchScope scope = typeElement.getResolveScope();

    if (infos.length == 1 && substitutor != null && substitutor != PsiSubstitutor.EMPTY) {
      ExpectedTypeInfo info = infos[0];
      Map<PsiTypeParameter, PsiType> map = substitutor.getSubstitutionMap();
      PsiType[] vals = map.values().toArray(PsiType.createArray(map.size()));
      PsiTypeParameter[] params = map.keySet().toArray(new PsiTypeParameter[map.size()]);

      final List<PsiTypeParameter> matchedParameters = matchingTypeParameters(substitutor, info.getType(), info.getKind());
      if (!matchedParameters.isEmpty()) {
        final List<PsiType> types = map(matchedParameters, it -> myFactory.createType(it));
        ContainerUtil.addAll(types, ExpectedTypesProvider.processExpectedTypes(infos, new MyTypeVisitor(manager, scope), myProject));
        builder.replaceElement(typeElement, new TypeExpression(myProject, types.toArray(PsiType.createArray(types.size()))));
        return;
      }
      else {
        PsiElementFactory factory = JavaPsiFacade.getInstance(myProject).getElementFactory();
        PsiType type = info.getType();
        PsiType defaultType = info.getDefaultType();
        try {
          PsiTypeElement inplaceTypeElement = ((PsiVariable)factory.createVariableDeclarationStatement("foo", type, null).getDeclaredElements()[0]).getTypeElement();

          PsiSubstitutor rawingSubstitutor = getRawingSubstitutor (myProject, context, targetClass);
          int substitionResult = substituteToTypeParameters(typeElement, inplaceTypeElement, vals, params, builder, rawingSubstitutor, true);
          if (substitionResult != SUBSTITUTED_NONE) {
            if (substitionResult == SUBSTITUTED_IN_PARAMETERS) {
              PsiJavaCodeReferenceElement refElement = typeElement.getInnermostComponentReferenceElement();
              LOG.assertTrue(refElement != null && refElement.getReferenceNameElement() != null);
              type = getComponentType(type);
              LOG.assertTrue(type != null);
              defaultType = getComponentType(defaultType);
              LOG.assertTrue(defaultType != null);
              ExpectedTypeInfo info1 = ExpectedTypesProvider.createInfo(((PsiClassType)defaultType).rawType(),
                                                                   TYPE_STRICTLY,
                                                                   ((PsiClassType)defaultType).rawType(),
                                                                   info.getTailType());
              MyTypeVisitor visitor = new MyTypeVisitor(manager, scope);
              builder.replaceElement(refElement.getReferenceNameElement(),
                                     new TypeExpression(myProject, ExpectedTypesProvider.processExpectedTypes(new ExpectedTypeInfo[]{info1}, visitor, myProject)));
            }

            return;
          }
        }
        catch (IncorrectOperationException e) {
          LOG.error(e);
        }
      }
    }

    PsiType[] types = infos.length == 0 ? new PsiType[] {typeElement.getType()} : ExpectedTypesProvider.processExpectedTypes(infos, new MyTypeVisitor(manager, scope), myProject);
    builder.replaceElement(typeElement,
                           new TypeExpression(myProject, types));
  }

  private static PsiSubstitutor getRawingSubstitutor(Project project, PsiElement context, PsiClass targetClass) {
    if (context == null || targetClass == null) return PsiSubstitutor.EMPTY;

    PsiTypeParameterListOwner currContext = PsiTreeUtil.getParentOfType(context, PsiTypeParameterListOwner.class);
    PsiManager manager = context.getManager();
    PsiSubstitutor substitutor = PsiSubstitutor.EMPTY;
    while (currContext != null && !manager.areElementsEquivalent(currContext, targetClass)) {
      PsiTypeParameter[] typeParameters = currContext.getTypeParameters();
      substitutor = JavaPsiFacade.getInstance(project).getElementFactory().createRawSubstitutor(substitutor, typeParameters);
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

  private int substituteToTypeParameters (PsiTypeElement typeElement,
                                          PsiTypeElement inplaceTypeElement,
                                          PsiType[] paramVals,
                                          PsiTypeParameter[] params,
                                          TemplateBuilder builder,
                                          PsiSubstitutor rawingSubstitutor,
                                          boolean toplevel) {
    PsiType type = inplaceTypeElement.getType();
    List<PsiType> types = new ArrayList<>();
    for (int i = 0; i < paramVals.length; i++) {
      PsiType val = paramVals[i];
      if (val == null) return SUBSTITUTED_NONE;
      if (type.equals(val)) {
        types.add(myFactory.createType(params[i]));
      }
    }

    if (!types.isEmpty()) {
      PsiType substituted = rawingSubstitutor.substitute(type);
      if (!CommonClassNames.JAVA_LANG_OBJECT.equals(substituted.getCanonicalText()) && (toplevel || substituted.equals(type))) {
        types.add(substituted);
      }

      builder.replaceElement(typeElement, new TypeExpression(myProject, types.toArray(PsiType.createArray(types.size()))));
      return toplevel ? SUBSTITUTED_IN_REF : SUBSTITUTED_IN_PARAMETERS;
    }

    boolean substituted = false;
    PsiJavaCodeReferenceElement ref = typeElement.getInnermostComponentReferenceElement();
    PsiJavaCodeReferenceElement inplaceRef = inplaceTypeElement.getInnermostComponentReferenceElement();
    if (ref != null) {
      LOG.assertTrue(inplaceRef != null);
      PsiTypeElement[] innerTypeElements = ref.getParameterList().getTypeParameterElements();
      PsiTypeElement[] inplaceInnerTypeElements = inplaceRef.getParameterList().getTypeParameterElements();
      for (int i = 0; i < innerTypeElements.length; i++) {
        substituted |= substituteToTypeParameters(innerTypeElements[i], inplaceInnerTypeElements[i], paramVals, params, builder,
                                                  rawingSubstitutor, false) != SUBSTITUTED_NONE;
      }
    }

    return substituted ? SUBSTITUTED_IN_PARAMETERS : SUBSTITUTED_NONE;
  }

  public static class MyTypeVisitor extends PsiTypeVisitor<PsiType> {
    private final GlobalSearchScope myResolveScope;
    private final PsiManager myManager;

    public MyTypeVisitor(PsiManager manager, GlobalSearchScope resolveScope) {
      myManager = manager;
      myResolveScope = resolveScope;
    }

    @Override
    public PsiType visitType(PsiType type) {
      if (type.equals(PsiType.NULL)) return PsiType.getJavaLangObject(myManager, myResolveScope);
      return type;
    }

    @Override
    public PsiType visitCapturedWildcardType(PsiCapturedWildcardType capturedWildcardType) {
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
    switch (kind) {
      case TYPE_STRICTLY:
        return type.equals(expectedType);
      case TYPE_OR_SUBTYPE:
        return expectedType.isAssignableFrom(type);
      case TYPE_OR_SUPERTYPE:
        return type.isAssignableFrom(expectedType);
      default:
        return false;
    }
  }
}
