// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
/**
 * The safe delete on the ModPlan engine, see {@link com.intellij.refactoring.modplan.safeDelete.ModPlanSafeDeleteRefactoring}; the
 * general flow is in {@link com.intellij.refactoring.modplan}.
 * <h2>Flow of the engine</h2>
 * The chain of {@link com.intellij.refactoring.modplan.safeDelete.ModPlanSafeDeleteRefactoring#chain}:
 * <ol>
 *   <li>The requested subjects, without the duplicates of one symbol.</li>
 *   <li>Each subject gets an owner: the first registered {@link com.intellij.refactoring.modplan.safeDelete.ModPlanSafeDeleteProvider}
 *   that is applicable, else {@code ModPlanGenericSafeDeleteProvider}, for named elements and files, where every reference is a
 *   conflict.</li>
 *   <li>The owner prepares the subject, {@code prepareRequest}, and may ask questions. The preparation refuses the subject, deletes it,
 *   deletes it with more subjects, or replaces it with other subjects. An added subject is prepared too, but it expands nothing more.</li>
 *   <li>The owner analyzes every deleted subject, {@code analyze}, and may ask questions; the analysis is a final step.</li>
 *   <li>The final step of the engine runs once, after the last question, with the request that holds every answer:
 *   <ul>
 *     <li>it runs the analysis of every deleted subject; a refused analysis refuses the whole plan;</li>
 *     <li>the edits are the edits of the usages, the last position in a file first, then one deletion of every subject;</li>
 *     <li>the conflicts are the conflicts of all the analyses, without a second broken conflict at the same place, and the usages in
 *     unloaded modules;</li>
 *     <li>{@link com.intellij.refactoring.modplan.ModPlan.Companion#createPlan} converts the edits into the plan.</li>
 *   </ul></li>
 * </ol>
 * <ul>
 *   <li>A provider finds the usages of a subject and describes their edits; it writes no PSI and shows no UI. A usage inside a deleted
 *   subject is deleted with it. A safe usage becomes an edit, a usage that still needs the subject a broken conflict, a usage in a
 *   comment or a non-code file a non-code usage.</li>
 *   <li>A legacy {@code SafeDeleteProcessorDelegate} that opts in with {@code asModPlan} runs through
 *   {@link com.intellij.refactoring.modplan.safeDelete.ModPlanLegacySafeDeleteProvider}. One registered bridge provider,
 *   {@code ModPlanLegacyBridgeSafeDeleteProvider}, is last in the order, so a language provider wins.</li>
 *   <li>The common options are in {@link com.intellij.refactoring.modplan.safeDelete.ModPlanSafeDeleteOptions}: the search in comments
 *   and strings, in non-code files, the search of related elements and the deletion in the whole hierarchy. A language adds its own,
 *   such as {@code ModPlanJavaSafeDeleteOptions}.</li>
 *   <li>{@code ModPlanSafeDeleteFlow} is the safe delete of the IDE on the engine, behind the registry key
 *   {@code refactoring.safeDelete.modPlan}; the legacy flow runs for every element that the engine does not support.</li>
 * </ul>
 */
@ApiStatus.Experimental
package com.intellij.refactoring.modplan.safeDelete;

import org.jetbrains.annotations.ApiStatus;
