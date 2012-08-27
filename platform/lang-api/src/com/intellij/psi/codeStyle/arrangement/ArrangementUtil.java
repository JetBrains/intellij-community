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
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.codeStyle.arrangement.model.ArrangementAtomMatchCondition;
import com.intellij.psi.codeStyle.arrangement.model.ArrangementCompositeMatchCondition;
import com.intellij.psi.codeStyle.arrangement.model.ArrangementMatchCondition;
import com.intellij.psi.codeStyle.arrangement.model.ArrangementMatchConditionVisitor;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * @author Denis Zhdanov
 * @since 7/17/12 11:24 AM
 */
public class ArrangementUtil {
  
  private ArrangementUtil() {
  }

  //region Serialization

  @NotNull
  public static List<ArrangementRule> readExternal(@NotNull Element element, @NotNull Language language) {
    final List<ArrangementRule> result = new ArrayList<ArrangementRule>();
    ArrangementRuleSerializer serializer = getSerializer(language);
    for (Object child : element.getChildren()) {
      ArrangementRule rule = serializer.deserialize((Element)child);
      if (rule != null) {
        result.add(rule);
      }
    }
    return result;
  }

  public static void writeExternal(@NotNull Element element, @NotNull List<ArrangementRule> rules, @NotNull Language language) {
    if (rules.isEmpty()) {
      return;
    }

    ArrangementRuleSerializer serializer = getSerializer(language);
    for (ArrangementRule rule : rules) {
      Element e = serializer.serialize(rule);
      if (e != null) {
        element.addContent(e);
      }
    }
  }

  private static ArrangementRuleSerializer getSerializer(@NotNull Language language) {
    Rearranger<?> rearranger = Rearranger.EXTENSION.forLanguage(language);
    if (rearranger instanceof ArrangementRuleSerializer) {
      return new CompositeArrangementRuleSerializer((ArrangementRuleSerializer)rearranger, DefaultArrangementRuleSerializer.INSTANCE);
    }
    return DefaultArrangementRuleSerializer.INSTANCE;
  }
  
  private static class CompositeArrangementRuleSerializer implements ArrangementRuleSerializer {

    @NotNull private final List<ArrangementRuleSerializer> mySerializers = new ArrayList<ArrangementRuleSerializer>();

    CompositeArrangementRuleSerializer(@NotNull ArrangementRuleSerializer ... serializers) {
      mySerializers.addAll(Arrays.asList(serializers));
    }

    @Nullable
    @Override
    public ArrangementRule deserialize(@NotNull Element element) {
      for (ArrangementRuleSerializer serializer : mySerializers) {
        ArrangementRule rule = serializer.deserialize(element);
        if (rule != null) {
          return rule;
        }
      }
      return null;
    }

    @Nullable
    @Override
    public Element serialize(ArrangementRule rule) {
      for (ArrangementRuleSerializer serializer : mySerializers) {
        Element element = serializer.serialize(rule);
        if (element != null) {
          return element;
        }
      }
      return null;
    }
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
   *   <li>all symbols between the resulting range start offset and given range's start offset are white spaces or tabulations;</li>
   *   <li>all symbols between the given range's end offset and resulting range's end offset are white spaces or tabulations;</li>
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
   * @param text          target text against which the ranges are built
   * @return              expanded range if possible; <code>null</code> otherwise
   */
  @Nullable
  public static TextRange expandToLine(@NotNull TextRange initialRange, @NotNull CharSequence text) {
    int startOffsetToUse = initialRange.getStartOffset();
    for (int i = startOffsetToUse - 1; i >= 0; i--) {
      char c = text.charAt(i);
      if (!StringUtil.isWhiteSpace(c)) {
        return null;
      }
      else if (c == '\n') {
        break;
      }
      else {
        startOffsetToUse = i;
      }
    }
    
    int endOffsetToUse = initialRange.getEndOffset();
    for (int i = endOffsetToUse; i < text.length(); i++) {
      char c = text.charAt(i);
      if (!StringUtil.isWhiteSpace(c)) {
        return null;
      }
      else if (c == '\n') {
        endOffsetToUse = i;
        break;
      }
    }
    
    return TextRange.create(startOffsetToUse, endOffsetToUse);
  }
  //endregion
}
