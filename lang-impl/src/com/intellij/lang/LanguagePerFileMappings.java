/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.lang;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.util.FileContentUtil;
import gnu.trove.THashMap;
import org.jdom.Element;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.util.*;

/**
 * @author peter
 */
public abstract class LanguagePerFileMappings<T> implements PersistentStateComponent<Element> {
  private Map<VirtualFile, T> myMappings = new HashMap<VirtualFile, T>();
  private final Project myProject;

  public LanguagePerFileMappings(final Project project) {
    myProject = project;
  }

  public synchronized Map<VirtualFile, T> getMappings() {
    cleanup();
    return Collections.unmodifiableMap(myMappings);
  }

  private void cleanup() {
    for (final VirtualFile file : new ArrayList<VirtualFile>(myMappings.keySet())) {
      if (!file.isValid()) {
        myMappings.clear();
      }
    }
  }

  @Nullable 
  public synchronized T getMapping(final VirtualFile file) {
    for (VirtualFile cur = file; ; cur = cur.getParent()) {
      final T dialect = myMappings.get(cur);
      if (dialect != null) return dialect;
      if (cur == null) break;
    }
    return null;
  }

  public synchronized void setMappings(final Map<VirtualFile, T> mappings) {
    myMappings = new HashMap<VirtualFile, T>(mappings);
    cleanup();
    FileContentUtil.reparseFiles(myProject, mappings.keySet(), true);
  }

  @TestOnly
  public synchronized void setMapping(final VirtualFile file, T dialect) {
    if (dialect == null) {
      myMappings.remove(file);
    }
    else {
      myMappings.put(file, dialect);
    }
    FileContentUtil.reparseFiles(myProject, Collections.singletonList(file), false);
  }

  protected abstract String serialize(T t);

  public synchronized Element getState() {
    cleanup();
    final Element element = new Element("x");
    final List<VirtualFile> files = new ArrayList<VirtualFile>(myMappings.keySet());
    Collections.sort(files, new Comparator<VirtualFile>() {
      public int compare(final VirtualFile o1, final VirtualFile o2) {
        if (o1 == null || o2 == null) return o1 == null ? o2 == null ? 0 : 1 : -1;
        return o1.getPath().compareTo(o2.getPath());
      }
    });
    for (VirtualFile file : files) {
      final T dialect = myMappings.get(file);
      final Element child = new Element("file");
      element.addContent(child);
      child.setAttribute("url", file == null ? "PROJECT" : file.getUrl());
      child.setAttribute("dialect", serialize(dialect));
    }
    return element;
  }

  public abstract List<T> getAvailableValues();

  public synchronized void loadState(final Element state) {
    final THashMap<String, T> dialectMap = new THashMap<String, T>();
    for (T dialect : getAvailableValues()) {
      dialectMap.put(serialize(dialect), dialect);
    }
    final List<Element> files = state.getChildren("file");
    for (Element fileElement : files) {
      final String url = fileElement.getAttributeValue("url");
      final String dialectID = fileElement.getAttributeValue("dialect");
      final T dialect = dialectMap.get(dialectID);
      if (dialect == null) continue;
      final VirtualFile file = url.equals("PROJECT") ? null : VirtualFileManager.getInstance().findFileByUrl(url);
      if (file != null || url.equals("PROJECT")) {
        myMappings.put(file, dialect);
      }
    }
  }
}
