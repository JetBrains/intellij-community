// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.deadCode;

import com.intellij.codeInspection.reference.RefClass;
import com.intellij.java.syntax.parser.JavaKeywords;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.uast.*;

import java.util.List;
import java.util.Set;

/**
 * @author Bas Leijdekkers
 */
@ApiStatus.Internal
public final class RefSerializationUtil {
  private RefSerializationUtil() {}

  private static boolean isExternalizableNoParameterConstructor(@NotNull UMethod method, @Nullable RefClass refClass) {
    if (!method.isConstructor()) return false;
    if (method.getVisibility() != UastVisibility.PUBLIC) return false;
    List<UParameter> parameterList = method.getUastParameters();
    if (!parameterList.isEmpty()) return false;
    UClass aClass = UDeclarationKt.getContainingDeclaration(method, UClass.class);
    return aClass == null || isExternalizable(aClass, refClass);
  }

  public static boolean isSerializationImplicitlyUsedField(@NotNull UField field) {
    String name = field.getName();
    if (CommonClassNames.SERIAL_VERSION_UID_FIELD_NAME.equals(name)) {
      if (!PsiTypes.longType().equals(field.getType())) return false;
    }
    else if ("serialPersistentFields".equals(name)) {
      if (field.getVisibility() != UastVisibility.PRIVATE) return false;
      if (!(field.getType() instanceof PsiArrayType arrayType) || arrayType.getArrayDimensions() != 1) return false;
      PsiType componentType = arrayType.getComponentType();
      PsiClass aClass = PsiUtil.resolveClassInClassTypeOnly(componentType);
      if (aClass != null && !"java.io.ObjectStreamField".equals(aClass.getQualifiedName())) return false;
    }
    else {
      return false;
    }
    if (!field.isStatic() || !field.isFinal()) return false;
    UClass aClass = UDeclarationKt.getContainingDeclaration(field, UClass.class);
    return aClass == null || isSerializable(aClass, null);
  }

  private static boolean isWriteObjectMethod(@NotNull UMethod method, @Nullable RefClass refClass) {
    if (!"writeObject".equals(method.getName())) return false;
    if (method.isStatic() || method.getVisibility() != UastVisibility.PRIVATE) return false;
    List<UParameter> parameters = method.getUastParameters();
    if (parameters.size() != 1) return false;
    if (!equalsToText(parameters.get(0).getType(), "java.io.ObjectOutputStream")) return false;
    UClass aClass = UDeclarationKt.getContainingDeclaration(method, UClass.class);
    return aClass == null || isSerializable(aClass, refClass);
  }

  private static boolean isReadObjectMethod(@NotNull UMethod method, @Nullable RefClass refClass) {
    if (!"readObject".equals(method.getName())) return false;
    if (method.isStatic() || method.getVisibility() != UastVisibility.PRIVATE) return false;
    List<UParameter> parameters = method.getUastParameters();
    if (parameters.size() != 1) return false;
    if (!equalsToText(parameters.get(0).getType(), "java.io.ObjectInputStream")) return false;
    if (!equalsToText(method.getReturnType(), JavaKeywords.VOID)) return false;
    UClass aClass = UDeclarationKt.getContainingDeclaration(method, UClass.class);
    return aClass == null || isSerializable(aClass, refClass);
  }

  private static boolean isReadObjectNoDataMethod(@NotNull UMethod method, @Nullable RefClass refClass) {
    if (!"readObjectNoData".equals(method.getName())) return false;
    if (method.isStatic() || method.getVisibility() != UastVisibility.PRIVATE) return false;
    if (!method.getUastParameters().isEmpty()) return false;
    if (!equalsToText(method.getReturnType(), JavaKeywords.VOID)) return false;
    UClass aClass = UDeclarationKt.getContainingDeclaration(method, UClass.class);
    return aClass == null || isSerializable(aClass, refClass);
  }

  private static boolean isWriteReplaceMethod(@NotNull UMethod method, @Nullable RefClass refClass) {
    if (!"writeReplace".equals(method.getName())) return false;
    if (method.isStatic()) return false;
    if (!method.getUastParameters().isEmpty()) return false;
    if (!equalsToText(method.getReturnType(), CommonClassNames.JAVA_LANG_OBJECT)) return false;
    UClass aClass = UDeclarationKt.getContainingDeclaration(method, UClass.class);
    return aClass == null || isSerializable(aClass, refClass);
  }

  private static boolean isReadResolveMethod(@NotNull UMethod method, @Nullable RefClass refClass) {
    if (!"readResolve".equals(method.getName())) return false;
    if (method.isStatic()) return false;
    if (!method.getUastParameters().isEmpty()) return false;
    if (!equalsToText(method.getReturnType(), CommonClassNames.JAVA_LANG_OBJECT)) return false;
    UClass aClass = UDeclarationKt.getContainingDeclaration(method, UClass.class);
    return aClass == null || isSerializable(aClass, refClass);
  }

  private static boolean equalsToText(PsiType type, @NotNull String text) {
    return type != null && type.equalsToText(text);
  }

  private static boolean isSerializable(@NotNull UClass aClass, @Nullable RefClass refClass) {
    return isSerializable(aClass, refClass, "java.io.Serializable");
  }

  private static boolean isExternalizable(@NotNull UClass aClass, @Nullable RefClass refClass) {
    return isSerializable(aClass, refClass, "java.io.Externalizable");
  }

  private static boolean isSerializable(@NotNull UClass aClass, @Nullable RefClass refClass, @NotNull String fqn) {
    PsiClass psiClass = aClass.getJavaPsi();
    Project project = psiClass.getProject();
    PsiClass serializableClass = DumbService.getInstance(project).computeWithAlternativeResolveEnabled(
      () -> JavaPsiFacade.getInstance(project).findClass(fqn, psiClass.getResolveScope()));
    return serializableClass != null && isSerializable(aClass, refClass, serializableClass);
  }

  private static boolean isSerializable(@Nullable UClass aClass, @Nullable RefClass refClass, @NotNull PsiClass serializableClass) {
    if (aClass == null) return false;
    if (aClass.getJavaPsi().isInheritor(serializableClass, true)) return true;
    if (refClass != null) {
      Set<RefClass> subClasses = refClass.getSubClasses();
      for (RefClass subClass : subClasses) {
        //TODO reimplement
        if (isSerializable(subClass.getUastElement(), subClass, serializableClass)) return true;
      }
    }
    return false;
  }

  public static boolean isSerializationMethod(@NotNull UMethod method, @Nullable RefClass refClass) {
    return isReadObjectMethod(method, refClass) ||
           isReadObjectNoDataMethod(method, refClass) ||
           isWriteObjectMethod(method, refClass) ||
           isReadResolveMethod(method, refClass) ||
           isWriteReplaceMethod(method, refClass) ||
           isExternalizableNoParameterConstructor(method, refClass);
  }
}
