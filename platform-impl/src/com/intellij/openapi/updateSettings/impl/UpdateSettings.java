package com.intellij.openapi.updateSettings.impl;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.util.JDOMExternalizableStringList;
import com.intellij.openapi.util.DefaultJDOMExternalizer;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.diagnostic.Logger;
import org.jdom.Element;

/**
 * @author yole
 */
@State(
  name = "UpdatesConfigurable",
  storages = {
    @Storage(
      id ="other",
      file = "$APP_CONFIG$/other.xml"
    )}
)
public class UpdateSettings implements PersistentStateComponent<Element> {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.updateSettings.impl.UpdateSettings"); 

  @SuppressWarnings({"WeakerAccess", "CanBeFinal"})
  public JDOMExternalizableStringList myPluginHosts = new JDOMExternalizableStringList();

  public boolean CHECK_NEEDED = true;
  public String CHECK_PERIOD = UpdateSettingsConfigurable.WEEKLY;
  public long LAST_TIME_CHECKED = 0;

  public static UpdateSettings getInstance() {
    return ServiceManager.getService(UpdateSettings.class);
  }

  public Element getState() {
    Element element = new Element("state");
    try {
      DefaultJDOMExternalizer.writeExternal(this, element);
    }
    catch (WriteExternalException e) {
      LOG.info(e);
    }
    return element;
  }

  public void loadState(final Element state) {
    try {
      DefaultJDOMExternalizer.readExternal(this, state);
    }
    catch (InvalidDataException e) {
      LOG.info(e);
    }
  }
}
