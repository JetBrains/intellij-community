/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.util.xml.highlighting;

import com.intellij.codeInsight.daemon.impl.analysis.XmlHighlightVisitor;
import com.intellij.ide.IdeBundle;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.resolve.reference.PsiReferenceProvider;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.JavaClassReference;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.JavaClassReferenceProvider;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlElement;
import com.intellij.util.ReflectionCache;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.xml.*;
import com.intellij.util.xml.impl.ConvertContextImpl;
import com.intellij.util.xml.impl.DomManagerImpl;
import com.intellij.util.xml.impl.GenericDomValueReference;
import com.intellij.util.xml.impl.GenericValueReferenceProvider;
import com.intellij.util.xml.reflect.DomChildrenDescription;
import com.intellij.util.xml.reflect.DomCollectionChildDescription;
import com.intellij.util.xml.reflect.DomGenericInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * @author peter
 */
public class DomHighlightingHelperImpl extends DomHighlightingHelper {
  private final GenericValueReferenceProvider myProvider = new GenericValueReferenceProvider();
  private final DomElementAnnotationsManagerImpl myAnnotationsManager;

  public DomHighlightingHelperImpl(final DomElementAnnotationsManagerImpl annotationsManager) {
    myAnnotationsManager = annotationsManager;
  }

  public void runAnnotators(DomElement element, DomElementAnnotationHolder holder, Class<? extends DomElement> rootClass) {
    myAnnotationsManager.annotate(element, holder, rootClass);
  }

  @NotNull
  public List<DomElementProblemDescriptor> checkRequired(DomElement element, final DomElementAnnotationHolder holder) {
    final Required required = element.getAnnotation(Required.class);
    if (required != null) {
      final XmlElement xmlElement = element.getXmlElement();
      if (xmlElement == null) {
        if (required.value()) {
          if (element instanceof GenericAttributeValue) {
            return Arrays.asList(holder.createProblem(element, IdeBundle.message("attribute.0.should.be.defined", element.getXmlElementName())));
          }
          return Arrays.asList(holder.createProblem(element, IdeBundle.message("child.tag.0.should.be.defined", element.getXmlElementName())));
        }
      }
      else if (element instanceof GenericDomValue) {
        return ContainerUtil.createMaybeSingletonList(checkRequiredGenericValue((GenericDomValue)element, required, holder));
      }
    }
    if (element.getXmlElement() != null) {
      final SmartList<DomElementProblemDescriptor> list = new SmartList<DomElementProblemDescriptor>();
      final DomGenericInfo info = element.getGenericInfo();
      for (final DomChildrenDescription description : info.getChildrenDescriptions()) {
        if (description instanceof DomCollectionChildDescription && description.getValues(element).isEmpty()) {
          final DomCollectionChildDescription childDescription = (DomCollectionChildDescription)description;
          final Required annotation = description.getAnnotation(Required.class);
          if (annotation != null && annotation.value()) {
            list.add(holder.createProblem(element, childDescription,
                                          IdeBundle.message("child.tag.0.should.be.defined", description.getXmlElementName())));
          }
        }
      }
      return list;
    }
    return Collections.emptyList();
  }

  @NotNull
  public List<DomElementProblemDescriptor> checkExtendClass(GenericDomValue element, final DomElementAnnotationHolder holder) {
    final Class genericValueParameter = DomUtil.getGenericValueParameter(element.getDomElementType());
    if (genericValueParameter == null || !ReflectionCache.isAssignable(genericValueParameter, PsiClass.class)) {
      return Collections.emptyList();
    }

    final Object valueObject = element.getValue();
    if (valueObject instanceof PsiClass) {
      ExtendClass extend = element.getAnnotation(ExtendClass.class);
      if (extend != null) {
        return checkExtendClass(element, (PsiClass)valueObject, extend.value(), extend.instantiatable(), extend.canBeDecorator(), holder);
      }
      else {
        final PsiReference[] references = myProvider.getReferencesByElement(DomUtil.getValueElement(element));
        for (PsiReference reference : references) {
          if (reference instanceof JavaClassReference) {
            final PsiReferenceProvider psiReferenceProvider = ((JavaClassReference)reference).getProvider();
            final String[] value = psiReferenceProvider instanceof JavaClassReferenceProvider ? JavaClassReferenceProvider.EXTEND_CLASS_NAMES
              .getValue(((JavaClassReferenceProvider)psiReferenceProvider).getOptions()) : null;
            if (value != null && value.length != 0) {
              for (String className : value) {
                final List<DomElementProblemDescriptor> problemDescriptors =
                  checkExtendClass(element, ((PsiClass)valueObject), className, false, false, holder);
                if (!problemDescriptors.isEmpty()) {
                  return problemDescriptors;
                }
              }
            }
          }
        }
      }
    }
    return Collections.emptyList();
  }

