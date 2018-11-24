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
package com.intellij.codeInspection.ex;

import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.Key;
import com.intellij.psi.PsiElement;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;

import java.util.Set;
import java.util.function.Function;

public class InspectionProfileWrapper {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInspection.ex.InspectionProfileWrapper");

  /**
   * Key that is assumed to hold strategy that customizes {@link InspectionProfileWrapper} object to use.
   * <p/>
   * I.e. given strategy (if any) receives {@link InspectionProfileWrapper} object that is going to be used so far and returns
   * {@link InspectionProfileWrapper} object that should be used later.
   */
  public static final Key<Function<InspectionProfileImpl, InspectionProfileWrapper>> CUSTOMIZATION_KEY = Key.create("Inspection Profile Wrapper Customization");

  // check whether some inspection got registered twice by accident. 've bit once.
  private static boolean alreadyChecked;

  protected final InspectionProfileImpl myProfile;

  public InspectionProfileWrapper(@NotNull InspectionProfileImpl profile) {
    myProfile = profile;
  }

  public static void checkInspectionsDuplicates(@NotNull InspectionToolWrapper[] toolWrappers) {
    if (alreadyChecked) {
      return;
    }

    alreadyChecked = true;
    Set<InspectionProfileEntry> uniqueTools = new THashSet<>(toolWrappers.length);
    for (InspectionToolWrapper toolWrapper : toolWrappers) {
      ProgressManager.checkCanceled();
      if (!uniqueTools.add(toolWrapper.getTool())) {
        LOG.error("Inspection " + toolWrapper.getDisplayName() + " (" + toolWrapper.getTool().getClass() + ") already registered");
      }
    }
  }

  public boolean isToolEnabled(final HighlightDisplayKey key, PsiElement element) {
    return myProfile.isToolEnabled(key, element);
  }

  public HighlightDisplayLevel getErrorLevel(@NotNull HighlightDisplayKey inspectionToolKey,
                                             PsiElement element) {
    return myProfile.getErrorLevel(inspectionToolKey, element);
  }

  public InspectionToolWrapper getInspectionTool(final String shortName, PsiElement element) {
    return myProfile.getInspectionTool(shortName, element);
  }

  @NotNull
  public InspectionProfileImpl getInspectionProfile() {
    return myProfile;
  }
}
