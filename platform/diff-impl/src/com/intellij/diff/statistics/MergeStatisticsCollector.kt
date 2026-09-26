// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.statistics

import com.intellij.diff.merge.MergeStatisticsAggregator
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.BooleanEventField
import com.intellij.internal.statistic.eventLog.events.EnumEventField
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.eventLog.events.IntEventField
import com.intellij.internal.statistic.eventLog.events.VarargEventId
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import org.jetbrains.annotations.ApiStatus.Internal

@Internal
object MergeStatisticsCollector : CounterUsagesCollector() {
  private val GROUP: EventLogGroup = EventLogGroup("vcs.merge", 6)

  // flow: which multi-file merge flow the event belongs to (ITERATIVE / ONE_SHOT), or STANDALONE for a merge viewer opened outside the dialog.
  // Declared first so it is initialized before every event registration below.
  private val FLOW: EnumEventField<MergeFlow> = EventFields.Enum("flow", MergeFlow::class.java)

  private val MERGE_RESULT: EnumEventField<MergeResult> = EventFields.Enum("result", MergeResult::class.java)
  private val SOURCE: EnumEventField<MergeResultSource> = EventFields.Enum("source", MergeResultSource::class.java)
  private val CHANGES: IntEventField = EventFields.Int("changes")
  private val AUTO_RESOLVABLE = EventFields.Int("autoResolvable")
  private val AUTO_RESOLVABLE_WITH_SEMANTICS = EventFields.Int("autoResolvableWithSemantics")
  private val FILE_LANGUAGE = EventFields.Language("fileLanguage", "Stores information about the base file")
  private val CONFLICTS = EventFields.Int("conflicts")
  private val EDITED = EventFields.Int("edited")
  private val UNRESOLVED = EventFields.Int("unresolved")
  private val AI_RESOLVED = EventFields.Int("aiResolved")
  private val AI_ROLLED_BACK = EventFields.Int("rolledBackAfterAi")
  private val AI_UNDONE = EventFields.Int("undoneAfterAi")
  private val AI_EDITED = EventFields.Int("editedAfterAi")

  private val FILE_MERGED_EVENT: VarargEventId = GROUP.registerVarargEvent("file.merged",
                                                                           MERGE_RESULT,
                                                                           SOURCE,
                                                                           CHANGES,
                                                                           EventFields.DurationMs,
                                                                           AUTO_RESOLVABLE,
                                                                           AUTO_RESOLVABLE_WITH_SEMANTICS,
                                                                           FILE_LANGUAGE,
                                                                           CONFLICTS,
                                                                           EDITED,
                                                                           UNRESOLVED,
                                                                           AI_RESOLVED,
                                                                           AI_ROLLED_BACK,
                                                                           AI_UNDONE,
                                                                           AI_EDITED,
                                                                           FLOW)

  private val SOURCE_DIALOG: EnumEventField<SourceDialog> = EventFields.Enum("dialog", SourceDialog::class.java)
  private val MERGE_ACTION: EnumEventField<MergeAction> = EventFields.Enum("action", MergeAction::class.java)
  private val CONFIRMATION_SHOWN: BooleanEventField = EventFields.Boolean("confirmationShown")
  private val CONFIRMATION_ACCEPTED: BooleanEventField = EventFields.Boolean("confirmationAccepted")
  private val BY_ESC: BooleanEventField = EventFields.Boolean("byEsc")

  private val DIALOG_MERGE_EVENT: VarargEventId = GROUP.registerVarargEvent("merge.event",
                                                                            SOURCE_DIALOG,
                                                                            MERGE_ACTION,
                                                                            CONFIRMATION_SHOWN,
                                                                            CONFIRMATION_ACCEPTED,
                                                                            BY_ESC,
                                                                            FLOW)

  // --- Multi-file merge dialog ("Magic Wand" & usage) events, see IJPL-242734 ---

