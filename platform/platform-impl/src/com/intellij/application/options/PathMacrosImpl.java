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
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.SystemProperties;
import com.intellij.util.concurrency.JBReentrantReadWriteLock;
import com.intellij.util.concurrency.LockFactory;
import com.intellij.util.containers.HashMap;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 *  @author dsl
 */
public class PathMacrosImpl extends PathMacros implements ApplicationComponent, NamedJDOMExternalizable, RoamingTypeDisabled {
  private static final Logger LOG = Logger.getInstance("#com.intellij.application.options.PathMacrosImpl");
  private final Map<String,String> myMacros = new HashMap<String, String>();
  private final JBReentrantReadWriteLock myLock = LockFactory.createReadWriteLock();
  private final List<String> myIgnoredMacros = new CopyOnWriteArrayList<String>();

  @NonNls
  public static final String MACRO_ELEMENT = "macro";
  @NonNls
  public static final String NAME_ATTR = "name";
  @NonNls
  public static final String VALUE_ATTR = "value";

  @NonNls
  public static final String IGNORED_MACRO_ELEMENT = "ignoredMacro";

  // predefined macros
  @NonNls
  public static final String APPLICATION_HOME_MACRO_NAME = "APPLICATION_HOME_DIR";
  @NonNls
  public static final String PROJECT_DIR_MACRO_NAME = "PROJECT_DIR";
  @NonNls
  public static final String MODULE_DIR_MACRO_NAME = "MODULE_DIR";
  @NonNls
  public static final String USER_HOME_MACRO_NAME = "USER_HOME";

  private static final Set<String> ourSystemMacroNames = new HashSet<String>();
  @NonNls public static final String EXT_FILE_NAME = "path.macros";

  static {
    ourSystemMacroNames.add(APPLICATION_HOME_MACRO_NAME);
    ourSystemMacroNames.add(PROJECT_DIR_MACRO_NAME);
    ourSystemMacroNames.add(MODULE_DIR_MACRO_NAME);
    ourSystemMacroNames.add(USER_HOME_MACRO_NAME);
  }

  private static final Set<String> ourToolsMacros = new HashSet<String>();
  static {
    ourToolsMacros.add("ClasspathEntry");
    ourToolsMacros.add("Classpath");
    ourToolsMacros.add("ColumnNumber");
    ourToolsMacros.add("FileClass");
    ourToolsMacros.add("FileDir");
    ourToolsMacros.add("FileDirRelativeToProjectRoot");
    ourToolsMacros.add("/FileDirRelativeToProjectRoot");
    ourToolsMacros.add("FileDirRelativeToSourcepath");
    ourToolsMacros.add("/FileDirRelativeToSourcepath");
    ourToolsMacros.add("FileExt");
    ourToolsMacros.add("FileFQPackage");
    ourToolsMacros.add("FileName");
    ourToolsMacros.add("FileNameWithoutExtension");
    ourToolsMacros.add("FilePackage");
    ourToolsMacros.add("FilePath");
    ourToolsMacros.add("FilePathRelativeToProjectRoot");
    ourToolsMacros.add("/FilePathRelativeToProjectRoot");
    ourToolsMacros.add("FilePathRelativeToSourcepath");
    ourToolsMacros.add("/FilePathRelativeToSourcepath");
    ourToolsMacros.add("FileRelativeDir");
    ourToolsMacros.add("/FileRelativeDir");
    ourToolsMacros.add("FileRelativePath");
    ourToolsMacros.add("/FileRelativePath");
    ourToolsMacros.add("JavaDocPath");
    ourToolsMacros.add("JDKPath");
    ourToolsMacros.add("LineNumber");
    ourToolsMacros.add("ModuleFileDir");
    ourToolsMacros.add("ModuleFilePath");
    ourToolsMacros.add("ModuleName");
    ourToolsMacros.add("ModuleSourcePath");
    ourToolsMacros.add("OutputPath");
    ourToolsMacros.add("ProjectFileDir");
    ourToolsMacros.add("ProjectFilePath");
    ourToolsMacros.add("ProjectName");
    ourToolsMacros.add("Projectpath");
    ourToolsMacros.add("Prompt");
    ourToolsMacros.add("SourcepathEntry");
    ourToolsMacros.add("Sourcepath");
  }

