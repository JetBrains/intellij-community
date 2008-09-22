package com.intellij.openapi.vfs.impl;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.*;
import com.intellij.openapi.vfs.ex.VirtualFileManagerEx;
import com.intellij.openapi.vfs.ex.dummy.DummyFileSystem;
import com.intellij.openapi.vfs.newvfs.BulkFileListener;
import com.intellij.openapi.vfs.newvfs.events.*;
import com.intellij.openapi.vfs.pointers.*;
import com.intellij.util.messages.MessageBus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;

import java.util.*;

public class VirtualFilePointerManagerImpl extends VirtualFilePointerManager implements ApplicationComponent{
  private final Map<VirtualFilePointerListener, TreeMap<String, VirtualFilePointerImpl>> myUrlToPointerMaps = new LinkedHashMap<VirtualFilePointerListener, TreeMap<String, VirtualFilePointerImpl>>();
  private final List<VirtualFilePointerContainerImpl> myContainers = new ArrayList<VirtualFilePointerContainerImpl>();
  private final VirtualFileManagerEx myVirtualFileManager;

  VirtualFilePointerManagerImpl(@NotNull VirtualFileManagerEx virtualFileManagerEx, MessageBus bus) {
    myVirtualFileManager = virtualFileManagerEx;
    bus.connect().subscribe(VirtualFileManager.VFS_CHANGES, new VFSEventsProcessor());
  }

  void clearPointerCaches(String url, VirtualFilePointerListener listener) {
    TreeMap<String, VirtualFilePointerImpl> urlToPointer = myUrlToPointerMaps.get(listener);
    if (urlToPointer == null && ApplicationManager.getApplication().isUnitTestMode()) return;
    urlToPointer.remove(VfsUtil.urlToPath(url));
    if (urlToPointer.isEmpty()) {
      myUrlToPointerMaps.remove(listener);
    }
  }

  private class EventDescriptor {
    private final VirtualFilePointerListener myListener;
    private final VirtualFilePointer[] myPointers;

    private EventDescriptor(@NotNull VirtualFilePointerListener listener, @NotNull List<VirtualFilePointer> pointers) {
      myListener = listener;
      Collection<VirtualFilePointerImpl> set = myUrlToPointerMaps.get(listener).values();
      ArrayList<VirtualFilePointer> result = new ArrayList<VirtualFilePointer>(pointers);
      result.retainAll(set);
      myPointers = result.isEmpty() ? VirtualFilePointer.EMPTY_ARRAY  : result.toArray(new VirtualFilePointer[result.size()]);
    }

    public void fireBefore() {
      if (myPointers.length != 0) {
        myListener.beforeValidityChanged(myPointers);
      }
    }

    public void fireAfter() {
      if (myPointers.length != 0) {
        myListener.validityChanged(myPointers);
      }
    }
  }

  private List<VirtualFilePointer> getPointersUnder(String url) {
    List<VirtualFilePointer> pointers = new ArrayList<VirtualFilePointer>();
    for (TreeMap<String, VirtualFilePointerImpl> urlToPointer : myUrlToPointerMaps.values()) {
      for (String pointerUrl : urlToPointer.keySet()) {
        if (startsWith(url, pointerUrl)) {
          VirtualFilePointer pointer = urlToPointer.get(pointerUrl);
          if (pointer != null) {
            pointers.add(pointer);
          }
        }
      }
    }
    return pointers;
  }

  private static boolean startsWith(final String url, final String pointerUrl) {
    String urlSuffix = stripSuffix(url);
    String pointerPrefix = stripToJarPrefix(pointerUrl);
    if (urlSuffix.length() > 0) {
      return Comparing.equal(stripToJarPrefix(url), pointerPrefix, SystemInfo.isFileSystemCaseSensitive) &&
             StringUtil.startsWith(urlSuffix, stripSuffix(pointerUrl));
    }

    return FileUtil.startsWith(pointerPrefix, stripToJarPrefix(url));
  }

