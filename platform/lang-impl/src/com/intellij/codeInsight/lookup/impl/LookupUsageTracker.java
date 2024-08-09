// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.lookup.impl;

import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.codeInsight.completion.BaseCompletionService;
import com.intellij.codeInsight.completion.CompletionContributor;
import com.intellij.codeInsight.lookup.Lookup;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupEvent;
import com.intellij.codeInsight.lookup.LookupListener;
import com.intellij.ide.ui.UISettings;
import com.intellij.internal.statistic.collectors.fus.fileTypes.FileTypeUsageCounterCollector;
import com.intellij.internal.statistic.collectors.fus.fileTypes.FileTypeUsageCounterCollector.FileTypeSchemaValidator;
import com.intellij.internal.statistic.eventLog.EventLogGroup;
import com.intellij.internal.statistic.eventLog.events.*;
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector;
import com.intellij.internal.statistic.utils.PluginInfoDetectorKt;
import com.intellij.lang.Language;
import com.intellij.lang.documentation.ide.impl.DocumentationPopupListener;
import com.intellij.openapi.application.WriteIntentReadAction;
import com.intellij.openapi.editor.EditorKind;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.IncompleteDependenciesService;
import com.intellij.openapi.project.IncompleteDependenciesService.DependenciesState;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiUtilCore;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

import static com.intellij.codeInsight.completion.BaseCompletionService.LOOKUP_ELEMENT_RESULT_ADD_TIMESTAMP_MILLIS;
import static com.intellij.codeInsight.completion.BaseCompletionService.LOOKUP_ELEMENT_RESULT_SET_ORDER;
import static com.intellij.codeInsight.completion.CompletionData.LOOKUP_ELEMENT_PSI_REFERENCE;
import static com.intellij.codeInsight.lookup.LookupElement.LOOKUP_ELEMENT_SHOW_TIMESTAMP_MILLIS;
import static com.intellij.codeInsight.lookup.impl.LookupTypedHandler.CANCELLATION_CHAR;

@ApiStatus.Internal
public final class LookupUsageTracker extends CounterUsagesCollector {
  public static final String FINISHED_EVENT_ID = "finished";
  public static final String GROUP_ID = "completion";
  public static final EventLogGroup GROUP = new EventLogGroup(GROUP_ID, 31);
  private static final EventField<String> SCHEMA = EventFields.StringValidatedByCustomRule("schema", FileTypeSchemaValidator.class);
  private static final BooleanEventField ALPHABETICALLY = EventFields.Boolean("alphabetically");
  private static final EnumEventField<EditorKind> EDITOR_KIND = EventFields.Enum("editor_kind", EditorKind.class);
  private static final EnumEventField<FinishType> FINISH_TYPE = EventFields.Enum("finish_type", FinishType.class);
  private static final LongEventField DURATION = EventFields.Long("duration");
  private static final IntEventField SELECTED_INDEX = EventFields.Int("selected_index");
  private static final IntEventField SELECTION_CHANGED = EventFields.Int("selection_changed");
  private static final IntEventField TYPING = EventFields.Int("typing");
  private static final IntEventField BACKSPACES = EventFields.Int("backspaces");
  private static final EnumEventField<CompletionChar> COMPLETION_CHAR = EventFields.Enum("completion_char", CompletionChar.class);
  private static final IntEventField TOKEN_LENGTH = EventFields.Int("token_length");
  private static final IntEventField QUERY_LENGTH = EventFields.Int("query_length");
  private static final ClassEventField CONTRIBUTOR = EventFields.Class("contributor");
  private static final ClassEventField PSI_REFERENCE = EventFields.Class("psi_reference");
  private static final LongEventField TIME_TO_SHOW = EventFields.Long("time_to_show");
  private static final LongEventField TIME_TO_SHOW_CORRECT_ELEMENT = EventFields.Long("time_to_show_correct_element");
  private static final LongEventField TIME_TO_SHOW_FIRST_ELEMENT = EventFields.Long("time_to_show_first_element");
  private static final LongEventField TIME_TO_COMPUTE_CORRECT_ELEMENT = EventFields.Long("time_to_compute_correct_element");
  private static final IntEventField ORDER_ADDED_CORRECT_ELEMENT = EventFields.Int("order_added_correct_element");
  private static final BooleanEventField DUMB_FINISH = EventFields.Boolean("dumb_finish");
  private static final BooleanEventField DUMB_START = EventFields.Boolean("dumb_start");
  private static final EnumEventField<DependenciesState> INCOMPLETE_DEPENDENCIES_MODE_ON_START = EventFields.Enum("incomplete_dependencies_mode_on_start", DependenciesState.class);
  private static final EnumEventField<DependenciesState> INCOMPLETE_DEPENDENCIES_MODE_ON_FINISH = EventFields.Enum("incomplete_dependencies_mode_on_finish", DependenciesState.class);
  private static final BooleanEventField QUICK_DOC_SHOWN = EventFields.Boolean("quick_doc_shown");
  private static final BooleanEventField QUICK_DOC_AUTO_SHOW = EventFields.Boolean("quick_doc_auto_show");
  private static final BooleanEventField QUICK_DOC_SCROLLED = EventFields.Boolean("quick_doc_scrolled");
  public static final ObjectEventField ADDITIONAL = EventFields.createAdditionalDataField(GROUP.getId(), FINISHED_EVENT_ID);
  public static final VarargEventId FINISHED = GROUP.registerVarargEvent(FINISHED_EVENT_ID,
                                                                         EventFields.Language,
                                                                         EventFields.CurrentFile,
                                                                         SCHEMA,
                                                                         ALPHABETICALLY,
                                                                         EDITOR_KIND,
                                                                         FINISH_TYPE,
                                                                         DURATION,
                                                                         SELECTED_INDEX,
                                                                         SELECTION_CHANGED,
                                                                         TYPING,
                                                                         BACKSPACES,
                                                                         COMPLETION_CHAR,
                                                                         TOKEN_LENGTH,
                                                                         QUERY_LENGTH,
                                                                         CONTRIBUTOR,
                                                                         PSI_REFERENCE,
                                                                         TIME_TO_SHOW,
                                                                         TIME_TO_SHOW_CORRECT_ELEMENT,
                                                                         TIME_TO_SHOW_FIRST_ELEMENT,
                                                                         TIME_TO_COMPUTE_CORRECT_ELEMENT,
                                                                         ORDER_ADDED_CORRECT_ELEMENT,
                                                                         DUMB_FINISH,
                                                                         DUMB_START,
                                                                         INCOMPLETE_DEPENDENCIES_MODE_ON_START,
                                                                         INCOMPLETE_DEPENDENCIES_MODE_ON_FINISH,
                                                                         QUICK_DOC_SHOWN,
                                                                         QUICK_DOC_AUTO_SHOW,
                                                                         QUICK_DOC_SCROLLED,
                                                                         ADDITIONAL);

