/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */
package com.intellij.util.xml.impl;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.xml.XmlDocument;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.testFramework.LightVirtualFile;
import com.intellij.util.ReflectionCache;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.xml.*;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Set;

/**
 * @author peter
 */
public class DomImplUtil {
  private static final Logger LOG = Logger.getInstance("#com.intellij.util.xml.impl.DomImplUtil");

  private DomImplUtil() {
  }

  public static boolean isTagValueGetter(final JavaMethod method) {
    if (!isGetter(method)) {
      return false;
    }
    if (hasTagValueAnnotation(method)) {
      return true;
    }
    if ("getValue".equals(method.getName())) {
      final JavaMethodSignature signature = method.getSignature();
      final Class<?> declaringClass = method.getDeclaringClass();
      if (signature.findAnnotation(SubTag.class, declaringClass) != null) return false;
      if (signature.findAnnotation(SubTagList.class, declaringClass) != null) return false;
      if (signature.findAnnotation(Convert.class, declaringClass) != null ||
          signature.findAnnotation(Resolve.class, declaringClass) != null) {
        return !ReflectionCache.isAssignable(GenericDomValue.class, method.getReturnType());
      }
      if (ReflectionCache.isAssignable(DomElement.class, method.getReturnType())) return false;
      return true;
    }
    return false;
  }

  private static boolean hasTagValueAnnotation(final JavaMethod method) {
    return method.getAnnotation(TagValue.class) != null;
  }

  public static boolean isGetter(final JavaMethod method) {
    @NonNls final String name = method.getName();
    if (method.getGenericParameterTypes().length != 0) {
      return false;
    }
    final Type returnType = method.getGenericReturnType();
    if (name.startsWith("get")) {
      return returnType != void.class;
    }
    return name.startsWith("is") && DomReflectionUtil.canHaveIsPropertyGetterPrefix(returnType);
  }


  public static boolean isTagValueSetter(final JavaMethod method) {
    boolean setter = method.getName().startsWith("set") && method.getGenericParameterTypes().length == 1 && method.getReturnType() == void.class;
    return setter && (hasTagValueAnnotation(method) || "setValue".equals(method.getName()));
  }

  @Nullable
  public static DomNameStrategy getDomNameStrategy(final Class<?> rawType, boolean isAttribute) {
    Class aClass = null;
    if (isAttribute) {
      NameStrategyForAttributes annotation = DomReflectionUtil.findAnnotationDFS(rawType, NameStrategyForAttributes.class);
      if (annotation != null) {
        aClass = annotation.value();
      }
    }
    if (aClass == null) {
      NameStrategy annotation = DomReflectionUtil.findAnnotationDFS(rawType, NameStrategy.class);
      if (annotation != null) {
        aClass = annotation.value();
      }
    }
    if (aClass != null) {
      if (HyphenNameStrategy.class.equals(aClass)) return DomNameStrategy.HYPHEN_STRATEGY;
      if (JavaNameStrategy.class.equals(aClass)) return DomNameStrategy.JAVA_STRATEGY;
      try {
        return (DomNameStrategy)aClass.newInstance();
      }
      catch (InstantiationException e) {
        LOG.error(e);
      }
      catch (IllegalAccessException e) {
        LOG.error(e);
      }
    }
    return null;
  }

  public static List<XmlTag> findSubTags(XmlTag tag, final EvaluatedXmlName name, final DomInvocationHandler handler) {
    return ContainerUtil.findAll(tag.getSubTags(), new Condition<XmlTag>() {
      public boolean value(XmlTag childTag) {
        return isNameSuitable(name, childTag, handler);
      }
    });
  }

  private static boolean isNameSuitable(final XmlName name, final XmlTag tag, @NotNull final DomInvocationHandler handler) {
    return isNameSuitable(handler.createEvaluatedXmlName(name), tag, handler);
  }

  private static boolean isNameSuitable(final EvaluatedXmlName evaluatedXmlName, final XmlTag tag, @NotNull final DomInvocationHandler handler) {
    return isNameSuitable(evaluatedXmlName, tag.getLocalName(), tag.getName(), tag.getNamespace(), handler);
  }

  public static boolean isNameSuitable(final EvaluatedXmlName evaluatedXmlName,
                                        final String localName,
                                        final String qName,
                                        final String namespace,
                                        final DomInvocationHandler handler) {
    final String localName1 = evaluatedXmlName.getLocalName();
    return (localName1.equals(localName) || localName1.equals(qName)) && evaluatedXmlName.isNamespaceAllowed(handler, namespace);
  }

  public static boolean containsTagName(final Set<XmlName> qnames1, final XmlTag subTag, final DomInvocationHandler handler) {
    return ContainerUtil.find(qnames1, new Condition<XmlName>() {
      public boolean value(XmlName name) {
        return isNameSuitable(name, subTag, handler);
      }
    }) != null;
  }

  @Nullable
  public static String getRootTagName(final PsiFile file) throws IOException {
    final VirtualFile virtualFile = file.getVirtualFile();
    if (virtualFile instanceof LightVirtualFile && file instanceof XmlFile && FileDocumentManager.getInstance().getCachedDocument(virtualFile) == null) {
      final XmlDocument document = ((XmlFile)file).getDocument();
      if (document != null) {
        final XmlTag tag = document.getRootTag();
        if (tag != null) {
          return tag.getLocalName();
        }
      }
      return null;
    }

    final NanoXmlUtil.RootTagNameBuilder builder = new NanoXmlUtil.RootTagNameBuilder();
    NanoXmlUtil.parseFile(file, builder);
    return builder.getResult();
  }

}