  private val MAGIC_WAND_RESULT: EnumEventField<MagicWandResult> = EventFields.Enum("magic_wand_result", MagicWandResult::class.java)

  // NB: not "place" — that is a reserved FUS platform data key (EventFields.ActionPlace) and would be stripped by the validator.
  private val PLACE: EnumEventField<SourceDialog> = EventFields.Enum("source_place", SourceDialog::class.java)
  private val SIDE: EnumEventField<MergeSide> = EventFields.Enum("side", MergeSide::class.java)
  private val SIDE_APPLIED_FROM: EnumEventField<SideAppliedFrom> = EventFields.Enum("applied_from", SideAppliedFrom::class.java)
  private val SELECTED_FILES_COUNT = EventFields.RoundedInt("selected_files_count")
  private val REVERT_USED_ON: EnumEventField<RevertUsedOn> = EventFields.Enum("revert_used_on", RevertUsedOn::class.java)
  private val OPEN_TIMES = EventFields.RoundedInt("times")
  private val OPEN_FROM: EnumEventField<FileOpenedFrom> = EventFields.Enum("opened_from", FileOpenedFrom::class.java)
  private val OPEN_USED_ON: EnumEventField<FileOpenedOn> = EventFields.Enum("open_used_on", FileOpenedOn::class.java)
  private val OPEN_HOW: EnumEventField<FileOpenedHow> = EventFields.Enum("how_opened", FileOpenedHow::class.java)
  private val ALL_REVIEWED: BooleanEventField = EventFields.Boolean("all_reviewed")

  /** Set on the [com.intellij.diff.merge.MergeRequest] so the three-side merge viewer can report which multi-file flow opened it. */
  @JvmField
  val MERGE_FLOW_KEY: Key<MergeFlow> = Key.create("MergeStatisticsCollector.MERGE_FLOW")

  // magic.wand.pressed: the Magic Wand's effect on a single file; logged once per affected file so the outcome distribution can be counted
  private val MAGIC_WAND_PRESSED_EVENT = GROUP.registerEvent("magic.wand.pressed", MAGIC_WAND_RESULT, FLOW)

  // magic.wand.session.result: best per-file effect of a single Magic Wand button press (a "session" here is one press, not the whole dialog)
  private val MAGIC_WAND_SESSION_EVENT = GROUP.registerEvent("magic.wand.session.result", MAGIC_WAND_RESULT, FLOW)

  // side_applied: user accepted the left or right side
  private val SIDE_APPLIED_EVENT: VarargEventId = GROUP.registerVarargEvent("side.applied",
                                                                            PLACE, SIDE, SIDE_APPLIED_FROM, SELECTED_FILES_COUNT, FLOW)

  // revert_action_used: user reverted a conflict resolution
  private val REVERT_USED_EVENT: VarargEventId = GROUP.registerVarargEvent("revert.used",
                                                                           PLACE, SELECTED_FILES_COUNT, REVERT_USED_ON, FLOW)

  // file_opened: user opened a file in the three-side merge viewer (times == 1 means the first open)
  private val FILE_OPENED_EVENT: VarargEventId = GROUP.registerVarargEvent("file.opened",
                                                                           OPEN_TIMES, OPEN_FROM, OPEN_USED_ON, OPEN_HOW, FLOW)

  // dialog_closed: the multi-file merge dialog was closed without finishing
  private val DIALOG_CLOSED_EVENT = GROUP.registerEvent("dialog.closed", FLOW)

  // dialog_accept: the user finished the multi-file merge dialog
  private val DIALOG_ACCEPT_EVENT = GROUP.registerEvent("dialog.accepted", ALL_REVIEWED, FLOW)

  override fun getGroup(): EventLogGroup = GROUP

  enum class MergeResult {
    SUCCESS,
    CANCELED,

    /** Iterative "Save and Close": the merge was left unfinished but the partial resolution was saved (not discarded). */
    SAVED
  }

