/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.lang;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.impl.FilePropertyPusher;
import com.intellij.openapi.roots.impl.PushedFilePropertiesUpdater;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.util.FileContentUtil;
import com.intellij.util.containers.ContainerUtil;
import gnu.trove.THashMap;
import org.jdom.Element;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author peter
 */
public abstract class LanguagePerFileMappings<T> implements PersistentStateComponent<Element>, PerFileMappings<T> {
  private final Map<VirtualFile, T> myMappings = new HashMap<VirtualFile, T>();
  private final Project myProject;

  public LanguagePerFileMappings(final Project project) {
    myProject = project;
  }

  @Nullable
  protected FilePropertyPusher<T> getFilePropertyPusher() {
    return null;
  }

  public Map<VirtualFile, T> getMappings() {
    synchronized (myMappings) {
      cleanup();
      return Collections.unmodifiableMap(myMappings);
    }
  }

  private void cleanup() {
    for (final VirtualFile file : new ArrayList<VirtualFile>(myMappings.keySet())) {
      if (file != null //PROJECT, top-level
          && !file.isValid()) {
        myMappings.remove(file);
      }
    }
  }

  @Nullable 
  public T getMapping(@Nullable final VirtualFile file) {
    if (file != null) {
      final FilePropertyPusher<T> pusher = getFilePropertyPusher();
      final T pushedValue = pusher == null? null : file.getUserData(pusher.getFileDataKey());
      if (pushedValue != null) return pushedValue;
    }
    synchronized (myMappings) {
      for (VirtualFile cur = file; ; cur = cur.getParent()) {
        final T dialect = myMappings.get(cur);
        if (dialect != null) return dialect;
        if (cur == null) break;
      }
    }
    return getDefaultMapping(file);
  }

  @Nullable
  protected T getDefaultMapping(@Nullable final VirtualFile file) {
    return null;
  }

  @Nullable
  public T getImmediateMapping(final VirtualFile file) {
    synchronized (myMappings) {
      return myMappings.get(file); 
    }
  }

  public void setMappings(final Map<VirtualFile, T> mappings) {
    synchronized (myMappings) {
      myMappings.clear();
      myMappings.putAll(mappings);
      cleanup();
    }
    handleMappingChange(mappings.keySet(), true);
  }

  public void setMapping(final VirtualFile file, T dialect) {
    synchronized (myMappings) {
      if (dialect == null) {
        myMappings.remove(file);
      }
      else {
        myMappings.put(file, dialect);
      }
    }
    handleMappingChange(ContainerUtil.createMaybeSingletonList(file), false);
  }

  private void handleMappingChange(final Collection<VirtualFile> files, final boolean includeOpenFiles) {
    FileContentUtil.reparseFiles(myProject, files, includeOpenFiles);
    final FilePropertyPusher<T> pusher = getFilePropertyPusher();
    if (pusher != null) {
      PushedFilePropertiesUpdater.getInstance(myProject).pushAll(pusher);
    }
  }

  public Collection<T> getAvailableValues(VirtualFile file) {
    return getAvailableValues();
  }

  protected abstract List<T> getAvailableValues();
  
  protected abstract String serialize(T t);

  public Element getState() {
    synchronized (myMappings) {
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
  }

  public void loadState(final Element state) {
    synchronized (myMappings) {
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
}
