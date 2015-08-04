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
import com.intellij.codeInsight.lookup.DefaultLookupItemRenderer;
import com.intellij.codeInsight.lookup.LookupElementPresentation;
import com.intellij.codeInsight.lookup.LookupItem;
import com.intellij.codeInsight.lookup.PsiTypeLookupItem;
import com.intellij.codeInsight.lookup.impl.JavaElementLookupRenderer;
import com.intellij.openapi.util.ClassConditionKey;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.reference.SoftReference;
import com.intellij.util.Function;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.util.Collections;
import java.util.Set;

/**
 * @author peter
 */
public class JavaPsiClassReferenceElement extends LookupItem<Object> {
  public static final Key<String> PACKAGE_NAME = Key.create("PACKAGE_NAME");
  public static final ClassConditionKey<JavaPsiClassReferenceElement> CLASS_CONDITION_KEY = ClassConditionKey.create(JavaPsiClassReferenceElement.class);
  private final Object myClass;
  private volatile Reference<PsiClass> myCache;
  private final String myQualifiedName;
  private String myForcedPresentableName;

  public JavaPsiClassReferenceElement(PsiClass psiClass) {
    super(psiClass.getName(), psiClass.getName());
    myQualifiedName = psiClass.getQualifiedName();
    final PsiFile file = psiClass.getContainingFile();
    myClass = file == null || file.getVirtualFile() == null || myQualifiedName == null ? psiClass : PsiAnchor.create(psiClass);
    JavaCompletionUtil.setShowFQN(this);
    setInsertHandler(AllClassesGetter.TRY_SHORTENING);
    setTailType(TailType.NONE);
  }

  public String getForcedPresentableName() {
    return myForcedPresentableName;
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
    if (myClass instanceof PsiAnchor) {
      PsiClass psiClass = SoftReference.dereference(myCache);
      if (psiClass != null) {
        return psiClass;
      }

      final PsiClass retrieve = (PsiClass)((PsiAnchor)myClass).retrieve();
      assert retrieve != null : myQualifiedName;
      myCache = new WeakReference<PsiClass>(retrieve);
      return retrieve;
    }
    return (PsiClass)myClass;
  }

  @Override
  public boolean isValid() {
    if (myClass instanceof PsiClass) {
      return ((PsiClass)myClass).isValid();
    }

    return ((PsiAnchor)myClass).retrieve() != null;
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
    LookupItem item = this;
    PsiClass psiClass = getObject();
    renderClassItem(presentation, item, psiClass, false);
  }

  public static void renderClassItem(LookupElementPresentation presentation, LookupItem item, PsiClass psiClass, boolean diamond) {
    if (!(psiClass instanceof PsiTypeParameter)) {
      presentation.setIcon(DefaultLookupItemRenderer.getRawIcon(item, presentation.isReal()));
    }

    final boolean bold = item.getAttribute(LookupItem.HIGHLIGHTED_ATTR) != null;
    boolean strikeout = JavaElementLookupRenderer.isToStrikeout(item);
    presentation.setItemText(getName(psiClass, item, diamond));
    presentation.setStrikeout(strikeout);
    presentation.setItemTextBold(bold);

    String tailText = getLocationString(item);
    PsiSubstitutor substitutor = (PsiSubstitutor)item.getAttribute(LookupItem.SUBSTITUTOR);

    if (item instanceof PsiTypeLookupItem) {
      if (((PsiTypeLookupItem)item).isIndicateAnonymous() &&
          (psiClass.isInterface() || psiClass.hasModifierProperty(PsiModifier.ABSTRACT)) ||
          ((PsiTypeLookupItem)item).isAddArrayInitializer()) {
        tailText = "{...}" + tailText;
      }
    }
    if (substitutor == null && !diamond && psiClass.getTypeParameters().length > 0) {
      tailText = "<" + StringUtil.join(psiClass.getTypeParameters(), new Function<PsiTypeParameter, String>() {
        @Override
        public String fun(PsiTypeParameter psiTypeParameter) {
          return psiTypeParameter.getName();
        }
      }, "," + (showSpaceAfterComma(psiClass) ? " " : "")) + ">" + tailText;
    }
    presentation.setTailText(tailText, true);
  }

  public static String getLocationString(LookupItem<?> item) {
    String pkgName = item.getAttribute(PACKAGE_NAME);
    return pkgName == null ? "" : " (" + pkgName + ")";
  }

  private static String getName(final PsiClass psiClass, final LookupItem<?> item, boolean diamond) {
    if (item instanceof JavaPsiClassReferenceElement) {
      String forced = ((JavaPsiClassReferenceElement)item).getForcedPresentableName();
      if (forced != null) {
        return forced;
      }
    }

    String name = PsiUtilCore.getName(psiClass);

    if (item.getAttribute(LookupItem.FORCE_QUALIFY) != null) {
      if (psiClass.getContainingClass() != null) {
        name = psiClass.getContainingClass().getName() + "." + name;
      }
    }

    if (diamond) {
      return name + "<>";
    }

    PsiSubstitutor substitutor = (PsiSubstitutor)item.getAttribute(LookupItem.SUBSTITUTOR);
    if (substitutor != null) {
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
    return CodeStyleSettingsManager.getSettings(element.getProject()).SPACE_AFTER_COMMA;
  }

}
