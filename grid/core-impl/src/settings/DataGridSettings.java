package com.intellij.database.settings;

import com.intellij.openapi.util.ModificationTracker;
import com.intellij.util.messages.Topic;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.ZoneId;
import java.util.List;

public interface DataGridSettings {
  int DEFAULT_PAGE_SIZE = 500;
  Topic<Listener> TOPIC = Topic.create("Data Grid settings", Listener.class);

  void setEnablePagingInInEditorResultsByDefault(boolean enablePagingInInEditorResultsByDefault);

  boolean isEnablePagingInInEditorResultsByDefault();

  boolean isDetectTextInBinaryColumns();

  boolean isDetectUUIDInBinaryColumns();

  boolean isAddToSortViaAltClick();

  void setAddToSortViaAltClick(boolean value);

  void setAutoTransposeMode(@NotNull AutoTransposeMode autoTransposeMode);

  @NotNull AutoTransposeMode getAutoTransposeMode();

  void setEnableLocalFilterByDefault(boolean enableLocalFilterByDefault);

  boolean isEnableLocalFilterByDefault();

  boolean isDisableGridFloatingToolbar();

  void setDisableGridFloatingToolbar(boolean disableGridFloatingToolbar);

  @NotNull PagingDisplayMode getPagingDisplayMode();

  void setPagingDisplayMode(@NotNull PagingDisplayMode pagingDisplayMode);

  boolean isEnableImmediateCompletionInGridCells();

  void setEnableImmediateCompletionInGridCells(boolean enableImmediateCompletionInGridCells);

  int getBytesLimitPerValue();

  int getFiltersHistorySize();

  void setBytesLimitPerValue(int value);

  @NotNull List<String> getDisabledAggregators();

  void setDisabledAggregators(@NotNull List<String> aggregators);

  String getWidgetAggregator();

  void setWidgetAggregator(String aggregator);

  boolean isNumberGroupingEnabled();

  char getNumberGroupingSeparator();

  char getDecimalSeparator();

  @NotNull
  String getInfinity();

  @NotNull
  String getNan();

  @Nullable
  String getEffectiveNumberPattern();

  @Nullable
  String getEffectiveDateTimePattern();

  @Nullable
  String getEffectiveZonedDateTimePattern();

  @Nullable
  String getEffectiveTimePattern();

  @Nullable
  String getEffectiveZonedTimePattern();

  @Nullable
  String getEffectiveDatePattern();

  @Nullable
  ZoneId getEffectiveZoneId();

  void fireChanged();

  void setPageSize(int value);

  int getPageSize();

  boolean isLimitPageSize();

  enum AutoTransposeMode {
    NEVER,
    ONE_ROW,
    ALWAYS
  }

  enum PagingDisplayMode {
    DATA_EDITOR_TOOLBAR,
    GRID_CENTER_FLOATING,
    GRID_LEFT_FLOATING,
    GRID_RIGHT_FLOATING
  }

  interface Listener {
    void settingsChanged();
  }

  @NotNull ModificationTracker getModificationTracker();

  default boolean isOpeningOfHttpsLinksAllowed() {
    return false;
  }

  default void setIsOpeningOfHttpsLinksAllowed(boolean value) { }

  default boolean isOpeningOfHttpLinksAllowed() {
    return false;
  }

  default void setIsOpeningOfHttpLinksAllowed(boolean value) { }

  default boolean isOpeningOfLocalFileUrlsAllowed() {
    return false;
  }

  default void setIsOpeningOfLocalFileUrlsAllowed(boolean value) { }

  default boolean isWebUrlWithoutProtocolAssumedHttp() {
    return false;
  }

  default void setIsWebUrlWithoutProtocolAssumedHttp(boolean value) { }

  default boolean isFloatingToolbarCustomizable() { return true; }

  default void setFloatingToolbarCustomizable(boolean value) { }
}
