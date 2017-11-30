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
package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.TailType;
import com.intellij.codeInsight.lookup.*;
import com.intellij.codeInsight.lookup.impl.JavaElementLookupRenderer;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.util.ClassConditionKey;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.psi.util.PsiFormatUtil;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.Set;

/**
 * @author peter
 */
public class JavaPsiClassReferenceElement extends LookupItem<Object> implements TypedLookupItem {
  public static final ClassConditionKey<JavaPsiClassReferenceElement> CLASS_CONDITION_KEY = ClassConditionKey.create(JavaPsiClassReferenceElement.class);
  private final SmartPsiElementPointer<PsiClass> myClass;
  private final String myQualifiedName;
  private String myForcedPresentableName;
  private String myPackageDisplayName;
  private PsiSubstitutor mySubstitutor = PsiSubstitutor.EMPTY;

  public JavaPsiClassReferenceElement(PsiClass psiClass) {
    super(psiClass.getName(), psiClass.getName());
    myQualifiedName = psiClass.getQualifiedName();
    myClass = SmartPointerManager.getInstance(psiClass.getProject()).createSmartPsiElementPointer(psiClass);
    setInsertHandler(AllClassesGetter.TRY_SHORTENING);
    setTailType(TailType.NONE);
    myPackageDisplayName = PsiFormatUtil.getPackageDisplayName(psiClass);
  }

  public String getForcedPresentableName() {
    return myForcedPresentableName;
  }

  @Nullable
  @Override
  public PsiType getType() {
    PsiClass psiClass = getObject();
    return JavaPsiFacade.getElementFactory(psiClass.getProject()).createType(psiClass, getSubstitutor());
  }

  public PsiSubstitutor getSubstitutor() {
    return mySubstitutor;
  }

  public JavaPsiClassReferenceElement setSubstitutor(PsiSubstitutor substitutor) {
    mySubstitutor = substitutor;
    return this;
  }

  @NotNull
  @Override
  public String getLookupString() {
    if (myForcedPresentableName != null) {
      return myForcedPresentableName;
    }
    return super.getLookupString();
  }

  @Override
  public Set<String> getAllLookupStrings() {
    if (myForcedPresentableName != null) {
      return Collections.singleton(myForcedPresentableName);
    }

    return super.getAllLookupStrings();
  }

  public void setForcedPresentableName(String forcedPresentableName) {
    myForcedPresentableName = forcedPresentableName;
  }

  @NotNull
  @Override
  public PsiClass getObject() {
    return ObjectUtils.assertNotNull(myClass.getElement());
  }

  @Override
  public boolean isValid() {
    return myClass.getElement() != null;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) return true;
    if (!(o instanceof JavaPsiClassReferenceElement)) return false;

    final JavaPsiClassReferenceElement that = (JavaPsiClassReferenceElement)o;

    if (myQualifiedName != null) {
      return myQualifiedName.equals(that.myQualifiedName);
    }

    return Comparing.equal(myClass, that.myClass);
  }

  public String getQualifiedName() {
    return myQualifiedName;
  }

  @Override
  public int hashCode() {
    final String s = myQualifiedName;
    return s == null ? 239 : s.hashCode();
  }

  @Override
  public void renderElement(LookupElementPresentation presentation) {
    renderClassItem(presentation, this, getObject(), false, " (" + myPackageDisplayName + ")", mySubstitutor);
  }

  public static void renderClassItem(LookupElementPresentation presentation, LookupElement item, PsiClass psiClass, boolean diamond,
                                     @NotNull String locationString, @NotNull PsiSubstitutor substitutor) {
    if (!(psiClass instanceof PsiTypeParameter)) {
      presentation.setIcon(DefaultLookupItemRenderer.getRawIcon(item, presentation.isReal()));
    }

    boolean strikeout = JavaElementLookupRenderer.isToStrikeout(item);
    presentation.setItemText(getName(psiClass, item, diamond, substitutor));
    presentation.setStrikeout(strikeout);

    String tailText = locationString;

    if (item instanceof PsiTypeLookupItem) {
      if (((PsiTypeLookupItem)item).isIndicateAnonymous() &&
          (psiClass.isInterface() || psiClass.hasModifierProperty(PsiModifier.ABSTRACT)) ||
          ((PsiTypeLookupItem)item).isAddArrayInitializer()) {
        tailText = "{...}" + tailText;
      }
    }
    if (substitutor == PsiSubstitutor.EMPTY && !diamond && psiClass.getTypeParameters().length > 0) {
      String separator = "," + (showSpaceAfterComma(psiClass) ? " " : "");
      tailText = "<" + StringUtil.join(psiClass.getTypeParameters(), PsiTypeParameter::getName, separator) + ">" + tailText;
    }
    presentation.setTailText(tailText, true);
  }

  public String getLocationString() {
    return " (" + myPackageDisplayName + ")";
  }

  private static String getName(final PsiClass psiClass, final LookupElement item, boolean diamond, @NotNull PsiSubstitutor substitutor) {
    String forced = item instanceof JavaPsiClassReferenceElement ? ((JavaPsiClassReferenceElement)item).getForcedPresentableName() :
                    item instanceof PsiTypeLookupItem ? ((PsiTypeLookupItem)item).getForcedPresentableName() :
                    null;
    if (forced != null) {
      return forced;
    }

    String name = PsiUtilCore.getName(psiClass);
    if (diamond) {
      return name + "<>";
    }

    if (substitutor != PsiSubstitutor.EMPTY) {
      final PsiTypeParameter[] params = psiClass.getTypeParameters();
      if (params.length > 0) {
        return name + formatTypeParameters(substitutor, params);
      }
    }

    return StringUtil.notNullize(name);
  }

  @Nullable
  private static String formatTypeParameters(@NotNull final PsiSubstitutor substitutor, final PsiTypeParameter[] params) {
    final boolean space = showSpaceAfterComma(params[0]);
    StringBuilder buffer = new StringBuilder();
    buffer.append("<");
    for(int i = 0; i < params.length; i++){
      final PsiTypeParameter param = params[i];
      final PsiType type = substitutor.substitute(param);
      if(type == null){
        return "";
      }
      if (type instanceof PsiClassType && ((PsiClassType)type).getParameters().length > 0) {
        buffer.append(((PsiClassType)type).rawType().getPresentableText()).append("<...>");
      } else {
        buffer.append(type.getPresentableText());
      }

      if(i < params.length - 1) {
        buffer.append(",");
        if (space) {
          buffer.append(" ");
        }
      }
    }
    buffer.append(">");
    return buffer.toString();
  }

  private static boolean showSpaceAfterComma(PsiClass element) {
    return CodeStyleSettingsManager.getSettings(element.getProject()).getCommonSettings(JavaLanguage.INSTANCE).SPACE_AFTER_COMMA;
  }

}