  // log from Merge Viewer
  @JvmStatic
  fun logMergeDialogEvent(
    project: Project?,
    action: MergeAction,
    confirmationShown: Boolean,
    confirmationAccepted: Boolean,
    byEsc: Boolean,
    flow: MergeFlow,
  ) {
    DIALOG_MERGE_EVENT.log(project,
                           SOURCE_DIALOG.with(SourceDialog.MERGE_VIEWER),
                           MERGE_ACTION.with(action),
                           CONFIRMATION_SHOWN.with(confirmationShown),
                           CONFIRMATION_ACCEPTED.with(confirmationAccepted),
                           BY_ESC.with(byEsc),
                           FLOW.with(flow))
  }

  /** Reports the Magic Wand's effect on a single file; call once per affected file. */
  @JvmStatic
  fun logMagicWandPressed(project: Project?, result: MagicWandResult) {
    // Magic Wand only exists in the iterative flow.
    MAGIC_WAND_PRESSED_EVENT.log(project, result, MergeFlow.ITERATIVE)
  }

  /** Reports the best per-file effect of a single Magic Wand button press; call once per press. */
  @JvmStatic
  fun logMagicWandSession(project: Project?, result: MagicWandResult) {
    // Magic Wand only exists in the iterative flow.
    MAGIC_WAND_SESSION_EVENT.log(project, result, MergeFlow.ITERATIVE)
  }

  /** Left/right side accepted from the multi-file conflicts table. */
  @JvmStatic
  fun logSideAppliedOnTable(project: Project?, side: MergeSide, from: SideAppliedFrom, selectedFilesCount: Int, flow: MergeFlow) {
    logSideApplied(project, SourceDialog.CONFLICTS_TABLE, side, from, selectedFilesCount, flow)
  }

  /** Left/right side accepted from within the three-side merge viewer. */
  @JvmStatic
  fun logSideAppliedInViewer(project: Project?, side: MergeSide, from: SideAppliedFrom, flow: MergeFlow) {
    logSideApplied(project, SourceDialog.MERGE_VIEWER, side, from, selectedFilesCount = 1, flow)
  }

  private fun logSideApplied(
    project: Project?,
    place: SourceDialog,
    side: MergeSide,
    from: SideAppliedFrom,
    selectedFilesCount: Int,
    flow: MergeFlow,
  ) {
    SIDE_APPLIED_EVENT.log(project,
                           PLACE.with(place),
                           SIDE.with(side),
                           SIDE_APPLIED_FROM.with(from),
                           SELECTED_FILES_COUNT.with(selectedFilesCount),
                           FLOW.with(flow))
  }

  /** Conflict resolution reverted from the multi-file conflicts table. */
  @JvmStatic
  fun logRevertUsedOnTable(project: Project?, selectedFilesCount: Int, usedOn: RevertUsedOn) {
    // The conflicts-table revert action only exists in the iterative flow.
    logRevertUsed(project, SourceDialog.CONFLICTS_TABLE, selectedFilesCount, usedOn, MergeFlow.ITERATIVE)
  }

  /** Conflict resolution reverted from within the three-side merge viewer. */
  @JvmStatic
  fun logRevertUsedInViewer(project: Project?, usedOn: RevertUsedOn, flow: MergeFlow) {
    logRevertUsed(project, SourceDialog.MERGE_VIEWER, selectedFilesCount = 1, usedOn, flow)
  }

  private fun logRevertUsed(project: Project?, place: SourceDialog, selectedFilesCount: Int, usedOn: RevertUsedOn, flow: MergeFlow) {
    REVERT_USED_EVENT.log(project,
                          PLACE.with(place),
                          SELECTED_FILES_COUNT.with(selectedFilesCount),
                          REVERT_USED_ON.with(usedOn),
                          FLOW.with(flow))
  }

