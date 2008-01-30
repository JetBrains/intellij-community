/*
 * Interface DebuggerActions
 * @author Jeka
 */
package com.intellij.debugger.actions;

import org.jetbrains.annotations.NonNls;
import com.intellij.xdebugger.impl.actions.XDebuggerActions;

public interface DebuggerActions extends XDebuggerActions {
  @NonNls String POP_FRAME = "Debugger.PopFrame";
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
  @NonNls String MARK_OBJECT = "Debugger.MarkObject";
  @NonNls String SET_VALUE = "Debugger.SetValue";
  @NonNls String EDIT_FRAME_SOURCE = "Debugger.EditFrameSource";
  @NonNls String EDIT_NODE_SOURCE = "Debugger.EditNodeSource";
  @NonNls String MUTE_BREAKPOINTS = "Debugger.MuteBreakpoints";
  @NonNls String REPRESENTATION_LIST = "Debugger.Representation";
  @NonNls String CUSTOMIZE_VIEWS = "Debugger.CustomizeContextView";
  @NonNls String CUSTOMIZE_THREADS_VIEW = "Debugger.CustomizeThreadsView";
  @NonNls String INSPECT = "Debugger.Inspect";
  @NonNls String EXPORT_THREADS = "ExportThreads";
  @NonNls String LAYOUT = "Debugger.Layout";
  @NonNls String DEBUGGER_VIEW_POPUP = "Debugger.View.Popup";
  @NonNls String DEBUGGER_VIEW_TOOLBAR = "Debugger.View.Toolbar";
}