  private LookupUsageTracker() {
  }

  static void trackLookup(long createdTimestamp, @NotNull LookupImpl lookup) {
    lookup.addLookupListener(new MyLookupTracker(createdTimestamp, lookup));
  }

  public static boolean isSelectedByTyping(@NotNull LookupImpl lookup, @NotNull LookupElement item) {
    var cancellationChar = lookup.getUserData(CANCELLATION_CHAR);
    String lookupString = item.getLookupString();
    String pattern = lookup.itemPattern(item);
    if (cancellationChar != null && lookupString.endsWith(cancellationChar.toString())) {
      return pattern.equals(lookupString.substring(0, lookupString.length() - 1));
    }
    return pattern.equals(lookupString);
  }

  @Override
  public EventLogGroup getGroup() {
    return GROUP;
  }

  private static final class MyLookupTracker implements LookupListener {
    private final @NotNull LookupImpl myLookup;
    private final long myCreatedTimestamp;
    private final long myTimeToShow;
    private @Nullable Long myTimestampFirstElementShown = null;
    private @Nullable Long myTimestampCorrectElementShown = null;
    private @Nullable Long myTimestampCorrectElementComputed = null;
    private @Nullable Integer myOrderComputedCorrectElement = null;
    private final boolean myIsDumbStart;
    private final DependenciesState myIncompleteDependenciesStateStart;
    private final @Nullable Language myLanguage;
    private final @NotNull MyTypingTracker myTypingTracker;

    private int mySelectionChangedCount = 0;
    private boolean myIsQuickDocShown = false;
    private final boolean myIsQuickDocAutoShow;
    private boolean myIsQuickDocScrolled = false;

    MyLookupTracker(long createdTimestamp, @NotNull LookupImpl lookup) {
      myLookup = lookup;
      myCreatedTimestamp = createdTimestamp;
      myTimeToShow = System.currentTimeMillis() - createdTimestamp;
      myIsDumbStart = DumbService.isDumb(lookup.getProject());
      myIncompleteDependenciesStateStart = lookup.getProject().getService(IncompleteDependenciesService.class).getState();
      myLanguage = getLanguageAtCaret(lookup);
      myTypingTracker = new MyTypingTracker();
      myIsQuickDocAutoShow = CodeInsightSettings.getInstance().AUTO_POPUP_JAVADOC_INFO;
      lookup.addPrefixChangeListener(myTypingTracker, lookup);
      lookup.getProject().getMessageBus().connect(lookup).subscribe(
        DocumentationPopupListener.TOPIC, new DocumentationPopupListener() {
          @Override
          public void popupShown() {
            myIsQuickDocShown = true;
          }

          @Override
          public void contentsScrolled() {
            myIsQuickDocScrolled = true;
          }
        });
    }

