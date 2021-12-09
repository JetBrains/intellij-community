// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.model.project.dependencies;

import com.intellij.openapi.externalSystem.util.BooleanBiFunction;
import com.intellij.openapi.externalSystem.util.IteratorUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public abstract class AbstractDependencyNode implements DependencyNode, Serializable {
  private final long id;
  @NotNull
  private final List<DependencyNode> dependencies;
  private String resolutionState;
  private String selectionReason;

  protected AbstractDependencyNode(long id) {
    this.id = id;
    dependencies = new ArrayList<DependencyNode>(0);
  }

  @Override
  public long getId() {
    return id;
  }

  @NotNull
  @Override
  public List<DependencyNode> getDependencies() {
    return dependencies;
  }

  @Nullable
  @Override
  public String getResolutionState() {
    return resolutionState;
  }

  public void setResolutionState(@Nullable String resolutionState) {
    this.resolutionState = resolutionState;
  }

  @Nullable
  @Override
  public String getSelectionReason() {
    return selectionReason;
  }

  public void setSelectionReason(@Nullable String selectionReason) {
    this.selectionReason = selectionReason;
  }

  @Override
  public final boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    AbstractDependencyNode node = (AbstractDependencyNode)o;
    if (id != node.id) return false;
    if (resolutionState != null ? !resolutionState.equals(node.resolutionState) : node.resolutionState != null) return false;
    if (selectionReason != null ? !selectionReason.equals(node.selectionReason) : node.selectionReason != null) return false;
    if (!match(node)) return false;
    if (!match(dependencies, node.dependencies)) return false;
    return true;
  }

  @Override
  public int hashCode() {
    int result = (int)(id ^ (id >>> 32));
    result = 31 * result + dependencies.size();
    result = 31 * result + (resolutionState != null ? resolutionState.hashCode() : 0);
    result = 31 * result + (selectionReason != null ? selectionReason.hashCode() : 0);
    return result;
  }

  protected abstract boolean match(AbstractDependencyNode o);

  private static boolean match(@NotNull Collection<DependencyNode> dependencies1,
                               @NotNull Collection<DependencyNode> dependencies2) {
    return IteratorUtils.match(
      new DependenciesIterator(dependencies1), new DependenciesIterator(dependencies2),
      new BooleanBiFunction<DependencyNode, DependencyNode>() {
        @Override
        public Boolean fun(DependencyNode o1, DependencyNode o2) {
          if (o1 instanceof AbstractDependencyNode && o2 instanceof AbstractDependencyNode) {
            AbstractDependencyNode o11 = (AbstractDependencyNode)o1;
            AbstractDependencyNode o21 = (AbstractDependencyNode)o2;
            if (o11.id != o21.id) return false;
            if (o11.resolutionState != null ? !o11.resolutionState.equals(o21.resolutionState) : o21.resolutionState != null) {
              return false;
            }
            return o11.match(o21);
          }
          else {
            return o1 == null ? o2 == null : o1.equals(o2);
          }
        }
      });
  }

  private static final class DependenciesIterator extends IteratorUtils.AbstractObjectGraphIterator<DependencyNode> {
    private DependenciesIterator(Collection<DependencyNode> dependencies) {
      super(dependencies);
    }

    @Override
    public Collection<? extends DependencyNode> getChildren(DependencyNode node) {
      return node.getDependencies();
    }
  }
}
