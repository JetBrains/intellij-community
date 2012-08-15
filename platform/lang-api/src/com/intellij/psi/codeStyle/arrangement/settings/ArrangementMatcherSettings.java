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
package com.intellij.psi.codeStyle.arrangement.settings;

import com.intellij.psi.codeStyle.arrangement.model.ArrangementSettingsAtomNode;
import com.intellij.psi.codeStyle.arrangement.model.ArrangementSettingsCompositeNode;
import com.intellij.psi.codeStyle.arrangement.model.ArrangementSettingsNode;
import com.intellij.psi.codeStyle.arrangement.model.ArrangementSettingsNodeVisitor;
import com.intellij.util.containers.HashSet;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Encapsulates information about {@link ArrangementSettingsNode standard arrangement match rule settings}.
 * <p/>
 * Not thread-safe.
 * 
 * @author Denis Zhdanov
 * @since 8/6/12 2:30 PM
 */
public class ArrangementMatcherSettings implements Cloneable {

  @NotNull private final List<ArrangementSettingsNode>  myConditions    = new ArrayList<ArrangementSettingsNode>();
  @NotNull private final Set<Object>                    myValues        = new HashSet<Object>();
  @NotNull private final ArrangementSettingsNodeVisitor myAddVisitor    = new MyAddVisitor();
  @NotNull private final ArrangementSettingsNodeVisitor myRemoveVisitor = new MyRemoveVisitor();

  @NotNull
  public List<ArrangementSettingsNode> getConditions() {
    return myConditions;
  }

  public boolean addCondition(@NotNull ArrangementSettingsNode condition) {
    int size = myConditions.size();
    condition.invite(myAddVisitor);
    return myConditions.size() > size;
  }

  public boolean removeCondition(@NotNull ArrangementSettingsNode condition) {
    int size = myConditions.size();
    condition.invite(myRemoveVisitor);
    return myConditions.size() < size;
  }

  public boolean hasCondition(@NotNull Object id) {
    return myValues.contains(id);
  }

  @Override
  public ArrangementMatcherSettings clone() {
    ArrangementMatcherSettings result = new ArrangementMatcherSettings();
    result.myConditions.addAll(myConditions);
    result.myValues.addAll(myValues);
    return result;
  }

  private class MyAddVisitor implements ArrangementSettingsNodeVisitor {
    @Override
    public void visit(@NotNull ArrangementSettingsAtomNode node) {
      myConditions.add(node);
      myValues.add(node.getValue());
    }

    @Override
    public void visit(@NotNull ArrangementSettingsCompositeNode node) {
      for (ArrangementSettingsNode n : node.getOperands()) {
        n.invite(this);
      }
    }
  }

  private class MyRemoveVisitor implements ArrangementSettingsNodeVisitor {
    @Override
    public void visit(@NotNull ArrangementSettingsAtomNode node) {
      myConditions.remove(node);
      myValues.remove(node.getValue());
    }

    @Override
    public void visit(@NotNull ArrangementSettingsCompositeNode node) {
      for (ArrangementSettingsNode n : node.getOperands()) {
        n.invite(this);
      }
    }
  }
}
