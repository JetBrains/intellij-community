// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
/**
 * The move of files and directories on the ModPlan engine, see {@link com.intellij.refactoring.modplan.move.ModPlanMoveRefactoring}; the
 * general flow is in {@link com.intellij.refactoring.modplan}.
 * <h2>Flow of the engine</h2>
 * The chain of {@link com.intellij.refactoring.modplan.move.ModPlanMoveRefactoring#chain} asks no question. Its final step makes the plan:
 * <ol>
 *   <li>The requested items, without the duplicates and without the items inside another requested directory.</li>
 *   <li>{@link com.intellij.refactoring.modplan.move.ModPlanMoveRefactoring#checkPlacement} checks the items and the target directory,
 *   {@link com.intellij.refactoring.modplan.move.ModPlanMoveOptions#TARGET_DIRECTORY}. A problem refuses the plan with
 *   {@code NOT_APPLICABLE} and the message of the {@link com.intellij.refactoring.modplan.move.ModPlanMoveProblem}.</li>
 *   <li>Each moved file, also each file inside a moved directory, gets the adapter of its {@code MoveFileHandler}, see
 *   {@code MoveFileHandler#asModPlan}. A file of a handler without an adapter refuses the plan with {@code NOT_SUPPORTED}.</li>
 *   <li>Each adapter analyzes its file with the places of all the moved items after the move,
 *   {@link com.intellij.refactoring.modplan.move.ModPlanMovePlaces}.</li>
 *   <li>The file references to each moved item, and to each item inside a moved directory, get their new relative paths. The file
 *   references in each moved file get new relative paths too, see {@link com.intellij.refactoring.modplan.move.ModPlanMoveUsage}. A
 *   reference from a root, such as {@code /css/site.css}, stays as it is if the root of its file stays.</li>
 *   <li>The place of each updated file reference is a usage of the plan, which a preview shows. A usage that the user excludes keeps its
 *   path, see {@link com.intellij.refactoring.modplan.ModPlanRefactoring#EXCLUDED_USAGES}.</li>
 *   <li>The edits are the edits of the adapters and of the file references. Then each missing directory of the target gets one creation,
 *   parents first. Then each item gets one move. A directory moves with its content in one move.</li>
 *   <li>The conflicts are the conflicts of the adapters, the file references that the move cannot update, and the usages in unloaded
 *   modules.</li>
 *   <li>{@link com.intellij.refactoring.modplan.ModPlan.Companion#createPlan} converts the edits into the plan. The write events are the
 *   events of the legacy move. Their data has the moved items and the target directory. The element listeners hear about each moved
 *   item.</li>
 * </ol>
 * <ul>
 *   <li>The default {@code MoveFileHandler} joins with an adapter that adds nothing,
 *   {@link com.intellij.refactoring.modplan.move.ModPlanMoveFileHandler#NOTHING}. The handler of a language has no adapter until the
 *   language joins the ModPlan move.</li>
 *   <li>The options are in {@link com.intellij.refactoring.modplan.move.ModPlanMoveOptions}: the target directory, the search in comments
 *   and strings, and the search for text. The default adapter reads no search option.</li>
 * </ul>
 */
@ApiStatus.Experimental
package com.intellij.refactoring.modplan.move;

import org.jetbrains.annotations.ApiStatus;