// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang;

import com.intellij.injected.editor.VirtualFileWindow;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.command.undo.BasicUndoableAction;
import com.intellij.openapi.command.undo.UndoManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.impl.FilePropertyPusher;
import com.intellij.openapi.roots.impl.PushedFilePropertiesUpdater;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.NonPhysicalFileSystem;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.newvfs.BulkFileListener;
import com.intellij.openapi.vfs.newvfs.events.VFileDeleteEvent;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.reference.SoftReference;
import com.intellij.testFramework.LightVirtualFile;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.JBIterable;
import gnu.trove.THashMap;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.lang.ref.WeakReference;
import java.util.*;

/**
 * @author gregsh
 */
public abstract class PerFileMappingsBase<T> implements PersistentStateComponent<Element>, PerFileMappings<T>, Disposable {
  private List<PerFileMappingState> myDeferredMappings;
  private final Map<VirtualFile, T> myMappings = ContainerUtil.newHashMap();

  public PerFileMappingsBase() {
    installDeleteUndo();
  }

  @Override
  public void dispose() {
  }

  @Nullable
  protected FilePropertyPusher<T> getFilePropertyPusher() {
    return null;
  }

  @Nullable
  protected Project getProject() { return null; }

  @NotNull
  @Override
  public Map<VirtualFile, T> getMappings() {
    synchronized (myMappings) {
      ensureStateLoaded();
      cleanup();
      return Collections.unmodifiableMap(myMappings);
    }
  }

  private void cleanup() {
    for (Iterator<VirtualFile> i = myMappings.keySet().iterator(); i.hasNext(); ) {
      VirtualFile file = i.next();
      if (file != null /* PROJECT, top-level */ && !file.isValid()) {
        i.remove();
      }
    }
  }

  @Override
  @Nullable
  public T getMapping(@Nullable VirtualFile file) {
    T t = getConfiguredMapping(file);
    return t == null ? getDefaultMapping(file) : t;
  }

  @Nullable
  public T getConfiguredMapping(@Nullable VirtualFile file) {
    FilePropertyPusher<T> pusher = getFilePropertyPusher();
    return getMappingInner(file, pusher == null ? null : pusher.getFileDataKey(), false);
  }

  @Nullable
  public T getDirectlyConfiguredMapping(@Nullable VirtualFile file) {
    return getMappingInner(file, null, true);
  }

  @Nullable
  private T getMappingInner(@Nullable VirtualFile file, @Nullable Key<T> pusherKey, boolean forHierarchy) {
    if (file instanceof VirtualFileWindow) {
      VirtualFileWindow window = (VirtualFileWindow)file;
      file = window.getDelegate();
    }
    VirtualFile originalFile = file instanceof LightVirtualFile ? ((LightVirtualFile)file).getOriginalFile() : null;
    if (Comparing.equal(originalFile, file)) originalFile = null;

    if (file != null) {
      T pushedValue = pusherKey == null ? null : file.getUserData(pusherKey);
      if (pushedValue != null) return pushedValue;
    }
    if (originalFile != null) {
      T pushedValue = pusherKey == null ? null : originalFile.getUserData(pusherKey);
      if (pushedValue != null) return pushedValue;
    }
    synchronized (myMappings) {
      ensureStateLoaded();
      T t = getMappingForHierarchy(file, myMappings);
      if (t != null) return t;
      t = getMappingForHierarchy(originalFile, myMappings);
      if (t != null) return t;
      if (forHierarchy) return null;
      return getNotInHierarchy(originalFile != null ? originalFile : file, myMappings);
    }
  }

  @Nullable
  protected T getNotInHierarchy(@Nullable VirtualFile file, @NotNull Map<VirtualFile, T> mappings) {
    if (getProject() == null || file == null ||
        file.getFileSystem() instanceof NonPhysicalFileSystem ||
        ProjectFileIndex.getInstance(getProject()).isInContent(file)) {
      return mappings.get(null);
    }
    return null;
  }

  private static <T> T getMappingForHierarchy(@Nullable VirtualFile file, @NotNull Map<VirtualFile, T> mappings) {
    for (VirtualFile cur = file; cur != null; cur = cur.getParent()) {
      T t = mappings.get(cur);
      if (t != null) return t;
    }
    return null;
  }

  @Override
  @Nullable
  public T getDefaultMapping(@Nullable VirtualFile file) {
    return null;
  }

