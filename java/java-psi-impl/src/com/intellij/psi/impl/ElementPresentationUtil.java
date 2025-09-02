// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl;

import com.intellij.codeInsight.TestFrameworks;
import com.intellij.core.JavaPsiBundle;
import com.intellij.ide.IconLayerProvider;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.util.Iconable;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.text.Strings;
import com.intellij.psi.*;
import com.intellij.psi.util.*;
import com.intellij.psi.util.CachedValueProvider.Result;
import com.intellij.ui.IconManager;
import com.intellij.ui.PlatformIcons;
import com.intellij.ui.icons.RowIcon;
import com.intellij.util.BitUtil;
import com.intellij.util.VisibilityIcons;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import org.jetbrains.annotations.NotNull;

import javax.swing.Icon;

public final class ElementPresentationUtil {
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

  private static final int CLASS_KIND_INTERFACE     = 10;
  private static final int CLASS_KIND_ANNOTATION    = 20;
  public static final int CLASS_KIND_CLASS          = 30;
  private static final int CLASS_KIND_ANONYMOUS     = 40;
  private static final int CLASS_KIND_ENUM          = 50;
  private static final int CLASS_KIND_ASPECT        = 60;
  public static final int CLASS_KIND_JSP            = 70;
  public static final int CLASS_KIND_EXCEPTION      = 80;
  public static final int CLASS_KIND_JUNIT_TEST    = 90;
  public static final int CLASS_KIND_RUNNABLE      = 100;
  private static final int CLASS_KIND_RECORD        = 110;

  //NOTE: these flags can be used in other plugins (e.g. Scala Plugin)
  public static final int FLAGS_ABSTRACT = 0x100;
  public static final int FLAGS_STATIC = 0x200;
  public static final int FLAGS_FINAL = 0x400;
  public static final int FLAGS_JUNIT_TEST = 0x2000;
  public static final int FLAGS_RUNNABLE = 0x4000;

  private static final Key<CachedValue<Integer>> CLASS_KIND_KEY = new Key<>("CLASS_KIND");

