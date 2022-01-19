// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.source.javadoc;

import com.intellij.codeInspection.SuppressionUtilCore;
import com.intellij.openapi.extensions.ExtensionPointListener;
import com.intellij.openapi.extensions.PluginDescriptor;
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
 * @see <a href="https://docs.oracle.com/javase/9/docs/specs/doc-comment-spec.html">Documentation Comment Specification</a>
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
    myInfos.add(new SimpleDocTagInfo("hidden", LanguageLevel.JDK_1_9, false, PsiElement.class));

    myInfos.add(new SimpleDocTagInfo("docRoot", LanguageLevel.JDK_1_3, true, PsiElement.class));
    myInfos.add(new SimpleDocTagInfo("inheritDoc", LanguageLevel.JDK_1_4, true, PsiElement.class));
    myInfos.add(new SimpleDocTagInfo("literal", LanguageLevel.JDK_1_5, true, PsiElement.class));
    myInfos.add(new SimpleDocTagInfo("code", LanguageLevel.JDK_1_5, true, PsiElement.class));
    myInfos.add(new SimpleDocTagInfo("index", LanguageLevel.JDK_1_9, true, PsiElement.class));
    myInfos.add(new SimpleDocTagInfo("summary", LanguageLevel.JDK_10, true, PsiElement.class));
    myInfos.add(new SimpleDocTagInfo("systemProperty", LanguageLevel.JDK_12, true, PsiElement.class));
    myInfos.add(new SimpleDocTagInfo("snippet", LanguageLevel.JDK_18, true, PsiElement.class));

    // not a standard tag, used by IDEA for suppressing inspections
    myInfos.add(new SimpleDocTagInfo(SuppressionUtilCore.SUPPRESS_INSPECTIONS_TAG_NAME, LanguageLevel.JDK_1_3, false, PsiElement.class));

    myInfos.add(new ParamDocTagInfo());
    myInfos.add(new ReturnDocTagInfo());
    myInfos.add(new SerialDocTagInfo());
    myInfos.add(new SeeDocTagInfo("see", false));
    myInfos.add(new SeeDocTagInfo("link", true));
    myInfos.add(new SeeDocTagInfo("linkplain", true));
    myInfos.add(new ExceptionTagInfo("exception"));
    myInfos.add(new ExceptionTagInfo("throws"));
    myInfos.add(new ServiceReferenceTagInfo("provides"));
    myInfos.add(new ServiceReferenceTagInfo("uses"));
    myInfos.add(new ValueDocTagInfo());

    Collections.addAll(myInfos, JavadocTagInfo.EP_NAME.getExtensions(project));

    for (CustomJavadocTagProvider extension : CustomJavadocTagProvider.EP_NAME.getExtensionList()) {
      myInfos.addAll(extension.getSupportedTags());
    }

    JavadocTagInfo.EP_NAME.getPoint(project).addExtensionPointListener(new ExtensionPointListener<JavadocTagInfo>() {
      @Override
      public void extensionAdded(@NotNull JavadocTagInfo extension, @NotNull PluginDescriptor pluginDescriptor) {
        myInfos.add(extension);
      }

      @Override
      public void extensionRemoved(@NotNull JavadocTagInfo extension, @NotNull PluginDescriptor pluginDescriptor) {
        myInfos.remove(extension);
      }
    }, false, project);

    CustomJavadocTagProvider.EP_NAME.addExtensionPointListener(new ExtensionPointListener<CustomJavadocTagProvider>() {
      @Override
      public void extensionAdded(@NotNull CustomJavadocTagProvider extension, @NotNull PluginDescriptor pluginDescriptor) {
        myInfos.addAll(extension.getSupportedTags());
      }

      @Override
      public void extensionRemoved(@NotNull CustomJavadocTagProvider extension, @NotNull PluginDescriptor pluginDescriptor) {
        myInfos.removeAll(extension.getSupportedTags());
      }
    }, null);
  }

  @Override
  public JavadocTagInfo @NotNull [] getTagInfos(PsiElement context) {
    List<JavadocTagInfo> result = new ArrayList<>();

    for (JavadocTagInfo info : myInfos) {
      if (info.isValidInContext(context)) {
        result.add(info);
      }
    }

    return result.toArray(new JavadocTagInfo[0]);
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