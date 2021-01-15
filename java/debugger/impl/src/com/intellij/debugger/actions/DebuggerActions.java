/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

/*
 * Interface DebuggerActions
 * @author Jeka
 */
package com.intellij.debugger.actions;

import com.intellij.xdebugger.impl.actions.XDebuggerActions;
import org.jetbrains.annotations.NonNls;

@Deprecated
public interface DebuggerActions extends XDebuggerActions {
  @NonNls String POP_FRAME = "Debugger.PopFrame";
  @NonNls String EVALUATION_DIALOG_POPUP = "Debugger.EvaluationDialogPopup";
  @NonNls String FRAME_PANEL_POPUP = "Debugger.FramePanelPopup";
  @NonNls String INSPECT_PANEL_POPUP = "Debugger.InspectPanelPopup";
  @NonNls String THREADS_PANEL_POPUP = "Debugger.ThreadsPanelPopup";
  @NonNls String WATCH_PANEL_POPUP = "Debugger.WatchesPanelPopup";
  @NonNls String EDIT_FRAME_SOURCE = "Debugger.EditFrameSource";
  @NonNls String EDIT_NODE_SOURCE = "Debugger.EditNodeSource";
  @NonNls String REPRESENTATION_LIST = "Debugger.Representation";
  @NonNls String CUSTOMIZE_VIEWS = "Debugger.CustomizeContextView";
  @NonNls String CUSTOMIZE_THREADS_VIEW = "Debugger.CustomizeThreadsView";
  @NonNls String EXPORT_THREADS = "ExportThreads";
  @NonNls String DUMP_THREADS = "DumpThreads";

}