  public static int getBasicClassKind(PsiClass aClass) {
    if (!aClass.isValid()) return CLASS_KIND_CLASS;

    if (aClass.isAnnotationType()) return CLASS_KIND_ANNOTATION;
    if (aClass.isEnum()) return CLASS_KIND_ENUM;
    if (aClass.isRecord()) return CLASS_KIND_RECORD;
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
      value = CachedValuesManager.getManager(aClass.getProject()).createCachedValue(aClass, () ->
        Result.createSingleDependency(Integer.valueOf(getClassKindImpl(aClass)), aClass), false
      );
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
    if (aClass.isRecord()) {
      return CLASS_KIND_RECORD;
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
        JavaPsiFacade.getInstance(manager.getProject()).findClass(CommonClassNames.JAVA_LANG_THROWABLE, aClass.getResolveScope());
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

  private static final Int2ObjectMap<Icon> BASE_ICON = new Int2ObjectOpenHashMap<>(20);

  static {
    IconManager iconManager = IconManager.getInstance();
    BASE_ICON.put(CLASS_KIND_CLASS, iconManager.tooltipOnlyIfComposite(iconManager.getPlatformIcon(PlatformIcons.Class)));
    BASE_ICON.put(CLASS_KIND_CLASS | FLAGS_ABSTRACT, iconManager.getPlatformIcon(PlatformIcons.AbstractClass));
    BASE_ICON.put(CLASS_KIND_ANNOTATION, iconManager.getPlatformIcon(PlatformIcons.Annotation));
    BASE_ICON.put(CLASS_KIND_ANONYMOUS, iconManager.getPlatformIcon(PlatformIcons.AnonymousClass));
    BASE_ICON.put(CLASS_KIND_ASPECT, iconManager.getPlatformIcon(PlatformIcons.Aspect));
    BASE_ICON.put(CLASS_KIND_ENUM, iconManager.getPlatformIcon(PlatformIcons.Enum));
    BASE_ICON.put(CLASS_KIND_EXCEPTION, iconManager.getPlatformIcon(PlatformIcons.ExceptionClass));
    BASE_ICON.put(CLASS_KIND_EXCEPTION | FLAGS_ABSTRACT, iconManager.getPlatformIcon(PlatformIcons.AbstractException));
    BASE_ICON.put(CLASS_KIND_INTERFACE, iconManager.tooltipOnlyIfComposite(iconManager.getPlatformIcon(PlatformIcons.Interface)));
    BASE_ICON.put(CLASS_KIND_JUNIT_TEST, iconManager.tooltipOnlyIfComposite(iconManager.getPlatformIcon(PlatformIcons.Class)));
    BASE_ICON.put(CLASS_KIND_JUNIT_TEST | FLAGS_ABSTRACT, iconManager.getPlatformIcon(PlatformIcons.AbstractClass));
    BASE_ICON.put(CLASS_KIND_RECORD, iconManager.getPlatformIcon(PlatformIcons.Record));
    BASE_ICON.put(CLASS_KIND_RUNNABLE, iconManager.getPlatformIcon(PlatformIcons.Class));
  }

  public static @NotNull Icon getClassIconOfKind(@NotNull PsiClass aClass, int classKind) {
    final boolean isAbstract = aClass.hasModifierProperty(PsiModifier.ABSTRACT);
    Icon result = BASE_ICON.get(classKind | (isAbstract ? FLAGS_ABSTRACT : 0));
    if (result == null) {
      if (isAbstract) {
        Icon alternative = BASE_ICON.get(classKind);
        if (alternative != null) return alternative;
      }
      throw new NullPointerException(
        "No icon registered for the class " + aClass + " of kind " + classKind + " (isAbstract=" + isAbstract + ")"
      );
    }
    return result;
  }

  public static String getDescription(PsiModifierListOwner member) {
    String noun;
    if (member instanceof PsiClass) noun = getClassNoun((PsiClass)member);
    else if (member instanceof PsiMethod) noun = JavaPsiBundle.message("node.method.tooltip");
    else if (member instanceof PsiField) noun = JavaPsiBundle.message("node.field.tooltip");
    else return null;
    String adj = getFlagsDescription(member);
    return (adj + " " + noun).trim();
  }

  private static String getClassNoun(final PsiClass aClass) {
    String noun;
    int kind = getClassKind(aClass);
    switch (kind) {
      case CLASS_KIND_ANNOTATION: noun = JavaPsiBundle.message("node.annotation.tooltip"); break;
      case CLASS_KIND_ANONYMOUS: noun = JavaPsiBundle.message("node.anonymous.class.tooltip"); break;
      case CLASS_KIND_ENUM: noun = JavaPsiBundle.message("node.enum.tooltip"); break;
      case CLASS_KIND_RECORD: noun = JavaPsiBundle.message("node.record.tooltip"); break;
      case CLASS_KIND_EXCEPTION: noun = JavaPsiBundle.message("node.exception.tooltip"); break;
      case CLASS_KIND_INTERFACE: noun = JavaPsiBundle.message("node.interface.tooltip"); break;
      case CLASS_KIND_JUNIT_TEST: noun = JavaPsiBundle.message("node.junit.test.tooltip"); break;
      case CLASS_KIND_RUNNABLE: noun = JavaPsiBundle.message("node.runnable.class.tooltip"); break;
      case CLASS_KIND_CLASS: 
      default: noun = JavaPsiBundle.message("node.class.tooltip");
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
    if (BitUtil.isSet(flags, FLAGS_ABSTRACT)) adj.append(" ").append(JavaPsiBundle.message("node.abstract.flag.tooltip"));
    if (BitUtil.isSet(flags, FLAGS_FINAL)) adj.append(" ").append(JavaPsiBundle.message("node.final.flag.tooltip"));
    if (BitUtil.isSet(flags, FLAGS_STATIC)) adj.append(" ").append(JavaPsiBundle.message("node.static.flag.tooltip"));
    PsiModifierList list = aClass.getModifierList();
    if (list != null) {
      int level = PsiUtil.getAccessLevel(list);
      if (level != PsiUtil.ACCESS_LEVEL_PUBLIC) {
        adj.append(" ").append(Strings.capitalize(JavaPsiBundle.visibilityPresentation(PsiUtil.getAccessModifier(level))));
      }
    }
    return adj.toString();
  }


  static {
    IconManager iconManager = IconManager.getInstance();
    iconManager.registerIconLayer(FLAGS_STATIC, iconManager.getPlatformIcon(PlatformIcons.StaticMark));
    iconManager.registerIconLayer(FLAGS_FINAL, iconManager.getPlatformIcon(PlatformIcons.FinalMark));
    iconManager.registerIconLayer(FLAGS_JUNIT_TEST, iconManager.getPlatformIcon(PlatformIcons.JunitTestMark));
    iconManager.registerIconLayer(FLAGS_RUNNABLE, iconManager.getPlatformIcon(PlatformIcons.RunnableMark));
  }

  public static Icon addVisibilityIcon(final PsiModifierListOwner element, final int flags, final RowIcon baseIcon) {
    if (BitUtil.isSet(flags, Iconable.ICON_FLAG_VISIBILITY)) {
      VisibilityIcons.setVisibilityIcon(element.getModifierList(), baseIcon);
    }
    return baseIcon;
  }
}