  @Nullable
  public T getImmediateMapping(@Nullable VirtualFile file) {
    synchronized (myMappings) {
      ensureStateLoaded();
      return myMappings.get(file);
    }
  }

  @Override
  public void setMappings(@NotNull Map<VirtualFile, T> mappings) {
    Collection<VirtualFile> oldFiles;
    synchronized (myMappings) {
      myDeferredMappings = null;
      oldFiles = ContainerUtil.newArrayList(myMappings.keySet());
      myMappings.clear();
      myMappings.putAll(mappings);
      cleanup();
    }
    Project project = getProject();
    handleMappingChange(mappings.keySet(), oldFiles, project != null && !project.isDefault());
  }

  @Override
  public void setMapping(@Nullable VirtualFile file, @Nullable T value) {
    synchronized (myMappings) {
      ensureStateLoaded();
      if (value == null) {
        myMappings.remove(file);
      }
      else {
        myMappings.put(file, value);
      }
    }
    List<VirtualFile> files = ContainerUtil.createMaybeSingletonList(file);
    handleMappingChange(files, files, false);
  }

  private void handleMappingChange(Collection<VirtualFile> files, Collection<VirtualFile> oldFiles, boolean includeOpenFiles) {
    Project project = getProject();
    FilePropertyPusher<T> pusher = getFilePropertyPusher();
    if (project != null && pusher != null) {
      for (VirtualFile oldFile : oldFiles) {
        if (oldFile == null) continue; // project
        oldFile.putUserData(pusher.getFileDataKey(), null);
      }
      if (!project.isDefault()) {
        PushedFilePropertiesUpdater.getInstance(project).pushAll(pusher);
      }
    }
    if (shouldReparseFiles()) {
      Project[] projects = project == null ? ProjectManager.getInstance().getOpenProjects() : new Project[]{project};
      for (Project p : projects) {
        PsiDocumentManager.getInstance(p).reparseFiles(files, includeOpenFiles);
      }
    }
  }

  public abstract List<T> getAvailableValues();

  @Nullable
  protected abstract String serialize(T t);

  @Override
  public Element getState() {
    synchronized (myMappings) {
      if (myDeferredMappings != null) {
        //noinspection deprecation
        return PerFileMappingState.write(myDeferredMappings, getValueAttribute());
      }

      cleanup();
      Element element = new Element("x");
      List<VirtualFile> files = new ArrayList<>(myMappings.keySet());
      Collections.sort(files, (o1, o2) -> {
        if (o1 == null || o2 == null) return o1 == null ? o2 == null ? 0 : 1 : -1;
        return o1.getPath().compareTo(o2.getPath());
      });
      for (VirtualFile file : files) {
        T value = myMappings.get(file);
        String valueStr = value == null ? null : serialize(value);
        if (valueStr == null) continue;
        Element child = new Element("file");
        element.addContent(child);
        child.setAttribute("url", file == null ? "PROJECT" : file.getUrl());
        //noinspection deprecation
        child.setAttribute(getValueAttribute(), valueStr);
      }
      return element;
    }
  }

  @Nullable
  protected T handleUnknownMapping(VirtualFile file, String value) {
    return null;
  }

  @SuppressWarnings("DeprecatedIsStillUsed")
  @NotNull
  @Deprecated
  // better to not override
  protected String getValueAttribute() {
    return "value";
  }

  @Override
  public void loadState(@NotNull Element element) {
    // read not under lock
    @SuppressWarnings("deprecation")
    List<PerFileMappingState> list = PerFileMappingState.read(element, getValueAttribute());
    synchronized (myMappings) {
      if (list.isEmpty()) {
        myMappings.clear();
        myDeferredMappings = null;
      }
      else {
        myDeferredMappings = list;
      }
    }
  }

  private void ensureStateLoaded() {
    synchronized (myMappings) {
      List<PerFileMappingState> state = myDeferredMappings;
      if (state == null) {
        return;
      }

      myDeferredMappings = null;
      THashMap<String, T> valuesMap = new THashMap<>();
      for (T value : getAvailableValues()) {
        String key = serialize(value);
        if (key != null) {
          valuesMap.put(key, value);
        }
      }
      myMappings.clear();
      for (PerFileMappingState entry : state) {
        String url = entry.getUrl();
        String valueStr = entry.getValue();
        VirtualFile file = "PROJECT".equals(url) ? null : VirtualFileManager.getInstance().findFileByUrl(url);
        T value = valuesMap.get(valueStr);
        if (value == null) {
          value = handleUnknownMapping(file, valueStr);
          if (value == null) continue;
        }
        if (file != null || url.equals("PROJECT")) {
          myMappings.put(file, value);
        }
      }
    }
  }