    @Override
    public void firstElementShown() {
      myTimestampFirstElementShown = System.currentTimeMillis();
    }

    @Override
    public void currentItemChanged(@NotNull LookupEvent event) {
      mySelectionChangedCount += 1;
    }

    @Override
    public void itemSelected(@NotNull LookupEvent event) {
      LookupElement item = event.getItem();
      char completionChar = event.getCompletionChar();
      if (item == null) {
        triggerLookupUsed(FinishType.CANCELED_BY_TYPING, null, completionChar);
      }
      else {
        myTimestampCorrectElementShown = item.getUserData(LOOKUP_ELEMENT_SHOW_TIMESTAMP_MILLIS);
        myTimestampCorrectElementComputed = item.getUserData(LOOKUP_ELEMENT_RESULT_ADD_TIMESTAMP_MILLIS);
        myOrderComputedCorrectElement = item.getUserData(LOOKUP_ELEMENT_RESULT_SET_ORDER);
        if (isSelectedByTyping(myLookup, item)) {
          triggerLookupUsed(FinishType.TYPED, item, completionChar);
        }
        else {
          triggerLookupUsed(FinishType.EXPLICIT, item, completionChar);
        }
      }
    }

    @Override
    public void lookupCanceled(@NotNull LookupEvent event) {
      LookupElement item = myLookup.getCurrentItem();
      if (item != null && isSelectedByTyping(myLookup, item)) {
        triggerLookupUsed(FinishType.TYPED, item, event.getCompletionChar());
      }
      else {
        FinishType detailedCancelType = event.isCanceledExplicitly() ? FinishType.CANCELED_EXPLICITLY : FinishType.CANCELED_BY_TYPING;
        triggerLookupUsed(detailedCancelType, null, event.getCompletionChar());
      }
    }

    private void triggerLookupUsed(@NotNull FinishType finishType, @Nullable LookupElement currentItem,
                                   char completionChar) {
      final List<EventPair<?>> data = WriteIntentReadAction.compute(
        //maybe readaction
        (Computable<List<EventPair<?>>>)() -> getCommonUsageInfo(finishType, currentItem, completionChar)
      );

      final List<EventPair<?>> additionalData = new ArrayList<>();
      LookupUsageDescriptor.EP_NAME.forEachExtensionSafe(usageDescriptor -> {
        if (PluginInfoDetectorKt.getPluginInfo(usageDescriptor.getClass()).isSafeToReport()) {
          additionalData.addAll(usageDescriptor.getAdditionalUsageData(
            new MyLookupResultDescriptor(myLookup, currentItem, finishType, myLanguage)));
        }
      });

      if (!additionalData.isEmpty()) {
        data.add(ADDITIONAL.with(new ObjectEventData(additionalData)));
      }

      FINISHED.log(myLookup.getProject(), data);
    }

    private void convertTimestampToDuration(List<EventPair<?>> data, @NotNull LongEventField field, @Nullable Long timestamp) {
      if (timestamp == null) return;
      data.add(field.with(timestamp - myCreatedTimestamp));
    }

