/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.psi.codeStyle.arrangement;

import com.intellij.lang.Language;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.codeStyle.arrangement.match.ArrangementEntryType;
import com.intellij.psi.codeStyle.arrangement.match.ArrangementModifier;
import com.intellij.psi.codeStyle.arrangement.model.*;
import com.intellij.util.containers.ContainerUtilRt;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * @author Denis Zhdanov
 * @since 7/17/12 11:24 AM
 */
public class ArrangementUtil {
  
  private ArrangementUtil() {
  }

  //region Serialization

  @Nullable
  public static ArrangementSettings readExternal(@NotNull Element element, @NotNull Language language) {
    ArrangementSettingsSerializer serializer = getSerializer(language);
    return serializer.deserialize(element);
  }

  public static void writeExternal(@NotNull Element element, @NotNull ArrangementSettings settings, @NotNull Language language) {
    ArrangementSettingsSerializer serializer = getSerializer(language);
    serializer.serialize(settings, element);
  }

  private static ArrangementSettingsSerializer getSerializer(@NotNull Language language) {
    Rearranger<?> rearranger = Rearranger.EXTENSION.forLanguage(language);
    if (rearranger instanceof ArrangementSettingsSerializer) {
      return (ArrangementSettingsSerializer)rearranger;
    }
    return DefaultArrangementSettingsSerializer.INSTANCE;
  }
  
  //endregion

  @NotNull
  public static ArrangementMatchCondition and(@NotNull ArrangementMatchCondition... nodes) {
    final ArrangementCompositeMatchCondition result = new ArrangementCompositeMatchCondition(ArrangementOperator.AND);
    final ArrangementMatchConditionVisitor visitor = new ArrangementMatchConditionVisitor() {
      @Override
      public void visit(@NotNull ArrangementAtomMatchCondition node) {
        result.addOperand(node);
      }

      @Override
      public void visit(@NotNull ArrangementCompositeMatchCondition node) {
        if (node.getOperator() == ArrangementOperator.AND) {
          for (ArrangementMatchCondition operand : node.getOperands()) {
            operand.invite(this);
          }
        }
        else {
          result.addOperand(node);
        }
      }
    };
    for (ArrangementMatchCondition node : nodes) {
      node.invite(visitor);
    }
    return result.getOperands().size() == 1 ? result.getOperands().iterator().next() : result;
  }

  //region ArrangementEntry

  /**
   * Tries to build a text range on the given arguments basis. It should conform to the criteria below:
   * <pre>
   * <ul>
   *   <li>it's start offset is located at the start of the same line where given range starts;</li>
   *   <li>it's end offset is located at the end of the same line where given range ends;</li>
   * </ul>
   * </pre>
   * This method is expected to be used in a situation when we want to arrange complete rows.
   * Example:
   * <pre>
   *   class Test {
   *        void test() {
   *        }
   *      int i;
   *   }
   * </pre>
   * Suppose, we want to locate fields before methods. We can move the exact field and method range then but indent will be broken,
   * i.e. we'll get the result below:
   * <pre>
   *   class Test {
   *        int i;
   *      void test() {
   *        }
   *   }
   * </pre>
   * We can expand field and method range to the whole lines and that would allow to achieve the desired result:
   * <pre>
   *   class Test {
   *      int i;
   *        void test() {
   *        }
   *   }
   * </pre>
   * 
   * @param initialRange  anchor range
   * @param document      target document against which the ranges are built
   * @return              expanded range if possible; <code>null</code> otherwise
   */
  @NotNull
  public static TextRange expandToLine(@NotNull TextRange initialRange, @NotNull Document document) {
    int startLine = document.getLineNumber(initialRange.getStartOffset());
    int startOffsetToUse = document.getLineStartOffset(startLine);

    int endLine = document.getLineNumber(initialRange.getEndOffset());
    int endOffsetToUse = document.getLineEndOffset(endLine);

    return TextRange.create(startOffsetToUse, endOffsetToUse);
  }
  //endregion
  
  @NotNull
  public static ArrangementSettingType parseType(@NotNull Object condition) throws IllegalArgumentException {
    if (condition instanceof ArrangementEntryType) {
      return ArrangementSettingType.TYPE;
    }
    else if (condition instanceof ArrangementModifier) {
      return ArrangementSettingType.MODIFIER;
    }
    else {
      throw new IllegalArgumentException(String.format(
        "Can't parse type for the given condition of class '%s': %s", condition.getClass(), condition
      ));
    }
  }

  @SuppressWarnings("AssignmentToForLoopParameter")
  @NotNull
  public static HierarchicalArrangementConditionNode group(@NotNull ArrangementMatchCondition condition,
                                                           @NotNull List<Set<ArrangementMatchCondition>> groupingRules)
  {
    if (groupingRules.isEmpty()) {
      // No grouping rules have been provided, use a flat structure. 
      return new HierarchicalArrangementConditionNode(condition);
    }

    final List<ArrangementMatchCondition> conditions = new ArrayList<ArrangementMatchCondition>();
    condition.invite(new ArrangementMatchConditionVisitor() {
      @Override
      public void visit(@NotNull ArrangementAtomMatchCondition condition) {
        conditions.add(condition);
      }

      @Override
      public void visit(@NotNull ArrangementCompositeMatchCondition condition) {
        if (condition.getOperator() == ArrangementOperator.AND && conditions.isEmpty() /* Don't process nested composite conditions*/) {
          for (ArrangementMatchCondition operand : condition.getOperands()) {
            conditions.add(operand);
          }
        }
        else {
          conditions.add(condition);
        }
      }
    });
    if (conditions.isEmpty()) {
      return new HierarchicalArrangementConditionNode(condition);
    }
    
    HierarchicalArrangementConditionNode result = null;
    for (Set<ArrangementMatchCondition> rules : groupingRules) {
      for (ArrangementMatchCondition rule : rules) {
        for (int i = 0; i < conditions.size(); i++) {
          ArrangementMatchCondition c = conditions.get(i);
          if (rule.equals(c)) {
            conditions.remove(i--);
            HierarchicalArrangementConditionNode node = new HierarchicalArrangementConditionNode(c);
            if (result == null) {
              result = node;
            }
            else {
              result.setChild(node);
            }
          }
        }
      }
    }

    if (!conditions.isEmpty()) {
      HierarchicalArrangementConditionNode node;
      if (conditions.size() == 1) {
        node = new HierarchicalArrangementConditionNode(conditions.get(0));
      }
      else {
        ArrangementCompositeMatchCondition c = new ArrangementCompositeMatchCondition(ArrangementOperator.AND);
        for (ArrangementMatchCondition operand : conditions) {
          c.addOperand(operand);
        }
        node = new HierarchicalArrangementConditionNode(c);
      }
      if (result == null) {
        result = node;
      }
      else {
        result.setChild(node);
      }
    }
    assert result != null;
    return result;
  }

  public static <T> Set<T> flatten(@NotNull Iterable<? extends Iterable<T>> data) {
    Set<T> result = ContainerUtilRt.newHashSet();
    for (Iterable<T> i : data) {
      for (T t : i) {
        result.add(t);
      }
    }
    return result;
  }
}