  @JvmStatic
  fun logFileOpened(project: Project?, times: Int, from: FileOpenedFrom, usedOn: FileOpenedOn, howOpened: FileOpenedHow, flow: MergeFlow) {
    FILE_OPENED_EVENT.log(project,
                          OPEN_TIMES.with(times),
                          OPEN_FROM.with(from),
                          OPEN_USED_ON.with(usedOn),
                          OPEN_HOW.with(howOpened),
                          FLOW.with(flow))
  }

  @JvmStatic
  fun logDialogClosed(project: Project?, flow: MergeFlow) {
    DIALOG_CLOSED_EVENT.log(project, flow)
  }

  @JvmStatic
  fun logDialogAccept(project: Project?, allReviewed: Boolean) {
    // Accept-and-finish only exists in the iterative flow.
    DIALOG_ACCEPT_EVENT.log(project, allReviewed, MergeFlow.ITERATIVE)
  }

  @JvmStatic
  fun logMergeFinished(
    project: Project?,
    result: MergeResult,
    source: MergeResultSource,
    aggregator: MergeStatisticsAggregator,
    flow: MergeFlow,
  ) {
    FILE_MERGED_EVENT.log(project) {
      add(FLOW.with(flow))
      add(MERGE_RESULT.with(result))
      add(SOURCE.with(source))
      add(CHANGES.with(aggregator.changes))
      add(EventFields.DurationMs.with(System.currentTimeMillis() - aggregator.initialTimestamp))
      add(AUTO_RESOLVABLE.with(aggregator.autoResolvable))
      add(AUTO_RESOLVABLE_WITH_SEMANTICS.with(aggregator.autoResolvableWithSemantics))
      add(CONFLICTS.with(aggregator.conflicts))
      add(EDITED.with(aggregator.edited()))
      add(UNRESOLVED.with(aggregator.unresolved))
      add(AI_RESOLVED.with(aggregator.resolvedByAi()))
      add(AI_ROLLED_BACK.with(aggregator.rolledBackAfterAI()))
      add(AI_UNDONE.with(aggregator.undoneAfterAI()))
      add(AI_EDITED.with(aggregator.editedAfterAI()))
      add(FILE_LANGUAGE.with(aggregator.language))
    }
  }
}

@Internal
enum class MergeResultSource {
  DIALOG_BUTTON,
  NOTIFICATION,
  DIALOG_CLOSING // for cancellation
}

@Internal
internal enum class SourceDialog {
  MERGE_VIEWER,
  CONFLICTS_TABLE
}

@Internal
enum class MergeAction {
  APPLY,
  CANCEL,
  LEFT,
  RIGHT,

  /** Iterative "Save and Close" button: distinct from CANCEL, which is a discard/abort. */
  SAVE_AND_CLOSE
}

/**
 * Effect of one Magic Wand ("Resolve automatically") press on a single file, ordered best-to-worst
 * (the [Enum.ordinal] is used to pick the best result across a session).
 */
@Internal
enum class MagicWandResult {
  FULLY_RESOLVED,
  PARTIALLY_RESOLVED,
  ONLY_SIDES_APPLIED,
  NO_EFFECT
}

@Internal
enum class MergeSide {
  LEFT,
  RIGHT
}

@Internal
enum class SideAppliedFrom {
  BUTTON,
  CONTEXT_MENU
}

@Internal
enum class RevertUsedOn {
  RESOLVED,
  UNRESOLVED,
  BOTH
}

@Internal
enum class FileOpenedFrom {
  ROW_CLICK,
  RESOLVE_BUTTON
}

@Internal
enum class FileOpenedOn {
  RESOLVED,
  UNRESOLVED
}

@Internal
enum class FileOpenedHow {
  INTENTIONALLY,
  AUTOMATICALLY
}

/**
 * Which multi-file merge flow an event belongs to.
 * [STANDALONE] is used for three-side merge viewer events that were not opened from the multi-file merge dialog.
 */
@Internal
enum class MergeFlow {
  ITERATIVE,
  ONE_SHOT,
  STANDALONE
}
