/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.packageDependencies;

import com.intellij.icons.AllIcons;
import com.intellij.ide.IdeBundle;
import com.intellij.openapi.components.MainConfigurationStateSplitter;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.NotNullLazyValue;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.scope.packageSet.*;
import com.intellij.ui.LayeredIcon;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.UIUtil;
import gnu.trove.THashMap;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

@State(
  name = "DependencyValidationManager",
  storages = @Storage(value = "scopes", stateSplitter = DependencyValidationManagerImpl.ScopesStateSplitter.class)
)
public class DependencyValidationManagerImpl extends DependencyValidationManager {
  private static final NotNullLazyValue<Icon> ourSharedScopeIcon = new NotNullLazyValue<Icon>() {
    @NotNull
    @Override
    protected Icon compute() {
      return new LayeredIcon(AllIcons.Ide.LocalScope, AllIcons.Nodes.Shared);
    }
  };

  private static class State {
    private final List<DependencyRule> rules = new ArrayList<>();
    private final Map<String, PackageSet> unnamedScopes = new THashMap<>();
  }

  private State myState = new State();
  private final NamedScopeManager myNamedScopeManager;

  private boolean mySkipImportStatements;
  private boolean mySkipImportStatementsWasSpecified;

  @NonNls private static final String DENY_RULE_KEY = "deny_rule";
  @NonNls private static final String FROM_SCOPE_KEY = "from_scope";
  @NonNls private static final String TO_SCOPE_KEY = "to_scope";
  @NonNls private static final String IS_DENY_KEY = "is_deny";
  @NonNls private static final String UNNAMED_SCOPE = "unnamed_scope";
  @NonNls private static final String VALUE = "value";

  public DependencyValidationManagerImpl(final Project project, NamedScopeManager namedScopeManager) {
    super(project);
    myNamedScopeManager = namedScopeManager;
    namedScopeManager.addScopeListener(() -> reloadScopes());
  }

  @Override
  @NotNull
  public List<NamedScope> getPredefinedScopes() {
    final List<NamedScope> predefinedScopes = new ArrayList<>();
    final CustomScopesProvider[] scopesProviders = CustomScopesProvider.CUSTOM_SCOPES_PROVIDER.getExtensions(myProject);
    for (CustomScopesProvider scopesProvider : scopesProviders) {
      predefinedScopes.addAll(scopesProvider.getFilteredScopes());
    }
    return predefinedScopes;
  }

  @Override
  public NamedScope getPredefinedScope(@NotNull String name) {
    final CustomScopesProvider[] scopesProviders = CustomScopesProvider.CUSTOM_SCOPES_PROVIDER.getExtensions(myProject);
    for (CustomScopesProvider scopesProvider : scopesProviders) {
      final NamedScope scope = scopesProvider instanceof CustomScopesProviderEx
                               ? ((CustomScopesProviderEx)scopesProvider).getCustomScope(name)
                               : CustomScopesProviderEx.findPredefinedScope(name, scopesProvider.getFilteredScopes());
      if (scope != null) {
        return scope;
      }
    }
    return null;
  }

  @Override
  public boolean hasRules() {
    return !myState.rules.isEmpty();
  }

  @Override
  @Nullable
  public DependencyRule getViolatorDependencyRule(@NotNull PsiFile from, @NotNull PsiFile to) {
    for (DependencyRule dependencyRule : myState.rules) {
      if (dependencyRule.isForbiddenToUse(from, to)) return dependencyRule;
    }

    return null;
  }

  @Override
  @NotNull
  public DependencyRule[] getViolatorDependencyRules(@NotNull PsiFile from, @NotNull PsiFile to) {
    ArrayList<DependencyRule> result = new ArrayList<>();
    for (DependencyRule dependencyRule : myState.rules) {
      if (dependencyRule.isForbiddenToUse(from, to)) {
        result.add(dependencyRule);
      }
    }
    return result.toArray(new DependencyRule[result.size()]);
  }

  @NotNull
  @Override
  public DependencyRule[] getApplicableRules(@NotNull PsiFile file) {
    ArrayList<DependencyRule> result = new ArrayList<>();
    for (DependencyRule dependencyRule : myState.rules) {
      if (dependencyRule.isApplicable(file)) {
        result.add(dependencyRule);
      }
    }
    return result.toArray(new DependencyRule[result.size()]);
  }

