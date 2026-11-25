// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
/**
 * The move on the ModPlan engine, see {@link com.intellij.refactoring.modplan.move.ModPlanMoveRefactoring}; the general flow is in
 * {@link com.intellij.refactoring.modplan}. A language joins through a {@link com.intellij.refactoring.modplan.move.ModPlanMoveProvider}.
 * <h2>Flow of the engine</h2>
 * The chain of {@link com.intellij.refactoring.modplan.move.ModPlanMoveRefactoring#chain} asks no question. Its final step makes the plan:
 * <ol>
 *   <li>The requested subjects, without the duplicates and without the subjects inside another requested subject. The owner of each
 *   subject is the first {@link com.intellij.refactoring.modplan.move.ModPlanMoveProvider} in the extension order that owns it. A subject
 *   without an owner, or with an owner that is legacy only, refuses the plan with {@code NOT_SUPPORTED}.</li>
 *   <li>The target directory, {@link com.intellij.refactoring.modplan.move.ModPlanMoveOptions#TARGET_DIRECTORY}. A target outside the
 *   project content refuses the plan with {@code NOT_APPLICABLE} and the message of {@code INVALID_DESTINATION}.</li>
 *   <li>Phase 1: {@link com.intellij.refactoring.modplan.move.ModPlanMoveProvider#place} of each owner, with its subjects. A refusal
 *   refuses the plan with {@code NOT_APPLICABLE} and the message of the {@link com.intellij.refactoring.modplan.move.ModPlanMoveProblem}.
 *   The engine merges the item moves of all owners. It checks the merged item moves,
 *   see {@link com.intellij.refactoring.modplan.move.ModPlanMoveRefactoring#checkPlacement}.</li>
 *   <li>The item moves give {@link com.intellij.refactoring.modplan.move.ModPlanMovePlaces}: the place of each moved item after the
 *   move.</li>
 *   <li>Phase 2: {@link com.intellij.refactoring.modplan.move.ModPlanMoveProvider#analyze} of each owner, with the places of all owners.
 *   The edits of an owner are the edits of the content, {@code Create} and {@code Delete}. The engine throws an
 *   {@code IllegalArgumentException} for an owner edit of the kind {@code Move} or {@code CreateDirectory}.</li>
 *   <li>The file references to each moved item, and to each item inside a moved directory, get their new relative paths. The file
 *   references in each moved file get new relative paths too, see {@link com.intellij.refactoring.modplan.move.ModPlanMoveUsage}. A
 *   reference from a root, such as {@code /css/site.css}, stays as it is if the root of its file stays. With
 *   {@link com.intellij.refactoring.modplan.move.ModPlanMoveOptions#SEARCH_FOR_REFERENCES} off, the move searches and updates no file
 *   reference. This step is the same for all owners.</li>
 *   <li>The place of each updated file reference is a usage of the plan, which a preview shows. A usage that the user excludes keeps its
 *   path, see {@link com.intellij.refactoring.modplan.ModPlanRefactoring#EXCLUDED_USAGES}.</li>
 *   <li>The edits, in this order:
 *     <ol>
 *       <li>The edits of the content of the owners and of the file references.</li>
 *       <li>One creation for each missing directory of the target and of the item moves, parents first.</li>
 *       <li>Each {@code Create} of an owner.</li>
 *       <li>One move for each item move. A directory moves with its content in one move.</li>
 *       <li>Each {@code Delete} of an owner.</li>
 *       <li>The deletion of each emptied directory.</li>
 *     </ol>
 *   </li>
 *   <li>The conflicts are the conflicts of the owners, the file references that the move cannot update, and the usages in unloaded
 *   modules.</li>
 *   <li>{@link com.intellij.refactoring.modplan.ModPlan.Companion#createPlan} converts the edits into the plan. The write events are the
 *   events of the legacy move. Their data has the moved subjects and the target directory. The element listeners hear about each moved
 *   subject.</li>
 * </ol>
 * <ul>
 *   <li>The file provider owns each file and directory that no other provider owns. A file that the {@code MoveFileHandler} of a
 *   language processes moves only in the legacy move, until the language joins the ModPlan move with a provider.</li>
 *   <li>The options are in {@link com.intellij.refactoring.modplan.move.ModPlanMoveOptions}: the target directory, the search for
 *   references, the search in comments and strings, and the search for text.</li>
 * </ul>
 */
@ApiStatus.Experimental
package com.intellij.refactoring.modplan.move;

import org.jetbrains.annotations.ApiStatus;