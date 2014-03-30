/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * @author ven
  */
public class GuessTypeParameters {
  private final JVMElementFactory myFactory;
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.daemon.impl.quickfix.GuessTypeParameters"); 

  public GuessTypeParameters(JVMElementFactory factory) {
    myFactory = factory;
  }

  private List<PsiType> matchingTypeParameters (PsiType[] paramVals, PsiTypeParameter[] params, ExpectedTypeInfo info) {
    PsiType type = info.getType();
    int kind = info.getKind();

    List<PsiType> result = new ArrayList<PsiType>();
    for (int i = 0; i < paramVals.length; i++) {
      PsiType val = paramVals[i];
      if (val != null) {
        switch (kind) {
          case ExpectedTypeInfo.TYPE_STRICTLY:
            if (val.equals(type)) result.add(myFactory.createType(params[i]));
            break;
          case ExpectedTypeInfo.TYPE_OR_SUBTYPE:
            if (type.isAssignableFrom(val)) result.add(myFactory.createType(params[i]));
            break;
          case ExpectedTypeInfo.TYPE_OR_SUPERTYPE:
            if (val.isAssignableFrom(type)) result.add(myFactory.createType(params[i]));
            break;
        }
      }
    }

    return result;
  }

  public void setupTypeElement (PsiTypeElement typeElement, ExpectedTypeInfo[] infos, PsiSubstitutor substitutor,
                                TemplateBuilder builder, @Nullable PsiElement context, PsiClass targetClass) {
    LOG.assertTrue(typeElement.isValid());
    ApplicationManager.getApplication().assertWriteAccessAllowed();

    PsiManager manager = typeElement.getManager();
    GlobalSearchScope scope = typeElement.getResolveScope();
    Project project = manager.getProject();

    if (infos.length == 1 && substitutor != null && substitutor != PsiSubstitutor.EMPTY) {
      ExpectedTypeInfo info = infos[0];
      Map<PsiTypeParameter, PsiType> map = substitutor.getSubstitutionMap();
      PsiType[] vals = map.values().toArray(PsiType.createArray(map.size()));
      PsiTypeParameter[] params = map.keySet().toArray(new PsiTypeParameter[map.size()]);

      List<PsiType> types = matchingTypeParameters(vals, params, info);
      if (!types.isEmpty()) {
        ContainerUtil.addAll(types, ExpectedTypesProvider.processExpectedTypes(infos, new MyTypeVisitor(manager, scope), project));
        builder.replaceElement(typeElement, new TypeExpression(project, types.toArray(PsiType.createArray(types.size()))));
        return;
      }
      else {
        PsiElementFactory factory = JavaPsiFacade.getInstance(manager.getProject()).getElementFactory();
        PsiType type = info.getType();
        PsiType defaultType = info.getDefaultType();
        try {
          PsiTypeElement inplaceTypeElement = ((PsiVariable)factory.createVariableDeclarationStatement("foo", type, null).getDeclaredElements()[0]).getTypeElement();

          PsiSubstitutor rawingSubstitutor = getRawingSubstitutor (context, targetClass);
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
                                                                   ExpectedTypeInfo.TYPE_STRICTLY,
                                                                   ((PsiClassType)defaultType).rawType(),
                                                                   info.getTailType());
              MyTypeVisitor visitor = new MyTypeVisitor(manager, scope);
              builder.replaceElement(refElement.getReferenceNameElement(),
                                     new TypeExpression(project, ExpectedTypesProvider.processExpectedTypes(new ExpectedTypeInfo[]{info1}, visitor, project)));
            }

            return;
          }
        }
        catch (IncorrectOperationException e) {
          LOG.error(e);
        }
      }
    }

    PsiType[] types = infos.length == 0 ? new PsiType[] {typeElement.getType()} : ExpectedTypesProvider.processExpectedTypes(infos, new MyTypeVisitor(manager, scope), project);
    builder.replaceElement(typeElement,
                           new TypeExpression(project, types));
  }

  private static PsiSubstitutor getRawingSubstitutor(PsiElement context, PsiClass targetClass) {
    if (context == null || targetClass == null) return PsiSubstitutor.EMPTY;

    PsiTypeParameterListOwner currContext = PsiTreeUtil.getParentOfType(context, PsiTypeParameterListOwner.class);
    PsiManager manager = context.getManager();
    PsiSubstitutor substitutor = PsiSubstitutor.EMPTY;
    while (currContext != null && !manager.areElementsEquivalent(currContext, targetClass)) {
      PsiTypeParameter[] typeParameters = currContext.getTypeParameters();
      substitutor = JavaPsiFacade.getInstance(context.getProject()).getElementFactory().createRawSubstitutor(substitutor, typeParameters);
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
    List<PsiType> types = new ArrayList<PsiType>();
    for (int i = 0; i < paramVals.length; i++) {
      PsiType val = paramVals[i];
      if (val == null) return SUBSTITUTED_NONE;
      if (type.equals(val)) {
        types.add(myFactory.createType(params[i]));
      }
    }

    if (!types.isEmpty()) {
      Project project = typeElement.getProject();
      PsiType substituted = rawingSubstitutor.substitute(type);
      if (!CommonClassNames.JAVA_LANG_OBJECT.equals(substituted.getCanonicalText()) && (toplevel || substituted.equals(type))) {
        types.add(substituted);
      }

      builder.replaceElement(typeElement, new TypeExpression(project, types.toArray(PsiType.createArray(types.size()))));
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
}
