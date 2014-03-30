/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

package com.intellij.execution.ui.layout.impl;

import com.intellij.execution.ui.layout.LayoutAttractionPolicy;
import com.intellij.execution.ui.layout.PlaceInGrid;
import com.intellij.execution.ui.layout.Tab;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.content.Content;
import com.intellij.util.xmlb.XmlSerializer;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.*;

public class RunnerLayout  {
  public static final Key<Integer> DEFAULT_INDEX = Key.create("RunnerLayoutDefaultIndex");
  public static final Key<Integer> DROP_INDEX = Key.create("RunnerLayoutDropIndex");
  private final String myID;

  protected Map<String, ViewImpl> myViews = new HashMap<String, ViewImpl>();
  private final Map<String, ViewImpl.Default> myDefaultViews = new HashMap<String, ViewImpl.Default>();

  protected Set<TabImpl> myTabs = new TreeSet<TabImpl>(new Comparator<TabImpl>() {
    @Override
    public int compare(final TabImpl o1, final TabImpl o2) {
      return o1.getIndex() - o2.getIndex();
    }
  });
  private final Map<Integer, TabImpl.Default> myDefaultTabs = new HashMap<Integer, TabImpl.Default>();

  protected General myGeneral = new General();
  private final Map<String, Pair<String, LayoutAttractionPolicy>> myDefaultFocus = new HashMap<String, Pair<String, LayoutAttractionPolicy>>();


  public RunnerLayout(@NotNull String ID) {
    myID = ID;
  }

  @Nullable
  public String getDefaultDisplayName(final int defaultIndex) {
    final TabImpl.Default tab = myDefaultTabs.get(defaultIndex);
    return tab != null ? tab.myDisplayName : null;
  }

  @NotNull
  public TabImpl getOrCreateTab(final int index) {
    TabImpl tab = findTab(index);
    if (tab != null) return tab;

    tab = createNewTab(index);

    return tab;
  }

  @NotNull
  private TabImpl createNewTab(final int index) {
    final TabImpl.Default defaultTab = getOrCreateDefaultTab(index);
    final TabImpl tab = defaultTab.createTab();

    myTabs.add(tab);

    return tab;
  }

  @NotNull
  private TabImpl.Default getOrCreateDefaultTab(final int index) {
    TabImpl.Default tab = myDefaultTabs.get(index);
    if (tab == null) {
      tab = new TabImpl.Default(index, null, null);
      myDefaultTabs.put(index, tab);
    }
    return tab;
  }

  @NotNull
  public TabImpl createNewTab() {
    int index = 0;
    for (TabImpl each : myTabs) {
      if (!isUsed(each)) return each;

      if (each.getIndex() < Integer.MAX_VALUE) {
        index = each.getIndex() + 1;
      }
      else {
        break;
      }
    }

    return createNewTab(index);
  }

  private boolean isUsed(@NotNull TabImpl tab) {
    for (ViewImpl each : myViews.values()) {
      if (each.getTab() == tab) return true;
    }

    return false;
  }

  @Nullable
  protected TabImpl findTab(int index) {
    for (TabImpl each : myTabs) {
      if (index == each.getIndex()) return each;
    }

    return null;
  }

  @NotNull
  public Element getState() {
    return write(new Element("layout"));
  }

  public void loadState(@NotNull Element state) {
    read(state);
  }

  @NotNull
  public Element read(@NotNull Element parentNode) {
    List tabs = parentNode.getChildren(StringUtil.getShortName(TabImpl.class.getName()));
    for (Object eachTabElement : tabs) {
      TabImpl eachTab = new TabImpl((Element)eachTabElement);
      getOrCreateTab(eachTab.getIndex()).read((Element)eachTabElement);
    }

    final List views = parentNode.getChildren(StringUtil.getShortName(ViewImpl.class.getName()));
    for (Object content : views) {
      final ViewImpl state = new ViewImpl(this, (Element)content);
      myViews.put(state.getID(), state);
    }

    XmlSerializer.deserializeInto(myGeneral, parentNode.getChild(StringUtil.getShortName(myGeneral.getClass().getName(), '$')));

    return parentNode;
  }

