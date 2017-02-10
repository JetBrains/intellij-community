/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.refactoring.typeCook;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiDiamondTypeUtil;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiUtil;
import com.intellij.refactoring.typeCook.deductive.PsiTypeVariableFactory;
import com.intellij.util.IncorrectOperationException;

import java.util.HashSet;
import java.util.Set;

/**
 * Created by IntelliJ IDEA.
 * User: db
 * Date: 30.07.2003
 * Time: 18:57:30
 * To change this template use Options | File Templates.
 */
public class Util {
  private static final Logger LOG = Logger.getInstance("#com.intellij.refactoring.typeCook.Util");

  public static PsiType createArrayType(PsiType theType, int level) {
    while (level-- > 0) {
      theType = theType.createArrayType();
    }

    return theType;
  }

  public static PsiClassType.ClassResolveResult resolveType(PsiType type) {
    final PsiClassType.ClassResolveResult resolveResult = PsiUtil.resolveGenericsClassInType(type);
    final PsiClass aClass = resolveResult.getElement();
    if (aClass instanceof PsiAnonymousClass) {
      final PsiClassType baseClassType = ((PsiAnonymousClass)aClass).getBaseClassType();
      return resolveType(resolveResult.getSubstitutor().substitute(baseClassType));
    }
    return resolveResult;
  }

  public static PsiType normalize(PsiType t, boolean objectBottom) {
    if (t instanceof PsiArrayType) {
      PsiType normType = normalize(((PsiArrayType)t).getComponentType(), objectBottom);

      return normType == null ? null : normType.createArrayType();
    }
    else if (t instanceof PsiClassType) {
      PsiClassType.ClassResolveResult result = resolveType(t);

      if (result == null) {
        return null;
      }

      PsiClass aclass = result.getElement();
      PsiSubstitutor subst = result.getSubstitutor();
      PsiManager manager = aclass.getManager();

      PsiSubstitutor newbst = PsiSubstitutor.EMPTY;
      boolean anyBottom = false;
      for (PsiTypeParameter typeParameter : PsiUtil.typeParametersIterable(aclass)) {
        PsiType p = subst.substitute(typeParameter);

        if (p != null) {
          PsiType pp = normalize(p, objectBottom);

          if (pp == null) {
            return null;
          }

          if (pp == Bottom.BOTTOM || (objectBottom && pp.getCanonicalText().equals(CommonClassNames.JAVA_LANG_OBJECT))) {
            anyBottom = true;
          }

          newbst = newbst.put(typeParameter, pp);
        }
        else {
          anyBottom = true;
        }
      }

      if (anyBottom || newbst == PsiSubstitutor.EMPTY) {
        newbst = JavaPsiFacade.getInstance(manager.getProject()).getElementFactory().createRawSubstitutor(aclass);
      }

      return JavaPsiFacade.getInstance(manager.getProject()).getElementFactory().createType(aclass, newbst);
    }
    else {
      return t;
    }
  }

  public static boolean isRaw(PsiType t, final Settings settings) {
    return isRaw(t, settings, true);
  }

  private static boolean isRaw(PsiType t, final Settings settings, final boolean upper) {
    if (t instanceof PsiClassType) {
      final PsiClassType.ClassResolveResult resolveResult = resolveType(t);

      if (resolveResult.getElement() == null) {
        return false;
      }

      if (PsiClassType.isRaw(resolveResult)) {
        return true;
      }

      final PsiSubstitutor subst = resolveResult.getSubstitutor();
      final PsiClass element = resolveResult.getElement();
      final PsiManager manager = element.getManager();

      if (settings.cookObjects() && upper &&
          t.equals(PsiType.getJavaLangObject(manager, GlobalSearchScope.allScope(manager.getProject())))) {
        return true;
      }

      for (PsiTypeParameter parameter : PsiUtil.typeParametersIterable(element)) {
        final PsiType actual = subst.substitute(parameter);
        if (isRaw(actual, settings, false)) return true;
      }

      return false;
    }
    else if (t instanceof PsiArrayType) {
      return !settings.preserveRawArrays() && isRaw(((PsiArrayType)t).getComponentType(), settings, upper);
    }

    return false;
  }