  public PathMacrosImpl() {
    setMacro(USER_HOME_MACRO_NAME, FileUtil.toSystemIndependentName(SystemProperties.getUserHome()));
  }

  public static PathMacrosImpl getInstanceEx() {
    return (PathMacrosImpl)ApplicationManager.getApplication().getComponent(PathMacros.class);
  }

  @NotNull
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

  public static Set<String> getToolMacroNames() {
    return ourToolsMacros;
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

  @Override
  public Collection<String> getIgnoredMacroNames() {
    return myIgnoredMacros;
  }

  public void setIgnoredMacroNames(@NotNull final Collection<String> names) {
    myIgnoredMacros.clear();
    myIgnoredMacros.addAll(names);
  }

  @Override
  public void addIgnoredMacro(@NotNull String name) {
    if (!myIgnoredMacros.contains(name)) myIgnoredMacros.add(name);
  }

  @Override
  public boolean isIgnoredMacroName(@NotNull String macro) {
    return myIgnoredMacros.contains(macro);
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

  public void removeAllMacros() {
    try {
      myLock.writeLock().lock();
      myMacros.clear();
    }
    finally {
      myLock.writeLock().unlock();
    }
  }

  public void setMacro(@NotNull String name, @NotNull String value) {
    if (value.trim().length() == 0) return;
    try {
      myLock.writeLock().lock();
      myMacros.put(name, value);
    }
    finally {
      myLock.writeLock().unlock();
    }
  }

  public void removeMacro(String name) {
    try {
      myLock.writeLock().lock();
      final String value = myMacros.remove(name);
      LOG.assertTrue(value != null);
    }
    finally {
      myLock.writeLock().unlock();
    }
  }

  public void readExternal(Element element) throws InvalidDataException {
    final List children = element.getChildren(MACRO_ELEMENT);
    for (Object aChildren : children) {
      Element macro = (Element)aChildren;
      final String name = macro.getAttributeValue(NAME_ATTR);
      final String value = macro.getAttributeValue(VALUE_ATTR);
      if (name == null || value == null) {
        throw new InvalidDataException();
      }

      myMacros.put(name, value);
    }

    final List ignoredChildren = element.getChildren(IGNORED_MACRO_ELEMENT);
    for (final Object child : ignoredChildren) {
      final Element macroElement = (Element)child;
      final String ignoredName = macroElement.getAttributeValue(NAME_ATTR);
      if (ignoredName != null && ignoredName.length() > 0 && !myIgnoredMacros.contains(ignoredName)) {
        myIgnoredMacros.add(ignoredName);
      }
    }
  }

  public void writeExternal(Element element) throws WriteExternalException {
    final Set<Map.Entry<String,String>> entries = myMacros.entrySet();
    for (Map.Entry<String, String> entry : entries) {
      final String value = entry.getValue();
      if (value != null && value.trim().length() > 0) {
        final Element macro = new Element(MACRO_ELEMENT);
        macro.setAttribute(NAME_ATTR, entry.getKey());
        macro.setAttribute(VALUE_ATTR, value);
        element.addContent(macro);
      }
    }

    for (final String macro : myIgnoredMacros) {
      final Element macroElement = new Element(IGNORED_MACRO_ELEMENT);
      macroElement.setAttribute(NAME_ATTR, macro);
      element.addContent(macroElement);
    }
  }

  public void addMacroReplacements(ReplacePathToMacroMap result) {
    final Set<String> macroNames = getUserMacroNames();
    for (final String name : macroNames) {
      final String value = getValue(name);
      if (value != null && value.trim().length() > 0) result.addMacroReplacement(value, name);
    }
  }


  public void addMacroExpands(ExpandMacroToPathMap result) {
    final Set<String> macroNames = getUserMacroNames();
    for (final String name : macroNames) {
      final String value = getValue(name);
      if (value != null && value.trim().length() > 0) result.addMacroExpand(name, value);
    }
  }

}
