package com.intellij.openapi.keymap.impl;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.keymap.Keymap;
import com.intellij.openapi.keymap.KeymapManager;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.util.WriteExternalException;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;

import java.util.ArrayList;
import java.util.Iterator;

/**
 * @author Eugene Belyaev
 */
public class DefaultKeymap implements JDOMExternalizable, ApplicationComponent {

  @NonNls
  private static final String KEY_MAP = "keymap";
  @NonNls
  private static final String NAME_ATTRIBUTE = "name";

  private ArrayList<Keymap> myKeymaps = new ArrayList<Keymap>();

  public static DefaultKeymap getInstance() {
    return ApplicationManager.getApplication().getComponent(DefaultKeymap.class);
  }

  public void disposeComponent() {
  }

  public void initComponent() { }

  public void readExternal(Element element) throws InvalidDataException{
    myKeymaps = new ArrayList<Keymap>();
    for (Iterator i = element.getChildren().iterator(); i.hasNext();) {
      Element child=(Element)i.next();
      if (KEY_MAP.equals(child.getName())) {
        String keymapName = child.getAttributeValue(NAME_ATTRIBUTE);
        DefaultKeymapImpl keymap = KeymapManager.MAC_OS_X_KEYMAP.equals(keymapName)
                                   ? new MacOSDefaultKeymap()
                                   : new DefaultKeymapImpl();
        keymap.readExternal(child, myKeymaps.toArray(new Keymap[myKeymaps.size()]));
        keymap.setName(keymapName);
        myKeymaps.add(keymap);
      }
    }
  }

  /**
   * We override this method to disable saving the keymap.
   */
  public void writeExternal(Element element) throws WriteExternalException{
    throw new WriteExternalException();
  }

  public Keymap[] getKeymaps() {
    return myKeymaps.toArray(new Keymap[myKeymaps.size()]);
  }

  public String getComponentName() {
    return "DefaultKeymap";
  }

}