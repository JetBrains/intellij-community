/*
 * Copyright 2003-2017 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.performance;

import com.intellij.codeInspection.options.OptPane;
import com.intellij.codeInspection.options.OptionController;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMExternalizer;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.psi.PsiExpressionList;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiNewExpression;
import com.intellij.psi.PsiType;
import com.intellij.util.JdomKt;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.TypeUtils;
import org.intellij.lang.annotations.Pattern;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

import java.util.Set;
import java.util.TreeSet;

import static com.intellij.codeInspection.options.OptPane.checkbox;

public final class CollectionsMustHaveInitialCapacityInspection
  extends BaseInspection {

  private final CollectionsListSettings mySettings = new CollectionsListSettings() {
    @Override
    protected Set<String> getDefaultSettings() {
      final Set<String> classes = new TreeSet<>(DEFAULT_COLLECTION_LIST);
      classes.add("java.util.BitSet");
      return classes;
    }
  };
  public boolean myIgnoreFields;

  @Override
  public void readSettings(@NotNull Element node) throws InvalidDataException {
    mySettings.readSettings(node);
    myIgnoreFields = JDOMExternalizer.readBoolean(node, "ignoreFields");
  }

  @Override
  public void writeSettings(@NotNull Element node) throws WriteExternalException {
    mySettings.writeSettings(node);
    if (myIgnoreFields) {
      JdomKt.addOptionTag(node, "ignoreFields", Boolean.toString(true), "setting");
    }
  }

  @Override
  public @NotNull OptPane getOptionsPane() {
    return mySettings.getOptionPane().prefix("mySettings")
      .append(checkbox("myIgnoreFields", InspectionGadgetsBundle.message(
      "inspection.collection.must.have.initial.capacity.initializers.option")));
  }

  @Pattern(VALID_ID_PATTERN)
  @Override
  @NotNull
  public String getID() {
    return "CollectionWithoutInitialCapacity";
  }

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message(
      "collections.must.have.initial.capacity.problem.descriptor");
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new CollectionInitialCapacityVisitor();
  }

  private class CollectionInitialCapacityVisitor extends BaseInspectionVisitor {

    @Override
    public void visitNewExpression(@NotNull PsiNewExpression expression) {
      super.visitNewExpression(expression);
      if (myIgnoreFields && expression.getParent() instanceof PsiField) {
        return;
      }

      final PsiType type = expression.getType();
      if (!mySettings.getCollectionClassesRequiringCapacity().contains(TypeUtils.resolvedClassName(type))) {
        return;
      }
      final PsiExpressionList argumentList = expression.getArgumentList();
      if (argumentList == null || !argumentList.isEmpty()) {
        return;
      }
      registerNewExpressionError(expression);
    }
  }
}