  private static String stripToJarPrefix(String url) {
    int separatorIndex = url.indexOf(JarFileSystem.JAR_SEPARATOR);
    if (separatorIndex < 0) return url;
    return url.substring(0, separatorIndex);
  }

  private static String stripSuffix(String url) {
    int separatorIndex = url.indexOf(JarFileSystem.JAR_SEPARATOR);
    if (separatorIndex < 0) return "";
    return url.substring(separatorIndex + JarFileSystem.JAR_SEPARATOR.length());
  }

  @TestOnly
  public synchronized void cleanupForNextTest() {
    myUrlToPointerMaps.clear();
    myContainers.clear();
  }

  @Deprecated
  public synchronized VirtualFilePointer create(String url, VirtualFilePointerListener listener) {
    return create(url, this, listener);
  }

  @NotNull
  public synchronized VirtualFilePointer create(@NotNull String url, @NotNull Disposable parent,VirtualFilePointerListener listener) {
    return create(null, url, parent, listener);
  }

  @Deprecated
  public synchronized VirtualFilePointer create(VirtualFile file, VirtualFilePointerListener listener) {
    return create(file, this, listener);
  }

  @NotNull
  public synchronized VirtualFilePointer create(@NotNull VirtualFile file, @NotNull Disposable parent, VirtualFilePointerListener listener) {
    return create(file, file.getUrl(), parent,listener);
  }

  private VirtualFilePointer create(VirtualFile file, String url, @NotNull final Disposable parentDisposable, VirtualFilePointerListener listener) {
    if (file != null && file.getFileSystem() instanceof DummyFileSystem) {
      return new VirtualFilePointerImpl(file, file.getUrl(), myVirtualFileManager, listener, parentDisposable);
    }

    TreeMap<String, VirtualFilePointerImpl> pathToPointer = getPathToPointerMap(listener);

    url = FileUtil.toSystemIndependentName(url);
    String protocol = VirtualFileManager.extractProtocol(url);
    VirtualFileSystem fileSystem = myVirtualFileManager.getFileSystem(protocol);
    assert fileSystem != null: "Illegal url: '"+url+"'";

    url = stripTrailingPathSeparator(url, protocol);

    String path = urlToPath(url);

    VirtualFilePointerImpl pointer = pathToPointer.get(path);
    final boolean notexists = pointer == null;

    if (notexists) {
      pointer = new VirtualFilePointerImpl(file, url, myVirtualFileManager, listener, parentDisposable);
      pathToPointer.put(path, pointer);
    }

    int newCount = pointer.incrementUsageCount();

    if (newCount == 1) {
      Disposer.register(parentDisposable, pointer);
    }
    else {
      //already registered
      final VirtualFilePointerImpl pointer1 = pointer;
      Disposer.register(parentDisposable, new Disposable() {
        public void dispose() {
          pointer1.dispose();
        }
      });
    }

    return pointer;
  }

  private static String stripTrailingPathSeparator(String url, String protocol) {
    String tail = StringUtil.trimStart(url, protocol);
    if (protocol.equals(JarFileSystem.PROTOCOL)) {
      int separator = tail.lastIndexOf(JarFileSystem.JAR_SEPARATOR);
      if (separator != -1) {
        tail = tail.substring(separator + JarFileSystem.JAR_SEPARATOR.length());
      }
    }
    while (tail.endsWith("/")) {
      tail = StringUtil.trimEnd(tail, "/");
      url = StringUtil.trimEnd(url, "/");
    }
    return url;
  }

  private String urlToPath(@NotNull String url) {
    VirtualFile virtualFile = myVirtualFileManager.findFileByUrl(url);
    if (virtualFile != null) return virtualFile.getPath();
    return VfsUtil.urlToPath(url);
  }