  @NotNull
  private List<DomElementProblemDescriptor> checkExtendClass(final GenericDomValue element, final PsiClass value, final String name, final boolean instantiatable,
                                                             final boolean canBeDecorator,
                                                             final DomElementAnnotationHolder holder) {
    final Project project = element.getManager().getProject();
    PsiClass extendClass = PsiManager.getInstance(project).findClass(name, GlobalSearchScope.allScope(project));
    if (extendClass != null) {
      final SmartList<DomElementProblemDescriptor> list = new SmartList<DomElementProblemDescriptor>();
      if (!name.equals(value.getQualifiedName()) && !value.isInheritor(extendClass, true)) {
        String message = IdeBundle.message("class.is.not.a.subclass", value.getQualifiedName(), extendClass.getQualifiedName());
        list.add(holder.createProblem(element, message));
      }
      else if (instantiatable) {
        if (value.hasModifierProperty(PsiModifier.ABSTRACT)) {
          list.add(holder.createProblem(element, IdeBundle.message("class.is.not.concrete", value.getQualifiedName())));
        }
        else if (!value.hasModifierProperty(PsiModifier.PUBLIC)) {
          list.add(holder.createProblem(element, IdeBundle.message("class.is.not.public", value.getQualifiedName())));
        }
        else if (!hasDefaultConstructor(value)) {
          if (canBeDecorator) {
            boolean hasConstructor = false;

            for (PsiMethod method : value.getConstructors()) {
              final PsiParameterList psiParameterList = method.getParameterList();
              if (psiParameterList.getParametersCount() != 1) continue;
              final PsiType psiType = psiParameterList.getParameters()[0].getTypeElement().getType();
              if (psiType instanceof PsiClassType) {
                final PsiClass psiClass = ((PsiClassType)psiType).resolve();
                if (psiClass != null && InheritanceUtil.isInheritorOrSelf(psiClass, extendClass, true)) {
                  hasConstructor = true;
                  break;
                }
              }
            }
            if (!hasConstructor) {
              list.add(holder.createProblem(element, IdeBundle.message("class.decorator.or.has.default.constructor", value.getQualifiedName())));
            }
          }
          else {
            list.add(holder.createProblem(element, IdeBundle.message("class.has.no.default.constructor", value.getQualifiedName())));
          }
        }
      }
      return list;
    }
    return Collections.emptyList();
  }

  @NotNull
  public List<DomElementProblemDescriptor> checkResolveProblems(GenericDomValue element, final DomElementAnnotationHolder holder) {
    final XmlElement valueElement = DomUtil.getValueElement(element);
    if (valueElement != null && !isSoftReference(element)) {
      final SmartList<DomElementProblemDescriptor> list = new SmartList<DomElementProblemDescriptor>();
      final PsiReference[] psiReferences = myProvider.getReferencesByElement(valueElement);
      GenericDomValueReference domReference = null;
      for (final PsiReference reference : psiReferences) {
        if (reference instanceof GenericDomValueReference) {
          domReference = (GenericDomValueReference)reference;
          break;
        }
      }
      final Converter converter = WrappingConverter.getDeepestConverter(element.getConverter(), element);
      final boolean domReferenceResolveOK = domReference != null && !hasBadResolve(element, domReference)
        || converter instanceof ResolvingConverter && ((ResolvingConverter)converter).getAdditionalVariants().contains(element.getStringValue());
      boolean hasBadResolve = false;
      if (!domReferenceResolveOK) {
        for (final PsiReference reference : psiReferences) {
          if (reference != domReference && hasBadResolve(element, reference)) {
            hasBadResolve = true;
            list.add(holder.createResolveProblem(element, reference));
          }
        }
        final boolean isResolvingConverter = converter instanceof ResolvingConverter;
        if (!hasBadResolve &&
            (domReference != null || isResolvingConverter &&
                                     hasBadResolve(element, domReference = new GenericDomValueReference(element)))) {
          hasBadResolve = true;
          final String errorMessage = converter
            .getErrorMessage(element.getStringValue(), new ConvertContextImpl(DomManagerImpl.getDomInvocationHandler(element)));
          if (errorMessage != null && XmlHighlightVisitor.getErrorDescription(domReference) != null) {
            list.add(holder.createResolveProblem(element, domReference));
          }
        }
      }
      if (!hasBadResolve && psiReferences.length == 0 && element.getValue() == null) {
        final String errorMessage = converter
          .getErrorMessage(element.getStringValue(), new ConvertContextImpl(DomManagerImpl.getDomInvocationHandler(element)));
        if (errorMessage != null) {
          list.add(holder.createProblem(element, errorMessage));
        }
      }
      return list;
    }
    return Collections.emptyList();
  }

