/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.ide.projectView.impl;

import com.intellij.ide.projectView.ProjectViewNestingRulesProvider;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.util.ModificationTracker;
import com.intellij.util.containers.SortedList;
import com.intellij.util.xmlb.annotations.Attribute;
import com.intellij.util.xmlb.annotations.XCollection;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/**
 * Holds data used by {@link NestingTreeStructureProvider} and {@link FileNestingInProjectViewDialog}.
 */
@State(
  name = "ProjectViewFileNesting",
  storages = @Storage("ui.lnf.xml")
)
public class ProjectViewFileNestingService implements PersistentStateComponent<ProjectViewFileNestingService.MyState>, ModificationTracker {
  private static final Logger LOG = Logger.getInstance(ProjectViewFileNestingService.class);

  private static final ExtensionPointName<ProjectViewNestingRulesProvider> EP_NAME =
    ExtensionPointName.create("com.intellij.projectViewNestingRulesProvider");

  public static final NestingRule[] DEFAULT_NESTING_RULES = loadDefaultNestingRules();

  private MyState myState = new MyState();
  private long myModCount = 0;

  @NotNull
  public static ProjectViewFileNestingService getInstance() {
    return ServiceManager.getService(ProjectViewFileNestingService.class);
  }

  @NotNull
  private static NestingRule[] loadDefaultNestingRules() {
    final List<NestingRule> result = new SortedList<>(Comparator.comparing(o -> o.getParentFileSuffix()));

    final ProjectViewNestingRulesProvider.Consumer consumer = new ProjectViewNestingRulesProvider.Consumer() {
      @Override
      public void addNestingRule(@NotNull final String parentFileSuffix, @NotNull final String childFileSuffix) {
        LOG.assertTrue(!parentFileSuffix.isEmpty() && !childFileSuffix.isEmpty(), "file suffix must not be empty");
        LOG.assertTrue(!parentFileSuffix.equals(childFileSuffix), "parent and child suffixes must be different: " + parentFileSuffix);
        result.add(new NestingRule(parentFileSuffix, childFileSuffix));
      }
    };

    for (ProjectViewNestingRulesProvider provider : EP_NAME.getExtensions()) {
      provider.addFileNestingRules(consumer);
    }

    return result.toArray(new NestingRule[0]);
  }

  @Override
  public MyState getState() {
    return myState;
  }

  @Override
  public void loadState(@NotNull final MyState state) {
    myState = state;
    myModCount++;
  }

  /**
   * This list of rules is used for serialization and for UI.
   * See also {@link NestingTreeStructureProvider}, it adjusts this list of rules to match its needs
   */
  @NotNull
  public List<NestingRule> getRules() {
    return myState.myRules;
  }

  public void setRules(@NotNull final List<NestingRule> rules) {
    myState.myRules.clear();
    myState.myRules.addAll(rules);
    myModCount++;
  }

  @Override
  public long getModificationCount() {
    return myModCount;
  }

  public static class MyState {
    @XCollection(propertyElementName = "nesting-rules")
    public List<NestingRule> myRules = new SortedList<>(Comparator.comparing(o -> o.getParentFileSuffix()));

    public MyState() {
      myRules.addAll(Arrays.asList(DEFAULT_NESTING_RULES));
    }
  }

  public static class NestingRule {
    @NotNull private String myParentFileSuffix;

    @NotNull private String myChildFileSuffix;

    @SuppressWarnings("unused") // used by serializer
    public NestingRule() {
      this("", "");
    }

    public NestingRule(@NotNull String parentFileSuffix, @NotNull String childFileSuffix) {
      myParentFileSuffix = parentFileSuffix;
      myChildFileSuffix = childFileSuffix;
    }

    @NotNull
    @Attribute("parent-file-suffix")
    public String getParentFileSuffix() {
      return myParentFileSuffix;
    }

    public void setParentFileSuffix(@NotNull final String parentFileSuffix) {
      myParentFileSuffix = parentFileSuffix;
    }

    @NotNull
    @Attribute("child-file-suffix")
    public String getChildFileSuffix() {
      return myChildFileSuffix;
    }

    public void setChildFileSuffix(@NotNull final String childFileSuffix) {
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
