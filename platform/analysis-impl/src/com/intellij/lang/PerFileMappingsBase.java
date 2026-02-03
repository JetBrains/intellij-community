// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.*;
import com.intellij.openapi.vfs.newvfs.BulkFileListener;
import com.intellij.openapi.vfs.newvfs.events.VFileDeleteEvent;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import com.intellij.psi.FilePropertyKey;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.reference.SoftReference;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.JBIterable;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;
import org.jetbrains.annotations.Unmodifiable;

import java.lang.ref.WeakReference;
import java.util.*;

/**
 * @author gregsh
 */
public abstract class PerFileMappingsBase<T> implements PersistentStateComponent<Element>, PerFileMappingsEx<T>, Disposable {
  private final Project myProject;
  private List<PerFileMappingState> myDeferredMappings;
  private final Map<VirtualFile, MappingValue<T>> myMappings = new HashMap<>();

  public PerFileMappingsBase() {
    this(null);
  }

  public PerFileMappingsBase(@Nullable Project project) {
    myProject = project;
    installDeleteUndo();
  }

  @Override
  public void dispose() {
  }

  protected @Nullable FilePropertyPusher<T> getFilePropertyPusher() {
    return null;
  }

  protected @Nullable Project getProject() { return myProject; }

  @Override
  public @Unmodifiable @NotNull Map<VirtualFile, T> getMappings() {
    synchronized (myMappings) {
      ensureStateLoaded();
      cleanup();
      return doGetMappings();
    }
  }

  private @Unmodifiable @NotNull Map<VirtualFile, T> doGetMappings() {
    return ContainerUtil.map2Map(myMappings.keySet(), it -> Pair.create(it, myMappings.get(it).value()));
  }

  private void cleanup() {
    for (Iterator<Map.Entry<VirtualFile, MappingValue<T>>> it = myMappings.entrySet().iterator(); it.hasNext(); ) {
      Map.Entry<VirtualFile, MappingValue<T>> entry = it.next();
      VirtualFile file = entry.getKey();
      MappingValue<T> mapping = entry.getValue();
      if (file == null) continue;
      if (mapping == null || !file.isValid() || mapping.unknownValue() == null && isDefaultMapping(file, mapping.value())) {
        it.remove();
      }
    }
  }

  @Override
  public @Nullable T getMapping(@Nullable VirtualFile file) {
    T t = getConfiguredMapping(file);
    return t == null ? getDefaultMapping(file) : t;
  }

  public @Nullable T getConfiguredMapping(@Nullable VirtualFile file) {
    FilePropertyPusher<T> pusher = getFilePropertyPusher();
    return getMappingInner(file, pusher == null ? null : pusher.getFilePropertyKey(), false);
  }

  public @Nullable T getDirectlyConfiguredMapping(@Nullable VirtualFile file) {
    return getMappingInner(file, null, true);
  }

  private @Nullable T getMappingInner(@Nullable VirtualFile file, @Nullable FilePropertyKey<T> pusherKey, boolean forHierarchy) {
    if (file instanceof VirtualFileWindow window) {
      file = window.getDelegate();
    }
    VirtualFile originalFile = ObjectUtils.doIfNotNull(file, VirtualFileUtil::originalFile);
    if (Comparing.equal(originalFile, file)) originalFile = null;

    if (file != null) {
      T pushedValue = pusherKey == null ? null : pusherKey.getPersistentValue(file);
      if (pushedValue != null) return pushedValue;
    }
    if (originalFile != null) {
      T pushedValue = pusherKey == null ? null : pusherKey.getPersistentValue(originalFile);
      if (pushedValue != null) return pushedValue;
    }
    synchronized (myMappings) {
      ensureStateLoaded();
      if (myMappings.isEmpty()) return null; // fast path
      T t = getMappingForHierarchy(file);
      if (t != null) return t;
      t = getMappingForHierarchy(originalFile);
      if (t != null) return t;
      if (forHierarchy && file != null) return null;
      return getNotInHierarchy(originalFile != null ? originalFile : file, doGetMappings());
    }
  }

  protected @Nullable T getNotInHierarchy(@Nullable VirtualFile file, @NotNull Map<VirtualFile, T> mappings) {
    if (getProject() == null || file == null ||
        file.getFileSystem() instanceof NonPhysicalFileSystem ||
        !getProject().isDefault() && ProjectFileIndex.getInstance(getProject()).isInContent(file)) {
      return mappings.get(null);
    }
    return null;
  }

  private T getMappingForHierarchy(@Nullable VirtualFile file) {
    for (VirtualFile cur = file; cur != null; cur = cur.getParent()) {
      T t = doGetImmediateMapping(cur);
      if (t != null) return t;
    }
    return null;
  }

  @Override
  public @Nullable T getDefaultMapping(@Nullable VirtualFile file) {
    return null;
  }

  protected boolean isDefaultMapping(@NotNull VirtualFile file, @NotNull T mapping) {
    return false;
  }

  public @Nullable T getImmediateMapping(@Nullable VirtualFile file) {
    synchronized (myMappings) {
      ensureStateLoaded();
      return doGetImmediateMapping(file);
    }
  }

  private @Nullable T doGetImmediateMapping(@Nullable VirtualFile key) {
    MappingValue<T> mappingValue = myMappings.get(key);
    return mappingValue != null ? mappingValue.value() : null;
  }

