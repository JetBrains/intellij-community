// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl;


import com.intellij.codeInspection.InspectionProfile;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;
import com.intellij.profile.ProfileChangeAdapter;
import com.intellij.psi.PsiFile;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.ObjectIntMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

/**
 * Track inspection tools which have {@link LocalInspectionTool#runForWholeFile()} overridden to true and
 * allow to quickly filter out files which have no whole-file-inspections configured in their inspection profile,
 */
@Service(Service.Level.PROJECT)
final class WholeFileLocalInspectionPassTracker implements Disposable {
  private final Set<PsiFile> mySkipWholeInspectionsCache = ContainerUtil.createWeakSet(); // guarded by mySkipWholeInspectionsCache
  private final ObjectIntMap<PsiFile> myPsiModificationCount = ContainerUtil.createWeakKeyIntValueMap(); // guarded by myPsiModificationCount

  WholeFileLocalInspectionPassTracker(@NotNull Project project) {
    project.getMessageBus().connect().subscribe(ProfileChangeAdapter.TOPIC, new ProfileChangeAdapter() {
      @Override
      public void profileChanged(@NotNull InspectionProfile profile) {
        clearCaches();
      }

      @Override
      public void profileActivated(InspectionProfile oldProfile, @Nullable InspectionProfile profile) {
        clearCaches();
      }
    });
  }

  boolean canSkipFile(@NotNull PsiFile file) {
    synchronized (mySkipWholeInspectionsCache) {
      if (mySkipWholeInspectionsCache.contains(file)) {
        return true;
      }
    }
    return false;
  }

  void lookThereAreNoWholeFileToolsConfiguredForThisFileSoWeCanProbablySkipItAltogether(@NotNull PsiFile file) {
    synchronized (mySkipWholeInspectionsCache) {
      mySkipWholeInspectionsCache.add(file);
    }
  }

  void informationApplied(@NotNull PsiFile file) {
    long modificationCount = file.getManager().getModificationTracker().getModificationCount();
    synchronized (myPsiModificationCount) {
      myPsiModificationCount.put(file, (int)modificationCount);
    }
  }

  static WholeFileLocalInspectionPassTracker getInstance(Project project) {
    return project.getService(WholeFileLocalInspectionPassTracker.class);
  }

  boolean isChanged(@NotNull PsiFile file) {
    long actualCount = file.getManager().getModificationTracker().getModificationCount();
    synchronized (myPsiModificationCount) {
      return actualCount != myPsiModificationCount.get(file);
    }
  }

  private void clearCaches() {
    synchronized (mySkipWholeInspectionsCache) {
      mySkipWholeInspectionsCache.clear();
    }
    synchronized (myPsiModificationCount) {
      myPsiModificationCount.clear();
    }
  }

  @Override
  public void dispose() {
    clearCaches();
  }
}
