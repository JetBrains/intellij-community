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
package com.intellij.refactoring.rename;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiImportStatement;
import com.intellij.refactoring.modplan.ModPlanEdit;
import com.intellij.refactoring.modplan.rename.ModPlanWritableUsageInfo;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;

/**
 * A single import of another class whose simple name the renamed class takes. After the rename the import would keep the simple name
 * for the other class, so the write deletes it. The references to the other class get its qualified name, see
 * {@link ClassHidesImportedClassUsageInfo}.
 *
 * <pre>{@code
 * // pack2/Usage.java
 * import java.util.List;
 * import pack1.*;
 * class Usage { void m() { List list = null; MyList myList = null; } }
 * // rename pack1.MyList to List
 * import pack1.*;
 * class Usage { void m() { java.util.List list = null; List myList = null; } }
 * }</pre>
 */
public class CollidingClassImportUsageInfo extends ResolvableCollisionUsageInfo implements ModPlanWritableUsageInfo {
  private final PsiImportStatement myImportStatement;

  public CollidingClassImportUsageInfo(PsiImportStatement element, PsiElement referencedElement) {
    super(element, referencedElement);
    myImportStatement = element;
  }

  public PsiImportStatement getImportStatement() {
    return myImportStatement;
  }

  /** The import of the other class of the new name is deleted, as the legacy rename of a class deletes it. */
  @Override
  public @NotNull List<ModPlanEdit> asModPlanEdits(@NotNull String newName, @NotNull Map<PsiElement, String> renamed) {
    return List.of(ModPlanEdit.delete(myImportStatement));
  }
}
