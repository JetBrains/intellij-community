/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.psi.impl;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.TestFrameworks;
import com.intellij.icons.AllIcons;
import com.intellij.ide.IconLayerProvider;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.util.Iconable;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.util.*;
import com.intellij.ui.RowIcon;
import com.intellij.util.PlatformIcons;
import com.intellij.util.VisibilityIcons;
import gnu.trove.TIntObjectHashMap;

import javax.swing.*;

public class ElementPresentationUtil implements PlatformIcons {
  private ElementPresentationUtil() {
  }


  public static int getFlags(PsiModifierListOwner element, final boolean isLocked) {
    final boolean isEnum = element instanceof PsiClass && ((PsiClass)element).isEnum();
    int flags = (element.hasModifierProperty(PsiModifier.FINAL) && !isEnum ? FLAGS_FINAL : 0)
                | (element.hasModifierProperty(PsiModifier.STATIC) && !isEnum ? FLAGS_STATIC : 0)
                | (isLocked ? ElementBase.FLAGS_LOCKED : 0);
    if (element instanceof PsiClass) {
      final PsiClass aClass = (PsiClass)element;
      if (element.hasModifierProperty(PsiModifier.ABSTRACT) && !((PsiClass)element).isInterface()) {
        flags |= FLAGS_ABSTRACT;
      }
      int kind = getClassKind(aClass);
      if (kind == CLASS_KIND_JUNIT_TEST) {
        flags |= FLAGS_JUNIT_TEST;
      }
      else if (kind == CLASS_KIND_RUNNABLE) {
        flags |= FLAGS_RUNNABLE;
      }
    }
    return flags;
  }

  public static RowIcon createLayeredIcon(Icon baseIcon, PsiModifierListOwner element, boolean isLocked) {
    return ElementBase.createLayeredIcon(element, baseIcon, getFlags(element, isLocked));
  }

  private static final int CLASS_KIND_INTERFACE     = 10;
  private static final int CLASS_KIND_ANNOTATION    = 20;
  public static final int CLASS_KIND_CLASS         = 30;
  private static final int CLASS_KIND_ANONYMOUS     = 40;
  private static final int CLASS_KIND_ENUM          = 50;
  private static final int CLASS_KIND_ASPECT        = 60;
  public static final int CLASS_KIND_JSP           = 70;
  public static final int CLASS_KIND_EXCEPTION = 80;
  private static final int CLASS_KIND_JUNIT_TEST = 90;
  private static final int CLASS_KIND_RUNNABLE = 100;

  private static final int FLAGS_ABSTRACT = 0x100;
  private static final int FLAGS_STATIC = 0x200;
  private static final int FLAGS_FINAL = 0x400;
  private static final int FLAGS_JUNIT_TEST = 0x2000;
  public static final int FLAGS_RUNNABLE = 0x4000;

  private static final Key<CachedValue<Integer>> CLASS_KIND_KEY = new Key<CachedValue<Integer>>("CLASS_KIND_KEY");

  public static int getBasicClassKind(PsiClass aClass) {
    if (!aClass.isValid()) return CLASS_KIND_CLASS;

    if (aClass.isAnnotationType()) return CLASS_KIND_ANNOTATION;
    if (aClass.isEnum()) return CLASS_KIND_ENUM;
    if (aClass.isInterface()) return CLASS_KIND_INTERFACE;
    if (aClass instanceof PsiAnonymousClass) return CLASS_KIND_ANONYMOUS;

    return CLASS_KIND_CLASS;
  }

  public static int getClassKind(final PsiClass aClass) {
    if (!aClass.isValid()) {
      aClass.putUserData(CLASS_KIND_KEY, null);
      return CLASS_KIND_CLASS;
    }

    CachedValue<Integer> value = aClass.getUserData(CLASS_KIND_KEY);
    if (value == null) {
      value = CachedValuesManager.getManager(aClass.getProject()).createCachedValue(new CachedValueProvider<Integer>() {
        @Override
        public Result<Integer> compute() {
          return Result.createSingleDependency(Integer.valueOf(getClassKindImpl(aClass)), aClass);
        }
      }, false);
      aClass.putUserData(CLASS_KIND_KEY, value);
    }
    return value.getValue().intValue();
  }

