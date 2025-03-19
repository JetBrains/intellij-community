// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.projectView.impl;

import com.intellij.ide.projectView.ProjectViewNestingRulesProvider;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.SettingsCategory;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.util.ModificationTracker;
import com.intellij.util.containers.SortedList;
import com.intellij.util.xmlb.annotations.Attribute;
import com.intellij.util.xmlb.annotations.XCollection;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Holds data used by {@link NestingTreeStructureProvider} and {@link FileNestingInProjectViewDialog}.
 */
@State(
  name = "ProjectViewFileNesting",
  storages = @Storage("ui.lnf.xml"),
  category = SettingsCategory.UI
)
public final class ProjectViewFileNestingService implements PersistentStateComponent<ProjectViewFileNestingService.MyState>, ModificationTracker {
  private static final Logger LOG = Logger.getInstance(ProjectViewFileNestingService.class);

  private static final ExtensionPointName<ProjectViewNestingRulesProvider> EP_NAME =
    ExtensionPointName.create("com.intellij.projectViewNestingRulesProvider");

  private MyState myState = new MyState();
  private long myModCount;

  public static @NotNull ProjectViewFileNestingService getInstance() {
    return ApplicationManager.getApplication().getService(ProjectViewFileNestingService.class);
  }

  static @NotNull List<NestingRule> loadDefaultNestingRules() {
    List<NestingRule> result = new ArrayList<>();

    final ProjectViewNestingRulesProvider.Consumer consumer = (parentFileSuffix, childFileSuffix) -> {
      LOG.assertTrue(!parentFileSuffix.isEmpty() && !childFileSuffix.isEmpty(), "file suffix must not be empty");
      LOG.assertTrue(!parentFileSuffix.equals(childFileSuffix), "parent and child suffixes must be different: " + parentFileSuffix);
      result.add(new NestingRule(parentFileSuffix, childFileSuffix));
    };

    for (ProjectViewNestingRulesProvider provider : EP_NAME.getExtensionList()) {
      provider.addFileNestingRules(consumer);
    }

    return result;
  }

  @Override
  public MyState getState() {
    return myState;
  }

  @Override
  public void loadState(final @NotNull MyState state) {
    myState = state;
    myModCount++;
  }

  /**
   * This list of rules is used for serialization and for UI.
   * See also {@link NestingTreeStructureProvider}, it adjusts this list of rules to match its needs
   */
  public @NotNull List<NestingRule> getRules() {
    return myState.myRules;
  }

  public void setRules(final @NotNull List<NestingRule> rules) {
    myState.myRules.clear();
    myState.myRules.addAll(rules);
    myModCount++;
  }

  @Override
  public long getModificationCount() {
    return myModCount;
  }

  public static final class MyState {
    @XCollection(propertyElementName = "nesting-rules")
    public List<NestingRule> myRules = new SortedList<>(Comparator.comparing(o -> o.getParentFileSuffix()));

    public MyState() {
      myRules.addAll(loadDefaultNestingRules());
    }
  }

  public static final class NestingRule {
    private @NotNull String myParentFileSuffix;

    private @NotNull String myChildFileSuffix;

    @SuppressWarnings("unused") // used by serializer
    public NestingRule() {
      this("", "");
    }

    public NestingRule(@NotNull String parentFileSuffix, @NotNull String childFileSuffix) {
      myParentFileSuffix = parentFileSuffix;
      myChildFileSuffix = childFileSuffix;
    }

    @Attribute("parent-file-suffix")
    public @NotNull String getParentFileSuffix() {
      return myParentFileSuffix;
    }

    public void setParentFileSuffix(final @NotNull String parentFileSuffix) {
      myParentFileSuffix = parentFileSuffix;
    }

    @Attribute("child-file-suffix")
    public @NotNull String getChildFileSuffix() {
      return myChildFileSuffix;
    }

    public void setChildFileSuffix(final @NotNull String childFileSuffix) {
      myChildFileSuffix = childFileSuffix;
    }

    @Override
    public String toString() {
      return myParentFileSuffix + "->" + myChildFileSuffix;
    }

    @Override
    public boolean equals(Object o) {
      return o instanceof NestingRule &&
             myParentFileSuffix.equals(((NestingRule)o).myParentFileSuffix) &&
             myChildFileSuffix.equals(((NestingRule)o).myChildFileSuffix);
    }

    @Override
    public int hashCode() {
      return myParentFileSuffix.hashCode() + 239 * myChildFileSuffix.hashCode();
    }
  }
}
