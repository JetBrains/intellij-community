/*
 * Copyright 2003-2022 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.serialization;

import com.intellij.codeInspection.options.OptPane;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.psi.*;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.SerializationUtils;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

import static com.intellij.codeInspection.options.OptPane.checkbox;
import static com.intellij.codeInspection.options.OptPane.pane;

public final class SerializableHasSerializationMethodsInspection extends SerializableInspectionBase {

  public boolean ignoreClassWithoutFields = false;

  public SerializableHasSerializationMethodsInspection() {
    superClassString = "java.io.Externalizable,java.awt.Component";
    parseString(superClassString, superClassList);
  }

  @Override
  @NotNull
  public String buildErrorString(Object... infos) {
    final boolean hasReadObject = ((Boolean)infos[0]).booleanValue();
    final boolean hasWriteObject = ((Boolean)infos[1]).booleanValue();
    if (!hasReadObject && !hasWriteObject) {
      return InspectionGadgetsBundle.message("serializable.has.serialization.methods.problem.descriptor");
    }
    else if (hasReadObject) {
      return InspectionGadgetsBundle.message("serializable.has.serialization.methods.problem.descriptor1");
    }
    else {
      return InspectionGadgetsBundle.message("serializable.has.serialization.methods.problem.descriptor2");
    }
  }

  @Override
  protected @NotNull OptPane getAdditionalOptions() {
    return pane(checkbox("ignoreClassWithoutFields", InspectionGadgetsBundle.message("serializable.has.serialization.methods.ignore.option")));
  }

  @Override
  public void writeSettings(@NotNull Element node) throws WriteExternalException {
    if (!superClassList.isEmpty()) superClassString = formatString(superClassList);
    defaultWriteSettings(node, "ignoreClassWithoutFields");
    writeBooleanOption(node, "ignoreClassWithoutFields", false);
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new SerializableHasSerializationMethodsVisitor();
  }

  private class SerializableHasSerializationMethodsVisitor extends BaseInspectionVisitor {

    @Override
    public void visitClass(@NotNull PsiClass aClass) {
      // no call to super, so it doesn't drill down
      if (aClass.isInterface() || aClass.isAnnotationType() || aClass.isEnum() || aClass.isRecord()) {
        return;
      }
      if (aClass instanceof PsiTypeParameter || aClass instanceof PsiEnumConstantInitializer) {
        return;
      }
      if (ignoreAnonymousInnerClasses && aClass instanceof PsiAnonymousClass) {
        return;
      }
      if (!SerializationUtils.isSerializable(aClass)) {
        return;
      }
      final boolean hasReadObject = SerializationUtils.hasReadObject(aClass);
      final boolean hasWriteObject = SerializationUtils.hasWriteObject(aClass);
      if (hasWriteObject && hasReadObject) {
        return;
      }
      if (isIgnoredSubclass(aClass)) {
        return;
      }
      if (ignoreClassWithoutFields) {
        final PsiField[] fields = aClass.getFields();
        boolean hasField = false;
        for (PsiField field : fields) {
          if (field.hasModifierProperty(PsiModifier.STATIC)) {
            continue;
          }
          hasField = true;
          break;
        }
        if (!hasField) {
          return;
        }
      }
      registerClassError(aClass, Boolean.valueOf(hasReadObject), Boolean.valueOf(hasWriteObject));
    }
  }
}