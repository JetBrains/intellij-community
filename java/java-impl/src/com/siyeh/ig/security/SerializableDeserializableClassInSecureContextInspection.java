// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.security;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.ControlFlowUtils;
import com.siyeh.ig.psiutils.SerializationUtils;
import com.siyeh.ig.serialization.SerializableInspectionBase;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;

/**
 * @author Bas Leijdekkers
 */
public final class SerializableDeserializableClassInSecureContextInspection extends SerializableInspectionBase {

  public SerializableDeserializableClassInSecureContextInspection() {
    superClassString = "java.awt.Component,java.lang.Throwable,java.lang.Enum";
    parseString(superClassString, superClassList);
  }

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    final Boolean serializable = (Boolean)infos[0];
    final Boolean deserializable = (Boolean)infos[1];
    if (serializable.booleanValue()) {
      return deserializable.booleanValue()
             ? InspectionGadgetsBundle.message("serializable.deserializable.class.in.secure.context.problem.descriptor")
             : InspectionGadgetsBundle.message("serializable.class.in.secure.context.problem.descriptor");
    }
    else {
      return InspectionGadgetsBundle.message("deserializable.class.in.secure.context.problem.descriptor");
    }
  }

  @Nullable
  @Override
  protected LocalQuickFix buildFix(Object... infos) {
    final Boolean serializable = (Boolean)infos[0];
    final Boolean deserializable = (Boolean)infos[1];
    final PsiClass aClass = (PsiClass)infos[2];
    final boolean addReadObjectMethod = deserializable.booleanValue() && !hasOwnReadObjectMethod(aClass);
    final boolean addWriteObjectMethod = serializable.booleanValue() && !hasOwnWriteObjectMethod(aClass);
    if (!addReadObjectMethod && !addWriteObjectMethod) {
      return null;
    }
    return new AddReadWriteObjectMethodsFix(addReadObjectMethod, addWriteObjectMethod);
  }

  private static boolean hasOwnReadObjectMethod(PsiClass aClass) {
    return ContainerUtil.exists(aClass.findMethodsByName("readObject", false), SerializationUtils::isReadObject);
  }

  private static boolean hasOwnWriteObjectMethod(PsiClass aClass) {
    return ContainerUtil.exists(aClass.findMethodsByName("writeObject", false), SerializationUtils::isWriteObject);
  }

  private static class AddReadWriteObjectMethodsFix extends PsiUpdateModCommandQuickFix {

    private final boolean myReadObject;
    private final boolean myWriteObject;

    AddReadWriteObjectMethodsFix(boolean readObject, boolean writeObject) {
      myReadObject = readObject;
      myWriteObject = writeObject;
    }

    @Nls
    @NotNull
    @Override
    public String getName() {
      if (myReadObject) {
        return myWriteObject ? getFamilyName() : InspectionGadgetsBundle.message("add.read.write.object.methods.fix.text2");
      }
      return InspectionGadgetsBundle.message("add.read.write.object.methods.fix.text");
    }

    @Nls
    @NotNull
    @Override
    public String getFamilyName() {
      return InspectionGadgetsBundle.message("add.read.write.object.methods.fix.family.name");
    }

    @Override
    protected void applyFix(@NotNull Project project, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
      final PsiClass containingClass = PsiTreeUtil.getParentOfType(element, PsiClass.class);
      if (containingClass == null) {
        return;
      }
      final String className = containingClass.getQualifiedName();
      final PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
      if (myReadObject) {
        final PsiMethod readObjectMethod = factory.createMethodFromText(
          "private void readObject(java.io.ObjectInputStream in) throws java.io.IOException, java.lang.ClassNotFoundException {" +
          "  throw new java.io.NotSerializableException(\"" + className + "\");" +
          "}", containingClass);
        containingClass.add(readObjectMethod);
      }
      if (myWriteObject) {
        final PsiMethod writeObjectMethod = factory.createMethodFromText(
          "private void writeObject(java.io.ObjectOutputStream out) throws java.io.IOException {" +
          "  throw new java.io.NotSerializableException(\"" + className + "\");" +
          "}", containingClass);
        containingClass.add(writeObjectMethod);
      }
    }
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new SerializableDeserializableClassInSecureContextVisitor();
  }

  private class SerializableDeserializableClassInSecureContextVisitor extends BaseInspectionVisitor {

    @Override
    public void visitClass(@NotNull PsiClass aClass) {
      if (aClass.isInterface() ||
          aClass.isAnnotationType() ||
          aClass.isRecord() ||
          aClass instanceof PsiTypeParameter ||
          !SerializationUtils.isSerializable(aClass) ||
          isIgnoredSubclass(aClass)) {
        return;
      }
      if (ignoreAnonymousInnerClasses && aClass instanceof PsiAnonymousClass) {
        return;
      }
      if (!hasSerializableState(aClass)) {
        // doesn't matter, class has no state anyway.
        return;
      }
      final boolean serializable = !hasWriteObjectMethodWhichAlwaysThrowsException(aClass);
      final boolean deserializable = !hasReadObjectMethodWhichAlwaysThrowsException(aClass);
      if (!serializable && !deserializable) {
        return;
      }
      registerClassError(aClass, Boolean.valueOf(serializable), Boolean.valueOf(deserializable), aClass);
    }

    private static boolean hasSerializableState(PsiClass aClass) {
      return Arrays.stream(aClass.getFields())
        .filter(f -> !f.hasModifierProperty(PsiModifier.STATIC))
        .filter(f -> !f.hasModifierProperty(PsiModifier.TRANSIENT))
        .anyMatch(f -> !(f instanceof PsiEnumConstant));
    }

    private static boolean hasReadObjectMethodWhichAlwaysThrowsException(PsiClass aClass) {
      for (final PsiMethod method : aClass.findMethodsByName("readObject", true)) {
        if (SerializationUtils.isReadObject(method)) {
          return ControlFlowUtils.methodAlwaysThrowsException((PsiMethod)method.getNavigationElement());
        }
      }
      return false;
    }

    private static boolean hasWriteObjectMethodWhichAlwaysThrowsException(PsiClass aClass) {
      for (final PsiMethod method : aClass.findMethodsByName("writeObject", true)) {
        if (SerializationUtils.isWriteObject(method)) {
          return ControlFlowUtils.methodAlwaysThrowsException((PsiMethod)method.getNavigationElement());
        }
      }
      return false;
    }
  }
}