    private List<EventPair<?>> getCommonUsageInfo(@NotNull FinishType finishType,
                                                  @Nullable LookupElement currentItem,
                                                  char completionChar) {
      List<EventPair<?>> data = new ArrayList<>();
      // Basic info
      data.add(EventFields.Language.with(myLanguage));
      PsiFile file = myLookup.getPsiFile();
      if (file != null) {
        data.add(EventFields.CurrentFile.with(file.getLanguage()));
        VirtualFile vFile = file.getVirtualFile();
        if (vFile != null) {
          String schema = FileTypeUsageCounterCollector.findSchema(myLookup.getProject(), vFile);
          if (schema != null) {
            data.add(SCHEMA.with(schema));
          }
        }
      }
      data.add(ALPHABETICALLY.with(UISettings.getInstance().getSortLookupElementsLexicographically()));
      data.add(EDITOR_KIND.with(myLookup.getEditor().getEditorKind()));

      // Quality
      data.add(FINISH_TYPE.with(finishType));
      data.add(DURATION.with(System.currentTimeMillis() - myCreatedTimestamp));
      data.add(SELECTED_INDEX.with(myLookup.getSelectedIndex()));
      data.add(SELECTION_CHANGED.with(mySelectionChangedCount));
      data.add(TYPING.with(myTypingTracker.typing));
      data.add(BACKSPACES.with(myTypingTracker.backspaces));
      data.add(COMPLETION_CHAR.with(CompletionChar.of(completionChar)));

      // Details
      if (currentItem != null) {
        data.add(TOKEN_LENGTH.with(currentItem.getLookupString().length()));
        data.add(QUERY_LENGTH.with(myLookup.itemPattern(currentItem).length()));
        CompletionContributor contributor = currentItem.getUserData(BaseCompletionService.LOOKUP_ELEMENT_CONTRIBUTOR);
        if (contributor != null) {
          data.add(CONTRIBUTOR.with(contributor.getClass()));
        }
        var psiReference = currentItem.getUserData(LOOKUP_ELEMENT_PSI_REFERENCE);
        if (psiReference != null) {
          data.add(PSI_REFERENCE.with(psiReference.getClass()));
        }
      }

      // Performance
      data.add(TIME_TO_SHOW.with(myTimeToShow));

      convertTimestampToDuration(data, TIME_TO_SHOW_CORRECT_ELEMENT, myTimestampCorrectElementShown);
      convertTimestampToDuration(data, TIME_TO_SHOW_FIRST_ELEMENT, myTimestampFirstElementShown);
      convertTimestampToDuration(data, TIME_TO_COMPUTE_CORRECT_ELEMENT, myTimestampCorrectElementComputed);
      if (myOrderComputedCorrectElement != null) {
        data.add(ORDER_ADDED_CORRECT_ELEMENT.with(myOrderComputedCorrectElement));
      }

      // Indexing
      data.add(DUMB_START.with(myIsDumbStart));
      data.add(DUMB_FINISH.with(DumbService.isDumb(myLookup.getProject())));
      data.add(INCOMPLETE_DEPENDENCIES_MODE_ON_START.with(myIncompleteDependenciesStateStart));
      data.add(INCOMPLETE_DEPENDENCIES_MODE_ON_FINISH.with(myLookup.getProject().getService(IncompleteDependenciesService.class).getState()));

      // Quick doc
      data.add(QUICK_DOC_SHOWN.with(myIsQuickDocShown));
      data.add(QUICK_DOC_AUTO_SHOW.with(myIsQuickDocAutoShow));
      data.add(QUICK_DOC_SCROLLED.with(myIsQuickDocScrolled));

      return data;
    }

    private static @Nullable Language getLanguageAtCaret(@NotNull LookupImpl lookup) {
      PsiFile psiFile = lookup.getPsiFile();
      if (psiFile != null) {
        return PsiUtilCore.getLanguageAtOffset(psiFile, lookup.getEditor().getCaretModel().getOffset());
      }
      return null;
    }

    private static final class MyTypingTracker implements PrefixChangeListener {
      int backspaces = 0;
      int typing = 0;

      @Override
      public void beforeTruncate() {
        backspaces += 1;
      }

      @Override
      public void beforeAppend(char c) {
        typing += 1;
      }
    }
  }

  public enum FinishType {
    TYPED, EXPLICIT, CANCELED_EXPLICITLY, CANCELED_BY_TYPING
  }

  private enum CompletionChar {
    ENTER, TAB, COMPLETE_STATEMENT, AUTO_INSERT, OTHER;

    static CompletionChar of(char completionChar) {
      return switch (completionChar) {
        case Lookup.NORMAL_SELECT_CHAR -> ENTER;
        case Lookup.REPLACE_SELECT_CHAR -> TAB;
        case Lookup.AUTO_INSERT_SELECT_CHAR -> AUTO_INSERT;
        case Lookup.COMPLETE_STATEMENT_SELECT_CHAR -> COMPLETE_STATEMENT;
        default -> OTHER;
      };
    }
  }

  private static final class MyLookupResultDescriptor implements LookupResultDescriptor {
    private final Lookup myLookup;
    private final LookupElement mySelectedItem;
    private final FinishType myFinishType;
    private final Language myLanguage;

    private MyLookupResultDescriptor(Lookup lookup,
                                     LookupElement item,
                                     FinishType type,
                                     Language language) {
      myLookup = lookup;
      mySelectedItem = item;
      myFinishType = type;
      myLanguage = language;
    }

    @Override
    public @NotNull Lookup getLookup() {
      return myLookup;
    }

    @Override
    public @Nullable LookupElement getSelectedItem() {
      return mySelectedItem;
    }

    @Override
    public FinishType getFinishType() {
      return myFinishType;
    }

    @Override
    public @Nullable Language getLanguage() {
      return myLanguage;
    }
  }
}