  @Override
  public void setMappings(@NotNull Map<VirtualFile, T> mappings) {
    Collection<VirtualFile> oldFiles;
    synchronized (myMappings) {
      myDeferredMappings = null;
      oldFiles = new ArrayList<>(myMappings.keySet());
      Set<VirtualFile> oldAndNewKeys = new HashSet<>();
      oldAndNewKeys.addAll(myMappings.keySet());
      oldAndNewKeys.addAll(mappings.keySet());
      for (VirtualFile key : oldAndNewKeys) {
        T oldValue = doGetImmediateMapping(key);
        T newValue = mappings.get(key);
        if (!Objects.equals(oldValue, newValue)) {
          if (newValue == null) {
            myMappings.remove(key);
          }
          else {
            myMappings.put(key, MappingValue.known(newValue));
          }
        }
      }

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
        myMappings.put(file, MappingValue.known(value));
      }
    }
    List<VirtualFile> files = ContainerUtil.createMaybeSingletonList(file);
    handleMappingChange(files, files, false);
  }

  private void handleMappingChange(Collection<? extends VirtualFile> files, Collection<? extends VirtualFile> oldFiles, boolean includeOpenFiles) {
    Project project = getProject();
    FilePropertyPusher<T> pusher = getFilePropertyPusher();
    if (project != null && pusher != null) {
      for (VirtualFile oldFile : oldFiles) {
        if (oldFile == null) continue; // project
        pusher.getFilePropertyKey().setPersistentValue(oldFile, null);
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

  public abstract @Unmodifiable @NotNull List<T> getAvailableValues();

  protected abstract @Nullable String serialize(@NotNull T t);

  @Override
  public Element getState() {
    synchronized (myMappings) {
      if (myDeferredMappings != null) {
        return PerFileMappingState.write(myDeferredMappings, getValueAttribute());
      }

      cleanup();
      Element element = new Element("x");
      List<VirtualFile> files = new ArrayList<>(myMappings.keySet());
      files.sort((o1, o2) -> {
        if (o1 == null || o2 == null) return o1 == null ? o2 == null ? 0 : 1 : -1;
        return o1.getPath().compareTo(o2.getPath());
      });
      for (VirtualFile file : files) {
        MappingValue<T> mappingValue = myMappings.get(file);
        T value = mappingValue != null ? mappingValue.value() : null;
        String valueStr = mappingValue != null && mappingValue.unknownValue() != null ? mappingValue.unknownValue() :
                          value == null ? null :
                          serialize(value);
        if (valueStr == null) continue;
        Element child = new Element("file");
        element.addContent(child);
        child.setAttribute("url", file == null ? "PROJECT" : file.getUrl());
        child.setAttribute(getValueAttribute(), valueStr);
      }
      return element;
    }
  }

  protected @Nullable T handleUnknownMapping(@Nullable VirtualFile file, String value) {
    return null;
  }

  // better to not override
  @Deprecated
  protected @NotNull String getValueAttribute() {
    return "value";
  }

  @Override
  public void loadState(@NotNull Element element) {
    // read not under lock
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
      Map<String, T> valuesMap = new HashMap<>();
      for (T value : getAvailableValues()) {
        String key = value == null ? null : serialize(value);
        if (key != null) {
          valuesMap.put(key, value);
        }
      }
      myMappings.clear();
      for (PerFileMappingState entry : state) {
        String url = entry.getUrl();
        String valueStr = entry.getValue();
        if (valueStr == null) continue;
        VirtualFile file;
        if ("PROJECT".equals(url)) {
          file = null;
        }
        else {
          file = VirtualFileManager.getInstance().findFileByUrl(url);
          if (file == null) continue;
        }

        T value = valuesMap.get(valueStr);
        if (value != null) {
          if (file == null || !isDefaultMapping(file, value)) {
            myMappings.put(file, MappingValue.known(value));
          }
        }
        else {
          value = handleUnknownMapping(file, valueStr);
          if (value != null) {
            myMappings.put(file, MappingValue.unknown(value, valueStr));
          }
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
      public void before(@NotNull List<? extends @NotNull VFileEvent> events) {
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
      public void after(@NotNull List<? extends @NotNull VFileEvent> events) {
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

        Map<String, T> removed = new HashMap<>();
        NavigableSet<VirtualFile> navSet = null;

        synchronized (myMappings) {
          ensureStateLoaded();
          for (VFileEvent event : eventsFiltered) {
            VirtualFile file = event.getFile();
            if (file == null) continue;
            String fileUrl = file.getUrl();
            if (!file.isDirectory()) {
              T m = doGetImmediateMapping(file);
              if (m != null) {
                removed.put(fileUrl, m);
              }
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
                T m = doGetImmediateMapping(child);
                removed.put(childUrl, m);
              }
            }
          }
        }
        return removed.isEmpty() ? null : new MyUndoableAction(new HashMap<>(), removed);
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

  private record MappingValue<T>(@NotNull T value, @Nullable String unknownValue) {
    static <T> @NotNull MappingValue<T> known(@NotNull T t) {
      return new MappingValue<T>(t, null);
    }

    static <T> @NotNull MappingValue<T> unknown(@NotNull T defaultValue, @NotNull String unknownValue) {
      return new MappingValue<T>(defaultValue, unknownValue);
    }
  }
}
