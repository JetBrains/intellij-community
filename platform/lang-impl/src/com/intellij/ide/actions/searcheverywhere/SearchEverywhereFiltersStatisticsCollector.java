// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions.searcheverywhere;

import com.intellij.ide.util.ElementsChooser;
import com.intellij.ide.util.gotoByName.FileTypeRef;
import com.intellij.ide.util.gotoByName.LanguageRef;
import com.intellij.internal.statistic.collectors.fus.LangCustomRuleValidator;
import com.intellij.internal.statistic.collectors.fus.fileTypes.FileTypeUsagesCollector;
import com.intellij.internal.statistic.eventLog.EventLogGroup;
import com.intellij.internal.statistic.eventLog.events.EventFields;
import com.intellij.internal.statistic.eventLog.events.EventId1;
import com.intellij.internal.statistic.eventLog.events.EventId2;
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector;

final class SearchEverywhereFiltersStatisticsCollector extends CounterUsagesCollector {

  public enum QuickFilterButtons {
    ALL, NONE, INVERT
  }

  public enum FilterTypes {
    LANGUAGE, FILE_TYPE, CONTRIBUTORS
  }

  private static final EventLogGroup GROUP = new EventLogGroup("search.everywhere.filters", 2);

  private static final EventId2<String, Boolean> FILE_TYPE_CHANGED_EVENT = GROUP.registerEvent(
    "file.type.changed",
    EventFields.StringValidatedByCustomRule("fileType", FileTypeUsagesCollector.ValidationRule.class),
    EventFields.Boolean("enabled")
  );

  private static final EventId2<String, Boolean> LANG_CHANGED_EVENT = GROUP.registerEvent(
    "lang.changed",
    EventFields.StringValidatedByCustomRule("langID", LangCustomRuleValidator.class),
    EventFields.Boolean("enabled")
  );

  private static final EventId2<String, Boolean> CONTRIBUTOR_CHANGED_EVENT = GROUP.registerEvent(
    "contributor.changed",
    EventFields.StringValidatedByCustomRule("contributorID", SearchEverywhereContributorValidationRule.class),
    EventFields.Boolean("enabled")
  );

  private static final EventId1<QuickFilterButtons> BUTTON_PRESSED_EVENT =
    GROUP.registerEvent("quick.filter.button", EventFields.Enum("buttonName", QuickFilterButtons.class));

  @Override
  public EventLogGroup getGroup() {
    return GROUP;
  }

  public abstract static class BaseFilterStatisticsCollector<T> implements ElementsChooser.StatisticsCollector<T> {
    @Override
    public void selectionInverted() {
      BUTTON_PRESSED_EVENT.log(QuickFilterButtons.INVERT);
    }

    @Override
    public void allSelected() {
      BUTTON_PRESSED_EVENT.log(QuickFilterButtons.ALL);
    }

    @Override
    public void noneSelected() {
      BUTTON_PRESSED_EVENT.log(QuickFilterButtons.NONE);
    }
  }

  public static final class FileTypeFilterCollector extends BaseFilterStatisticsCollector<FileTypeRef> {
    @Override
    public void elementMarkChanged(FileTypeRef element, boolean isMarked) {
      FILE_TYPE_CHANGED_EVENT.log(element.getName(), isMarked);
    }
  }

  public static final class LangFilterCollector extends BaseFilterStatisticsCollector<LanguageRef> {
    @Override
    public void elementMarkChanged(LanguageRef element, boolean isMarked) {
      LANG_CHANGED_EVENT.log(element.getId(), isMarked);
    }
  }

  public static final class ContributorFilterCollector extends BaseFilterStatisticsCollector<String> {
    @Override
    public void elementMarkChanged(String contributorID, boolean isMarked) {
      CONTRIBUTOR_CHANGED_EVENT.log(contributorID, isMarked);
    }
  }
}
