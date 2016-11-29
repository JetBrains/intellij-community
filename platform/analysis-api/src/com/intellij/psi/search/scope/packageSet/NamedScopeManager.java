/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.psi.search.scope.packageSet;

import com.intellij.icons.AllIcons;
import com.intellij.ide.IdeBundle;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.components.StoragePathMacros;
import com.intellij.openapi.project.Project;
import com.intellij.util.xmlb.SkipDefaultValuesSerializationFilters;
import com.intellij.util.xmlb.XmlSerializer;
import com.intellij.util.xmlb.annotations.AbstractCollection;
import com.intellij.util.xmlb.annotations.Tag;
import org.jdom.Element;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;

@State(name = "NamedScopeManager", storages = @Storage(StoragePathMacros.WORKSPACE_FILE))
public class NamedScopeManager extends NamedScopesHolder {
  public OrderState myOrderState = new OrderState();

  public NamedScopeManager(final Project project) {
    super(project);
  }

  public static NamedScopeManager getInstance(Project project) {
    return ServiceManager.getService(project, NamedScopeManager.class);
  }

  @Override
  public void loadState(Element state) {
    super.loadState(state);
    XmlSerializer.deserializeInto(myOrderState, state);
  }

  @Override
  public Element getState() {
    Element state = super.getState();
    XmlSerializer.serializeInto(myOrderState, state, new SkipDefaultValuesSerializationFilters());
    return state;
  }

  @Override
  public String getDisplayName() {
    return IdeBundle.message("local.scopes.node.text");
  }

  @Override
  public Icon getIcon() {
    return AllIcons.Ide.LocalScope;
  }

  public static class OrderState {
    @Tag("order")
    @AbstractCollection(surroundWithTag = false, elementTag = "scope", elementValueAttribute = "name")
    public List<String> myOrder = new ArrayList<>();

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      OrderState state = (OrderState)o;
      return !(myOrder != null ? !myOrder.equals(state.myOrder) : state.myOrder != null);
    }

    @Override
    public int hashCode() {
      return myOrder != null ? myOrder.hashCode() : 0;
    }
  }
}
