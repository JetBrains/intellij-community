/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
    myInfos = new ArrayList<>();

    myInfos.add(new AuthorDocTagInfo());
    myInfos.add(new SimpleDocTagInfo("deprecated", LanguageLevel.JDK_1_3, false, PsiElement.class));
    myInfos.add(new SimpleDocTagInfo("serialData", LanguageLevel.JDK_1_3, false, PsiMethod.class));
    myInfos.add(new SimpleDocTagInfo("serialField", LanguageLevel.JDK_1_3, false, PsiField.class));
    myInfos.add(new SimpleDocTagInfo("since", LanguageLevel.JDK_1_3, false, PsiElement.class, PsiPackage.class));
    myInfos.add(new SimpleDocTagInfo("version", LanguageLevel.JDK_1_3, false, PsiClass.class, PsiPackage.class));
    myInfos.add(new SimpleDocTagInfo("apiNote", LanguageLevel.JDK_1_8, false, PsiElement.class));
    myInfos.add(new SimpleDocTagInfo("implNote", LanguageLevel.JDK_1_8, false, PsiElement.class));
    myInfos.add(new SimpleDocTagInfo("implSpec", LanguageLevel.JDK_1_8, false, PsiElement.class));

    myInfos.add(new SimpleDocTagInfo("docRoot", LanguageLevel.JDK_1_3, true, PsiElement.class));
    myInfos.add(new SimpleDocTagInfo("inheritDoc", LanguageLevel.JDK_1_4, true, PsiElement.class));
    myInfos.add(new SimpleDocTagInfo("literal", LanguageLevel.JDK_1_5, true, PsiElement.class));
    myInfos.add(new SimpleDocTagInfo("code", LanguageLevel.JDK_1_5, true, PsiElement.class));

    //Not a standard tag, but added by IDEA for inspection suppression
    myInfos.add(new SimpleDocTagInfo(SuppressionUtilCore.SUPPRESS_INSPECTIONS_TAG_NAME, LanguageLevel.JDK_1_3, false, PsiElement.class));

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

  @Override
  @NotNull
  public JavadocTagInfo[] getTagInfos(PsiElement context) {
    List<JavadocTagInfo> result = new ArrayList<>();

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
      if (info.getName().equals(name)) {
        return info;
      }
    }

    return null;
  }
}