  private static int getClassKindImpl(PsiClass aClass) {
    if (!aClass.isValid()) return CLASS_KIND_CLASS;

    if (aClass.isAnnotationType()) {
      return CLASS_KIND_ANNOTATION;
    }
    if (aClass.isEnum()) {
      return CLASS_KIND_ENUM;
    }
    if (aClass.isInterface()) {
      return CLASS_KIND_INTERFACE;
    }
    if (aClass instanceof PsiAnonymousClass) {
      return CLASS_KIND_ANONYMOUS;
    }

    if (!DumbService.getInstance(aClass.getProject()).isDumb()) {
      final PsiManager manager = aClass.getManager();
      final PsiClass javaLangTrowable =
        JavaPsiFacade.getInstance(manager.getProject()).findClass("java.lang.Throwable", aClass.getResolveScope());
      final boolean isException = javaLangTrowable != null && InheritanceUtil.isInheritorOrSelf(aClass, javaLangTrowable, true);
      if (isException) {
        return CLASS_KIND_EXCEPTION;
      }

      if (TestFrameworks.getInstance().isTestClass(aClass)) {
        return CLASS_KIND_JUNIT_TEST;
      }
      if (PsiClassUtil.isRunnableClass(aClass, false) && PsiMethodUtil.findMainMethod(aClass) != null) {
        return CLASS_KIND_RUNNABLE;
      }
    }
    return CLASS_KIND_CLASS;
  }

  private static final TIntObjectHashMap<Icon> BASE_ICON = new TIntObjectHashMap<Icon>(20);
  static {
    BASE_ICON.put(CLASS_KIND_CLASS, CLASS_ICON);
    BASE_ICON.put(CLASS_KIND_CLASS | FLAGS_ABSTRACT, ABSTRACT_CLASS_ICON);
    BASE_ICON.put(CLASS_KIND_ANNOTATION, ANNOTATION_TYPE_ICON);
    BASE_ICON.put(CLASS_KIND_ANNOTATION | FLAGS_ABSTRACT, ANNOTATION_TYPE_ICON);
    BASE_ICON.put(CLASS_KIND_ANONYMOUS, ANONYMOUS_CLASS_ICON);
    BASE_ICON.put(CLASS_KIND_ANONYMOUS | FLAGS_ABSTRACT, ANONYMOUS_CLASS_ICON);
    BASE_ICON.put(CLASS_KIND_ASPECT, ASPECT_ICON);
    BASE_ICON.put(CLASS_KIND_ASPECT | FLAGS_ABSTRACT, ASPECT_ICON);
    BASE_ICON.put(CLASS_KIND_ENUM, ENUM_ICON);
    BASE_ICON.put(CLASS_KIND_ENUM | FLAGS_ABSTRACT, ENUM_ICON);
    BASE_ICON.put(CLASS_KIND_EXCEPTION, EXCEPTION_CLASS_ICON);
    BASE_ICON.put(CLASS_KIND_EXCEPTION | FLAGS_ABSTRACT, AllIcons.Nodes.AbstractException);
    BASE_ICON.put(CLASS_KIND_INTERFACE, INTERFACE_ICON);
    BASE_ICON.put(CLASS_KIND_INTERFACE | FLAGS_ABSTRACT, INTERFACE_ICON);
    BASE_ICON.put(CLASS_KIND_JUNIT_TEST, CLASS_ICON);
    BASE_ICON.put(CLASS_KIND_JUNIT_TEST | FLAGS_ABSTRACT, ABSTRACT_CLASS_ICON);
    BASE_ICON.put(CLASS_KIND_RUNNABLE, CLASS_ICON);
  }

