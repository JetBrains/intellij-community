package com.intellij.dupLocator;

import com.intellij.lang.Language;
import com.intellij.openapi.components.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.util.containers.HashMap;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

/**
 * @author Eugene.Kudelevsky
 */
@State(
  name = "MultiLanguageDuplocatorSettings",
  storages = {
    @Storage(file = StoragePathMacros.APP_CONFIG + "/duplocatorSettings.xml")
  }
)
public class MultilanguageDuplocatorSettings implements PersistentStateComponent<Element> {
  private static final Logger LOG = Logger.getInstance("#com.intellij.dupLocator.MultiLanguageDuplocatorSettings");

  private final Map<String, ExternalizableDuplocatorState> mySettingsMap = new HashMap<String, ExternalizableDuplocatorState>();

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
      final Element element = new Element("state");
      for (String name : mySettingsMap.keySet()) {
        final Element child = element.addContent("object");
        element.setAttribute("language", name);
        final JDOMExternalizable settingsObject = mySettingsMap.get(name);
        try {
          settingsObject.writeExternal(child);
        }
        catch (WriteExternalException e) {
          LOG.error(e);
          return null;
        }
      }
      return element;
    }
  }

  @Override
  public void loadState(Element state) {
    synchronized (mySettingsMap) {
      if (state == null) {
        return;
      }

      for (Object o : state.getChildren("object")) {
        final Element objectElement = (Element)o;
        final String language = objectElement.getAttributeValue("language");

        if (language != null) {
          final JDOMExternalizable stateObject = mySettingsMap.get(language);
          if (stateObject != null) {
            try {
              stateObject.readExternal(objectElement);
            }
            catch (InvalidDataException e) {
              LOG.error(e);
            }
          }
        }
      }
    }
  }
}
