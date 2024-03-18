// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.xml.impl;

import com.intellij.java.JavaBundle;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.JavaClassReference;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.xml.XmlElement;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ProcessingContext;
import com.intellij.util.ReflectionUtil;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.JBIterable;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomUtil;
import com.intellij.util.xml.ExtendClass;
import com.intellij.util.xml.GenericDomValue;
import com.intellij.util.xml.highlighting.DomCustomAnnotationChecker;
import com.intellij.util.xml.highlighting.DomElementAnnotationHolder;
import com.intellij.util.xml.highlighting.DomElementProblemDescriptor;
import com.intellij.util.xml.highlighting.DomHighlightingHelper;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.stream.Collectors;

public final class ExtendsClassChecker extends DomCustomAnnotationChecker<ExtendClass> {
  private static final GenericValueReferenceProvider ourProvider = new GenericValueReferenceProvider();

  @Override
  @NotNull
  public Class<ExtendClass> getAnnotationClass() {
    return ExtendClass.class;
  }

  @Override
  public List<DomElementProblemDescriptor> checkForProblems(@NotNull final ExtendClass extend,
                                                            @NotNull final DomElement _element,
                                                            @NotNull final DomElementAnnotationHolder holder,
                                                            @NotNull final DomHighlightingHelper helper) {
    if (!(_element instanceof GenericDomValue element)) return Collections.emptyList();

    if (!isPsiClassType(element)) return Collections.emptyList();

    final Object valueObject = element.getValue();
    PsiClass psiClass = null;

    if (valueObject instanceof PsiClass cls) {
      psiClass = cls;
    }
    else if (valueObject instanceof PsiClassType classType) {
      psiClass = classType.resolve();
    }

    if (psiClass != null) {
      return checkExtendClass(element, psiClass, extend.value(),
                              extend.instantiatable(), extend.canBeDecorator(), extend.allowInterface(),
                              extend.allowNonPublic(), extend.allowAbstract(), extend.allowEnum(), holder);
    }
    return Collections.emptyList();
  }

  @NotNull
  public static List<DomElementProblemDescriptor> checkExtendClass(final GenericDomValue element,
                                                                   final PsiClass value,
                                                                   final String[] names,
                                                                   final boolean instantiatable,
                                                                   final boolean canBeDecorator,
                                                                   final boolean allowInterface,
                                                                   final boolean allowNonPublic,
                                                                   final boolean allowAbstract,
                                                                   final boolean allowEnum,
                                                                   final DomElementAnnotationHolder holder) {
    final Project project = element.getManager().getProject();
    Set<PsiClass> toExtend =
      Arrays.stream(names)
        .filter(Objects::nonNull)
        .map(s -> JavaPsiFacade.getInstance(project).findClass(s, GlobalSearchScope.allScope(project)))
        .filter(Objects::nonNull)
        .collect(Collectors.toSet());

    final SmartList<DomElementProblemDescriptor> list = new SmartList<>();
    if (toExtend.size() > 0) {
      if (!Arrays.asList(names).contains(value.getQualifiedName())
          && ContainerUtil.find(toExtend, aClass -> value.isInheritor(aClass, true)) == null) {
        String message = JavaBundle.message("class.is.not.a.subclass", value.getQualifiedName(), StringUtil.join(names, ","));
        list.add(holder.createProblem(element, message));
      }
    }

    if (instantiatable) {
      if (value.hasModifierProperty(PsiModifier.ABSTRACT)) {
        list.add(holder.createProblem(element, JavaBundle.message("class.is.not.concrete", value.getQualifiedName())));
      }
      else if (!allowNonPublic && !value.hasModifierProperty(PsiModifier.PUBLIC)) {
        list.add(holder.createProblem(element, JavaBundle.message("class.is.not.public", value.getQualifiedName())));
      }
      else if (!PsiUtil.hasDefaultConstructor(value, true, true)) {
        if (canBeDecorator) {
          boolean hasConstructor = false;

          for (PsiMethod method : value.getConstructors()) {
            final PsiParameterList psiParameterList = method.getParameterList();
            if (psiParameterList.getParametersCount() != 1) continue;
            PsiTypeElement typeElement = psiParameterList.getParameters()[0].getTypeElement();
            if (typeElement != null) {
              final PsiType psiType = typeElement.getType();
              if (psiType instanceof PsiClassType) {
                final PsiClass psiClass = ((PsiClassType)psiType).resolve();
                if (psiClass != null &&
                    ContainerUtil.find(toExtend, aClass -> InheritanceUtil.isInheritorOrSelf(psiClass, aClass, true)) != null) {
                  hasConstructor = true;
                  break;
                }
              }
            }
          }
          if (!hasConstructor) {
            list.add(
              holder.createProblem(element, JavaBundle.message("class.decorator.or.has.default.constructor", value.getQualifiedName())));
          }
        }
        else {
          list.add(holder.createProblem(element, JavaBundle.message("class.has.no.default.constructor", value.getQualifiedName())));
        }
      }
    }
    if (!allowInterface && value.isInterface()) {
      list.add(holder.createProblem(element, JavaBundle.message("interface.not.allowed", value.getQualifiedName())));
    }
    if (!allowEnum && value.isEnum()) {
      list.add(holder.createProblem(element, JavaBundle.message("enum.not.allowed", value.getQualifiedName())));
    }
    if (!allowAbstract && value.hasModifierProperty(PsiModifier.ABSTRACT) && !value.isInterface()) {
      list.add(holder.createProblem(element, JavaBundle.message("abstract.class.not.allowed", value.getQualifiedName())));
    }
    return list;
  }

  public static List<DomElementProblemDescriptor> checkExtendsClassInReferences(final GenericDomValue element,
                                                                                final DomElementAnnotationHolder holder) {
    if (!isPsiClassType(element)) {
      return Collections.emptyList();
    }

    final Object valueObject = element.getValue();
    if (!(valueObject instanceof PsiClass)) return Collections.emptyList();

    final XmlElement valueElement = DomUtil.getValueElement(element);
    if (valueElement == null) return Collections.emptyList();

    PsiReference[] references = ourProvider.getReferencesByElement(valueElement, new ProcessingContext());
    String[] superClasses = JBIterable.of(references)
      .filter(JavaClassReference.class)
      .unique(o -> o.getProvider())
      .flatten(o -> o.getSuperClasses())
      .unique()
      .toArray(ArrayUtil.EMPTY_STRING_ARRAY);
    return checkExtendClass(element,
                            ((PsiClass)valueObject),
                            superClasses,
                            false,
                            false,
                            true,
                            false,
                            true,
                            true,
                            holder);
  }

  private static boolean isPsiClassType(GenericDomValue element) {
    final Class genericValueParameter = DomUtil.getGenericValueParameter(element.getDomElementType());
    if (genericValueParameter != null && (ReflectionUtil.isAssignable(genericValueParameter, PsiClass.class) ||
                                          ReflectionUtil.isAssignable(genericValueParameter, PsiType.class))) {
      return true;
    }
    return false;
  }
}
