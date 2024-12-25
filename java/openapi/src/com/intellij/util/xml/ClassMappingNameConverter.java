// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.xml;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiType;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.codeStyle.SuggestedNameInfo;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * @see MappingClass
 * @author Dmitry Avdeev
 */
public class ClassMappingNameConverter extends ResolvingConverter.StringConverter {

  private static final Logger LOG = Logger.getInstance(ClassMappingNameConverter.class);

  @Override
  public @NotNull Collection<String> getVariants(@NotNull ConvertContext context) {
    DomElement parent = context.getInvocationElement().getParent();
    assert parent != null;
    List<DomElement> children = DomUtil.getDefinedChildren(parent, true, true);
    DomElement classElement = ContainerUtil.find(children, domElement -> domElement.getAnnotation(MappingClass.class) != null);
    if (classElement == null) return Collections.emptyList();
    Object value = ((GenericDomValue<?>)classElement).getValue();
    if (value == null) return Collections.emptyList();
    PsiType type;
    if (value instanceof PsiType) {
      type = (PsiType)value;
    }
    else if (value instanceof PsiClass) {
      type = PsiTypesUtil.getClassType((PsiClass)value);
    }
    else {
      LOG.error("wrong type: " + value.getClass());
      return Collections.emptyList();
    }
    JavaCodeStyleManager codeStyleManager = JavaCodeStyleManager.getInstance(context.getProject());
    SuggestedNameInfo info = codeStyleManager.suggestVariableName(VariableKind.LOCAL_VARIABLE, null, null, type);
    return Arrays.asList(info.names);
  }

  @Override
  public PsiElement resolve(String o, @NotNull ConvertContext context) {
    DomElement parent = context.getInvocationElement().getParent();
    assert parent != null;
    return parent.getXmlElement();
  }

  @Override
  public boolean isReferenceTo(@NotNull PsiElement element,
                               String stringValue,
                               @Nullable String resolveResult,
                               @NotNull ConvertContext context) {
    return element.getManager().areElementsEquivalent(element, resolve(stringValue, context));
  }
}