  private TreeMap<String, VirtualFilePointerImpl> getPathToPointerMap(VirtualFilePointerListener listener) {
    TreeMap<String, VirtualFilePointerImpl> urlToPointer = myUrlToPointerMaps.get(listener);
    if (urlToPointer == null) {
      urlToPointer = new TreeMap<String, VirtualFilePointerImpl>(SystemInfo.isFileSystemCaseSensitive ? new Comparator<String>() {
        public int compare(@NotNull String url1, @NotNull String url2) {
          return url1.compareTo(url2);
        }
      } : new Comparator<String>() {
        public int compare(@NotNull String url1, @NotNull String url2) {
          return url1.compareToIgnoreCase(url2);
        }
      });
      myUrlToPointerMaps.put(listener, urlToPointer);
    }
    return urlToPointer;
  }

  @Deprecated // see com.intellij.openapi.vfs.impl.VirtualFilePointerManagerImpl.duplicate()
  public synchronized VirtualFilePointer duplicate(VirtualFilePointer pointer, VirtualFilePointerListener listener) {
    return duplicate(pointer, this, listener);
  }

  @NotNull
  public synchronized VirtualFilePointer duplicate(@NotNull VirtualFilePointer pointer, @NotNull Disposable parent,
                                                   VirtualFilePointerListener listener) {
    return create(pointer.getUrl(), parent, listener);
  }

  @Deprecated()
  public synchronized void kill(VirtualFilePointer pointer, final VirtualFilePointerListener listener) {
  }

  public void initComponent() {
  }

  public void disposeComponent() {
    Disposer.dispose(this);
    for (Map.Entry<VirtualFilePointerListener, TreeMap<String, VirtualFilePointerImpl>> entry : myUrlToPointerMaps.entrySet()) {
      VirtualFilePointerListener listener = entry.getKey();
      TreeMap<String, VirtualFilePointerImpl> map = entry.getValue();
      for (VirtualFilePointerImpl pointer : map.values()) {
        pointer.throwNotDisposedError("Not disposed pointer: listener="+listener);
      }
    }

    //if (myListenerToPointersMap.isEmpty()) {
    //  System.err.println("All pointers are disposed");
    //}
    for (VirtualFilePointerContainerImpl container : myContainers) {
      throw new RuntimeException("Not disposed container " + container);
    }
  }

  public void dispose() {
    int i = 0;
  }

  @NotNull
  public String getComponentName() {
    return "SmartVirtualPointerManager";
  }

  private void cleanContainerCaches() {
    for (VirtualFilePointerContainerImpl container : myContainers) {
      container.dropCaches();
    }
  }

  @Deprecated
  public synchronized VirtualFilePointerContainer createContainer() {
    return createContainer(this);
  }

  @Deprecated // see createContainer(VirtualFilePointerFactory factory, Disposable parent)
  public synchronized VirtualFilePointerContainer createContainer(final VirtualFilePointerFactory factory) {
    final VirtualFilePointerContainerImpl virtualFilePointerContainer = new VirtualFilePointerContainerImpl(this, this, null){
      @Override
      protected VirtualFilePointer create(VirtualFile file) {
        return factory.create(file);
      }

      @Override
      protected VirtualFilePointer create(String url) {
        return factory.create(url);
      }

      @Override
      protected VirtualFilePointer duplicate(VirtualFilePointer virtualFilePointer) {
        return factory.duplicate(virtualFilePointer);
      }
    };
    return registerContainer(this, virtualFilePointerContainer);
  }

  @NotNull
  public VirtualFilePointerContainer createContainer(@NotNull Disposable parent) {
    return createContainer(parent, null);
  }

  @NotNull
  public synchronized VirtualFilePointerContainer createContainer(@NotNull Disposable parent, VirtualFilePointerListener listener) {
    return registerContainer(parent, new VirtualFilePointerContainerImpl(this, parent, listener));
  }