  @Override
  public boolean skipImportStatements() {
    return mySkipImportStatements;
  }

  @Override
  public void setSkipImportStatements(final boolean skip) {
    mySkipImportStatements = skip;
  }

  @NotNull
  @Override
  public Map<String, PackageSet> getUnnamedScopes() {
    return myState.unnamedScopes;
  }

  @NotNull
  @Override
  public DependencyRule[] getAllRules() {
    List<DependencyRule> rules = myState.rules;
    return rules.toArray(new DependencyRule[rules.size()]);
  }

  @Override
  public void removeAllRules() {
    myState.rules.clear();
  }

  private void addRule(@NotNull DependencyRule rule, @NotNull State state) {
    appendUnnamedScope(rule.getFromScope(), state);
    appendUnnamedScope(rule.getToScope(), state);
    state.rules.add(rule);
  }

  @Override
  public void addRule(@NotNull DependencyRule rule) {
    addRule(rule, myState);
  }

  private void appendUnnamedScope(@NotNull NamedScope fromScope, @NotNull State state) {
    if (getScope(fromScope.getName()) == null) {
      final PackageSet packageSet = fromScope.getValue();
      if (packageSet != null && !state.unnamedScopes.containsKey(packageSet.getText())) {
        state.unnamedScopes.put(packageSet.getText(), packageSet);
      }
    }
  }

  @Override
  public String getDisplayName() {
    return IdeBundle.message("shared.scopes.node.text");
  }

  @Override
  public Icon getIcon() {
    return ourSharedScopeIcon.getValue();
  }

  @Override
  public void loadState(Element element) {
    Element option = element.getChild("option");
    if (option != null && "SKIP_IMPORT_STATEMENTS".equals(option.getAttributeValue("name"))) {
      mySkipImportStatementsWasSpecified = !myProject.isDefault();
      mySkipImportStatements = Boolean.parseBoolean(option.getAttributeValue("value"));
    }

    super.loadState(element);

    final NamedScope[] scopes = getEditableScopes();
    Arrays.sort(scopes, (s1, s2) -> {
      final String name1 = s1.getName();
      final String name2 = s2.getName();
      if (Comparing.equal(name1, name2)){
        return 0;
      }
      final List<String> order = myNamedScopeManager.myOrderState.myOrder;
      final int i1 = order.indexOf(name1);
      final int i2 = order.indexOf(name2);
      return i1 > i2 ? 1 : -1;
    });
    super.setScopes(scopes);

    State state = new State();
    final PackageSetFactory packageSetFactory = PackageSetFactory.getInstance();
    for (Element unnamedScope : element.getChildren(UNNAMED_SCOPE)) {
      try {
        final String packageSet = unnamedScope.getAttributeValue(VALUE);
        state.unnamedScopes.put(packageSet, packageSetFactory.compile(packageSet));
      }
      catch (ParsingException ignored) {
        //skip pattern
      }
    }

    readRules(element, state);
    myState = state;
  }

  private void readRules(@NotNull Element element, State state) {
    for (Element rule1 : element.getChildren(DENY_RULE_KEY)) {
      DependencyRule rule = readRule(rule1);
      if (rule != null) {
        addRule(rule, state);
      }
    }
  }

  @Override
  public Element getState() {
    Element element = super.getState();
    assert element != null;
    if (mySkipImportStatements || mySkipImportStatementsWasSpecified) {
      element.addContent(new Element("option").setAttribute("name", "SKIP_IMPORT_STATEMENTS").setAttribute("value", Boolean.toString(mySkipImportStatements)));
    }

    State state = myState;
    if (!state.unnamedScopes.isEmpty()) {
      String[] unnamedScopes = state.unnamedScopes.keySet().toArray(new String[state.unnamedScopes.size()]);
      Arrays.sort(unnamedScopes);
      for (String unnamedScope : unnamedScopes) {
        element.addContent(new Element(UNNAMED_SCOPE).setAttribute(VALUE, unnamedScope));
      }
    }

    writeRules(element, state);
    return element;
  }

