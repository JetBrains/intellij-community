// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.search.scope.packageSet;

import com.intellij.analysis.AnalysisBundle;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.components.StoragePathMacros;
import com.intellij.openapi.project.Project;
import com.intellij.util.xmlb.SkipDefaultValuesSerializationFilters;
import com.intellij.util.xmlb.XmlSerializer;
import com.intellij.util.xmlb.annotations.XCollection;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@State(name = "NamedScopeManager", storages = @Storage(StoragePathMacros.WORKSPACE_FILE))
public final class NamedScopeManager extends NamedScopesHolder {
  public OrderState myOrderState = new OrderState();

  public NamedScopeManager(final Project project) {
    super(project);
  }

  @NotNull
  public static NamedScopeManager getInstance(Project project) {
    return project.getService(NamedScopeManager.class);
  }

  @Override
  public void loadState(@NotNull Element state) {
    super.loadState(state);
    XmlSerializer.deserializeInto(myOrderState, state);
  }

  @NotNull
  @Override
  public Element getState() {
    Element state = super.getState();
    XmlSerializer.serializeInto(myOrderState, state, new SkipDefaultValuesSerializationFilters());
    return state;
  }

  @NotNull
  @Override
  public String getDisplayName() {
    return AnalysisBundle.message("local.scopes.node.text");
  }

  @Override
  public Icon getIcon() {
    return AllIcons.Ide.LocalScope;
  }

  public static final class OrderState {
    @XCollection(elementName = "scope", valueAttributeName = "name", propertyElementName = "order")
    public List<String> myOrder = new ArrayList<>();

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      OrderState state = (OrderState)o;
      return Objects.equals(myOrder, state.myOrder);
    }

    @Override
    public int hashCode() {
      return myOrder != null ? myOrder.hashCode() : 0;
    }
  }
}
