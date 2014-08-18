/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.util.xml;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Condition;
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
 * @see com.intellij.util.xml.MappingClass
 * @author Dmitry Avdeev
 */
public class ClassMappingNameConverter extends ResolvingConverter.StringConverter {

  private final static Logger LOG = Logger.getInstance(ClassMappingNameConverter.class);

  @NotNull
  @Override
  public Collection<String> getVariants(ConvertContext context) {
    DomElement parent = context.getInvocationElement().getParent();
    assert parent != null;
    List<DomElement> children = DomUtil.getDefinedChildren(parent, true, true);
    DomElement classElement = ContainerUtil.find(children, new Condition<DomElement>() {
      @Override
      public boolean value(DomElement domElement) {
        return domElement.getAnnotation(MappingClass.class) != null;
      }
    });
    if (classElement == null) return Collections.emptyList();
    Object value = ((GenericDomValue)classElement).getValue();
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
  public PsiElement resolve(String o, ConvertContext context) {
    DomElement parent = context.getInvocationElement().getParent();
    assert parent != null;
    return parent.getXmlElement();
  }

  @Override
  public boolean isReferenceTo(@NotNull PsiElement element,
                               String stringValue,
                               @Nullable String resolveResult,
                               ConvertContext context) {
    return element.getManager().areElementsEquivalent(element, resolve(stringValue, context));
  }
}
