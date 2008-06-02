package com.intellij.application.options;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathMacros;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.components.ExpandMacroToPathMap;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.NamedJDOMExternalizable;
import com.intellij.openapi.util.RoamingTypeDisabled;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.util.concurrency.JBReentrantReadWriteLock;
import com.intellij.util.concurrency.LockFactory;
import com.intellij.util.containers.HashMap;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 *  @author dsl
 */
public class PathMacrosImpl extends PathMacros implements ApplicationComponent, NamedJDOMExternalizable, RoamingTypeDisabled {
  private static final Logger LOG = Logger.getInstance("#com.intellij.application.options.PathMacrosImpl");
  private final Map<String,String> myMacros = new HashMap<String, String>();
  private final Map<String,String> myMacrosDescriptions = new HashMap<String, String>();
  private JBReentrantReadWriteLock myLock = LockFactory.createReadWriteLock();

  @NonNls
  public static final String MACRO_ELEMENT = "macro";
  @NonNls
  public static final String NAME_ATTR = "name";
  @NonNls
  public static final String VALUE_ATTR = "value";
  @NonNls
  public static final String DESCRIPTION_ATTR = "description";
  // predefined macros
  @NonNls
  public static final String APPLICATION_HOME_MACRO_NAME = "APPLICATION_HOME_DIR";
  @NonNls
  public static final String PROJECT_DIR_MACRO_NAME = "PROJECT_DIR";
  @NonNls
  public static final String MODULE_DIR_MACRO_NAME = "MODULE_DIR";

  private static final Set<String> ourSystemMacroNames = new HashSet<String>();
  @NonNls public static final String EXT_FILE_NAME = "path.macros";

  {
    ourSystemMacroNames.add(APPLICATION_HOME_MACRO_NAME);
    ourSystemMacroNames.add(PROJECT_DIR_MACRO_NAME);
    ourSystemMacroNames.add(MODULE_DIR_MACRO_NAME);
  }

  public static PathMacrosImpl getInstanceEx() {
    return (PathMacrosImpl)ApplicationManager.getApplication().getComponent(PathMacros.class);
  }

  public String getComponentName() {
    return "PathMacrosImpl";
  }

  public void initComponent() { }

  public void disposeComponent() {
  }

  public String getExternalFileName() {
    return EXT_FILE_NAME;
  }

  public Set<String> getUserMacroNames() {
    myLock.readLock().lock();
    try {
      return myMacros.keySet();
    }
    finally {
      myLock.readLock().unlock();
    }
  }

  public Set<String> getSystemMacroNames() {
    try {
      myLock.readLock().lock();
      return ourSystemMacroNames;
    }
    finally {
      myLock.readLock().unlock();
    }
  }

  public Set<String> getAllMacroNames() {
    final Set<String> userMacroNames = getUserMacroNames();
    final Set<String> systemMacroNames = getSystemMacroNames();
    final Set<String> allNames = new HashSet<String>(userMacroNames.size() + systemMacroNames.size());
    allNames.addAll(systemMacroNames);
    allNames.addAll(userMacroNames);
    return allNames;
  }

  public String getValue(String name) {
    try {
      myLock.readLock().lock();
      return myMacros.get(name);
    }
    finally {
      myLock.readLock().unlock();
    }
  }

  @Nullable
  public String getDescription(String name) {
    return myMacrosDescriptions.get(name);
  }

  public void removeAllMacros() {
    try {
      myLock.writeLock().lock();
      myMacros.clear();
      myMacrosDescriptions.clear();
    }
    finally {
      myLock.writeLock().unlock();
    }
  }

  public void setMacro(@NotNull String name, @NotNull String value, @Nullable String description) {
    try {
      myLock.writeLock().lock();
      myMacros.put(name, value);
      myMacrosDescriptions.put(name, description);
    }
    finally {
      myLock.writeLock().unlock();
    }
  }

  public void removeMacro(String name) {
    try {
      myLock.writeLock().lock();
      final String value = myMacros.remove(name);
      myMacrosDescriptions.remove(name);
      LOG.assertTrue(value != null);
    }
    finally {
      myLock.writeLock().unlock();
    }
  }

  public void readExternal(Element element) throws InvalidDataException {
    final List children = element.getChildren(MACRO_ELEMENT);
    for (int i = 0; i < children.size(); i++) {
      Element macro = (Element)children.get(i);
      final String name = macro.getAttributeValue(NAME_ATTR);
      final String value = macro.getAttributeValue(VALUE_ATTR);
      if (name == null || value == null) {
        throw new InvalidDataException();
      }

      myMacros.put(name, value);

      final String description = macro.getAttributeValue(DESCRIPTION_ATTR);
      if (description != null) {
        myMacrosDescriptions.put(name, description);
      }
    }
  }

  public void writeExternal(Element element) throws WriteExternalException {
    final Set<Map.Entry<String,String>> entries = myMacros.entrySet();
    for (Map.Entry<String, String> entry : entries) {
      final Element macro = new Element(MACRO_ELEMENT);
      macro.setAttribute(NAME_ATTR, entry.getKey());
      macro.setAttribute(VALUE_ATTR, entry.getValue());

      final String description = myMacrosDescriptions.get(entry.getKey());
      if (description != null) {
        macro.setAttribute(DESCRIPTION_ATTR, description);
      }

      element.addContent(macro);
    }
  }

  public void addMacroReplacements(ReplacePathToMacroMap result) {
    final Set<String> macroNames = getUserMacroNames();
    for (final String name : macroNames) {
      result.addMacroReplacement(getValue(name), name);
    }
  }


  public void addMacroExpands(ExpandMacroToPathMap result) {
    final Set<String> macroNames = getUserMacroNames();
    for (final String name : macroNames) {
      result.addMacroExpand(name, getValue(name));
    }
  }

}
