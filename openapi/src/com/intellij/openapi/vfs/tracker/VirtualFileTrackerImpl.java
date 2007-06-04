package com.intellij.openapi.vfs.tracker;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.*;
import com.intellij.util.containers.ConcurrentHashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author mike
 */
public class VirtualFileTrackerImpl implements VirtualFileTracker {
  private Map<String, Set<VirtualFileListener>> myNonRefreshTrackers = new ConcurrentHashMap<String, Set<VirtualFileListener>>();
  private Map<String, Set<VirtualFileListener>> myAllTrackers = new ConcurrentHashMap<String, Set<VirtualFileListener>>();

  public VirtualFileTrackerImpl(VirtualFileManager virtualFileManager) {
    virtualFileManager.addVirtualFileListener(new VirtualFileListener() {
      public void propertyChanged(final VirtualFilePropertyEvent event) {
        final Collection<VirtualFileListener> listeners = getListeners(event.getFile(), event.isFromRefresh());
        if (listeners == null) return;

        for (VirtualFileListener listener : listeners) {
          listener.propertyChanged(event);
        }
      }

      public void contentsChanged(final VirtualFileEvent event) {
        final Collection<VirtualFileListener> listeners = getListeners(event.getFile(), event.isFromRefresh());
        if (listeners == null) return;

        for (VirtualFileListener listener : listeners) {
          listener.contentsChanged(event);
        }
      }

      public void fileCreated(final VirtualFileEvent event) {
        final Collection<VirtualFileListener> listeners = getListeners(event.getFile(), event.isFromRefresh());
        if (listeners == null) return;

        for (VirtualFileListener listener : listeners) {
          listener.fileCreated(event);
        }
      }

      public void fileDeleted(final VirtualFileEvent event) {
        final Collection<VirtualFileListener> listeners = getListeners(event.getFile(), event.isFromRefresh());
        if (listeners == null) return;

        for (VirtualFileListener listener : listeners) {
          listener.fileDeleted(event);
        }
      }

      public void fileMoved(final VirtualFileMoveEvent event) {
        final Collection<VirtualFileListener> listeners = getListeners(event.getFile(), event.isFromRefresh());
        if (listeners == null) return;

        for (VirtualFileListener listener : listeners) {
          listener.fileMoved(event);
        }
      }

      public void fileCopied(final VirtualFileCopyEvent event) {
        final Collection<VirtualFileListener> listeners = getListeners(event.getFile(), event.isFromRefresh());
        if (listeners == null) return;

        for (VirtualFileListener listener : listeners) {
          listener.fileCopied(event);
        }
      }

      public void beforePropertyChange(final VirtualFilePropertyEvent event) {
        final Collection<VirtualFileListener> listeners = getListeners(event.getFile(), event.isFromRefresh());
        if (listeners == null) return;

        for (VirtualFileListener listener : listeners) {
          listener.beforePropertyChange(event);
        }
      }

      public void beforeContentsChange(final VirtualFileEvent event) {
        final Collection<VirtualFileListener> listeners = getListeners(event.getFile(), event.isFromRefresh());
        if (listeners == null) return;

        for (VirtualFileListener listener : listeners) {
          listener.beforeContentsChange(event);
        }
      }

      public void beforeFileDeletion(final VirtualFileEvent event) {
        final Collection<VirtualFileListener> listeners = getListeners(event.getFile(), event.isFromRefresh());
        if (listeners == null) return;

        for (VirtualFileListener listener : listeners) {
          listener.beforeFileDeletion(event);
        }
      }

      public void beforeFileMovement(final VirtualFileMoveEvent event) {
        final Collection<VirtualFileListener> listeners = getListeners(event.getFile(), event.isFromRefresh());
        if (listeners == null) return;

        for (VirtualFileListener listener : listeners) {
          listener.beforeFileMovement(event);
        }
      }
    });
  }

  public void addTracker(
    @NotNull final String fileUrl,
    @NotNull final VirtualFileListener listener,
    final boolean fromRefreshOnly,
    @NotNull Disposable parentDisposable) {

    getSet(fileUrl, myAllTrackers).add(listener);

    if (!fromRefreshOnly) {
      getSet(fileUrl, myNonRefreshTrackers).add(listener);
    }

    Disposer.register(parentDisposable, new Disposable() {
      public void dispose() {
        getSet(fileUrl, myAllTrackers).remove(listener);
        if (!fromRefreshOnly) {
          getSet(fileUrl, myNonRefreshTrackers).remove(listener);
        }
      }
    });
  }

  private static Set<VirtualFileListener> getSet(final String fileUrl, final Map<String, Set<VirtualFileListener>> map) {
    Set<VirtualFileListener> listeners = map.get(fileUrl);

    if (listeners == null) {
      listeners = new ConcurrentHashSet<VirtualFileListener>();
      map.put(fileUrl, listeners);
    }
    return listeners;
  }

  @Nullable
  private Collection<VirtualFileListener> getListeners(VirtualFile virtualFile, boolean fromRefresh) {
    final String url = virtualFile.getUrl();

    final Set<VirtualFileListener> listeners;

    if (!fromRefresh) {
      listeners = myNonRefreshTrackers.get(url);
    }
    else {
      listeners = myAllTrackers.get(url);
    }

    if (listeners == null || listeners.isEmpty()) return null;

    return listeners;
  }
}
