/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.lang;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import com.intellij.openapi.vfs.newvfs.events.VFilePropertyChangeEvent;
import com.intellij.util.SmartList;
import gnu.trove.THashMap;
import gnu.trove.THashSet;
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

  public Map<VirtualFile, T> getMappings() {
    return Collections.unmodifiableMap(myMappings);
  }

  @Nullable 
  public T getMapping(final VirtualFile file) {
    for (VirtualFile cur = file; ; cur = cur.getParent()) {
      final T dialect = myMappings.get(cur);
      if (dialect != null) return dialect;
      if (cur == null) break;
    }
    return null;
  }

  public void setMappings(final Map<VirtualFile, T> mappings) {
    myMappings = new HashMap<VirtualFile, T>(mappings);
    final Set<VFilePropertyChangeEvent> list = new THashSet<VFilePropertyChangeEvent>();
    for (VirtualFile file : mappings.keySet()) {
      saveOrReload(file, list);
    }
    for (VirtualFile open : FileEditorManager.getInstance(myProject).getOpenFiles()) {
      if (!mappings.containsKey(open)) {
        saveOrReload(open, list);
      }
    }
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        ApplicationManager.getApplication().getMessageBus().syncPublisher(VirtualFileManager.VFS_CHANGES)
            .after(new ArrayList<VFileEvent>(list));
      }
    });
  }

  @TestOnly
  public void setMapping(final VirtualFile file, T dialect) {
    if (dialect == null) {
      myMappings.remove(file);
    }
    else {
      myMappings.put(file, dialect);
    }
    final SmartList<VFilePropertyChangeEvent> list = new SmartList<VFilePropertyChangeEvent>();
    saveOrReload(file, list);
    ApplicationManager.getApplication().getMessageBus().syncPublisher(VirtualFileManager.VFS_CHANGES).after(list);
  }

  private static void saveOrReload(final VirtualFile virtualFile, Collection<VFilePropertyChangeEvent> events) {
    if (virtualFile == null || virtualFile.isDirectory()) {
      return;
    }
    final FileDocumentManager documentManager = FileDocumentManager.getInstance();
    if (documentManager.isFileModified(virtualFile)) {
      Document document = documentManager.getDocument(virtualFile);
      if (document != null) {
        documentManager.saveDocument(document);
      }
    }
    events.add(new VFilePropertyChangeEvent(null, virtualFile, VirtualFile.PROP_NAME, virtualFile.getName(),
                                                                     virtualFile.getName(), false));


  }

  protected abstract String serialize(T t);

  public Element getState() {
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

  public void loadState(final Element state) {
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
