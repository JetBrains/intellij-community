// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.ui.layout.impl;

import com.intellij.configurationStore.XmlSerializer;
import com.intellij.execution.ui.layout.LayoutAttractionPolicy;
import com.intellij.execution.ui.layout.PlaceInGrid;
import com.intellij.execution.ui.layout.Tab;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.content.Content;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import org.jdom.Element;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.*;

public final class RunnerLayout  {
  public static final Key<Integer> DEFAULT_INDEX = Key.create("RunnerLayoutDefaultIndex");
  public static final Key<Integer> DROP_INDEX = Key.create("RunnerLayoutDropIndex");

  private final Map<String, ViewImpl> myViews = new LinkedHashMap<>();
  private final Map<String, ViewImpl.Default> myDefaultViews = new HashMap<>();

  private final Set<TabImpl> myTabs = new TreeSet<>(Comparator.comparingInt(TabImpl::getIndex));
  private final Int2ObjectMap<TabImpl.Default> myDefaultTabs = new Int2ObjectOpenHashMap<>();

  private final General myGeneral = new General();
  private final Map<String, Pair<String, LayoutAttractionPolicy>> myDefaultFocus = new HashMap<>();
  private Set<String> myLightWeightIds = null;

  public @Nullable String getDefaultDisplayName(final int defaultIndex) {
    final TabImpl.Default tab = myDefaultTabs.get(defaultIndex);
    return tab != null ? tab.myDisplayName : null;
  }

  public @Nullable Icon getDefaultIcon(int defaultIndex) {
    TabImpl.Default tab = myDefaultTabs.get(defaultIndex);
    return tab != null ? tab.myIcon : null;
  }

  public int getDefaultTabIndex(String contentId) {
    ViewImpl.Default viewDefault = myDefaultViews.get(contentId);
    return viewDefault != null ? viewDefault.getTabID() : -1;
  }

  public @Nullable PlaceInGrid getDefaultPlaceInGrid(String contentId) {
    ViewImpl.Default viewDefault = myDefaultViews.get(contentId);
    return viewDefault != null ? viewDefault.getPlaceInGrid() : null;
  }

  public boolean getDefaultIsMinimized(String contentId) {
    ViewImpl.Default viewDefault = myDefaultViews.get(contentId);
    return viewDefault != null && viewDefault.isMinimizedInGrid();
  }

  @ApiStatus.Internal
  public @NotNull TabImpl getOrCreateTab(final int index) {
    if (index < 0) {
      return createNewTab();
    }

    TabImpl tab = findTab(index);
    if (tab != null) return tab;

    tab = createNewTab(index);

    return tab;
  }

  private @NotNull TabImpl createNewTab(final int index) {
    final TabImpl.Default defaultTab = getOrCreateDefaultTab(index);
    final TabImpl tab = defaultTab.createTab();

    myTabs.add(tab);

    return tab;
  }

  private @NotNull TabImpl.Default getOrCreateDefaultTab(final int index) {
    TabImpl.Default tab = myDefaultTabs.get(index);
    if (tab == null) {
      tab = new TabImpl.Default(index, null, null);
      myDefaultTabs.put(index, tab);
    }
    return tab;
  }

  @ApiStatus.Internal
  public @NotNull TabImpl createNewTab() {
    final int index = myTabs.stream().mapToInt(x -> x.getIndex()).max().orElse(-1) + 1;
    return createNewTab(index);
  }

  private boolean isUsed(@NotNull TabImpl tab) {
    for (ViewImpl each : myViews.values()) {
      if (each.getTab() == tab) return true;
    }

    return false;
  }

  private @Nullable TabImpl findTab(int index) {
    for (TabImpl each : myTabs) {
      if (index == each.getIndex()) return each;
    }

    return null;
  }

  public @NotNull Element getState() {
    return write(new Element("layout"));
  }

  public void loadState(@NotNull Element state) {
    read(state);
  }

  public @NotNull Element read(@NotNull Element parentNode) {
    List<Element> tabs = parentNode.getChildren(StringUtil.getShortName(TabImpl.class.getName()));
    for (Element eachTabElement : tabs) {
      TabImpl eachTab = XmlSerializer.deserialize(eachTabElement, TabImpl.class);
      XmlSerializer.deserializeInto(eachTabElement, getOrCreateTab(eachTab.getIndex()));
    }

    final List<Element> views = parentNode.getChildren(StringUtil.getShortName(ViewImpl.class.getName()));
    for (Element content : views) {
      final ViewImpl view = new ViewImpl();
      XmlSerializer.deserializeInto(content, view);
      view.assignTab(getOrCreateTab(view.getTabIndex()));
      myViews.put(view.getID(), view);
    }

    Element general = parentNode.getChild(StringUtil.getShortName(myGeneral.getClass().getName(), '$'));
    XmlSerializer.deserializeInto(general == null ? new Element("state") : general, myGeneral);
    return parentNode;
  }

