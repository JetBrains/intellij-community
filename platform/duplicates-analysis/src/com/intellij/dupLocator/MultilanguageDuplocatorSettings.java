package com.intellij.dupLocator;

import com.intellij.lang.Language;
import com.intellij.openapi.components.*;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.util.xmlb.SkipDefaultValuesSerializationFilters;
import com.intellij.util.xmlb.XmlSerializer;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.TreeMap;

/**
 * @author Eugene.Kudelevsky
 */
@State(
  name = "MultiLanguageDuplocatorSettings",
  storages = @Storage(file = StoragePathMacros.APP_CONFIG + "/duplocatorSettings.xml")
)
public class MultilanguageDuplocatorSettings implements PersistentStateComponent<Element> {
  private final Map<String, ExternalizableDuplocatorState> mySettingsMap = new TreeMap<String, ExternalizableDuplocatorState>();

  public static MultilanguageDuplocatorSettings getInstance() {
    return ServiceManager.getService(MultilanguageDuplocatorSettings.class);
  }

  public void registerState(@NotNull Language language, @NotNull ExternalizableDuplocatorState state) {
    synchronized (mySettingsMap) {
      mySettingsMap.put(language.getDisplayName(), state);
    }
  }

  public ExternalizableDuplocatorState getState(@NotNull Language language) {
    synchronized (mySettingsMap) {
      return mySettingsMap.get(language.getDisplayName());
    }
  }

  @Override
  public Element getState() {
    synchronized (mySettingsMap) {
      Element state = new Element("state");
      if (mySettingsMap.isEmpty()) {
        return state;
      }

      SkipDefaultValuesSerializationFilters filter = new SkipDefaultValuesSerializationFilters();
      for (String name : mySettingsMap.keySet()) {
        Element child = XmlSerializer.serialize(mySettingsMap.get(name), filter);
        if (!JDOMUtil.isEmpty(child)) {
          child.setName("object");
          child.setAttribute("language", name);
          state.addContent(child);
        }
      }
      return state;
    }
  }

  @Override
  public void loadState(Element state) {
    synchronized (mySettingsMap) {
      if (state == null) {
        return;
      }

      for (Element objectElement : state.getChildren("object")) {
        String language = objectElement.getAttributeValue("language");
        if (language != null) {
          ExternalizableDuplocatorState stateObject = mySettingsMap.get(language);
          if (stateObject != null) {
            XmlSerializer.deserializeInto(stateObject, objectElement);
          }
        }
      }
    }
  }
}
