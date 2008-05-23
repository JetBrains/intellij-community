package com.intellij.openapi.keymap.impl;

import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.components.ExportableApplicationComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.keymap.KeyMapBundle;
import com.intellij.openapi.keymap.Keymap;
import com.intellij.openapi.keymap.KeymapManagerListener;
import com.intellij.openapi.keymap.ex.KeymapManagerEx;
import com.intellij.openapi.util.*;
import com.intellij.util.UniqueFileNamesProvider;
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

  private ArrayList<Keymap> myKeymaps = new ArrayList<Keymap>();
  private KeymapImpl myActiveKeymap;
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

  KeymapManagerImpl(DefaultKeymap defaultKeymap) {
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
    return myKeymaps.toArray(new Keymap[myKeymaps.size()]);
  }

  @Nullable
  public Keymap getKeymap(String name) {
    for (Keymap keymap : myKeymaps) {
      if (name.equals(keymap.getName())) {
        return keymap;
      }
    }
    return null;
  }

  public Keymap getActiveKeymap() {
    return myActiveKeymap;
  }

  public void setActiveKeymap(Keymap activeKeymap) {
    myActiveKeymap = (KeymapImpl) activeKeymap;
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

  public void addKeymap(Keymap keymap) {
    myKeymaps.add(keymap);
  }

  public void removeAllKeymapsExceptUnmodifiable() {
    Iterator<Keymap> it = myKeymaps.iterator();
    while (it.hasNext()) {
      Keymap keymap = it.next();
      if (keymap.canModify()) {
        it.remove();
      }
    }
    myActiveKeymap = null;
    if (myKeymaps.size() > 0) {
      myActiveKeymap = (KeymapImpl) myKeymaps.get(0);
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
    if (myActiveKeymap != null) {
      Element e = new Element(ACTIVE_KEYMAP);
      e.setAttribute(NAME_ATTRIBUTE, myActiveKeymap.getName());
      element.addContent(e);
    }
  }

  private void load(){
    for (File file : getKeymapFiles()) {
      try {
        readKeymap(file, myKeymaps);
      }
      catch (InvalidDataException e) {
        LOG.error("Invalid data in file: " + file.getAbsolutePath() + " Reason: " + e.getMessage());
      }
      catch (JDOMException e) {
        LOG.error("Invalid JDOM: " + file.getAbsolutePath());
      }
      catch (IOException e) {
        LOG.error(e);
      }
    }
  }

  private void readKeymap(File file, ArrayList<Keymap> keymaps) throws JDOMException,InvalidDataException, IOException{
    if (!file.exists()) return;
    Document document = JDOMUtil.loadDocument(file);
    if (document == null) throw new InvalidDataException();
    Element root = document.getRootElement();
    if (root == null || !KEYMAP.equals(root.getName())) {
      throw new InvalidDataException();
    }
    KeymapImpl keymap = new KeymapImpl();
    keymap.readExternal(root, getAllKeymaps());
    keymaps.add(keymap);
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

  public void save() throws IOException{
    File directory = getKeymapDirectory(true);
    if (directory == null) {
      LOG.error("Keymap directory does not exist and cannot be created");
      return;
    }

    File[] files = getKeymapFiles();

    ArrayList<String> filePaths = new ArrayList<String>();
    ArrayList<Document> documents = new ArrayList<Document>();

    UniqueFileNamesProvider namesProvider = new UniqueFileNamesProvider();
    for (final Keymap keymap : myKeymaps) {
      if (!keymap.canModify()) {
        continue;
      }

      Document document = new Document(((KeymapImpl)keymap).writeExternal());
      String filePath = directory.getAbsolutePath() + File.separator + namesProvider.suggestName(keymap.getName()) + '.' + XML_FILE_EXT;

      filePaths.add(filePath);
      documents.add(document);
    }

    JDOMUtil.updateFileSet(
      files,
      filePaths.toArray(new String[filePaths.size()]),
      documents.toArray(new Document[documents.size()]), "\n");
  }

  private void fireActiveKeymapChanged() {
    for (KeymapManagerListener listener : myListeners) {
      listener.activeKeymapChanged(myActiveKeymap);
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