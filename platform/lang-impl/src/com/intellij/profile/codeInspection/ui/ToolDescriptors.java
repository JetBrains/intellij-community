// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.profile.codeInspection.ui;

import com.intellij.codeInspection.ex.Descriptor;
import com.intellij.codeInspection.ex.InspectionProfileModifiableModel;
import com.intellij.codeInspection.ex.ScopeToolState;
import com.intellij.openapi.project.Project;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * @author Dmitry Batkovich
 */
public final class ToolDescriptors {

  private final @NotNull Descriptor myDefaultDescriptor;
  private final @NotNull List<Descriptor> myNonDefaultDescriptors;

  private ToolDescriptors(final @NotNull Descriptor defaultDescriptor,
                          final @NotNull List<Descriptor> nonDefaultDescriptors) {
    myDefaultDescriptor = defaultDescriptor;
    myNonDefaultDescriptors = nonDefaultDescriptors;
  }

  public static ToolDescriptors fromScopeToolState(final ScopeToolState state,
                                                   @NotNull InspectionProfileModifiableModel profile,
                                                   final Project project) {
    List<ScopeToolState> nonDefaultTools = profile.getNonDefaultTools(state.getTool().getShortName(), project);
    ArrayList<Descriptor> descriptors = new ArrayList<>(nonDefaultTools.size());
    for (final ScopeToolState nonDefaultToolState : nonDefaultTools) {
      descriptors.add(new Descriptor(nonDefaultToolState, profile, project));
    }
    return new ToolDescriptors(new Descriptor(state, profile, project), descriptors);
  }

  public @NotNull Descriptor getDefaultDescriptor() {
    return myDefaultDescriptor;
  }

  public @NotNull List<Descriptor> getNonDefaultDescriptors() {
    return myNonDefaultDescriptors;
  }

  public @NotNull Stream<Descriptor> getDescriptors() {
    return StreamEx.of(getNonDefaultDescriptors()).prepend(getDefaultDescriptor());
  }

  public @NotNull ScopeToolState getDefaultScopeToolState() {
    return myDefaultDescriptor.getState();
  }
}