  @NotNull
  public Element write(@NotNull Element parentNode) {
    for (ViewImpl eachState : myViews.values()) {
      eachState.write(parentNode);
    }

    for (TabImpl eachTab : myTabs) {
      if (isUsed(eachTab)) {
        eachTab.write(parentNode);
      }
    }

    parentNode.addContent(XmlSerializer.serialize(myGeneral));

    return parentNode;
  }


  public void resetToDefault() {
    myViews.clear();
    myTabs.clear();
  }

  public boolean isToolbarHorizontal() {
    return false;
  }

  public void setToolbarHorizontal(boolean horizontal) {
    myGeneral.horizontalToolbar = horizontal;
  }

  @NotNull
  public ViewImpl getStateFor(@NotNull Content content) {
    return getOrCreateView(getOrCreateContentId(content));
  }

  public void clearStateFor(@NotNull Content content) {
    final ViewImpl view = myViews.remove(getOrCreateContentId(content));
    if (view != null) {
      final Tab tab = view.getTab();
      if (tab instanceof TabImpl) {
        myTabs.remove(tab);
      }
    }
  }

  @NotNull
  private static String getOrCreateContentId(@NotNull Content content) {
    @NonNls String id = content.getUserData(ViewImpl.ID);
    if (id == null) {
      id = "UnknownView-" + content.getDisplayName();
      content.putUserData(ViewImpl.ID, id);
    }
    return id;
  }

  @NotNull
  private ViewImpl getOrCreateView(@NotNull String id) {
    if (myViews.containsKey(id)) {
      return myViews.get(id);
    }
    final ViewImpl.Default defaultView = getOrCreateDefault(id);
    final ViewImpl view = defaultView.createView(this);
    myViews.put(id, view);
    return view;
  }

  @NotNull
  private ViewImpl.Default getOrCreateDefault(@NotNull String id) {
    if (myDefaultViews.containsKey(id)) {
      return myDefaultViews.get(id);
    }
    return setDefault(id, Integer.MAX_VALUE, PlaceInGrid.bottom, false);
  }


  @NotNull
  public TabImpl.Default setDefault(int tabID, String displayName, Icon icon) {
    final TabImpl.Default tab = new TabImpl.Default(tabID, displayName, icon);
    myDefaultTabs.put(tabID, tab);
    return tab;
  }

  @NotNull
  public ViewImpl.Default setDefault(@NotNull String id, int tabIndex, @NotNull PlaceInGrid placeInGrid, boolean isMinimized) {
    final ViewImpl.Default view = new ViewImpl.Default(id, tabIndex, placeInGrid, isMinimized);
    myDefaultViews.put(id, view);
    return view;
  }

  @NotNull
  public PlaceInGrid getDefaultGridPlace(@NotNull Content content) {
    return getOrCreateDefault(getOrCreateContentId(content)).getPlaceInGrid();
  }

  public boolean isToFocus(final String id, @NotNull String condition) {
    return Comparing.equal(id, getToFocus(condition));
  }

  public void setToFocus(final String id, @NotNull String condition) {
    myGeneral.focusOnCondition.put(condition, id);
  }

  public void setDefaultToFocus(@NotNull String id, @NotNull String condition, @NotNull final LayoutAttractionPolicy policy) {
    myDefaultFocus.put(condition, Pair.create(id, policy));
  }

  @Nullable
  public String getToFocus(@NotNull String condition) {
    return myGeneral.focusOnCondition.containsKey(condition) ? myGeneral.focusOnCondition.get(condition) :
           myDefaultFocus.containsKey(condition) ? myDefaultFocus.get(condition).getFirst() : null;
  }

  @NotNull
  public LayoutAttractionPolicy getAttractionPolicy(@NotNull String condition) {
    final Pair<String, LayoutAttractionPolicy> pair = myDefaultFocus.get(condition);
    return pair == null ? new LayoutAttractionPolicy.FocusOnce() : pair.getSecond();
  }

  public static class General {
    public volatile boolean horizontalToolbar = false;
    public volatile Map<String, String> focusOnCondition = new HashMap<String, String>();
  }
}
