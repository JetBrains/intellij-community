/*
 * Interface DebuggerActions
 * @author Jeka
 */
package com.intellij.debugger.actions;

import org.jetbrains.annotations.NonNls;

public interface DebuggerActions {
  @NonNls String RESUME = "Resume";
  @NonNls String PAUSE = "Pause";
  @NonNls String SHOW_EXECUTION_POINT = "ShowExecutionPoint";
  @NonNls String STEP_OVER = "StepOver";
  @NonNls String STEP_INTO = "StepInto";
  @NonNls String FORCE_STEP_INTO = "ForceStepInto";
  @NonNls String STEP_OUT = "StepOut";
  @NonNls String POP_FRAME = "Debugger.PopFrame";
  @NonNls String RUN_TO_CURSOR = "RunToCursor";
  @NonNls String FORCE_RUN_TO_CURSOR = "ForceRunToCursor";
  @NonNls String VIEW_BREAKPOINTS = "ViewBreakpoints";
  @NonNls String EVALUATE_EXPRESSION = "EvaluateExpression";
  @NonNls String EVALUATION_DIALOG_POPUP = "Debugger.EvaluationDialogPopup";
  @NonNls String FRAME_PANEL_POPUP = "Debugger.FramePanelPopup";
  @NonNls String INSPECT_PANEL_POPUP = "Debugger.InspectPanelPopup";
  @NonNls String THREADS_PANEL_POPUP = "Debugger.ThreadsPanelPopup";
  @NonNls String WATCH_PANEL_POPUP = "Debugger.WatchesPanelPopup";
  @NonNls String DEBUGGER_TREE = "DebuggerTree";
  @NonNls String DEBUGGER_PANEL = "DebuggerPanel";
  @NonNls String REMOVE_WATCH = "Debugger.RemoveWatch";
  @NonNls String NEW_WATCH = "Debugger.NewWatch";
  @NonNls String ADD_TO_WATCH = "Debugger.AddToWatch";
  @NonNls String EDIT_WATCH = "Debugger.EditWatch";
  @NonNls String SET_VALUE = "Debugger.SetValue";
  @NonNls String EDIT_FRAME_SOURCE = "Debugger.EditFrameSource";
  @NonNls String EDIT_NODE_SOURCE = "Debugger.EditNodeSource";
  @NonNls String MUTE_BREAKPOINTS = "Debugger.MuteBreakpoints";
  @NonNls String TOGGLE_STEP_SUSPEND_POLICY = "Debugger.ToggleStepThreadSuspendPolicy";
  @NonNls String REPRESENTATION_LIST = "Debugger.Representation";
  @NonNls String CUSTOMIZE_VIEWS = "Debugger.CustomizeContextView";
  @NonNls String CUSTOMIZE_THREADS_VIEW = "Debugger.CustomizeThreadsView";
  @NonNls String INSPECT = "Debugger.Inspect";
  @NonNls String EXPORT_THREADS = "EXPORT_THREADS";
}