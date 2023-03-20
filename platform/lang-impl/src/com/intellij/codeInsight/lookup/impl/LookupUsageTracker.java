// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.lookup.impl;

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
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiUtilCore;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

import static com.intellij.codeInsight.completion.BaseCompletionService.LOOKUP_ELEMENT_RESULT_ADD_TIMESTAMP_MILLIS;
import static com.intellij.codeInsight.completion.BaseCompletionService.LOOKUP_ELEMENT_RESULT_SET_ORDER;
import static com.intellij.codeInsight.lookup.LookupElement.LOOKUP_ELEMENT_SHOW_TIMESTAMP_MILLIS;

public final class LookupUsageTracker extends CounterUsagesCollector {
  public static final String FINISHED_EVENT_ID = "finished";
  public static final String GROUP_ID = "completion";
  public static final EventLogGroup GROUP = new EventLogGroup(GROUP_ID, 13);
  private static final EventField<String> SCHEMA = EventFields.StringValidatedByCustomRule("schema", FileTypeSchemaValidator.class);
  private static final BooleanEventField ALPHABETICALLY = EventFields.Boolean("alphabetically");
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
  private static final LongEventField TIME_TO_SHOW = EventFields.Long("time_to_show");
  private static final LongEventField TIME_TO_SHOW_CORRECT_ELEMENT = EventFields.Long("time_to_show_correct_element");
  private static final LongEventField TIME_TO_SHOW_FIRST_ELEMENT = EventFields.Long("time_to_show_first_element");
  private static final LongEventField TIME_TO_COMPUTE_CORRECT_ELEMENT = EventFields.Long("time_to_compute_correct_element");
  private static final IntEventField ORDER_ADDED_CORRECT_ELEMENT = EventFields.Int("order_added_correct_element");
  private static final BooleanEventField DUMB_FINISH = EventFields.Boolean("dumb_finish");
  private static final BooleanEventField DUMB_START = EventFields.Boolean("dumb_start");
  public static final ObjectEventField ADDITIONAL = EventFields.createAdditionalDataField(GROUP.getId(), FINISHED_EVENT_ID);
  public static final VarargEventId FINISHED = GROUP.registerVarargEvent(FINISHED_EVENT_ID,
                                                                         EventFields.Language,
                                                                         EventFields.CurrentFile,
                                                                         SCHEMA,
                                                                         ALPHABETICALLY,
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
                                                                         TIME_TO_SHOW,
                                                                         TIME_TO_SHOW_CORRECT_ELEMENT,
                                                                         TIME_TO_SHOW_FIRST_ELEMENT,
                                                                         TIME_TO_COMPUTE_CORRECT_ELEMENT,
                                                                         ORDER_ADDED_CORRECT_ELEMENT,
                                                                         DUMB_FINISH,
                                                                         DUMB_START,
                                                                         ADDITIONAL);

  private LookupUsageTracker() {
  }

  static void trackLookup(long createdTimestamp, @NotNull LookupImpl lookup) {
    lookup.addLookupListener(new MyLookupTracker(createdTimestamp, lookup));
  }

  @Override
  public EventLogGroup getGroup() {
    return GROUP;
  }

  private static class MyLookupTracker implements LookupListener {
    private final @NotNull LookupImpl myLookup;
    private final long myCreatedTimestamp;
    private final long myTimeToShow;
    private @Nullable Long myTimestampFirstElementShown = null;
    private @Nullable Long myTimestampCorrectElementShown = null;
    private @Nullable Long myTimestampCorrectElementComputed = null;
    private @Nullable Integer myOrderComputedCorrectElement = null;
    private final boolean myIsDumbStart;
    private final @Nullable Language myLanguage;
    private final @NotNull MyTypingTracker myTypingTracker;

    private int mySelectionChangedCount = 0;


    MyLookupTracker(long createdTimestamp, @NotNull LookupImpl lookup) {
      myLookup = lookup;
      myCreatedTimestamp = createdTimestamp;
      myTimeToShow = System.currentTimeMillis() - createdTimestamp;
      myIsDumbStart = DumbService.isDumb(lookup.getProject());
      myLanguage = getLanguageAtCaret(lookup);
      myTypingTracker = new MyTypingTracker();
      lookup.addPrefixChangeListener(myTypingTracker, lookup);
    }

    @Override
    public void firstElementShown() {
      myTimestampFirstElementShown = System.currentTimeMillis();
    }

    @Override
    public void currentItemChanged(@NotNull LookupEvent event) {
      mySelectionChangedCount += 1;
    }

    private boolean isSelectedByTyping(@NotNull LookupElement item) {
      if (myLookup.itemPattern(item).equals(item.getLookupString())) {
        return true;
      }
      return false;
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
        if (isSelectedByTyping(item)) {
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
      if (item != null && isSelectedByTyping(item)) {
        triggerLookupUsed(FinishType.TYPED, item, event.getCompletionChar());
      }
      else {
        FinishType detailedCancelType = event.isCanceledExplicitly() ? FinishType.CANCELED_EXPLICITLY : FinishType.CANCELED_BY_TYPING;
        triggerLookupUsed(detailedCancelType, null, event.getCompletionChar());
      }
    }

    private void triggerLookupUsed(@NotNull FinishType finishType, @Nullable LookupElement currentItem,
                                   char completionChar) {
      final List<EventPair<?>> data = getCommonUsageInfo(finishType, currentItem, completionChar);

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
      return data;
    }

    @Nullable
    private static Language getLanguageAtCaret(@NotNull LookupImpl lookup) {
      PsiFile psiFile = lookup.getPsiFile();
      if (psiFile != null) {
        return PsiUtilCore.getLanguageAtOffset(psiFile, lookup.getEditor().getCaretModel().getOffset());
      }
      return null;
    }

    private static class MyTypingTracker implements PrefixChangeListener {
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

  private static class MyLookupResultDescriptor implements LookupResultDescriptor {
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
