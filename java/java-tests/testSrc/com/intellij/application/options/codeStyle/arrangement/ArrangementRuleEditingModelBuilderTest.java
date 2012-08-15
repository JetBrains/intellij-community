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

import com.intellij.psi.codeStyle.arrangement.ArrangementUtil;
import com.intellij.psi.codeStyle.arrangement.JavaRearranger;
import com.intellij.psi.codeStyle.arrangement.match.ArrangementEntryType;
import com.intellij.psi.codeStyle.arrangement.match.ArrangementModifier;
import com.intellij.psi.codeStyle.arrangement.model.ArrangementSettingType;
import com.intellij.psi.codeStyle.arrangement.model.ArrangementSettingsAtomNode;
import com.intellij.psi.codeStyle.arrangement.model.ArrangementSettingsNode;
import gnu.trove.TIntObjectHashMap;
import org.jetbrains.annotations.NotNull;
import org.junit.Before;
import org.junit.Test;

import javax.swing.tree.DefaultMutableTreeNode;

import static com.intellij.psi.codeStyle.arrangement.match.ArrangementModifier.PUBLIC;
import static com.intellij.psi.codeStyle.arrangement.match.ArrangementModifier.STATIC;
import static org.junit.Assert.assertEquals;

/**
 * @author Denis Zhdanov
 * @since 08/15/2012
 */
public class ArrangementRuleEditingModelBuilderTest {

  @NotNull private ArrangementRuleEditingModelBuilder             myBuilder;
  @NotNull private DefaultMutableTreeNode                         myRoot;
  @NotNull private TIntObjectHashMap<ArrangementRuleEditingModel> myRowMappings;
  @NotNull private JavaRearranger                                 myGrouper;

  @Before
  public void setUp() {
    myBuilder = new ArrangementRuleEditingModelBuilder();
    myRoot = new DefaultMutableTreeNode();
    myRowMappings = new TIntObjectHashMap<ArrangementRuleEditingModel>();
    myGrouper = new JavaRearranger();
  }

  @Test
  public void mapToTheSameLayer() {
    build(ArrangementUtil.and(atom(PUBLIC), atom(STATIC)));
    // TODO den uncomment
    //assertEquals(1, myRowMappings.size());
  }

  private void build(@NotNull ArrangementSettingsNode node) {
    myBuilder.build(node, myRoot, myGrouper, myRowMappings);
  }

  private static ArrangementSettingsAtomNode atom(@NotNull Object condition) {
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
    return new ArrangementSettingsAtomNode(type, condition);
  }
}
