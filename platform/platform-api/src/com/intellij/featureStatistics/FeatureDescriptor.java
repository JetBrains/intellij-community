// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.featureStatistics;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ArrayUtilRt;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class FeatureDescriptor {
  @NotNull private String myId;
  @NotNull private String myDisplayName;
  @Nullable private String myGroupId;
  @Nullable private String myTipId;
  @Nullable private Set<String> myDependencies;
  private int myDaysBeforeFirstShowUp;
  private int myDaysBetweenSuccessiveShowUps;
  private int myMinUsageCount;
  private int myUtilityScore;  // should be from 1 to 5, required for tips sorting in Tips of the Day
  private boolean myNeedToBeShownInGuide = true;
  private final List<FeatureUsageEvent.Action> myActionEvents = new ArrayList<>();
  private final List<FeatureUsageEvent.Intention> myIntentionEvents = new ArrayList<>();

  private int myUsageCount;
  private long myLastTimeShown;
  private long myLastTimeUsed;
  private int myShownCount;
  @Nullable private final ProductivityFeaturesProvider myProvider;

  private static final Logger LOG = Logger.getInstance(FeatureDescriptor.class);
  @NonNls private static final String ATTRIBUTE_COUNT = "count";
  @NonNls private static final String ATTRIBUTE_LAST_SHOWN = "last-shown";
  @NonNls private static final String ATTRIBUTE_LAST_USED = "last-used";
  @NonNls private static final String ATTRIBUTE_SHOWN_COUNT = "shown-count";
  @NonNls private static final String ATTRIBUTE_ID = "id";
  @NonNls private static final String ATTRIBUTE_TIP_ID = "tip-id";
  @NonNls private static final String ATTRIBUTE_FIRST_SHOW = "first-show";
  @NonNls private static final String ATTRIBUTE_SUCCESSIVE_SHOW = "successive-show";
  @NonNls private static final String ATTRIBUTE_MIN_USAGE_COUNT = "min-usage-count";
  @NonNls private static final String ATTRIBUTE_UTILITY_SCORE = "utility-score";
  @NonNls private static final String ATTRIBUTE_SHOW_IN_GUIDE = "show-in-guide";
  @NonNls private static final String ATTRIBUTE_CLASS_NAME = "class-name";
  @NonNls private static final String ELEMENT_DEPENDENCY = "dependency";
  @NonNls private static final String ELEMENT_TRACK_ACTION = "track-action";
  @NonNls private static final String ELEMENT_TRACK_INTENTION = "track-intention";

  FeatureDescriptor(@NotNull GroupDescriptor group, @Nullable ProductivityFeaturesProvider provider, @NotNull Element featureElement) {
    myGroupId = group.getId();
    myProvider = provider;
    readExternal(featureElement);
  }

  public FeatureDescriptor(@NonNls @NotNull String id,
                           @NonNls @Nullable String groupId,
                           @NonNls @Nullable String tipId,
                           @NotNull String displayName,
                           int daysBeforeFirstShowUp,
                           int daysBetweenSuccessiveShowUps,
                           @Nullable Set<String> dependencies,
                           int minUsageCount,
                           @Nullable ProductivityFeaturesProvider provider) {
    this(id, groupId, tipId, displayName, daysBeforeFirstShowUp, daysBetweenSuccessiveShowUps, dependencies, minUsageCount, 3, provider);
  }

  public FeatureDescriptor(@NonNls @NotNull String id,
                           @NonNls @Nullable String groupId,
                           @NonNls @Nullable String tipId,
                           @NotNull String displayName,
                           int daysBeforeFirstShowUp,
                           int daysBetweenSuccessiveShowUps,
                           @Nullable Set<String> dependencies,
                           int minUsageCount,
                           int utilityScore,
                           @Nullable ProductivityFeaturesProvider provider) {
    myId = id;
    myGroupId = groupId;
    myTipId = tipId;
    myDisplayName = displayName;
    myDaysBeforeFirstShowUp = daysBeforeFirstShowUp;
    myDaysBetweenSuccessiveShowUps = daysBetweenSuccessiveShowUps;
    myDependencies = dependencies;
    myMinUsageCount = minUsageCount;
    myUtilityScore = utilityScore;
    myProvider = provider;
  }

  private void readExternal(Element element) {
    myId = Objects.requireNonNull(element.getAttributeValue(ATTRIBUTE_ID));
    myTipId = element.getAttributeValue(ATTRIBUTE_TIP_ID);
    myDisplayName = FeatureStatisticsBundle.message(myId);
    String needToBeShownInGuide = element.getAttributeValue(ATTRIBUTE_SHOW_IN_GUIDE);
    if (needToBeShownInGuide != null) {
      myNeedToBeShownInGuide = Boolean.parseBoolean(needToBeShownInGuide);
    }
    myDaysBeforeFirstShowUp = StringUtil.parseInt(element.getAttributeValue(ATTRIBUTE_FIRST_SHOW), 1);
    myDaysBetweenSuccessiveShowUps = StringUtil.parseInt(element.getAttributeValue(ATTRIBUTE_SUCCESSIVE_SHOW), 3);
    String minUsageCount = element.getAttributeValue(ATTRIBUTE_MIN_USAGE_COUNT);
    myMinUsageCount = minUsageCount == null ? 1 : Integer.parseInt(minUsageCount);
    myUtilityScore = StringUtil.parseInt(element.getAttributeValue(ATTRIBUTE_UTILITY_SCORE), 3);
    List<Element> actionEvents = element.getChildren(ELEMENT_TRACK_ACTION);
    for (Element actionElement : actionEvents) {
      @NonNls String actionId = actionElement.getAttributeValue(ATTRIBUTE_ID);
      if (actionId != null) {
        myActionEvents.add(FeatureUsageEvent.createActionEvent(myId, actionId));
      }
    }
    List<Element> intentionEvents = element.getChildren(ELEMENT_TRACK_INTENTION);
    for (Element intentionElement : intentionEvents) {
      @NonNls String intentionClassName = intentionElement.getAttributeValue(ATTRIBUTE_CLASS_NAME);
      if (intentionClassName != null) {
        myIntentionEvents.add(FeatureUsageEvent.createIntentionEvent(myId, intentionClassName));
      }
    }
    List<Element> dependencies = element.getChildren(ELEMENT_DEPENDENCY);
    if (!dependencies.isEmpty()) {
      myDependencies = new HashSet<>();
      for (Element dependencyElement : dependencies) {
        myDependencies.add(dependencyElement.getAttributeValue(ATTRIBUTE_ID));
      }
    }
  }

  @NotNull
  public String getId() {
    return myId;
  }

  public @Nullable String getGroupId() {
    return myGroupId;
  }

  /**
   * @deprecated Use {@code getTipId()} instead
   */
  @Deprecated
  public @Nullable String getTipFileName() {
    return myTipId;
  }

  public @Nullable String getTipId() {
    return myTipId;
  }

  public List<FeatureUsageEvent.Action> getActionEvents() {
    return myActionEvents;
  }

  public List<FeatureUsageEvent.Intention> getIntentionEvents() {
    return myIntentionEvents;
  }

  @NotNull
  public String getDisplayName() {
    return myDisplayName;
  }

  public boolean isNeedToBeShownInGuide() {
    return myNeedToBeShownInGuide;
  }

  public int getUsageCount() {
    return myUsageCount;
  }

  public Class<? extends ProductivityFeaturesProvider> getProvider() {
    if (myProvider == null) {
      return null;
    }
    return myProvider.getClass();
  }

  void triggerUsed() {
    myLastTimeUsed = System.currentTimeMillis();
    myUsageCount++;
  }

  public boolean isUnused() {
    return myUsageCount < myMinUsageCount;
  }

  public void adjustUsageInfo(int newUsageCount, long newLastTimeUsed) {
    myUsageCount = Math.max(myUsageCount, newUsageCount);
    myLastTimeUsed = Math.max(myLastTimeUsed, newLastTimeUsed);
  }

  public String toString() {

    return "id = [" +
           myId +
           "], displayName = [" +
           myDisplayName +
           "], groupId = [" +
           myGroupId +
           "], usageCount = [" +
           myUsageCount +
           "]";
  }

  public int getDaysBeforeFirstShowUp() {
    return myDaysBeforeFirstShowUp;
  }

  public int getDaysBetweenSuccessiveShowUps() {
    return myDaysBetweenSuccessiveShowUps;
  }

  public int getMinUsageCount() {
    return myMinUsageCount;
  }

  public int getUtilityScore() {
    return myUtilityScore;
  }

  public long getLastTimeShown() {
    return myLastTimeShown;
  }

  public String[] getDependencyFeatures() {
    if (myDependencies == null) return ArrayUtilRt.EMPTY_STRING_ARRAY;
    return ArrayUtilRt.toStringArray(myDependencies);
  }

  void triggerShown() {
    myLastTimeShown = System.currentTimeMillis();
    myShownCount++;
  }

  public long getLastTimeUsed() {
    return myLastTimeUsed;
  }

  public int getShownCount() {
    return myShownCount;
  }

  void copyStatistics(FeatureDescriptor statistics) {
    myUsageCount = statistics.getUsageCount();
    myLastTimeShown = statistics.getLastTimeShown();
    myLastTimeUsed = statistics.getLastTimeUsed();
    myShownCount = statistics.getShownCount();
  }

  void readStatistics(Element element) {
    String count = element.getAttributeValue(ATTRIBUTE_COUNT);
    String lastShown = element.getAttributeValue(ATTRIBUTE_LAST_SHOWN);
    String lastUsed = element.getAttributeValue(ATTRIBUTE_LAST_USED);
    String shownCount = element.getAttributeValue(ATTRIBUTE_SHOWN_COUNT);

    myUsageCount = count == null ? 0 : Integer.parseInt(count);
    myLastTimeShown = lastShown == null ? 0 : Long.parseLong(lastShown);
    myLastTimeUsed = lastUsed == null ? 0 : Long.parseLong(lastUsed);
    myShownCount = shownCount == null ? 0 : Integer.parseInt(shownCount);
  }

  void writeStatistics(Element element) {
    element.setAttribute(ATTRIBUTE_COUNT, String.valueOf(getUsageCount()));
    element.setAttribute(ATTRIBUTE_LAST_SHOWN, String.valueOf(getLastTimeShown()));
    element.setAttribute(ATTRIBUTE_LAST_USED, String.valueOf(getLastTimeUsed()));
    element.setAttribute(ATTRIBUTE_SHOWN_COUNT, String.valueOf(getShownCount()));
  }
}