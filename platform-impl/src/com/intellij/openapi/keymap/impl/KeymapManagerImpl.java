package com.intellij.openapi.keymap.impl;

import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.components.ExportableApplicationComponent;
import com.intellij.openapi.components.RoamingType;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.keymap.KeyMapBundle;
import com.intellij.openapi.keymap.Keymap;
import com.intellij.openapi.keymap.KeymapManagerListener;
import com.intellij.openapi.keymap.ex.KeymapManagerEx;
import com.intellij.openapi.options.SchemeProcessor;
import com.intellij.openapi.options.SchemesManager;
import com.intellij.openapi.options.SchemesManagerFactory;
import com.intellij.openapi.options.Scheme;
import com.intellij.openapi.util.*;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * @author Anton Katilin
 * @author Eugene Belyaev
 * @author Vladimir Kondratyev
 */
public class KeymapManagerImpl extends KeymapManagerEx implements NamedJDOMExternalizable, ExportableApplicationComponent,
                                                                  RoamingTypePerPlatform {

  private static final Logger LOG = Logger.getInstance("#com.intellij.keymap.KeymapManager");

  private List<KeymapManagerListener> myListeners = new CopyOnWriteArrayList<KeymapManagerListener>();
  private String myActiveKeymapName;
  private Map<String, String> myBoundShortcuts = new HashMap<String, String>();

  @NonNls
  private static final String KEYMAP = "keymap";
  @NonNls
  private static final String KEYMAPS = "keymaps";
  @NonNls
  private static final String ACTIVE_KEYMAP = "active_keymap";
  @NonNls
  private static final String NAME_ATTRIBUTE = "name";
  @NonNls
  private static final String XML_FILE_EXT = "xml";
  private SchemesManager<Keymap, KeymapImpl> mySchemesManager;

  KeymapManagerImpl(DefaultKeymap defaultKeymap, SchemesManagerFactory factory) {
    mySchemesManager = factory.createSchemesManager(
        "$ROOT_CONFIG$/keymaps",
        new SchemeProcessor<KeymapImpl>(){
          public KeymapImpl readScheme(final Document schemeContent) throws InvalidDataException, IOException, JDOMException {
            return readKeymap(schemeContent);
          }

          public Document writeScheme(final KeymapImpl scheme) throws WriteExternalException {
            return new Document(scheme.writeExternal());
          }

          public boolean shouldBeSaved(final KeymapImpl scheme) {
            return scheme.canModify();
          }

          public void initScheme(final KeymapImpl scheme) {
            
          }

          public void onSchemeAdded(final KeymapImpl scheme) {

          }

          public void onSchemeDeleted(final KeymapImpl scheme) {
          }

          public void onCurrentSchemeChanged(final Scheme newCurrentScheme) {
            
          }
        },
        RoamingType.PER_USER);

    Keymap[] keymaps = defaultKeymap.getKeymaps();
    for (Keymap keymap : keymaps) {
      addKeymap(keymap);
      String systemDefaultKeymap = SystemInfo.isMac ? MAC_OS_X_KEYMAP : DEFAULT_IDEA_KEYMAP;
      if (systemDefaultKeymap.equals(keymap.getName())) {
        setActiveKeymap(keymap);
      }
    }
    load();
  }

  private Keymap getFirstUnmodifiableKeymap() {
    for (Keymap keymap : mySchemesManager.getAllSchemes()) {
      if (!keymap.canModify()) return keymap;
    }
    return null;
  }

  @NotNull
  public File[] getExportFiles() {
    return new File[]{PathManager.getOptionsFile(this),getKeymapDirectory(true)};
  }

  @NotNull
  public String getPresentableName() {
    return KeyMapBundle.message("key.maps.name");
  }

  public void disposeComponent() {
  }

  public void initComponent() {
  }

  public Keymap[] getAllKeymaps() {
    Collection<Keymap> keymaps = mySchemesManager.getAllSchemes();
    return keymaps.toArray(new Keymap[keymaps.size()]);
  }

  @Nullable
  public Keymap getKeymap(String name) {
    return mySchemesManager.findSchemeByName( name);
  }

  public Keymap getActiveKeymap() {
    return mySchemesManager.getCurrentScheme();
  }

  public void setActiveKeymap(Keymap activeKeymap) {
    mySchemesManager.setCurrentSchemeName(activeKeymap == null ? null : activeKeymap.getName());
    fireActiveKeymapChanged();
  }

  public void bindShortcuts(String sourceActionId, String targetActionId) {
    myBoundShortcuts.put(targetActionId, sourceActionId);
  }

  public Set<String> getBoundActions() {
    return myBoundShortcuts.keySet();
  }

  public String getActionBinding(String actionId) {
    return myBoundShortcuts.get(actionId);
  }

  public SchemesManager<Keymap, KeymapImpl> getSchemesManager() {
    return mySchemesManager;    
  }

  public void addKeymap(Keymap keymap) {
    mySchemesManager.addNewScheme(keymap, true);
  }

  public void removeAllKeymapsExceptUnmodifiable() {
    for (Keymap keymap : mySchemesManager.getAllSchemes()) {
      if (keymap.canModify()) {
        mySchemesManager.removeScheme(keymap);
      }
    }
    mySchemesManager.setCurrentSchemeName(null);

    Collection<Keymap> keymaps = mySchemesManager.getAllSchemes();
    if (keymaps.size() > 0) {
      mySchemesManager.setCurrentSchemeName(keymaps.iterator().next().getName());
    }
  }

  public String getExternalFileName() {
    return "keymap";
  }

  public void readExternal(Element element) throws InvalidDataException{
    Element child = element.getChild(ACTIVE_KEYMAP);
    if (child != null) {
      myActiveKeymapName = child.getAttributeValue(NAME_ATTRIBUTE);
    }

    if (myActiveKeymapName != null) {
      Keymap keymap = getKeymap(myActiveKeymapName);
      if (keymap != null) {
        setActiveKeymap(keymap);
      }
    }
  }

  public void writeExternal(Element element) throws WriteExternalException{
    if (mySchemesManager.getCurrentScheme() != null) {
      Element e = new Element(ACTIVE_KEYMAP);
      e.setAttribute(NAME_ATTRIBUTE, mySchemesManager.getCurrentScheme().getName());
      element.addContent(e);
    }
  }

  private void load(){
    mySchemesManager.loadSchemes();
  }

  private KeymapImpl readKeymap(Document document) throws JDOMException,InvalidDataException, IOException{
    if (document == null) throw new InvalidDataException();
    Element root = document.getRootElement();
    if (root == null || !KEYMAP.equals(root.getName())) {
      throw new InvalidDataException();
    }
    KeymapImpl keymap = new KeymapImpl();
    keymap.readExternal(root, getAllKeymaps());

    return keymap;
  }

  @Nullable
  private static File getKeymapDirectory(boolean toCreate) {
    String directoryPath = PathManager.getConfigPath() + File.separator + KEYMAPS;
    File directory = new File(directoryPath);
    if (!directory.exists()) {
      if (!toCreate) return null;
      if (!directory.mkdir()) {
        LOG.error("Cannot create directory: " + directory.getAbsolutePath());
        return null;
      }
    }
    return directory;
  }

  private static File[] getKeymapFiles() {
    File directory = getKeymapDirectory(false);
    if (directory == null) {
      return new File[0];
    }
    File[] ret = directory.listFiles(new FileFilter() {
      public boolean accept(File file){
        return !file.isDirectory() && file.getName().toLowerCase().endsWith('.' + XML_FILE_EXT);
      }
    });
    if (ret == null) {
      LOG.error("Cannot read directory: " + directory.getAbsolutePath());
      return new File[0];
    }
    return ret;
  }

  private void fireActiveKeymapChanged() {
    for (KeymapManagerListener listener : myListeners) {
      listener.activeKeymapChanged(mySchemesManager.getCurrentScheme());
    }
  }

  public void addKeymapManagerListener(KeymapManagerListener listener) {
    myListeners.add(listener);
  }

  public void removeKeymapManagerListener(KeymapManagerListener listener) {
    myListeners.remove(listener);
  }

  @NotNull
  public String getComponentName() {
    return "KeymapManager";
  }
}