  public @NotNull Element write(@NotNull Element parentNode) {
    for (ViewImpl eachState : myViews.values()) {
      if (myLightWeightIds != null && myLightWeightIds.contains(eachState.getID())) {
        continue;
      }

      Element element = XmlSerializer.serialize(eachState);
      parentNode.addContent(element == null ? new Element("ViewImpl") : element);
    }

    for (TabImpl eachTab : myTabs) {
      if (isUsed(eachTab)) {
        Element element = XmlSerializer.serialize(eachTab);
        parentNode.addContent(element == null ? new Element("TabImpl") : element);
      }
    }

    Element generalContent = XmlSerializer.serialize(myGeneral);
    if (generalContent != null) {
      parentNode.addContent(generalContent);
    }
    return parentNode;
  }

  public void resetToDefault() {
    myViews.clear();
    myTabs.clear();
  }

  public @NotNull ViewImpl getStateFor(@NotNull Content content) {
    return getOrCreateView(getOrCreateContentId(content));
  }

  public void clearStateFor(@NotNull Content content) {
    String id = getOrCreateContentId(content);
    clearStateForId(id);
  }

  public void clearStateForId(@NotNull String id) {
    myDefaultViews.remove(id);
    final ViewImpl view = myViews.remove(id);
    if (view != null) {
      final Tab tab = view.getTab();
      if (tab instanceof TabImpl) {
        myTabs.remove(tab);
      }
    }
  }

  private static @NotNull String getOrCreateContentId(@NotNull Content content) {
    @NonNls String id = content.getUserData(ViewImpl.ID);
    if (id == null) {
      id = "UnknownView-" + content.getDisplayName();
      content.putUserData(ViewImpl.ID, id);
    }
    return id;
  }

  private @NotNull ViewImpl getOrCreateView(@NotNull String id) {
    ViewImpl view = myViews.get(id);
    if (view == null) {
      view = getOrCreateDefault(id).createView(this);
      myViews.put(id, view);
    }
    return view;
  }

  public @Nullable ViewImpl getViewById(@NotNull String id) {
    return myViews.get(id);
  }

  private @NotNull ViewImpl.Default getOrCreateDefault(@NotNull String id) {
    if (myDefaultViews.containsKey(id)) {
      return myDefaultViews.get(id);
    }
    return setDefault(id, Integer.MAX_VALUE, PlaceInGrid.bottom, false);
  }

  @ApiStatus.Internal
  public @NotNull TabImpl.Default setDefault(int tabID, String displayName, Icon icon) {
    final TabImpl.Default tab = new TabImpl.Default(tabID, displayName, icon);
    myDefaultTabs.put(tabID, tab);
    return tab;
  }

  @ApiStatus.Internal
  public @NotNull ViewImpl.Default setDefault(@NotNull String id, int tabIndex, @NotNull PlaceInGrid placeInGrid, boolean isMinimized) {
    final ViewImpl.Default view = new ViewImpl.Default(id, tabIndex, placeInGrid, isMinimized);
    myDefaultViews.put(id, view);
    return view;
  }

  public @NotNull PlaceInGrid getDefaultGridPlace(@NotNull Content content) {
    return getOrCreateDefault(getOrCreateContentId(content)).getPlaceInGrid();
  }

  public boolean isToFocus(final String id, @NotNull String condition) {
    return Objects.equals(id, getToFocus(condition));
  }

  public void setToFocus(final String id, @NotNull String condition) {
    myGeneral.focusOnCondition.put(condition, id);
  }

  public void setDefaultToFocus(@NotNull String id, @NotNull String condition, final @NotNull LayoutAttractionPolicy policy) {
    myDefaultFocus.put(condition, Pair.create(id, policy));
  }

  void cancelDefaultFocusBy(@NotNull String condition) {
    myDefaultFocus.remove(condition);
  }

  public @Nullable String getToFocus(@NotNull String condition) {
    return myGeneral.focusOnCondition.containsKey(condition) ? myGeneral.focusOnCondition.get(condition) :
           myDefaultFocus.containsKey(condition) ? myDefaultFocus.get(condition).getFirst() : null;
  }

  public @NotNull LayoutAttractionPolicy getAttractionPolicy(@NotNull String condition) {
    final Pair<String, LayoutAttractionPolicy> pair = myDefaultFocus.get(condition);
    return pair == null ? new LayoutAttractionPolicy.FocusOnce() : pair.getSecond();
  }

  /**
   * States of contents marked as "lightweight" won't be persisted
   */
  public void setLightWeight(Content content) {
    if (myLightWeightIds == null) {
      myLightWeightIds = new HashSet<>();
    }
    myLightWeightIds.add(getOrCreateContentId(content));
  }

  public boolean isTabLabelsHidden() {
    return myGeneral.isTabLabelsHidden;
  }

  public void setTabLabelsHidden(boolean tabLabelsHidden) {
    myGeneral.isTabLabelsHidden = tabLabelsHidden;
  }

  public static final class General {
    public volatile boolean horizontalToolbar = false;
    public volatile Map<String, String> focusOnCondition = new HashMap<>();
    public volatile boolean isTabLabelsHidden = true;
  }
}