  @NotNull
  public List<DomElementProblemDescriptor> checkNameIdentity(DomElement element, final DomElementAnnotationHolder holder) {
    final String elementName = ElementPresentationManager.getElementName(element);
    if (StringUtil.isNotEmpty(elementName)) {
      final DomElement domElement = DomUtil.findDuplicateNamedValue(element, elementName);
      if (domElement != null) {
        final String typeName = ElementPresentationManager.getTypeNameForObject(element);
        final GenericDomValue genericDomValue = domElement.getGenericInfo().getNameDomElement(element);
        if (genericDomValue != null) {
          return Arrays.asList(holder.createProblem(genericDomValue, domElement.getRoot().equals(element.getRoot())
                                                                     ? IdeBundle.message("model.highlighting.identity", typeName)
                                                                     : IdeBundle.message("model.highlighting.identity.in.other.file", typeName,
                                                                                         domElement.getXmlTag().getContainingFile().getName())));
        }
      }
    }
    return Collections.emptyList();
  }

  private static boolean hasBadResolve(GenericDomValue value, PsiReference reference) {
    return XmlHighlightVisitor.hasBadResolve(reference);
  }

  private static boolean isSoftReference(GenericDomValue value) {
    final Resolve resolve = value.getAnnotation(Resolve.class);
    if (resolve != null && resolve.soft()) return true;

    final Convert convert = value.getAnnotation(Convert.class);
    if (convert != null && convert.soft()) return true;

    final Referencing referencing = value.getAnnotation(Referencing.class);
    if (referencing != null && referencing.soft()) return true;

    return false;
  }

  @Nullable
  private static DomElementProblemDescriptor checkRequiredGenericValue(final GenericDomValue child, final Required required,
                                                                       final DomElementAnnotationHolder annotator) {
    assert child.getXmlTag() != null;

    final String stringValue = child.getStringValue();
    if (stringValue == null) return null;

    if (required.nonEmpty() && isEmpty(child, stringValue)) {
      return annotator.createProblem(child, IdeBundle.message("value.must.not.be.empty"));
    }
    if (required.identifier() && !PsiManager.getInstance(child.getManager().getProject()).getNameHelper().isIdentifier(stringValue)) {
      return annotator.createProblem(child, IdeBundle.message("value.must.be.identifier"));
    }
    return null;
  }

  private static boolean isEmpty(final GenericDomValue child, final String stringValue) {
    if (stringValue.trim().length() != 0) {
      return false;
    }
    if (child instanceof GenericAttributeValue) {
      final XmlAttributeValue value = ((GenericAttributeValue)child).getXmlAttributeValue();
      if (value != null && value.getTextRange().isEmpty()) {
        return false;
      }
    }
    return true;
  }


  private static boolean hasDefaultConstructor(PsiClass clazz) {
    final PsiMethod[] constructors = clazz.getConstructors();
    if (constructors.length > 0) {
      for (PsiMethod cls: constructors) {
        if ((cls.hasModifierProperty(PsiModifier.PUBLIC) || cls.hasModifierProperty(PsiModifier.PROTECTED)) && cls.getParameterList().getParametersCount() == 0) {
          return true;
        }
      }
    } else {
      final PsiClass superClass = clazz.getSuperClass();
      return superClass == null || hasDefaultConstructor(superClass);
    }
    return false;
  }

}
