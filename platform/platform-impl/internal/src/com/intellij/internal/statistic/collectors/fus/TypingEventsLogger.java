// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.collectors.fus;

import com.intellij.internal.performance.LatencyDistributionRecord;
import com.intellij.internal.performance.LatencyDistributionRecordKey;
import com.intellij.internal.statistic.collectors.fus.actions.persistence.ToolWindowCollector.ToolWindowUtilValidator;
import com.intellij.internal.statistic.eventLog.EventLogGroup;
import com.intellij.internal.statistic.eventLog.events.EnumEventField;
import com.intellij.internal.statistic.eventLog.events.EventFields;
import com.intellij.internal.statistic.eventLog.events.EventId;
import com.intellij.internal.statistic.eventLog.events.EventId2;
import com.intellij.internal.statistic.eventLog.events.EventId3;
import com.intellij.internal.statistic.eventLog.events.EventPair;
import com.intellij.internal.statistic.eventLog.events.IntEventField;
import com.intellij.internal.statistic.eventLog.events.StringEventField;
import com.intellij.internal.statistic.eventLog.events.VarargEventId;
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector;
import com.intellij.internal.statistic.utils.EventRateThrottleResult;
import com.intellij.internal.statistic.utils.EventsRateWindowThrottle;
import com.intellij.internal.statistic.utils.StatisticsUploadAssistant;
import com.intellij.lang.Language;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.actionSystem.ex.AnActionListener;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorKind;
import com.intellij.openapi.editor.actionSystem.LatencyListener;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.FileEditorManagerEvent;
import com.intellij.openapi.fileEditor.FileEditorManagerListener;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;

@ApiStatus.Internal
public final class TypingEventsLogger extends CounterUsagesCollector {
  private static final EventLogGroup GROUP = new EventLogGroup("editor.typing", 12);

  private static final EnumEventField<EditorKind> EDITOR_KIND = EventFields.Enum("editor_kind", EditorKind.class);
  private static final StringEventField TOOL_WINDOW =
    EventFields.StringValidatedByCustomRule("toolwindow_id", ToolWindowUtilValidator.class);
  private static final VarargEventId TYPED = GROUP.registerVarargEvent("typed", EDITOR_KIND, TOOL_WINDOW, EventFields.Language);
  private static final IntEventField SELECTION_LENGTH =
    EventFields.Int("selection_length", "How many selected characters were deleted");
  private static final EnumEventField<SelectionDeleteAction> DELETE_ACTION =
    EventFields.Enum("delete_action", SelectionDeleteAction.class);
  private static final VarargEventId SELECTION_DELETED =
    GROUP.registerVarargEvent("selection.deleted", EDITOR_KIND, EventFields.Language, SELECTION_LENGTH, DELETE_ACTION);
  private static final EventId TOO_MANY_EVENTS = GROUP.registerEvent("too.many.events");
  private static final IntEventField LATENCY_NUMBER_EVENTS = EventFields.Int("number_of_events");
  private static final IntEventField LATENCY_MAX = EventFields.Int("latency_max_ms");
  private static final IntEventField LATENCY_90 = EventFields.Int("latency_90_ms");
  private static final IntEventField LATENCY_50 = EventFields.Int("latency_50_ms");
  private static final VarargEventId LATENCY = GROUP.registerVarargEvent("latency", LATENCY_MAX, LATENCY_90, LATENCY_50, LATENCY_NUMBER_EVENTS, EventFields.FileType);
  private static final EventId2<Language, Language> TYPED_IN_INJECTED = GROUP.registerEvent(
    "typed.in.injected.language",
    EventFields.Language("original_lang"), EventFields.Language("injected_lang")
  );
  private static final EventId TOO_MANY_INJECTED_EVENTS = GROUP.registerEvent("too.many.injected.events");

  private static final EventsRateWindowThrottle ourThrottle =
    new EventsRateWindowThrottle(8000, 60 * 60 * 1000, System.currentTimeMillis());
  private static final EventsRateWindowThrottle ourInjectedThrottle =
    new EventsRateWindowThrottle(500, 60 * 60 * 1000, System.currentTimeMillis());

  @Override
  public EventLogGroup getGroup() {
    return GROUP;
  }

  public static void logTypedInInjected(Project project, PsiFile originalFile, PsiFile injectedFile) {
    if (!StatisticsUploadAssistant.isCollectAllowedOrForced()) return;

    EventRateThrottleResult result = ourInjectedThrottle.tryPass(System.currentTimeMillis());
    if (result == EventRateThrottleResult.ACCEPT) {
      TYPED_IN_INJECTED.log(project, originalFile.getLanguage(), injectedFile.getLanguage());
    }
    else if (result == EventRateThrottleResult.DENY_AND_REPORT) {
      TOO_MANY_INJECTED_EVENTS.log(project);
    }
  }

  public static void logSelectionDeleted(@NotNull Editor editor,
                                         @NotNull DataContext dataContext,
                                         int selectionLength,
                                         @NotNull SelectionDeleteAction deleteAction) {
    Project project = CommonDataKeys.PROJECT.getData(dataContext);
    if (project == null) return;
    Language fileLanguage = DataContextUtils.getFileLanguage(dataContext);
    if (fileLanguage == null) return;
    logSelectionDeletedInternal(project, editor, fileLanguage, selectionLength, deleteAction);
  }

