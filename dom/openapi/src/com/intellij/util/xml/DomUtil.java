/*
 * Copyright (c) 2005 Your Corporation. All Rights Reserved.
 */
package com.intellij.util.xml;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.*;
import com.intellij.util.ReflectionCache;
import com.intellij.util.ReflectionUtil;
import com.intellij.util.SmartList;
import com.intellij.util.xml.reflect.DomAttributeChildDescription;
import com.intellij.util.xml.reflect.DomCollectionChildDescription;
import com.intellij.util.xml.reflect.DomFixedChildDescription;
import com.intellij.util.xml.reflect.DomGenericInfo;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.util.*;

/**
 * @author peter
 */
public class DomUtil {
  public static final TypeVariable<Class<GenericValue>> GENERIC_VALUE_TYPE_VARIABLE = ReflectionCache.getTypeParameters(GenericValue.class)[0];

  private DomUtil() {
  }

  public static Class extractParameterClassFromGenericType(Type type) {
    return getGenericValueParameter(type);
  }

  public static boolean isGenericValueType(Type type) {
    return getGenericValueParameter(type) != null;
  }

  @Nullable
  public static <T extends DomElement> T findByName(@NotNull Collection<T> list, @NonNls @NotNull String name) {
    for (T element: list) {
      String elementName = element.getGenericInfo().getElementName(element);
      if (elementName != null && elementName.equals(name)) {
        return element;
      }
    }
    return null;
  }

  @NotNull
  public static String[] getElementNames(@NotNull Collection<? extends DomElement> list) {
    ArrayList<String> result = new ArrayList<String>(list.size());
    if (list.size() > 0) {
      for (DomElement element: list) {
        String name = element.getGenericInfo().getElementName(element);
        if (name != null) {
          result.add(name);
        }
      }
    }
    return result.toArray(new String[result.size()]);
  }

  @NotNull
  public static List<XmlTag> getElementTags(@NotNull Collection<? extends DomElement> list) {
    ArrayList<XmlTag> result = new ArrayList<XmlTag>(list.size());
    for (DomElement element: list) {
      XmlTag tag = element.getXmlTag();
      if (tag != null) {
        result.add(tag);
      }
    }
    return result;
  }

  @NotNull
  public static XmlTag[] getElementTags(@NotNull DomElement[] list) {
    XmlTag[] result = new XmlTag[list.length];
    int i = 0;
    for (DomElement element: list) {
      XmlTag tag = element.getXmlTag();
      if (tag != null) {
        result[i++] = tag;
      }
    }
    return result;
  }

  @Nullable
  public static List<JavaMethod> getFixedPath(DomElement element) {
    assert element.isValid();
    final LinkedList<JavaMethod> methods = new LinkedList<JavaMethod>();
    while (true) {
      final DomElement parent = element.getParent();
      if (parent instanceof DomFileElement) {
        break;
      }
      final JavaMethod method = getGetterMethod(element, parent);
      if (method == null) {
        return null;
      }
      methods.addFirst(method);
      element = element.getParent();
    }
    return methods;
  }

  @Nullable
  private static JavaMethod getGetterMethod(final DomElement element, final DomElement parent) {
    final String xmlElementName = element.getXmlElementName();
    final String namespace = element.getXmlElementNamespaceKey();
    final DomGenericInfo genericInfo = parent.getGenericInfo();

    if (element instanceof GenericAttributeValue) {
      final DomAttributeChildDescription description = genericInfo.getAttributeChildDescription(xmlElementName, namespace);
      assert description != null;
      return description.getGetterMethod();
    }

    final DomFixedChildDescription description = genericInfo.getFixedChildDescription(xmlElementName, namespace);
    return description != null ? description.getGetterMethod(description.getValues(parent).indexOf(element)) : null;
  }

  public static Class getGenericValueParameter(Type type) {
    return ReflectionUtil.substituteGenericType(GENERIC_VALUE_TYPE_VARIABLE, type);
  }

  @Nullable
  public static XmlElement getValueElement(GenericDomValue domValue) {
    if (domValue instanceof GenericAttributeValue) {
      final GenericAttributeValue value = (GenericAttributeValue)domValue;
      final XmlAttributeValue attributeValue = value.getXmlAttributeValue();
      return attributeValue == null ? value.getXmlAttribute() : attributeValue;
    } else {
      return domValue.getXmlTag();
    }
  }