  /**
   * convert external raw types to types explicitly parameterized by Bottom
   */
  public static PsiType banalize(final PsiType t) {
    if (t instanceof PsiClassType) {
      final PsiClassType.ClassResolveResult result = resolveType(t);
      final PsiClass theClass = result.getElement();

      if (theClass == null) {
        return t;
      }

      final PsiSubstitutor theSubst = result.getSubstitutor();
      final PsiManager theManager = theClass.getManager();

      PsiSubstitutor subst = PsiSubstitutor.EMPTY;

      for (final PsiTypeParameter theParm : theSubst.getSubstitutionMap().keySet()) {
        final PsiType actualType = theSubst.substitute(theParm);

        if (actualType == null /*|| actualType instanceof PsiWildcardType*/) {
          subst = subst.put(theParm, Bottom.BOTTOM);
        }
        else if (actualType instanceof PsiWildcardType) {
          final PsiWildcardType wctype = (PsiWildcardType)actualType;
          final PsiType bound = wctype.getBound();

          if (bound == null) {
            subst = subst.put(theParm, actualType);
          }
          else {
            final PsiType banabound = banalize(bound);

            subst = subst.put(theParm, wctype.isExtends()
                                       ? PsiWildcardType.createExtends(theManager, banabound)
                                       : PsiWildcardType.createSuper(theManager, banabound));
          }
        }
        else {
          final PsiType banType = banalize(actualType);

          if (banType == null) {
            return t;
          }

          subst = subst.put(theParm, banType);
        }
      }

      return JavaPsiFacade.getInstance(theManager.getProject()).getElementFactory().createType(theClass, subst);
    }
    else if (t instanceof PsiArrayType) {
      return banalize(((PsiArrayType)t).getComponentType()).createArrayType();
    }

    return t;
  }

  public static PsiSubstitutor composeSubstitutors(PsiSubstitutor f, PsiSubstitutor g) {
    if (f == PsiSubstitutor.EMPTY) {
      return g;
    }

    PsiSubstitutor subst = PsiSubstitutor.EMPTY;
    Set<PsiTypeParameter> base = g.getSubstitutionMap().keySet();

    for (PsiTypeParameter p : base) {
      PsiType type = g.substitute(p);
      subst = subst.put(p, type == null ? null : f.substitute(type));
    }

    return subst;
  }

  public static boolean bindsTypeParameters(PsiType t, Set<PsiTypeParameter> params) {
    if (t instanceof PsiWildcardType) {
      final PsiWildcardType wct = ((PsiWildcardType)t);
      final PsiType bound = wct.getBound();

      return bound != null && wct.isExtends() && bindsTypeParameters(bound, params);
    }

    final PsiClassType.ClassResolveResult result = resolveType(t);
    final PsiClass theClass = result.getElement();
    final PsiSubstitutor theSubst = result.getSubstitutor();

    if (theClass == null) {
      return false;
    }

    if (theClass instanceof PsiTypeParameter) {
      return params == null || params.contains(theClass);
    }
    else if (theClass.hasTypeParameters()) {
      for (PsiTypeParameter parameter : PsiUtil.typeParametersIterable(theClass)) {
        PsiType bound = theSubst.substitute(parameter);

        if (bound != null && bindsTypeParameters(bound, params)) {
          return true;
        }
      }
    }

    return false;
  }

  public static PsiType getType(PsiElement element) {
    if (element instanceof PsiVariable) {
      return ((PsiVariable)element).getType();
    }
    else if (element instanceof PsiExpression) {
      return ((PsiExpression)element).getType();
    }
    else if (element instanceof PsiMethod) {
      return ((PsiMethod)element).getReturnType();
    }

    return null;
  }

  public static PsiType createParameterizedType(final PsiType t, final PsiTypeVariableFactory factory, final PsiElement context) {
    return createParameterizedType(t, factory, true, context);
  }

  public static PsiType createParameterizedType(final PsiType t, final PsiTypeVariableFactory factory) {
    return createParameterizedType(t, factory, true, null);
  }

