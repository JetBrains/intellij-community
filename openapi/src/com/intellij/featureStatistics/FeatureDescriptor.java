/*
 * Copyright 2000-2005 JetBrains s.r.o.
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
package com.intellij.featureStatistics;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.util.ArrayUtil;
import org.jdom.Element;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
public class FeatureDescriptor{
  private String myId;
  private String myGroupId;
  private String myTipFileName;
  private String myDisplayName;
  private int myDaysBeforeFirstShowUp;
  private int myDaysBetweenSuccesiveShowUps;
  private Set<String> myDependencies;
  private int myMinUsageCount;

  private int myUsageCount;
  private long myLastTimeShown;
  private long myLastTimeUsed;
  private long myAverageFrequency;
  private int myShownCount;
  private ProductivityFeaturesProvider myProvider;

  FeatureDescriptor(GroupDescriptor group) {
    myGroupId = group.getId();
  }

  FeatureDescriptor(final String id) {
    myId = id;
  }

  FeatureDescriptor(String id, String tipFileName, String displayName) {
    myId = id;
    myTipFileName = tipFileName;
    myDisplayName = displayName;
  }

  public FeatureDescriptor(String id,
                       String groupId,
                       String tipFileName,
                       String displayName,
                       int daysBeforeFirstShowUp,
                       int daysBetweenSuccesiveShowUps,
                       Set<String> dependencies,
                       int minUsageCount,
                       ProductivityFeaturesProvider provider) {
    myId = id;
    myGroupId = groupId;
    myTipFileName = tipFileName;
    myDisplayName = displayName;
    myDaysBeforeFirstShowUp = daysBeforeFirstShowUp;
    myDaysBetweenSuccesiveShowUps = daysBetweenSuccesiveShowUps;
    myDependencies = dependencies;
    myMinUsageCount = minUsageCount;
    myProvider = provider;
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  void readExternal(Element element) {
    myId = element.getAttributeValue("id");
    myTipFileName = element.getAttributeValue("tip-file");
    myDisplayName = element.getAttributeValue("name");
    myDaysBeforeFirstShowUp = Integer.parseInt(element.getAttributeValue("first-show"));
    myDaysBetweenSuccesiveShowUps = Integer.parseInt(element.getAttributeValue("successive-show"));
    String minUsageCount = element.getAttributeValue("min-usage-count");
    myMinUsageCount = minUsageCount == null ? 1 : Integer.parseInt(minUsageCount);
    List depenencies = element.getChildren("dependency");
    if (depenencies != null && depenencies.size() > 0) {
      myDependencies = new HashSet<String>();
      for (int i = 0; i < depenencies.size(); i++) {
        Element dependencyElement = (Element)depenencies.get(i);
        myDependencies.add(dependencyElement.getAttributeValue("id"));
      }
    }
  }

  public String getId() {
    return myId;
  }

  public String getGroupId() {
    return myGroupId;
  }

  public String getTipFileName() {
    return myTipFileName;
  }

  public String getDisplayName() {
    return myDisplayName;
  }

  public int getUsageCount() {
    return myUsageCount;
  }

  public Class<? extends ProductivityFeaturesProvider> getProvider() {
    if (myProvider == null){
      return null;
    }
    return myProvider.getClass();
  }

  void triggerUsed() {
    long current = System.currentTimeMillis();
    myAverageFrequency *= myUsageCount;
    long delta = myUsageCount > 0 ? current - Math.max(myLastTimeUsed, ApplicationManager.getApplication().getStartTime()) : 0;
    myLastTimeUsed = current;
    myUsageCount++;
    myAverageFrequency += delta;
    myAverageFrequency /= myUsageCount;
  }

  public boolean isUnused() {
    return myUsageCount < myMinUsageCount;
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  public String toString() {
    StringBuffer buffer = new StringBuffer();

    buffer.append("id = [");
    buffer.append(myId);
    buffer.append("], displayName = [");
    buffer.append(myDisplayName);
    buffer.append("], groupId = [");
    buffer.append(myGroupId);
    buffer.append("], usageCount = [");
    buffer.append(myUsageCount);
    buffer.append("]");

    return buffer.toString();
  }

  public int getDaysBeforeFirstShowUp() {
    return myDaysBeforeFirstShowUp;
  }

  public int getDaysBetweenSuccesiveShowUps() {
    return myDaysBetweenSuccesiveShowUps;
  }

  public int getMinUsageCount() {
    return myMinUsageCount;
  }

  public long getLastTimeShown() {
    return myLastTimeShown;
  }

  public String[] getDependencyFeatures() {
    if (myDependencies == null) return ArrayUtil.EMPTY_STRING_ARRAY;
    return myDependencies.toArray(new String[myDependencies.size()]);
  }

  void triggerShown() {
    myLastTimeShown = System.currentTimeMillis();
    myShownCount++;
  }

  public long getLastTimeUsed() {
    return myLastTimeUsed;
  }

  public long getAverageFrequency() {
    return myAverageFrequency;
  }

  public int getShownCount() {
    return myShownCount;
  }

  void copyStatistics(FeatureDescriptor statistics){
    myUsageCount = statistics.getUsageCount();
    myLastTimeShown = statistics.getLastTimeShown();
    myLastTimeUsed = statistics.getLastTimeUsed();
    myAverageFrequency = statistics.getAverageFrequency();
    myShownCount = statistics.getShownCount();
  }

  void readStatistics(Element element) {
    String count = element.getAttributeValue("count");
    String lastShown = element.getAttributeValue("last-shown");
    String lastUsed = element.getAttributeValue("last-used");
    String averageFrequency = element.getAttributeValue("average-frequency");
    String shownCount = element.getAttributeValue("shown-count");

    myUsageCount = count == null ? 0 : Integer.parseInt(count);
    myLastTimeShown = lastShown == null ? 0 : Long.parseLong(lastShown);
    myLastTimeUsed = lastUsed == null ? 0 : Long.parseLong(lastUsed);
    myAverageFrequency = averageFrequency == null ? 0 : Long.parseLong(averageFrequency);
    myShownCount = shownCount == null ? 0 : Integer.parseInt(shownCount);
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  void writeStatistics(Element element) {
    element.setAttribute("count", String.valueOf(getUsageCount()));
    element.setAttribute("last-shown", String.valueOf(getLastTimeShown()));
    element.setAttribute("last-used", String.valueOf(getLastTimeUsed()));
    element.setAttribute("average-frequency", String.valueOf(getAverageFrequency()));
    element.setAttribute("shown-count", String.valueOf(getShownCount()));
  }
}