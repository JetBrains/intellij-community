// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.roots.ui.configuration.projectRoot;

import com.intellij.facet.Facet;
import com.intellij.facet.FacetTestCase;
import com.intellij.ide.projectView.impl.ModuleGroup;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.ui.MasterDetailsComponent.MyNode;
import com.intellij.openapi.ui.NamedConfigurable;

import javax.swing.JComponent;
import java.util.List;

public class ModuleStructureConfigurableTest extends FacetTestCase {
  public void testComparatorOrdersNodeKindsConsistently() {
    var group = new MyNode(new ModuleGroupConfigurable(new ModuleGroup(List.of("z-group")), "z-group"));
    var module = new MyNode(new ModuleConfigurable(myModule, "a-module"));
    var facet = new MyNode(new FacetConfigurable(createFacet("m-facet"), "m-facet"));
    var string = new MyNode(new StringConfigurable("string", "0-string"));
    var other = new MyNode(new OtherConfigurable(new Object(), "other"));

    assertOrdered(group, module);
    assertOrdered(module, facet);
    assertOrdered(group, facet);
    assertOrdered(group, string);
    assertOrdered(module, string);
    assertOrdered(facet, string);
    assertOrdered(string, other);
  }

  public void testComparatorUsesNaturalOrderForDifferentConfigurableClasses() {
    var module2 = new MyNode(new ModuleTwoConfigurable(myModule, "module 2"));
    var module10 = new MyNode(new ModuleTenConfigurable(myModule, "module 10"));

    assertOrdered(module2, module10);
  }

  private static void assertOrdered(MyNode first, MyNode second) {
    assertTrue(ModuleStructureConfigurable.NODE_COMPARATOR.compare(first, second) < 0);
    assertTrue(ModuleStructureConfigurable.NODE_COMPARATOR.compare(second, first) > 0);
  }

  private abstract static class TestConfigurable<T> extends NamedConfigurable<T> {
    private final T myEditableObject;
    private String myDisplayName;

    private TestConfigurable(T editableObject, String displayName) {
      super(false, null);
      myEditableObject = editableObject;
      myDisplayName = displayName;
    }

    @Override
    public String getDisplayName() {
      return myDisplayName;
    }

    @Override
    public void setDisplayName(String name) {
      myDisplayName = name;
    }

    @Override
    public T getEditableObject() {
      return myEditableObject;
    }

    @Override
    public String getBannerSlogan() {
      return null;
    }

    @Override
    public JComponent createOptionsPanel() {
      return null;
    }

    @Override
    public boolean isModified() {
      return false;
    }

    @Override
    public void apply() { }
  }

  private static final class ModuleGroupConfigurable extends TestConfigurable<ModuleGroup> {
    private ModuleGroupConfigurable(ModuleGroup group, String displayName) {
      super(group, displayName);
    }
  }

  private static class ModuleConfigurable extends TestConfigurable<Module> {
    private ModuleConfigurable(Module module, String displayName) {
      super(module, displayName);
    }
  }

  private static final class ModuleTwoConfigurable extends ModuleConfigurable {
    private ModuleTwoConfigurable(Module module, String displayName) {
      super(module, displayName);
    }
  }

  private static final class ModuleTenConfigurable extends ModuleConfigurable {
    private ModuleTenConfigurable(Module module, String displayName) {
      super(module, displayName);
    }
  }

  private static final class FacetConfigurable extends TestConfigurable<Facet<?>> {
    private FacetConfigurable(Facet<?> facet, String displayName) {
      super(facet, displayName);
    }
  }

  private static final class StringConfigurable extends TestConfigurable<String> {
    private StringConfigurable(String value, String displayName) {
      super(value, displayName);
    }
  }

  private static final class OtherConfigurable extends TestConfigurable<Object> {
    private OtherConfigurable(Object value, String displayName) {
      super(value, displayName);
    }
  }
}