  public static Icon getClassIconOfKind(PsiClass aClass, int classKind) {
    final boolean isAbstract = aClass.hasModifierProperty(PsiModifier.ABSTRACT);
    return BASE_ICON.get(classKind | (isAbstract ? FLAGS_ABSTRACT : 0));
  }

  public static String getDescription(PsiModifierListOwner member) {
    String noun;
    if (member instanceof PsiClass) noun = getClassNoun((PsiClass)member);
    else if (member instanceof PsiMethod) noun = CodeInsightBundle.message("node.method.tooltip");
    else if (member instanceof PsiField) noun = CodeInsightBundle.message("node.field.tooltip");
    else return null;
    String adj = getFlagsDescription(member);
    return (adj + " " + noun).trim();
  }

  private static String getClassNoun(final PsiClass aClass) {
    String noun;
    int kind = getClassKind(aClass);
    switch (kind) {
      case CLASS_KIND_ANNOTATION: noun = CodeInsightBundle.message("node.annotation.tooltip"); break;
      case CLASS_KIND_ANONYMOUS: noun = CodeInsightBundle.message("node.anonymous.class.tooltip"); break;
      case CLASS_KIND_ENUM: noun = CodeInsightBundle.message("node.enum.tooltip"); break;
      case CLASS_KIND_EXCEPTION: noun = CodeInsightBundle.message("node.exception.tooltip"); break;
      case CLASS_KIND_INTERFACE: noun = CodeInsightBundle.message("node.interface.tooltip"); break;
      case CLASS_KIND_JUNIT_TEST: noun = CodeInsightBundle.message("node.junit.test.tooltip"); break;
      case CLASS_KIND_RUNNABLE: noun = CodeInsightBundle.message("node.runnable.class.tooltip"); break;
      default:
      case CLASS_KIND_CLASS: noun = CodeInsightBundle.message("node.class.tooltip"); break;
    }
    return noun;
  }

  private static String getFlagsDescription(final PsiModifierListOwner aClass) {
    int flags = getFlags(aClass, false);
    String adj = "";
    for (IconLayerProvider provider : Extensions.getExtensions(IconLayerProvider.EP_NAME)) {
      if (provider.getLayerIcon(aClass, false) != null) {
        adj += " " + provider.getLayerDescription();
      }
    }
    if ((flags & FLAGS_ABSTRACT) != 0) adj += " " + CodeInsightBundle.message("node.abstract.flag.tooltip");
    if ((flags & FLAGS_FINAL) != 0) adj += " " + CodeInsightBundle.message("node.final.flag.tooltip");
    if ((flags & FLAGS_STATIC) != 0) adj += " " + CodeInsightBundle.message("node.static.flag.tooltip");
    PsiModifierList list = aClass.getModifierList();
    if (list != null) {
      int level = PsiUtil.getAccessLevel(list);
      if (level != PsiUtil.ACCESS_LEVEL_PUBLIC) {
        adj += " " + StringUtil.capitalize(PsiBundle.visibilityPresentation(PsiUtil.getAccessModifier(level)));
      }
    }
    return adj;
  }


  static {
    ElementBase.registerIconLayer(FLAGS_STATIC, AllIcons.Nodes.StaticMark);
    ElementBase.registerIconLayer(FLAGS_FINAL, AllIcons.Nodes.FinalMark);
    ElementBase.registerIconLayer(FLAGS_JUNIT_TEST, AllIcons.Nodes.JunitTestMark);
    ElementBase.registerIconLayer(FLAGS_RUNNABLE, AllIcons.Nodes.RunnableMark);
  }

  public static Icon addVisibilityIcon(final PsiModifierListOwner element, final int flags, final RowIcon baseIcon) {
    if ((flags & Iconable.ICON_FLAG_VISIBILITY) != 0) {
      VisibilityIcons.setVisibilityIcon(element.getModifierList(), baseIcon);
    }
    return baseIcon;
  }
}
