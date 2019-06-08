// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.TestFrameworks;
import com.intellij.icons.AllIcons;
import com.intellij.ide.IconLayerProvider;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.util.Iconable;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.util.*;
import com.intellij.ui.IconManager;
import com.intellij.ui.icons.RowIcon;
import com.intellij.util.BitUtil;
import com.intellij.util.PlatformIcons;
import com.intellij.util.VisibilityIcons;
import gnu.trove.TIntObjectHashMap;
import org.jetbrains.annotations.ApiStatus;

import javax.swing.*;

public final class ElementPresentationUtil implements PlatformIcons {
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

  @Deprecated
  @ApiStatus.ScheduledForRemoval
  public static com.intellij.ui.RowIcon createLayeredIcon(Icon baseIcon, PsiModifierListOwner element, boolean isLocked) {
    return (com.intellij.ui.RowIcon)IconManager.getInstance().createLayeredIcon(element, baseIcon, getFlags(element, isLocked));
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

  private static final Key<CachedValue<Integer>> CLASS_KIND_KEY = new Key<>("CLASS_KIND_KEY");

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
      value = CachedValuesManager.getManager(aClass.getProject()).createCachedValue(
        () -> CachedValueProvider.Result.createSingleDependency(Integer.valueOf(getClassKindImpl(aClass)), aClass), false);
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

  private static final TIntObjectHashMap<Icon> BASE_ICON = new TIntObjectHashMap<>(20);
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
    StringBuilder adj = new StringBuilder();
    for (IconLayerProvider provider : IconLayerProvider.EP_NAME.getExtensionList()) {
      if (provider.getLayerIcon(aClass, false) != null) {
        adj.append(" ").append(provider.getLayerDescription());
      }
    }
    if (BitUtil.isSet(flags, FLAGS_ABSTRACT)) adj.append(" ").append(CodeInsightBundle.message("node.abstract.flag.tooltip"));
    if (BitUtil.isSet(flags, FLAGS_FINAL)) adj.append(" ").append(CodeInsightBundle.message("node.final.flag.tooltip"));
    if (BitUtil.isSet(flags, FLAGS_STATIC)) adj.append(" ").append(CodeInsightBundle.message("node.static.flag.tooltip"));
    PsiModifierList list = aClass.getModifierList();
    if (list != null) {
      int level = PsiUtil.getAccessLevel(list);
      if (level != PsiUtil.ACCESS_LEVEL_PUBLIC) {
        adj.append(" ").append(StringUtil.capitalize(PsiBundle.visibilityPresentation(PsiUtil.getAccessModifier(level))));
      }
    }
    return adj.toString();
  }


  static {
    IconManager iconManager = IconManager.getInstance();
    iconManager.registerIconLayer(FLAGS_STATIC, AllIcons.Nodes.StaticMark);
    iconManager.registerIconLayer(FLAGS_FINAL, AllIcons.Nodes.FinalMark);
    iconManager.registerIconLayer(FLAGS_JUNIT_TEST, AllIcons.Nodes.JunitTestMark);
    iconManager.registerIconLayer(FLAGS_RUNNABLE, AllIcons.Nodes.RunnableMark);
  }

  @Deprecated
  @ApiStatus.ScheduledForRemoval
  public static Icon addVisibilityIcon(PsiModifierListOwner element, final int flags, com.intellij.ui.RowIcon baseIcon) {
    return addVisibilityIcon(element, flags, ((RowIcon)baseIcon));
  }

  public static Icon addVisibilityIcon(final PsiModifierListOwner element, final int flags, final RowIcon baseIcon) {
    if (Registry.is("ide.completion.show.visibility.icon") && BitUtil.isSet(flags, Iconable.ICON_FLAG_VISIBILITY)) {
      VisibilityIcons.setVisibilityIcon(element.getModifierList(), baseIcon);
    }
    return baseIcon;
  }
}
