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

import com.intellij.psi.codeStyle.arrangement.model.*;
import com.intellij.psi.codeStyle.arrangement.model.ArrangementAtomMatchCondition;
import com.intellij.psi.codeStyle.arrangement.model.ArrangementMatchCondition;
import com.intellij.util.containers.HashSet;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Encapsulates information about {@link ArrangementMatchCondition standard arrangement match rule settings}.
 * <p/>
 * Not thread-safe.
 * 
 * @author Denis Zhdanov
 * @since 8/6/12 2:30 PM
 */
public class ArrangementMatcherSettings implements Cloneable {

  @NotNull private final List<ArrangementMatchCondition> myConditions    = new ArrayList<ArrangementMatchCondition>();
  @NotNull private final Set<Object>                     myValues        = new HashSet<Object>();
  @NotNull private final ArrangementSettingsNodeVisitor  myAddVisitor    = new MyAddVisitor();
  @NotNull private final ArrangementSettingsNodeVisitor  myRemoveVisitor = new MyRemoveVisitor();

  @NotNull
  public List<ArrangementMatchCondition> getConditions() {
    return myConditions;
  }

  public boolean addCondition(@NotNull ArrangementMatchCondition condition) {
    int size = myConditions.size();
    condition.invite(myAddVisitor);
    return myConditions.size() > size;
  }

  public boolean removeCondition(@NotNull ArrangementMatchCondition condition) {
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
    public void visit(@NotNull ArrangementAtomMatchCondition setting) {
      myConditions.add(setting);
      myValues.add(setting.getValue());
    }

    @Override
    public void visit(@NotNull ArrangementCompositeMatchCondition setting) {
      for (ArrangementMatchCondition n : setting.getOperands()) {
        n.invite(this);
      }
    }
  }

  private class MyRemoveVisitor implements ArrangementSettingsNodeVisitor {
    @Override
    public void visit(@NotNull ArrangementAtomMatchCondition setting) {
      myConditions.remove(setting);
      myValues.remove(setting.getValue());
    }

    @Override
    public void visit(@NotNull ArrangementCompositeMatchCondition setting) {
      for (ArrangementMatchCondition n : setting.getOperands()) {
        n.invite(this);
      }
    }
  }
}
