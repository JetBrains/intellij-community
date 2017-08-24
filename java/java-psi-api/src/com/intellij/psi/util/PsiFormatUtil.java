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
package com.intellij.psi.util;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.util.BitUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.VisibilityUtil;
import org.intellij.lang.annotations.MagicConstant;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class PsiFormatUtil extends PsiFormatUtilBase {
  @MagicConstant(flags = {
    SHOW_MODIFIERS, SHOW_TYPE, TYPE_AFTER, SHOW_CONTAINING_CLASS, SHOW_FQ_NAME, SHOW_NAME, SHOW_MODIFIERS,
    SHOW_INITIALIZER, SHOW_RAW_TYPE, SHOW_RAW_NON_TOP_TYPE, SHOW_FQ_CLASS_NAMES, USE_INTERNAL_CANONICAL_TEXT})
  public @interface FormatVariableOptions { }

  @MagicConstant(flags = {
    SHOW_MODIFIERS, MODIFIERS_AFTER, SHOW_TYPE, TYPE_AFTER, SHOW_CONTAINING_CLASS, SHOW_FQ_NAME, SHOW_NAME,
    SHOW_PARAMETERS, SHOW_THROWS, SHOW_RAW_TYPE, SHOW_RAW_NON_TOP_TYPE, SHOW_FQ_CLASS_NAMES, USE_INTERNAL_CANONICAL_TEXT})
  public @interface FormatMethodOptions { }

  @MagicConstant(flags = {
    SHOW_MODIFIERS, SHOW_NAME, SHOW_ANONYMOUS_CLASS_VERBOSE, SHOW_FQ_NAME, MODIFIERS_AFTER,
    SHOW_EXTENDS_IMPLEMENTS, SHOW_REDUNDANT_MODIFIERS, JAVADOC_MODIFIERS_ONLY})
  public @interface FormatClassOptions { }

  public static String formatVariable(@NotNull PsiVariable variable, @FormatVariableOptions int options, PsiSubstitutor substitutor) {
    StringBuilder buffer = new StringBuilder();
    formatVariable(variable, options, substitutor, buffer);
    return buffer.toString();
  }

  private static void formatVariable(@NotNull PsiVariable variable,
                                     @FormatVariableOptions int options,
                                     PsiSubstitutor substitutor,
                                     @NotNull StringBuilder buffer) {
    if (BitUtil.isSet(options, SHOW_MODIFIERS) && !BitUtil.isSet(options, MODIFIERS_AFTER)) {
      formatModifiers(variable, options,buffer);
    }
    if (BitUtil.isSet(options, SHOW_TYPE) && !BitUtil.isSet(options, TYPE_AFTER)) {
      appendSpaceIfNeeded(buffer);
      buffer.append(formatType(variable.getType(), options, substitutor));
    }
    if (variable instanceof PsiField && BitUtil.isSet(options, SHOW_CONTAINING_CLASS)) {
      PsiClass aClass = ((PsiField)variable).getContainingClass();
      if (aClass != null) {
        String className = aClass.getName();
        if (className != null) {
          appendSpaceIfNeeded(buffer);
          if (BitUtil.isSet(options, SHOW_FQ_NAME)) {
            buffer.append(ObjectUtils.notNull(aClass.getQualifiedName(), className));
          }
          else {
            buffer.append(className);
          }
          buffer.append('.');
        }
      }
      if (BitUtil.isSet(options, SHOW_NAME)) {
        buffer.append(variable.getName());
      }
    }
    else{
      if (BitUtil.isSet(options, SHOW_NAME)) {
        String name = variable.getName();
        if (StringUtil.isNotEmpty(name)) {
          appendSpaceIfNeeded(buffer);
          buffer.append(name);
        }
      }
    }
    if (BitUtil.isSet(options, SHOW_TYPE) && BitUtil.isSet(options, TYPE_AFTER)) {
      if (BitUtil.isSet(options, SHOW_NAME) && variable.getName() != null) {
        buffer.append(':');
      }
      buffer.append(formatType(variable.getType(), options, substitutor));
    }
    if (BitUtil.isSet(options, SHOW_MODIFIERS) && BitUtil.isSet(options, MODIFIERS_AFTER)) {
      formatModifiers(variable, options,buffer);
    }
    if (BitUtil.isSet(options, SHOW_INITIALIZER)) {
      PsiExpression initializer = variable.getInitializer();
      if (initializer != null) {
        buffer.append(" = ");
        String text = PsiExpressionTrimRenderer.render(initializer);
        int index1 = text.lastIndexOf('\n');
        if (index1 < 0) index1 = text.length();
        int index2 = text.lastIndexOf('\r');
        if (index2 < 0) index2 = text.length();
        int index = Math.min(index1, index2);
        buffer.append(text.substring(0, index));
        if (index < text.length()) {
          buffer.append(" ...");
        }
      }
    }
  }

  public static String formatMethod(@NotNull PsiMethod method,
                                    @NotNull PsiSubstitutor substitutor,
                                    @FormatMethodOptions int options,
                                    @FormatVariableOptions int parameterOptions) {
    return formatMethod(method, substitutor, options, parameterOptions, MAX_PARAMS_TO_SHOW);
  }

  public static String formatMethod(@NotNull PsiMethod method,
                                    @NotNull PsiSubstitutor substitutor,
                                    @FormatMethodOptions int options,
                                    @FormatVariableOptions int parameterOptions,
                                    int maxParametersToShow) {
    StringBuilder buffer = new StringBuilder();
    formatMethod(method, substitutor, options, parameterOptions, maxParametersToShow,buffer);
    return buffer.toString();
  }

  private static void formatMethod(@NotNull PsiMethod method,
                                   @NotNull PsiSubstitutor substitutor,
                                   @FormatMethodOptions int options,
                                   @FormatVariableOptions int parameterOptions,
                                   int maxParametersToShow,
                                   StringBuilder buffer) {
    if (BitUtil.isSet(options, SHOW_MODIFIERS) && !BitUtil.isSet(options, MODIFIERS_AFTER)) {
      formatModifiers(method, options,buffer);
    }
    if (BitUtil.isSet(options, SHOW_TYPE) && !BitUtil.isSet(options, TYPE_AFTER)) {
      PsiType type = method.getReturnType();
      if (type != null) {
        appendSpaceIfNeeded(buffer);
        buffer.append(formatType(type, options, substitutor));
      }
    }
    if (BitUtil.isSet(options, SHOW_CONTAINING_CLASS)) {
      PsiClass aClass = method.getContainingClass();
      if (aClass != null) {
        appendSpaceIfNeeded(buffer);
        String name = aClass.getName();
        if (name != null) {
          if (BitUtil.isSet(options, SHOW_FQ_NAME)) {
            buffer.append(ObjectUtils.notNull(aClass.getQualifiedName(), name));
          }
          else {
            buffer.append(name);
          }
          buffer.append('.');
        }
      }
      if (BitUtil.isSet(options, SHOW_NAME)) {
        buffer.append(method.getName());
      }
    }
    else {
      if (BitUtil.isSet(options, SHOW_NAME)) {
        appendSpaceIfNeeded(buffer);
        buffer.append(method.getName());
      }
    }
    if (BitUtil.isSet(options, SHOW_PARAMETERS)) {
      buffer.append('(');
      PsiParameter[] params = method.getParameterList().getParameters();
      for (int i = 0; i < Math.min(params.length, maxParametersToShow); i++) {
        PsiParameter parm = params[i];
        if (i > 0) {
          buffer.append(", ");
        }
        buffer.append(formatVariable(parm, parameterOptions, substitutor));
      }
      if (params.length > maxParametersToShow) {
        buffer.append(", ...");
      }
      buffer.append(')');
    }
    if (BitUtil.isSet(options, SHOW_TYPE) && BitUtil.isSet(options, TYPE_AFTER)) {
      PsiType type = method.getReturnType();
      if (type != null) {
        if (buffer.length() > 0) {
          buffer.append(':');
        }
        buffer.append(formatType(type, options, substitutor));
      }
    }
    if (BitUtil.isSet(options, SHOW_MODIFIERS) && BitUtil.isSet(options, MODIFIERS_AFTER)) {
      formatModifiers(method, options,buffer);
    }
    if (BitUtil.isSet(options, SHOW_THROWS)) {
      String throwsText = formatReferenceList(method.getThrowsList(), options);
      if (!throwsText.isEmpty()) {
        appendSpaceIfNeeded(buffer);
        buffer.append("throws ");
        buffer.append(throwsText);
      }
    }
  }

  @NotNull
  public static String formatClass(@NotNull PsiClass aClass, @FormatClassOptions int options) {
    StringBuilder buffer = new StringBuilder();

    if (BitUtil.isSet(options, SHOW_MODIFIERS) && !BitUtil.isSet(options, MODIFIERS_AFTER)) {
      formatModifiers(aClass, options,buffer);
    }

    if (BitUtil.isSet(options, SHOW_NAME)) {
      if (aClass instanceof PsiAnonymousClass && BitUtil.isSet(options, SHOW_ANONYMOUS_CLASS_VERBOSE)) {
        final PsiClassType baseClassReference = ((PsiAnonymousClass)aClass).getBaseClassType();
        PsiClass baseClass = baseClassReference.resolve();
        String name = baseClass == null ? baseClassReference.getPresentableText() : formatClass(baseClass, options);
        buffer.append(PsiBundle.message("anonymous.class.derived.display", name));
      }
      else {
        String name = aClass.getName();
        if (name != null) {
          appendSpaceIfNeeded(buffer);
          if (BitUtil.isSet(options, SHOW_FQ_NAME)) {
            String qName = aClass.getQualifiedName();
            if (qName != null) {
              buffer.append(qName);
            }
            else {
              buffer.append(aClass.getName());
            }
          }
          else {
            buffer.append(aClass.getName());
          }
        }
      }
    }

    if (BitUtil.isSet(options, SHOW_MODIFIERS) && BitUtil.isSet(options, MODIFIERS_AFTER)) {
      formatModifiers(aClass, options,buffer);
    }

    if (BitUtil.isSet(options, SHOW_EXTENDS_IMPLEMENTS)) {
      PsiReferenceList extendsList = aClass.getExtendsList();
      if (extendsList != null) {
        String extendsText = formatReferenceList(extendsList, options);
        if (!extendsText.isEmpty()) {
          appendSpaceIfNeeded(buffer);
          buffer.append("extends ");
          buffer.append(extendsText);
        }
      }

      PsiReferenceList implementsList = aClass.getImplementsList();
      if (implementsList != null) {
        String implementsText = formatReferenceList(implementsList, options);
        if (!implementsText.isEmpty()) {
          appendSpaceIfNeeded(buffer);
          buffer.append("implements ");
          buffer.append(implementsText);
        }
      }
    }

    return buffer.toString();
  }

  /** @deprecated use {@link #formatModifiers(PsiModifierListOwner, int)} (to be removed in IDEA 2019) */
  public static String formatModifiers(PsiElement element, int options) throws IllegalArgumentException {
    if (element instanceof PsiModifierListOwner) {
      return formatModifiers((PsiModifierListOwner)element, options);
    }
    else {
      throw new IllegalArgumentException();
    }
  }

  @NotNull
  public static String formatModifiers(@NotNull PsiModifierListOwner element, int options) {
    StringBuilder buffer = new StringBuilder();
    formatModifiers(element, options, buffer);
    return buffer.toString();
  }

  private static void formatModifiers(PsiModifierListOwner element, int options, StringBuilder buffer) {
    PsiModifierList list = element.getModifierList();
    if (list == null) return;

    if (!BitUtil.isSet(options, SHOW_REDUNDANT_MODIFIERS)
        ? list.hasExplicitModifier(PsiModifier.PUBLIC)
        : list.hasModifierProperty(PsiModifier.PUBLIC)) {
      appendModifier(buffer, PsiModifier.PUBLIC);
    }

    if (list.hasModifierProperty(PsiModifier.PROTECTED)) {
      appendModifier(buffer, PsiModifier.PROTECTED);
    }
    if (list.hasModifierProperty(PsiModifier.PRIVATE)) {
      appendModifier(buffer, PsiModifier.PRIVATE);
    }

    if (!BitUtil.isSet(options, SHOW_REDUNDANT_MODIFIERS)
        ? list.hasExplicitModifier(PsiModifier.PACKAGE_LOCAL)
        : list.hasModifierProperty(PsiModifier.PACKAGE_LOCAL)) {
      if (element instanceof PsiClass && element.getParent() instanceof PsiDeclarationStatement) {// local class
        append(buffer, PsiBundle.message("local.class.preposition"));
      }
      else {
        appendModifier(buffer, PsiModifier.PACKAGE_LOCAL);
      }
    }

    if (!BitUtil.isSet(options, SHOW_REDUNDANT_MODIFIERS)
        ? list.hasExplicitModifier(PsiModifier.STATIC)
        : list.hasModifierProperty(PsiModifier.STATIC)) appendModifier(buffer, PsiModifier.STATIC);

    boolean isInterface = element instanceof PsiClass && ((PsiClass)element).isInterface();
    if (!isInterface && //cls modifier list
        (!BitUtil.isSet(options, SHOW_REDUNDANT_MODIFIERS)
         ? list.hasExplicitModifier(PsiModifier.ABSTRACT)
         : list.hasModifierProperty(PsiModifier.ABSTRACT))) appendModifier(buffer, PsiModifier.ABSTRACT);

    if (!BitUtil.isSet(options, SHOW_REDUNDANT_MODIFIERS)
        ? list.hasExplicitModifier(PsiModifier.FINAL)
        : list.hasModifierProperty(PsiModifier.FINAL)) appendModifier(buffer, PsiModifier.FINAL);

    if (list.hasModifierProperty(PsiModifier.NATIVE) && !BitUtil.isSet(options, JAVADOC_MODIFIERS_ONLY)) {
      appendModifier(buffer, PsiModifier.NATIVE);
    }
    if (list.hasModifierProperty(PsiModifier.SYNCHRONIZED) && !BitUtil.isSet(options, JAVADOC_MODIFIERS_ONLY)) {
      appendModifier(buffer, PsiModifier.SYNCHRONIZED);
    }
    if (list.hasModifierProperty(PsiModifier.STRICTFP) && !BitUtil.isSet(options, JAVADOC_MODIFIERS_ONLY)) {
      appendModifier(buffer, PsiModifier.STRICTFP);
    }
    if (list.hasModifierProperty(PsiModifier.TRANSIENT) &&
        element instanceof PsiVariable // javac 5 puts transient attr for methods
       ) {
      appendModifier(buffer, PsiModifier.TRANSIENT);
    }
    if (list.hasModifierProperty(PsiModifier.VOLATILE)) {
      appendModifier(buffer, PsiModifier.VOLATILE);
    }
  }

  private static void appendModifier(final StringBuilder buffer, @PsiModifier.ModifierConstant @NotNull String modifier) {
    append(buffer, VisibilityUtil.toPresentableText(modifier));
  }

  private static void append(StringBuilder buffer, String modifier) {
    appendSpaceIfNeeded(buffer);
    buffer.append(modifier);
  }

  public static String formatReferenceList(PsiReferenceList list, int options) {
    StringBuilder buffer = new StringBuilder();
    PsiJavaCodeReferenceElement[] refs = list.getReferenceElements();
    for(int i = 0; i < refs.length; i++) {
      PsiJavaCodeReferenceElement ref = refs[i];
      if (i > 0) {
        buffer.append(", ");
      }
      buffer.append(formatReference(ref, options));
    }
    return buffer.toString();
  }

  public static String formatType(@Nullable PsiType type, int options, @NotNull PsiSubstitutor substitutor) {
    type = substitutor.substitute(type);
    if (BitUtil.isSet(options, SHOW_RAW_TYPE)) {
      type = TypeConversionUtil.erasure(type);
    }
    else if (BitUtil.isSet(options, SHOW_RAW_NON_TOP_TYPE)) {
      if (!(PsiUtil.resolveClassInType(type) instanceof PsiTypeParameter)) {
        final boolean preserveEllipsis = type instanceof PsiEllipsisType;
        type = TypeConversionUtil.erasure(type);
        if (preserveEllipsis && type instanceof PsiArrayType) {
          type = new PsiEllipsisType(((PsiArrayType)type).getComponentType());
        }
      }
    }
    if (type == null) return "null";
    return !BitUtil.isSet(options, SHOW_FQ_CLASS_NAMES) ? type.getPresentableText(false) :
           !BitUtil.isSet(options, USE_INTERNAL_CANONICAL_TEXT) ? type.getCanonicalText(false) :
           type.getInternalCanonicalText();
  }

  public static String formatReference(PsiJavaCodeReferenceElement ref, int options) {
    return !BitUtil.isSet(options, SHOW_FQ_CLASS_NAMES) ? ref.getText() : ref.getCanonicalText();
  }

  @Nullable
  public static String getExternalName(PsiModifierListOwner owner) {
    return getExternalName(owner, true);
  }

  @Nullable
  public static String getExternalName(PsiModifierListOwner owner, final boolean showParamName) {
    return getExternalName(owner, showParamName, MAX_PARAMS_TO_SHOW);
  }

  @Nullable
  public static String getExternalName(PsiModifierListOwner owner, final boolean showParamName, int maxParamsToShow) {
    final StringBuilder builder = new StringBuilder();
    if (owner instanceof PsiClass) {
      ClassUtil.formatClassName((PsiClass)owner, builder);
      return builder.toString();
    }
    final PsiClass psiClass = PsiTreeUtil.getParentOfType(owner, PsiClass.class, false);
    if (psiClass == null) return null;
    ClassUtil.formatClassName(psiClass, builder);
    if (owner instanceof PsiMethod) {
      builder.append(" ");
      formatMethod((PsiMethod)owner, PsiSubstitutor.EMPTY,
                   SHOW_NAME | SHOW_FQ_NAME | SHOW_TYPE | SHOW_PARAMETERS | SHOW_FQ_CLASS_NAMES,
                   showParamName ? SHOW_NAME | SHOW_TYPE | SHOW_FQ_CLASS_NAMES : SHOW_TYPE | SHOW_FQ_CLASS_NAMES, maxParamsToShow, builder);
    }
    else if (owner instanceof PsiField) {
      builder.append(" ").append(((PsiField)owner).getName());
    }
    else if (owner instanceof PsiParameter) {
      final PsiElement declarationScope = ((PsiParameter)owner).getDeclarationScope();
      if (!(declarationScope instanceof PsiMethod)) {
        return null;
      }
      final PsiMethod psiMethod = (PsiMethod)declarationScope;

      builder.append(" ");
      formatMethod(psiMethod, PsiSubstitutor.EMPTY,
                   SHOW_NAME | SHOW_FQ_NAME | SHOW_TYPE | SHOW_PARAMETERS | SHOW_FQ_CLASS_NAMES,
                   showParamName ? SHOW_NAME | SHOW_TYPE | SHOW_FQ_CLASS_NAMES : SHOW_TYPE | SHOW_FQ_CLASS_NAMES, maxParamsToShow, builder);
      builder.append(" ");

      if (showParamName) {
        formatVariable((PsiVariable)owner, SHOW_NAME, PsiSubstitutor.EMPTY, builder);
      }
      else {
        builder.append(psiMethod.getParameterList().getParameterIndex((PsiParameter)owner));
      }
    }
    else {
      return null;
    }
    return builder.toString();
  }

  public static String getPackageDisplayName(@NotNull final PsiClass psiClass) {
    if (psiClass instanceof PsiTypeParameter) {
      PsiTypeParameterListOwner owner = ((PsiTypeParameter)psiClass).getOwner();
      String ownerName = null;
      if (owner instanceof PsiClass) {
        ownerName = ((PsiClass)owner).getQualifiedName();
        if (ownerName == null) {
          ownerName = owner.getName();
        }
      }
      else if (owner instanceof PsiMethod) {
        ownerName = owner.getName();
      }
      return ownerName == null ? "type parameter" : "type parameter of " + ownerName;
    }

    String packageName = psiClass.getQualifiedName();
    packageName = packageName == null || packageName.lastIndexOf('.') <= 0 ? "" : packageName.substring(0, packageName.lastIndexOf('.'));
    if (packageName.isEmpty()) {
      packageName = "default package";
    }
    return packageName;
  }
}