  public static void logSelectionDeleted(@NotNull Project project,
                                         @NotNull Editor editor,
                                         @NotNull PsiFile file,
                                         int selectionLength,
                                         @NotNull SelectionDeleteAction deleteAction) {
    logSelectionDeletedInternal(project, editor, file.getLanguage(), selectionLength, deleteAction);
  }

  private static void logSelectionDeletedInternal(@NotNull Project project,
                                                  @NotNull Editor editor,
                                                  @NotNull Language language,
                                                  int selectionLength,
                                                  @NotNull SelectionDeleteAction deleteAction) {
    if (selectionLength <= 0 || !StatisticsUploadAssistant.isCollectAllowedOrForced()) return;

    ArrayList<EventPair<?>> pairs = new ArrayList<>(4);
    try {
      pairs.add(EDITOR_KIND.with(editor.getEditorKind()));
    }
    catch (UnsupportedOperationException ignore) {
      // See com.intellij.openapi.editor.impl.ImaginaryEditor
    }
    pairs.add(EventFields.Language.with(language));
    pairs.add(SELECTION_LENGTH.with(selectionLength));
    pairs.add(DELETE_ACTION.with(deleteAction));
    SELECTION_DELETED.log(project, pairs);
  }

  public enum SelectionDeleteAction {
    BACKSPACE,
    DELETE,
    TYPING,
  }

  public static final class TypingEventsListener implements AnActionListener {
    @Override
    public void beforeEditorTyping(char c, @NotNull DataContext dataContext) {
      if (!StatisticsUploadAssistant.isCollectAllowedOrForced()) return;

      EventRateThrottleResult result = ourThrottle.tryPass(System.currentTimeMillis());
      Project project = CommonDataKeys.PROJECT.getData(dataContext);
      if (result == EventRateThrottleResult.ACCEPT) {
        ArrayList<EventPair<?>> pairs = new ArrayList<>(3);

        Editor editor = CommonDataKeys.EDITOR.getData(dataContext);
        if (editor != null) {
          try {
            pairs.add(EDITOR_KIND.with(editor.getEditorKind()));
          } catch (UnsupportedOperationException e) {
            // See com.intellij.openapi.editor.impl.ImaginaryEditor
          }
        }

        ToolWindow toolWindow = PlatformDataKeys.TOOL_WINDOW.getData(dataContext);
        if (toolWindow != null) {
          pairs.add(TOOL_WINDOW.with(toolWindow.getId()));
        }

        Language fileLanguage = DataContextUtils.getFileTypeLanguageByEditor(dataContext);
        if (fileLanguage != null) {
          pairs.add(EventFields.Language.with(fileLanguage));
        }

        TYPED.log(project, pairs);
      }
      else if (result == EventRateThrottleResult.DENY_AND_REPORT) {
        TOO_MANY_EVENTS.log(project);
      }
    }
  }

  public static final class TypingLatencyReporter implements FileEditorManagerListener, LatencyListener {
    public TypingLatencyReporter() {
      ApplicationManager.getApplication().getMessageBus().connect().subscribe(LatencyListener.TOPIC, this);
    }

    private LatencyDistributionRecord myLatencyRecord;
    private Editor myCurrentEditor;
    private VirtualFile myCurrentFile;

    @Override
    public void selectionChanged(@NotNull FileEditorManagerEvent event) {
      logCurrentLatency();

      FileEditor editor = event.getNewEditor();
      if (editor instanceof TextEditor te) {
        myCurrentFile = event.getNewFile();
        myCurrentEditor = te.getEditor();
        myLatencyRecord = new LatencyDistributionRecord(new LatencyDistributionRecordKey("FUS"));
      }
      else {
        myCurrentEditor = null;
        myLatencyRecord = null;
      }
    }

    private void logCurrentLatency() {
      if (myLatencyRecord != null && myLatencyRecord.getTotalLatency().getTotalLatency() > 0) {
        LATENCY.log(LATENCY_MAX.with(myLatencyRecord.getTotalLatency().getMaxLatency()),
                    LATENCY_90.with(myLatencyRecord.getTotalLatency().percentile(90)),
                    LATENCY_50.with(myLatencyRecord.getTotalLatency().percentile(50)),
                    LATENCY_NUMBER_EVENTS.with(myLatencyRecord.getTotalLatency().getSamples().size()),
                    EventFields.FileType.with(myCurrentFile.getFileType()));
      }
    }

    @Override
    public void fileClosed(@NotNull FileEditorManager source, @NotNull VirtualFile file) {
      if (file.equals(myCurrentFile)) {
        logCurrentLatency();
        myCurrentEditor = null;
        myLatencyRecord = null;
      }
    }

    @Override
    public void recordTypingLatency(@NotNull Editor editor, String action, long latencyMs) {
      if (editor == myCurrentEditor) {
        myLatencyRecord.update(action, (int) latencyMs);
      }
    }
  }
}
