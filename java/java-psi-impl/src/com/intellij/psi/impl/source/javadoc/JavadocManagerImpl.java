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
package com.intellij.psi.impl.source.javadoc;

import com.intellij.codeInspection.SuppressionUtilCore;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.project.Project;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.javadoc.CustomJavadocTagProvider;
import com.intellij.psi.javadoc.JavadocManager;
import com.intellij.psi.javadoc.JavadocTagInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author mike
 */
public class JavadocManagerImpl implements JavadocManager {
  private final List<JavadocTagInfo> myInfos;

  public JavadocManagerImpl(Project project) {
    myInfos = new ArrayList<JavadocTagInfo>();

    myInfos.add(new SimpleDocTagInfo("author", PsiClass.class, PsiPackage.class, LanguageLevel.JDK_1_3));
    myInfos.add(new SimpleDocTagInfo("deprecated", PsiElement.class, false, LanguageLevel.JDK_1_3));
    myInfos.add(new SimpleDocTagInfo("serialData", PsiMethod.class, false, LanguageLevel.JDK_1_3));
    myInfos.add(new SimpleDocTagInfo("serialField", PsiField.class, false, LanguageLevel.JDK_1_3));
    myInfos.add(new SimpleDocTagInfo("since", PsiElement.class, PsiPackage.class, LanguageLevel.JDK_1_3));
    myInfos.add(new SimpleDocTagInfo("version", PsiClass.class, PsiPackage.class, LanguageLevel.JDK_1_3));
    myInfos.add(new SimpleDocTagInfo("apiNote", PsiElement.class, false, LanguageLevel.JDK_1_8));
    myInfos.add(new SimpleDocTagInfo("implNote", PsiElement.class, false, LanguageLevel.JDK_1_8));
    myInfos.add(new SimpleDocTagInfo("implSpec", PsiElement.class, false, LanguageLevel.JDK_1_8));

    myInfos.add(new SimpleDocTagInfo("docRoot", PsiElement.class, true, LanguageLevel.JDK_1_3));
    myInfos.add(new SimpleDocTagInfo("inheritDoc", PsiElement.class, true, LanguageLevel.JDK_1_4));
    myInfos.add(new SimpleDocTagInfo("literal", PsiElement.class, true, LanguageLevel.JDK_1_5));
    myInfos.add(new SimpleDocTagInfo("code", PsiElement.class, true, LanguageLevel.JDK_1_5));

    //Not a standard tag, but added by IDEA for inspection suppression
    myInfos.add(new SimpleDocTagInfo(SuppressionUtilCore.SUPPRESS_INSPECTIONS_TAG_NAME, PsiElement.class, false, LanguageLevel.JDK_1_3));

    myInfos.add(new ParamDocTagInfo());
    myInfos.add(new ReturnDocTagInfo());
    myInfos.add(new SerialDocTagInfo());
    myInfos.add(new SeeDocTagInfo("see", false));
    myInfos.add(new SeeDocTagInfo("link", true));
    myInfos.add(new SeeDocTagInfo("linkplain", true));
    myInfos.add(new ExceptionTagInfo("exception"));
    myInfos.add(new ExceptionTagInfo("throws"));
    myInfos.add(new ValueDocTagInfo());

    Collections.addAll(myInfos, Extensions.getExtensions(JavadocTagInfo.EP_NAME, project));
    for (CustomJavadocTagProvider extension : Extensions.getExtensions(CustomJavadocTagProvider.EP_NAME)) {
      myInfos.addAll(extension.getSupportedTags());
    }
  }

  @Deprecated
  public void registerTagInfo(@NotNull JavadocTagInfo info) {
    myInfos.add(info);
  }

  @Override
  @NotNull
  public JavadocTagInfo[] getTagInfos(PsiElement context) {
    List<JavadocTagInfo> result = new ArrayList<JavadocTagInfo>();

    for (JavadocTagInfo info : myInfos) {
      if (info.isValidInContext(context)) {
        result.add(info);
      }
    }

    return result.toArray(new JavadocTagInfo[result.size()]);
  }

  @Override
  @Nullable
  public JavadocTagInfo getTagInfo(String name) {
    for (JavadocTagInfo info : myInfos) {
      if (info.getName().equals(name)) return info;
    }

    return null;
  }
}
