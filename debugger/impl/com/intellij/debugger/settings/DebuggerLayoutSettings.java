package com.intellij.debugger.settings;

import com.intellij.debugger.ui.DebuggerContentInfo;
import com.intellij.debugger.ui.content.newUI.NewContentState;
import com.intellij.debugger.ui.content.newUI.PlaceInGrid;
import com.intellij.debugger.ui.content.newUI.Tab;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.util.Key;
import com.intellij.ui.content.Content;
import com.intellij.util.xmlb.XmlSerializer;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

@State(
  name = "DebuggerLayoutSettings",
  storages = {
    @Storage(
      id ="other",
      file = "$APP_CONFIG$/debugger.layout.xml"
    )}
)
public class DebuggerLayoutSettings implements PersistentStateComponent<Element>, ApplicationComponent {

  private static final String VIEW_STATE = "viewState";

  private Map<String, NewContentState> myContentStates = new HashMap<String, NewContentState>();
  private Set<Tab> myTabs = new HashSet<Tab>();

  private General myGeneral = new General();

  public Element getState() {
    return write(new Element("layout"));
  }

  public void loadState(final Element state) {
    read(state);
  }

  public Element read(final Element parentNode)  {
    final List newContents = parentNode.getChildren(VIEW_STATE);
    myContentStates.clear();
    for (Object content : newContents) {
      final NewContentState state = new NewContentState(this, (Element)content);
      myContentStates.put(state.getID(), state);
    }

    List tabs = parentNode.getChildren(Tab.TAB);
    for (Object eachTabElement : tabs) {
      Tab eachTab = new Tab((Element)eachTabElement);
      getOrCreateTab(eachTab.getIndex()).read((Element)eachTabElement);
    }

    XmlSerializer.deserializeInto(myGeneral, parentNode);

    return parentNode;
  }

  public Element write(final Element parentNode) {
    for (NewContentState eachState : myContentStates.values()) {
      final Element content = new Element(VIEW_STATE);
      parentNode.addContent(content);
      eachState.write(content);
    }

    for (Tab eachTab : myTabs) {
      eachTab.write(parentNode);
    }

    parentNode.addContent(XmlSerializer.serialize(myGeneral));

    return parentNode;
  }

  @NotNull
  public Tab getOrCreateTab(final int index) {
    Tab tab = findTab(index);
    if (tab != null) return tab;

    tab = new Tab(index, index == 0 ? "Debugger" : null, null);
    myTabs.add(tab);

    return tab;
  }

  @Nullable
  private Tab findTab(int index) {
    for (Tab each : myTabs) {
      if (index == each.getIndex()) return each;
    }

    return null;
  }

  public NewContentState getStateFor(Content content) {
    Key key = content.getUserData(DebuggerContentInfo.CONTENT_ID);

    assert key != null : "Content for debugger UI must be specified with: " + DebuggerContentInfo.CONSOLE_CONTENT;

    NewContentState state = myContentStates.get(key.toString());
    return state != null ? state : getDefaultContentState(content);
  }

  private NewContentState getDefaultContentState(final Content content) {
    NewContentState state;

    final Key kind = content.getUserData(DebuggerContentInfo.CONTENT_ID);
    if (DebuggerContentInfo.FRAME_CONTENT.equals(kind)) {
      state =  new NewContentState(kind.toString(), getOrCreateTab(0), PlaceInGrid.left, false);
    } else if (DebuggerContentInfo.VARIABLES_CONTENT.equals(kind)) {
      state =  new NewContentState(kind.toString(), getOrCreateTab(0), PlaceInGrid.center, false);
    } else if (DebuggerContentInfo.WATCHES_CONTENT.equals(kind)) {
      state =  new NewContentState(kind.toString(), getOrCreateTab(0), PlaceInGrid.right, false);
    } else if (DebuggerContentInfo.CONSOLE_CONTENT.equals(kind)) {
      state =  new NewContentState(kind.toString(), getOrCreateTab(1), PlaceInGrid.bottom, false);
    } else {
      state =  new NewContentState(kind.toString(), getOrCreateTab(Integer.MAX_VALUE), PlaceInGrid.bottom, false);
    }

    myContentStates.put(state.getID(), state);

    return state;
  }

  public int getDefaultSelectedTabIndex() {
    return 0;
  }

  public void setSelectedTabIndex(int index) {
    myGeneral.selectedTab = index;
  }

  @NonNls
  @NotNull
  public String getComponentName() {
    return "DebuggerLayoutSettings";
  }

  public void initComponent() {
  }

  public void disposeComponent() {
  }

  public void resetToDefault() {
    myContentStates.clear();
    myTabs.clear();
  }

  public boolean isToolbarHorizontal() {
    return myGeneral.horizontalToolbar;
  }

  public void setToolbarHorizontal(boolean horizontal) {
    myGeneral.horizontalToolbar = horizontal;
  }

  public static class General {
    public volatile boolean horizontalToolbar = false;
    public volatile int selectedTab = 0;
  }

}