  private VirtualFilePointerContainer registerContainer(Disposable parent, @NotNull final VirtualFilePointerContainerImpl virtualFilePointerContainer) {
    myContainers.add(virtualFilePointerContainer);
    Disposer.register(parent, new Disposable() {
      public void dispose() {
        virtualFilePointerContainer.dispose();
        boolean removed = false;
        // compare by identity since VirtualFilePointerContainer has too smart equals
        for (int i = 0; i < myContainers.size(); i++) {
          VirtualFilePointerContainerImpl container = myContainers.get(i);
          if (container == virtualFilePointerContainer) {
            myContainers.remove(i);
            removed = true;
            break;
          }
        }
        if (!ApplicationManager.getApplication().isUnitTestMode()) {
          assert removed;
        }
      }

      public String toString() {
        return "Disposing container " + virtualFilePointerContainer;
      }
    });
    return virtualFilePointerContainer;
  }

  private class VFSEventsProcessor implements BulkFileListener {
    private List<EventDescriptor> myEvents = null;
    private List<String> myUrlsToUpdate = null;
    private List<VirtualFilePointer> myPointersToUdate = null;

    public void before(final List<? extends VFileEvent> events) {
      cleanContainerCaches();
      List<VirtualFilePointer> toFireEvents = new ArrayList<VirtualFilePointer>();
      List<String> toUpdateUrl = new ArrayList<String>();

      for (VFileEvent event : events) {
        if (event instanceof VFileDeleteEvent) {
          final VFileDeleteEvent deleteEvent = (VFileDeleteEvent)event;
          String url = deleteEvent.getFile().getPath();
          toFireEvents.addAll(getPointersUnder(url));
        }
        else if (event instanceof VFileCreateEvent) {
          final VFileCreateEvent createEvent = (VFileCreateEvent)event;
          String url = createEvent.getPath();
          toFireEvents.addAll(getPointersUnder(url));
        }
        else if (event instanceof VFileCopyEvent) {
          final VFileCopyEvent copyEvent = (VFileCopyEvent)event;
          String url = copyEvent.getNewParent().getPath() + "/" + copyEvent.getFile().getName();
          toFireEvents.addAll(getPointersUnder(url));
        }
        else if (event instanceof VFileMoveEvent) {
          final VFileMoveEvent moveEvent = (VFileMoveEvent)event;
          List<VirtualFilePointer> pointers = getPointersUnder(moveEvent.getFile().getPath());
          for (VirtualFilePointer pointer : pointers) {
            VirtualFile file = pointer.getFile();
            if (file != null) {
              toUpdateUrl.add(file.getPath());
            }
          }
        }
        else if (event instanceof VFilePropertyChangeEvent) {
          final VFilePropertyChangeEvent change = (VFilePropertyChangeEvent)event;
          if (VirtualFile.PROP_NAME.equals(change.getPropertyName())) {
            List<VirtualFilePointer> pointers = getPointersUnder(change.getFile().getPath());
            for (VirtualFilePointer pointer : pointers) {
              VirtualFile file = pointer.getFile();
              if (file != null) {
                toUpdateUrl.add(file.getPath());
              }
            }
          }
        }
      }

      myEvents = new ArrayList<EventDescriptor>();
      for (VirtualFilePointerListener listener : myUrlToPointerMaps.keySet()) {
        if (listener == null) continue;
        EventDescriptor event = new EventDescriptor(listener, toFireEvents);
        myEvents.add(event);
        event.fireBefore();
      }

      myPointersToUdate = toFireEvents;
      myUrlsToUpdate = toUpdateUrl;
    }

    public void after(final List<? extends VFileEvent> events) {
      cleanContainerCaches();

      if (myUrlsToUpdate != null) {
        for (String url : myUrlsToUpdate) {
          for (TreeMap<String, VirtualFilePointerImpl> urlToPointer : myUrlToPointerMaps.values()) {
            VirtualFilePointerImpl pointer = urlToPointer.remove(url);
            if (pointer != null) {
              String path = VfsUtil.urlToPath(pointer.getUrl());
              urlToPointer.put(path, pointer);
            }
          }
        }

        for (VirtualFilePointer pointer : myPointersToUdate) {
          ((VirtualFilePointerImpl)pointer).update();
        }

        for (EventDescriptor event : myEvents) {
          event.fireAfter();
        }

        myUrlsToUpdate = null;
        myEvents = null;
        myPointersToUdate = null;
      }
    }
  }
}
