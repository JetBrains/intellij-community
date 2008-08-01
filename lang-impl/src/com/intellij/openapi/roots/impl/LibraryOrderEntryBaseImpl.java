package com.intellij.openapi.roots.impl;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.RootProvider;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.pointers.VirtualFilePointerContainer;
import com.intellij.openapi.vfs.pointers.VirtualFilePointerManager;
import com.intellij.openapi.vfs.pointers.VirtualFilePointerListener;
import com.intellij.openapi.vfs.pointers.VirtualFilePointer;
import com.intellij.util.containers.HashMap;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Map;

/**
 *  @author dsl
 */
abstract class LibraryOrderEntryBaseImpl extends OrderEntryBaseImpl {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.roots.impl.LibraryOrderEntryBaseImpl");
  private final Map<OrderRootType, VirtualFilePointerContainer> myRootContainers;
  private final MyRootSetChangedListener myRootSetChangedListener = new MyRootSetChangedListener();
  private RootProvider myCurrentlySubscribedRootProvider = null;
  protected final ProjectRootManagerImpl myProjectRootManagerImpl;

  LibraryOrderEntryBaseImpl(RootModelImpl rootModel, ProjectRootManagerImpl instanceImpl, VirtualFilePointerManager filePointerManager) {
    super(rootModel);
    myRootContainers = new HashMap<OrderRootType, VirtualFilePointerContainer>();
    for (OrderRootType type : OrderRootType.getAllTypes()) {
      myRootContainers.put(type, filePointerManager.createContainer(this, new VirtualFilePointerListener() {
        public void beforeValidityChanged(VirtualFilePointer[] pointers) {
           getRootModel().fireBeforeExternalChange();
        }

        public void validityChanged(VirtualFilePointer[] pointers) {
          getRootModel().fireAfterExternalChange();
        }
      }));
    }
    myProjectRootManagerImpl = instanceImpl;
  }

  protected final void init(RootProvider rootProvider) {
    if (rootProvider == null) return;
    updatePathsFromProviderAndSubscribe(rootProvider);
  }

  private void updatePathsFromProviderAndSubscribe(final RootProvider rootProvider) {
    updatePathsFromProvider(rootProvider);
    resubscribe(rootProvider);
  }

  private void updatePathsFromProvider(final RootProvider rootProvider) {
    final OrderRootType[] allTypes = OrderRootType.getAllTypes();
    for (OrderRootType type : allTypes) {
      final VirtualFilePointerContainer container = myRootContainers.get(type);
      container.clear();
      if (rootProvider != null) {
        final String[] urls = rootProvider.getUrls(type);
        for (String url : urls) {
          container.add(url);
        }
      }
    }
  }

  private boolean needUpdateFromProvider(final RootProvider rootProvider) {
    final OrderRootType[] allTypes = OrderRootType.getAllTypes();
    for (OrderRootType type : allTypes) {
      final VirtualFilePointerContainer container = myRootContainers.get(type);
      final String[] urls = container.getUrls();
      final String[] providerUrls = rootProvider.getUrls(type);
      if (!Arrays.equals(urls, providerUrls)) return true;
    }
    return false;
  }

  @NotNull
  public VirtualFile[] getFiles(OrderRootType type) {
    if (type == OrderRootType.COMPILATION_CLASSES || type == OrderRootType.CLASSES_AND_OUTPUT) {
      return myRootContainers.get(OrderRootType.CLASSES).getDirectories();
    }
    return myRootContainers.get(type).getDirectories();
  }

  @NotNull
  public String[] getUrls(OrderRootType type) {
    LOG.assertTrue(!getRootModel().getModule().isDisposed());
    if (type == OrderRootType.COMPILATION_CLASSES || type == OrderRootType.CLASSES_AND_OUTPUT) {
      return myRootContainers.get(OrderRootType.CLASSES).getUrls();
    }
    return myRootContainers.get(type).getUrls();
  }

  public VirtualFile[] getRootFiles(OrderRootType type) {
    return myRootContainers.get(type).getDirectories();
  }

  public String[] getRootUrls(OrderRootType type) {
    return myRootContainers.get(type).getUrls();
  }

  public final Module getOwnerModule() {
    return getRootModel().getModule();
  }

  protected void updateFromRootProviderAndSubscribe(RootProvider wrapper) {
    getRootModel().fireBeforeExternalChange();
    updatePathsFromProviderAndSubscribe(wrapper);
    getRootModel().fireAfterExternalChange();
  }

  private void updateFromRootProvider(RootProvider wrapper) {
    getRootModel().fireBeforeExternalChange();
    updatePathsFromProvider(wrapper);
    getRootModel().fireAfterExternalChange();
  }

  private void resubscribe(RootProvider wrapper) {
    unsubscribe();
    subscribe(wrapper);
  }

  private void subscribe(RootProvider wrapper) {
    if (wrapper != null) {
      addListenerToWrapper(wrapper, myRootSetChangedListener);
    }
    myCurrentlySubscribedRootProvider = wrapper;
  }

  protected void addListenerToWrapper(final RootProvider wrapper,
                                      final RootProvider.RootSetChangedListener rootSetChangedListener) {
    myProjectRootManagerImpl.addRootSetChangedListener(rootSetChangedListener, wrapper);
  }


  private void unsubscribe() {
    if (myCurrentlySubscribedRootProvider != null) {
      final RootProvider wrapper = myCurrentlySubscribedRootProvider;
      removeListenerFromWrapper(wrapper, myRootSetChangedListener);
    }
    myCurrentlySubscribedRootProvider = null;
  }

  protected void removeListenerFromWrapper(final RootProvider wrapper,
                                           final RootProvider.RootSetChangedListener rootSetChangedListener) {
    myProjectRootManagerImpl.removeRootSetChangedListener(rootSetChangedListener, wrapper);
  }


  public void dispose() {
    super.dispose();
    //for (VirtualFilePointerContainer virtualFilePointerContainer : new THashSet<VirtualFilePointerContainer>(myRootContainers.values())) {
    //  virtualFilePointerContainer.killAll();
    //}
    unsubscribe();
  }

  private class MyRootSetChangedListener implements RootProvider.RootSetChangedListener {

    public MyRootSetChangedListener() {
    }

    public void rootSetChanged(RootProvider wrapper) {
      if (needUpdateFromProvider(wrapper)) {
        updateFromRootProvider(wrapper);
      }
    }
  }
}