  public static List<? extends DomElement> getIdentitySiblings(DomElement element) {
    final Method nameValueMethod = ElementPresentationManager.findNameValueMethod(element.getClass());
    if (nameValueMethod != null) {
      final NameValue nameValue = DomReflectionUtil.findAnnotationDFS(nameValueMethod, NameValue.class);
      if (nameValue == null || nameValue.unique()) {
        final String stringValue = ElementPresentationManager.getElementName(element);
        if (stringValue != null) {
          final DomElement parent = element.getManager().getIdentityScope(element);
          final DomGenericInfo domGenericInfo = parent.getGenericInfo();
          final String tagName = element.getXmlElementName();
          final DomCollectionChildDescription childDescription = domGenericInfo.getCollectionChildDescription(tagName, element.getXmlElementNamespaceKey());
          if (childDescription != null) {
            final ArrayList<DomElement> list = new ArrayList<DomElement>(childDescription.getValues(parent));
            list.remove(element);
            return list;
          }
        }
      }
    }
    return Collections.emptyList();
  }

  public static <T> List<T> getChildrenOfType(@NotNull final DomElement parent, final Class<T> type) {
    final List<T> result = new SmartList<T>();
    parent.acceptChildren(new DomElementVisitor() {
      public void visitDomElement(final DomElement element) {
        if (type.isInstance(element)) {
          result.add((T)element);
        }
      }
    });
    return result;
  }

  public static <T> List<T> getDefinedChildrenOfType(@NotNull final DomElement parent, final Class<T> type) {
    final List<T> result = new SmartList<T>();
    parent.acceptChildren(new DomElementVisitor() {
      public void visitDomElement(final DomElement element) {
        if (type.isInstance(element) && element.getXmlElement() != null) {
          result.add((T)element);
        }
      }
    });
    return result;
  }

  @Nullable
  public static DomElement findDuplicateNamedValue(DomElement element, String newName) {
    return ElementPresentationManager.findByName(getIdentitySiblings(element), newName);
  }

  public static boolean isAncestor(@NotNull DomElement ancestor, @NotNull DomElement descendant, boolean strict) {
    if (!strict && ancestor.equals(descendant)) return true;
    final DomElement parent = descendant.getParent();
    return parent != null && isAncestor(ancestor, parent, false);
  }

  public static void acceptAvailableChildren(final DomElement element, final DomElementVisitor visitor) {
    final XmlTag tag = element.getXmlTag();
    if (tag != null) {
      for (XmlTag xmlTag : tag.getSubTags()) {
        final DomElement childElement = element.getManager().getDomElement(xmlTag);
        if (childElement != null) {
          childElement.accept(visitor);
        }
      }
    }
  }

  public static Collection<Class> getAllInterfaces(final Class aClass, final Collection<Class> result) {
    final Class[] interfaces = ReflectionCache.getInterfaces(aClass);
    result.addAll(Arrays.asList(interfaces));
    if (aClass.getSuperclass() != null) {
      getAllInterfaces(aClass.getSuperclass(), result);
    }
    for (Class anInterface : interfaces) {
      getAllInterfaces(anInterface, result);
    }
    return result;
  }

  @Nullable
  public static <T> T getParentOfType(final DomElement element, final Class<T> requiredClass, final boolean strict) {
    for (DomElement curElement = strict && element != null? element.getParent() : element;
         curElement != null;
         curElement = curElement.getParent()) {
      if (requiredClass.isInstance(curElement)) {
        return (T)curElement;
      }
    }
    return null;
  }

  @Nullable
  public static DomElement getContextElement(@Nullable final Editor editor) {
    if(editor == null) return null;

    final Project project = editor.getProject();
    if (project == null) return null;

    final PsiFile file = PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument());
    if (!(file instanceof XmlFile)) {
      return null;
    }

    return getDomElement(file.findElementAt(editor.getCaretModel().getOffset()));
  }

  @Nullable
  public static DomElement getDomElement(@Nullable final PsiElement element) {
    if (element == null) return null;

    final Project project = element.getProject();
    final DomManager domManager = DomManager.getDomManager(project);
    final XmlAttribute attr = PsiTreeUtil.getParentOfType(element, XmlAttribute.class, false);
    if (attr != null) {
      final GenericAttributeValue value = domManager.getDomElement(attr);
      if (value != null) return value;
    }

    XmlTag tag = PsiTreeUtil.getParentOfType(element, XmlTag.class, false);
    while (tag != null) {
      final DomElement domElement = domManager.getDomElement(tag);
      if(domElement != null) return domElement;

      tag = tag.getParentTag();
    }
    return null;
  }
}
