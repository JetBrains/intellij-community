package com.intellij.openapi.util.registry;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.util.containers.HashMap;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.PropertyKey;

import java.lang.ref.Reference;
import java.lang.ref.SoftReference;
import java.util.*;

@State(
    name = "Registry",
    storages = {
        @Storage(
            id = "other",
            file="$APP_CONFIG$/other.xml")}
)
public class Registry implements PersistentStateComponent<Element> {

  private static Reference<ResourceBundle> ourBundle;

  @NonNls
  private static final String REGISTRY_BUNDLE = "misc.registry";

  private LinkedHashMap<String, String> myUserProperties = new LinkedHashMap<String, String>();
  private Map<String, String> myLoadedUserProperties = new HashMap<String, String>();

  private Map<String, RegistryValue> myValues = new HashMap<String, RegistryValue>();

  public static RegistryValue get(@PropertyKey(resourceBundle = REGISTRY_BUNDLE) String key) {
    final Registry registry = Registry.getInstance();

    if (registry.myValues.containsKey(key)) {
      return registry.myValues.get(key);
    } else {
      final RegistryValue value = new RegistryValue(registry, key);
      registry.myValues.put(key, value);
      return value;
    }
  }

  public static boolean is(@PropertyKey(resourceBundle = REGISTRY_BUNDLE) String key) {
    return get(key).asBoolean();
  }

  ResourceBundle getBundle() {
    ResourceBundle bundle = null;
    if (ourBundle != null) bundle = ourBundle.get();
    if (bundle == null) {
      bundle = ResourceBundle.getBundle(REGISTRY_BUNDLE);
      ourBundle = new SoftReference<ResourceBundle>(bundle);
    }
    return bundle;
  }


  public static Registry getInstance() {
    return ServiceManager.getService(Registry.class);
  }

  public Element getState() {
    final Element state = new Element("registry");
    for (String eachKey : myUserProperties.keySet()) {
      final Element entry = new Element("entry");
      entry.setAttribute("key", eachKey);
      entry.setAttribute("value", myUserProperties.get(eachKey));
      state.addContent(entry);
    }
    return state;
  }

  public void loadState(Element state) {
    final List entries = state.getChildren("entry");
    for (Object each : entries) {
      Element eachEntery = (Element) each;
      final String eachKey = eachEntery.getAttributeValue("key");
      final String eachValue = eachEntery.getAttributeValue("value");
      if (eachKey != null && eachValue != null) {
        myUserProperties.put(eachKey, eachValue);
      }
    }
    myLoadedUserProperties.putAll(myUserProperties);
  }

  Map<String, String> getUserProperties() {
    return myUserProperties;
  }

  public List<RegistryValue> getAll() {
    final Registry registry = Registry.getInstance();
    final ResourceBundle bundle = registry.getBundle();
    final Enumeration<String> keys = bundle.getKeys();

    final ArrayList<RegistryValue> result = new ArrayList<RegistryValue>();

    while (keys.hasMoreElements()) {
      final String each = keys.nextElement();
      if (each.endsWith(".description") || each.endsWith(".restartRequired")) continue;
      result.add(get(each));
    }

    return result;
  }

  public void restoreDefaults() {
    final HashMap<String, String> old = new HashMap<String, String>();
    old.putAll(myUserProperties);
    for (String each : old.keySet()) {
      get(each).resetToDefault();      
    }
  }

  public boolean isInDefaultState() {
    return getUserProperties().size() == 0;
  }

  public boolean isRestartNeeded() {
    return isRestartNeeded(myUserProperties) || isRestartNeeded(myLoadedUserProperties);
  }

  private boolean isRestartNeeded(Map<String, String> map) {
    for (String s : map.keySet()) {
      final RegistryValue eachValue = get(s);
      if (eachValue.isRestartRequired() && eachValue.isChangedSinceAppStart()) return true;
    }

    return false;
  }
}