  private static void writeRules(@NotNull Element element, @NotNull State state) {
    for (DependencyRule rule : state.rules) {
      Element ruleElement = writeRule(rule);
      if (ruleElement != null) {
        element.addContent(ruleElement);
      }
    }
  }

  @Override
  @Nullable
  public NamedScope getScope(@Nullable String name) {
    return getScope(name, myState);
  }

  private NamedScope getScope(@Nullable String name, @NotNull State state) {
    final NamedScope scope = super.getScope(name);
    if (scope == null) {
      final PackageSet packageSet = state.unnamedScopes.get(name);
      if (packageSet != null) {
        return new NamedScope.UnnamedScope(packageSet);
      }
    }
    //compatibility for predefined scopes: rename Project -> All
    if (scope == null && Comparing.strEqual(name, "Project")) {
      return super.getScope("All");
    }
    return scope;
  }

  @Nullable
  private static Element writeRule(DependencyRule rule) {
    NamedScope fromScope = rule.getFromScope();
    NamedScope toScope = rule.getToScope();
    if (fromScope == null || toScope == null) return null;
    Element ruleElement = new Element(DENY_RULE_KEY);
    ruleElement.setAttribute(FROM_SCOPE_KEY, fromScope.getName());
    ruleElement.setAttribute(TO_SCOPE_KEY, toScope.getName());
    ruleElement.setAttribute(IS_DENY_KEY, Boolean.valueOf(rule.isDenyRule()).toString());
    return ruleElement;
  }

  @Nullable
  private DependencyRule readRule(@NotNull Element ruleElement) {
    String fromScope = ruleElement.getAttributeValue(FROM_SCOPE_KEY);
    String toScope = ruleElement.getAttributeValue(TO_SCOPE_KEY);
    String denyRule = ruleElement.getAttributeValue(IS_DENY_KEY);
    if (fromScope == null || toScope == null || denyRule == null) return null;
    final NamedScope fromNamedScope = getScope(fromScope);
    final NamedScope toNamedScope = getScope(toScope);
    if (fromNamedScope == null || toNamedScope == null) return null;
    return new DependencyRule(fromNamedScope, toNamedScope, Boolean.valueOf(denyRule).booleanValue());
  }

  static final class ScopesStateSplitter extends MainConfigurationStateSplitter {
    @NotNull
    @Override
    protected String getSubStateFileName(@NotNull Element element) {
      return element.getAttributeValue("name");
    }

    @NotNull
    @Override
    protected String getComponentStateFileName() {
      return "scope_settings";
    }

    @NotNull
    @Override
    protected String getSubStateTagName() {
      return "scope";
    }
  }

  private final List<Pair<NamedScope, NamedScopesHolder>> myScopePairs = ContainerUtil.createLockFreeCopyOnWriteList();

  private void reloadScopes() {
    UIUtil.invokeLaterIfNeeded(() -> {
      if (getProject().isDisposed()) {
        return;
      }

      List<Pair<NamedScope, NamedScopesHolder>> scopeList = new ArrayList<>();
      addScopesToList(scopeList, this);
      addScopesToList(scopeList, myNamedScopeManager);
      myScopePairs.clear();
      myScopePairs.addAll(scopeList);

      Element element = new Element("rules_2_reload");
      writeRules(element, myState);
      State state = new State();
      readRules(element, state);
      myState = state;
    });
  }

  private static void addScopesToList(@NotNull final List<Pair<NamedScope, NamedScopesHolder>> scopeList,
                                      @NotNull final NamedScopesHolder holder) {
    for (NamedScope scope : holder.getScopes()) {
      scopeList.add(Pair.create(scope, holder));
    }
  }

  @NotNull
  public List<Pair<NamedScope, NamedScopesHolder>> getScopeBasedHighlightingCachedScopes() {
    return myScopePairs;
  }

  @Override
  public void fireScopeListeners() {
    super.fireScopeListeners();
    reloadScopes();
  }

  @Override
  public void setScopes(NamedScope[] scopes) {
    super.setScopes(scopes);
    final List<String> order = myNamedScopeManager.myOrderState.myOrder;
    order.clear();
    for (NamedScope scope : scopes) {
      order.add(scope.getName());
    }
  }
}
