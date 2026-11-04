// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

/**
 * The rename on the ModPlan engine, see {@link com.intellij.refactoring.modplan.rename.ModPlanRenameRefactoring}; the general flow is in
 * {@link com.intellij.refactoring.modplan}.
 * <p>
 * The order in which the engine calls a provider is in {@link com.intellij.refactoring.modplan.rename.ModPlanRenameProvider}.
 * <ul>
 *   <li>A provider finds the usages of a subject and describes their edits. It writes no PSI and shows no UI. Only the owner of a
 *   subject prepares it and searches its usages.</li>
 *   <li>A legacy {@code RenamePsiElementProcessor} runs through a registered subclass of
 *   {@link com.intellij.refactoring.modplan.rename.ModPlanBridgeRenameProvider}, a subclass of
 *   {@link com.intellij.refactoring.modplan.rename.ModPlanRenameProviderBase}, which the providers of the legacy {@code UsageInfo}s
 *   share. One transitional provider,
 *   {@code ModPlanHeadlessRenameProvider}, serves the default processor and the headless processors. It is last in the order, so a
 *   language provider wins.</li>
 *   <li>The common options are in {@link com.intellij.refactoring.modplan.rename.ModPlanRenameOptions}: the new name, the search in
 *   comments and in text occurrences, and the related renames.</li>
 *   <li>{@code ModPlanRenameFlow} is the rename of the rename dialog and of the inline rename of members on the engine, behind the
 *   registry key {@code refactoring.rename.modPlan}; the legacy rename runs for every element that the engine does not support.</li>
 * </ul>
 */
@ApiStatus.Experimental
package com.intellij.refactoring.modplan.rename;

import org.jetbrains.annotations.ApiStatus;