  private static PsiType createParameterizedType(final PsiType t,
                                                 final PsiTypeVariableFactory factory,
                                                 final boolean upper,
                                                 final PsiElement context) {
    if (t == null || (upper && t.getCanonicalText().equals(CommonClassNames.JAVA_LANG_OBJECT))) {
      return factory.create(context);
    }

    if (t instanceof PsiClassType) {
      final PsiClassType.ClassResolveResult result = resolveType(t);
      final PsiSubstitutor aSubst = result.getSubstitutor();
      final PsiClass aClass = result.getElement();

      PsiSubstitutor theSubst = PsiSubstitutor.EMPTY;

      final Set<PsiTypeVariable> cluster = new HashSet<>();

      for (final PsiTypeParameter parm : aSubst.getSubstitutionMap().keySet()) {
        final PsiType type = createParameterizedType(aSubst.substitute(parm), factory, false, context);

        if (type instanceof PsiTypeVariable) {
          cluster.add((PsiTypeVariable)type);
        }

        theSubst = theSubst.put(parm, type);
      }

      if (cluster.size() > 1) {
        factory.registerCluster(cluster);
      }

      return JavaPsiFacade.getInstance(aClass.getProject()).getElementFactory().createType(aClass, theSubst);
    }
    else if (t instanceof PsiArrayType) {
      return createParameterizedType(((PsiArrayType)t).getComponentType(), factory, upper, context).createArrayType();
    }

    return t;
  }

  public static boolean bindsTypeVariables(final PsiType t) {
    if (t == null) {
      return false;
    }

    if (t instanceof PsiTypeVariable) {
      return true;
    }

    if (t instanceof PsiArrayType) {
      return bindsTypeVariables(((PsiArrayType)t).getComponentType());
    }

    if (t instanceof PsiWildcardType) {
      return bindsTypeVariables(((PsiWildcardType)t).getBound());
    }

    if (t instanceof PsiIntersectionType) {
      final PsiType[] conjuncts = ((PsiIntersectionType)t).getConjuncts();
      for (PsiType conjunct : conjuncts) {
        if (bindsTypeVariables(conjunct)) return true;
      }
      return false;
    }

    final PsiClassType.ClassResolveResult result = resolveType(t);

    if (result.getElement() != null) {
      final PsiSubstitutor subst = result.getSubstitutor();

      for (final PsiType psiType : subst.getSubstitutionMap().values()) {
        if (bindsTypeVariables(psiType)) {
          return true;
        }
      }
    }

    return false;
  }

  public static void changeType(final PsiElement element, final PsiType type) {
    try {
      if (element instanceof PsiTypeCastExpression) {
        final PsiTypeCastExpression cast = ((PsiTypeCastExpression)element);

        cast.getCastType().replace(JavaPsiFacade.getInstance(cast.getProject()).getElementFactory().createTypeElement(type));
      }
      else if (element instanceof PsiVariable) {
        final PsiVariable field = ((PsiVariable)element);

        field.normalizeDeclaration();
        field.getTypeElement().replace(JavaPsiFacade.getInstance(field.getProject()).getElementFactory().createTypeElement(type));
      }
      else if (element instanceof PsiMethod) {
        final PsiMethod method = ((PsiMethod)element);

        method.getReturnTypeElement().replace(JavaPsiFacade.getInstance(method.getProject()).getElementFactory().createTypeElement(type));
      }
      else if (element instanceof PsiNewExpression) {
        final PsiNewExpression newx = (PsiNewExpression)element;
        final PsiClassType.ClassResolveResult result = resolveType(type);

        if (result == null) {
          return;
        }

        final PsiSubstitutor subst = result.getSubstitutor();
        final PsiTypeParameter[] parms = result.getElement().getTypeParameters();

        if (parms.length > 0 && subst.substitute(parms[0]) != null) {
          PsiJavaCodeReferenceElement classReference = newx.getClassOrAnonymousClassReference();
          PsiReferenceParameterList list = classReference.getParameterList();

          if (list == null) {
            return;
          }

          final PsiElementFactory factory = JavaPsiFacade.getInstance(newx.getProject()).getElementFactory();

          PsiTypeElement[] elements = list.getTypeParameterElements();
          for (PsiTypeElement element1 : elements) {
            element1.delete();
          }

          for (PsiTypeParameter parm : parms) {
            PsiType aType = subst.substitute(parm);

            if (aType instanceof PsiWildcardType) {
              aType = ((PsiWildcardType)aType).getBound();
            }

            list
              .add(factory.createTypeElement(aType == null ? PsiType.getJavaLangObject(list.getManager(), list.getResolveScope()) : aType));
          }

          if (PsiDiamondTypeUtil.canCollapseToDiamond(newx, newx, newx.getType())) {
            PsiDiamondTypeUtil.replaceExplicitWithDiamond(list);
          }
        }
      }
      else {
        LOG.error("Unexpected element type " + element.getClass().getName());
      }
    }
    catch (IncorrectOperationException e) {
      LOG.error("Incorrect Operation Exception thrown in CastRole.\n");
    }
  }
}