  @TestOnly
  public void cleanupForNextTest() {
    synchronized (myMappings) {
      myDeferredMappings = null;
      myMappings.clear();
    }
  }

  protected boolean shouldReparseFiles() {
    return true;
  }

  public boolean hasMappings() {
    synchronized (myMappings) {
      ensureStateLoaded();
      return !myMappings.isEmpty();
    }
  }

  private void installDeleteUndo() {
    Application app = ApplicationManager.getApplication();
    if (app == null) return;
    app.getMessageBus().connect(this).subscribe(VirtualFileManager.VFS_CHANGES, new BulkFileListener() {
      WeakReference<MyUndoableAction> lastAction;

      @Override
      public void before(@NotNull List<? extends VFileEvent> events) {
        if (CommandProcessor.getInstance().isUndoTransparentActionInProgress()) return;
        Project project = CommandProcessor.getInstance().getCurrentCommandProject();
        if (project == null || !project.isOpen()) return;

        MyUndoableAction action = createUndoableAction(events);
        if (action == null) return;
        action.doRemove(action.removed);
        lastAction = new WeakReference<>(action);
        UndoManager.getInstance(project).undoableActionPerformed(action);
      }

      @Override
      public void after(@NotNull List<? extends VFileEvent> events) {
        MyUndoableAction action = SoftReference.dereference(lastAction);
        lastAction = null;
        if (action != null) {
          ApplicationManager.getApplication().invokeLater(() -> action.doAdd(action.added));
        }
      }

      @Nullable
      MyUndoableAction createUndoableAction(@NotNull List<? extends VFileEvent> events) {
        // NOTE: VFS handles renames, so the code for RENAME events is deleted (see history)
        List<? extends VFileEvent> eventsFiltered = JBIterable.from(events).filter(VFileDeleteEvent.class).toList();
        if (eventsFiltered.isEmpty()) return null;

        Map<String, T> removed = ContainerUtil.newHashMap();
        Map<String, T> added = ContainerUtil.newHashMap();
        NavigableSet<VirtualFile> navSet = null;

        synchronized (myMappings) {
          ensureStateLoaded();
          for (VFileEvent event : eventsFiltered) {
            VirtualFile file = event.getFile();
            if (file == null) continue;
            String fileUrl = file.getUrl();
            if (!file.isDirectory()) {
              T m = myMappings.get(file);
              if (m != null) removed.put(fileUrl, m);
            }
            else {
              if (navSet == null) {
                navSet = new TreeSet<>(
                  (f1, f2) -> Comparing.compare(f1 == null ? null : f1.getUrl(), f2 == null ? null : f2.getUrl()));
                navSet.addAll(myMappings.keySet());
              }
              for (VirtualFile child : navSet.tailSet(file)) {
                if (!VfsUtilCore.isAncestor(file, child, false)) break;
                String childUrl = child.getUrl();
                T m = myMappings.get(child);
                removed.put(childUrl, m);
              }
            }
          }
        }
        return removed.isEmpty() && added.isEmpty() ? null : new MyUndoableAction(added, removed);
      }
    });
  }

  private class MyUndoableAction extends BasicUndoableAction {
    final Map<String, T> added;
    final Map<String, T> removed;

    MyUndoableAction(Map<String, T> added, Map<String, T> removed) {
      this.added = added;
      this.removed = removed;
    }

    @Override
    public void undo() {
      doRemove(added);
      doAdd(removed);
    }

    @Override
    public void redo() {
      doRemove(removed);
      doAdd(added);
    }

    void doAdd(Map<String, T> toAdd) {
      if (toAdd == null) return;
      for (String url : toAdd.keySet()) {
        VirtualFile file = VirtualFileManager.getInstance().findFileByUrl(url);
        if (file == null) continue;
        setMapping(file, toAdd.get(url));
      }
    }

    void doRemove(Map<String, T> toRemove) {
      if (toRemove != null) {
        synchronized (myMappings) {
          for (String url : toRemove.keySet()) {
            VirtualFile file = VirtualFileManager.getInstance().findFileByUrl(url);
            if (file == null) continue;
            myMappings.remove(file);
          }
        }
      }
    }
  }
}
