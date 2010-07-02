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
import org.jetbrains.annotations.TestOnly;

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
    final Collection<VirtualFile> oldFiles;
    synchronized (myMappings) {
      oldFiles = new ArrayList<VirtualFile>(myMappings.keySet());
      myMappings.clear();
      myMappings.putAll(mappings);
      cleanup();
    }
    handleMappingChange(mappings.keySet(), oldFiles, true);
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
    final List<VirtualFile> files = ContainerUtil.createMaybeSingletonList(file);
    handleMappingChange(files, files, false);
  }

  private void handleMappingChange(final Collection<VirtualFile> files, Collection<VirtualFile> oldFiles, final boolean includeOpenFiles) {
    final FilePropertyPusher<T> pusher = getFilePropertyPusher();
    if (pusher != null) {
      for (VirtualFile oldFile : oldFiles) {
        if (oldFile == null) continue; // project
        oldFile.putUserData(pusher.getFileDataKey(), null);
      }
      PushedFilePropertiesUpdater.getInstance(myProject).pushAll(pusher);
    }
    FileContentUtil.reparseFiles(myProject, files, includeOpenFiles);
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

  @TestOnly
  public void cleanupForNextTest() {
    synchronized (myMappings) {
      myMappings.clear();
    }
  }

}
