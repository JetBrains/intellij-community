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
package com.intellij.application.options.codeStyle.arrangement;

import com.intellij.openapi.util.Pair;
import com.intellij.psi.codeStyle.arrangement.ArrangementRule;
import com.intellij.psi.codeStyle.arrangement.JavaRearranger;
import com.intellij.psi.codeStyle.arrangement.match.ArrangementEntryType;
import com.intellij.psi.codeStyle.arrangement.match.ArrangementModifier;
import com.intellij.psi.codeStyle.arrangement.match.StdArrangementEntryMatcher;
import com.intellij.psi.codeStyle.arrangement.model.ArrangementAtomMatchCondition;
import com.intellij.psi.codeStyle.arrangement.model.ArrangementMatchCondition;
import com.intellij.psi.codeStyle.arrangement.model.ArrangementSettingType;
import com.intellij.ui.treeStructure.Tree;
import gnu.trove.TIntIntHashMap;
import gnu.trove.TIntObjectHashMap;
import org.jetbrains.annotations.NotNull;
import org.junit.Before;

import javax.swing.*;
import javax.swing.tree.TreePath;
import java.util.Arrays;

import static org.junit.Assert.assertTrue;

/**
 * @author Denis Zhdanov
 * @since 8/16/12 11:04 AM
 */
public abstract class AbstractArrangementRuleEditingModelTest {

  @NotNull protected ArrangementRuleEditingModelBuilder                 myBuilder;
  @NotNull protected JTree                                              myTree;
  @NotNull protected ArrangementTreeNode                                myRoot;
  @NotNull protected TIntObjectHashMap<ArrangementRuleEditingModelImpl> myRowMappings;
  @NotNull protected JavaRearranger                                     myGrouper;

  @Before
  public void setUp() {
    myBuilder = new ArrangementRuleEditingModelBuilder();
    myRoot = new ArrangementTreeNode(null);
    myTree = new Tree(myRoot);
    myTree.expandPath(new TreePath(myRoot));
    myRowMappings = new TIntObjectHashMap<ArrangementRuleEditingModelImpl>();
    myGrouper = new JavaRearranger();
  }

  protected void configure(@NotNull ArrangementMatchCondition matchCondition) {
    Pair<ArrangementRuleEditingModelImpl,TIntIntHashMap> pair = myBuilder.build(
      new ArrangementRule<StdArrangementEntryMatcher>(new StdArrangementEntryMatcher(matchCondition)), myTree, myRoot, null, myGrouper
    );
    myRowMappings.put(pair.first.getRow(), pair.first);
  }

  protected static ArrangementAtomMatchCondition atom(@NotNull Object condition) {
    final ArrangementSettingType type;
    if (condition instanceof ArrangementEntryType) {
      type = ArrangementSettingType.TYPE;
    }
    else if (condition instanceof ArrangementModifier) {
      type = ArrangementSettingType.MODIFIER;
    }
    else {
      throw new IllegalArgumentException(String.format("Unexpected condition of class %s: %s", condition.getClass(), condition));
    }
    return new ArrangementAtomMatchCondition(type, condition);
  }

  protected void checkRows(int... rows) {
    for (int row : rows) {
      assertTrue(
        String.format("Expected to find mappings for rows %s. Actual: %s", Arrays.toString(rows), Arrays.toString(myRowMappings.keys())),
        myRowMappings.containsKey(row)
      );
    }
  }
}
