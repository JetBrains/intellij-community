// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.dnd;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Ref;
import com.intellij.util.Function;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

/**
 * @author Konstantin Bulenkov
 */
public final class DnDSupport implements DnDTarget, DnDSource, DnDDropHandler.WithResult, Disposable {
  private final JComponent myComponent;
  private final Function<? super DnDActionInfo, ? extends DnDDragStartBean> myBeanProvider;
  private final Function<? super DnDActionInfo, ? extends DnDImage> myImageProvider;
  private final @Nullable DnDDropHandler.WithResult myHandler;
  private final @Nullable DnDTargetChecker myChecker;
  private final Runnable myDropEndedCallback;
  private final DnDDropActionHandler myDropActionHandler;
  private final Runnable myCleanUpCallback;
  private final boolean myAsTarget;
  private final boolean myAsSource;

  private DnDSupport(JComponent component,
                     Function<? super DnDActionInfo, ? extends DnDDragStartBean> beanProvider,
                     Function<? super DnDActionInfo, ? extends DnDImage> imageProvider,
                     DnDDropHandler.WithResult handler,
                     DnDTargetChecker checker,
                     Runnable dropEndedCallback,
                     Disposable parent,
                     DnDDropActionHandler dropActionHandler,
                     Runnable cleanUpCallback,
                     boolean asTarget,
                     boolean asSource,
                     boolean asNativeTarget) {
    myComponent = component;
    myBeanProvider = beanProvider;
    myImageProvider = imageProvider;
    myHandler = handler;
    myChecker = checker;
    myDropEndedCallback = dropEndedCallback;
    myDropActionHandler = dropActionHandler;
    myCleanUpCallback = cleanUpCallback;
    myAsTarget = asTarget;
    myAsSource = asSource;
    if (myAsTarget) {
      DnDManager.getInstance().registerTarget(asNativeTarget ? new DnDNativeTargetWrapper(this) : this, myComponent);
    }
    if (myAsSource) {
      DnDManager.getInstance().registerSource(this, myComponent);
    }
    if (parent != null) {
      Disposer.register(parent, this);
    }
  }

  @Override
  public boolean canStartDragging(DnDAction action, Point dragOrigin) {
    return myBeanProvider != null
           && myAsSource
           && myBeanProvider.fun(new DnDActionInfo(action, dragOrigin)) != null;
  }


  @Override
  public DnDDragStartBean startDragging(DnDAction action, Point dragOrigin) {
    return  myBeanProvider.fun(new DnDActionInfo(action, dragOrigin));
  }

  @Override
  public Pair<Image, Point> createDraggedImage(DnDAction action, Point dragOrigin) {
    if (myImageProvider != null) {
      final DnDImage image = myImageProvider.fun(new DnDActionInfo(action, dragOrigin));
      if (image != null) {
        final Point point = image.getPoint();
        return Pair.create(image.getImage(), point == null ? dragOrigin : point);
      }
    }
    return null;
  }

  @Override
  public void dragDropEnd() {
    if (myDropEndedCallback != null) {
      myDropEndedCallback.run();
    }
  }

  @Override
  public void dropActionChanged(int gestureModifiers) {
    if (myDropActionHandler != null) {
      myDropActionHandler.dropActionChanged(gestureModifiers);
    }
  }

  @Override
  public boolean update(DnDEvent event) {
    if (myChecker == null) {
      event.setDropPossible(true);
      return false;
    }
    return myChecker.update(event);
  }

  @Override
  public boolean tryDrop(DnDEvent aEvent) {
    return myHandler == null || myHandler.tryDrop(aEvent);
  }

  @Override
  public void cleanUpOnLeave() {
    if (myCleanUpCallback != null) {
      myCleanUpCallback.run();
    }
  }

  @Override
  public void dispose() {
    if (myAsSource) {
      DnDManager.getInstance().unregisterSource(this, myComponent);
    }
    if (myAsTarget) {
      DnDManager.getInstance().unregisterTarget(this, myComponent);
    }
  }

  private static final class DnDNativeTargetWrapper implements DnDNativeTarget, DnDDropHandler.WithResult {
    @NotNull private final DnDSupport myTarget;

    private DnDNativeTargetWrapper(@NotNull DnDSupport target) {
      myTarget = target;
    }

    @Override
    public void cleanUpOnLeave() {
      myTarget.cleanUpOnLeave();
    }

    @Override
    public void updateDraggedImage(Image image, Point dropPoint, Point imageOffset) {
      myTarget.updateDraggedImage(image, dropPoint, imageOffset);
    }

    @Override
    public boolean tryDrop(DnDEvent event) {
      return myTarget.tryDrop(event);
    }

    @Override
    public boolean update(DnDEvent event) {
      return myTarget.update(event);
    }
  }

  @NotNull
  public static DnDSupportBuilder createBuilder(@NotNull JComponent component) {
    final JComponent myComponent = component;
    final Ref<Boolean> asTarget = Ref.create(true);
    final Ref<Boolean> asSource = Ref.create(true);
    final Ref<Boolean> asNativeTarget = Ref.create(false);
    final Ref<Function<DnDActionInfo, DnDImage>> imageProvider = Ref.create(null);
    final Ref<Function<DnDActionInfo, DnDDragStartBean>> beanProvider = Ref.create(null);
    final Ref<Runnable> dropEnded = Ref.create(null);
    final Ref<Disposable> disposable = Ref.create(null);
    final Ref<DnDDropHandler.WithResult> dropHandler = Ref.create(null);
    final Ref<DnDTargetChecker> targetChecker = Ref.create(null);
    final Ref<DnDDropActionHandler> dropActionHandler = Ref.create(null);
    final Ref<Runnable> cleanUp = Ref.create(null);

    return new DnDSupportBuilder() {
      @Override
      public DnDSupportBuilder disableAsTarget() {
        asTarget.set(false);
        return this;
      }

      @Override
      public DnDSupportBuilder disableAsSource() {
        asSource.set(false);
        return this;
      }

      @Override
      public DnDSupportBuilder enableAsNativeTarget() {
        asNativeTarget.set(true);
        return this;
      }

      @Override
      public DnDSupportBuilder setImageProvider(Function<DnDActionInfo, DnDImage> fun) {
        imageProvider.set(fun);
        return this;
      }

      @Override
      public DnDSupportBuilder setBeanProvider(Function<DnDActionInfo, DnDDragStartBean> fun) {
        beanProvider.set(fun);
        return this;
      }

      @Override
      public DnDSupportBuilder setDropHandler(DnDDropHandler handler) {
        return setDropHandlerWithResult(e -> {
          handler.drop(e);
          return true;
        });
      }

      @Override
      public DnDSupportBuilder setDropHandlerWithResult(DnDDropHandler.WithResult handler) {
        dropHandler.set(handler);
        return this;
      }

      @Override
      public DnDSupportBuilder setTargetChecker(DnDTargetChecker checker) {
        targetChecker.set(checker);
        return this;
      }

      @Override
      public DnDSupportBuilder setDropActionHandler(DnDDropActionHandler handler) {
        dropActionHandler.set(handler);
        return this;
      }

      @Override
      public DnDSupportBuilder setDisposableParent(Disposable parent) {
        disposable.set(parent);
        return this;
      }

      @Override
      public DnDSupportBuilder setCleanUpOnLeaveCallback(Runnable callback) {
        cleanUp.set(callback);
        return this;
      }

      @Override
      public DnDSupportBuilder setDropEndedCallback(Runnable callback) {
        dropEnded.set(callback);
        return this;
      }

      @Override
      public void install() {
        new DnDSupport(myComponent,
                          beanProvider.get(),
                          imageProvider.get(),
                          dropHandler.get(),
                          targetChecker.get(),
                          dropEnded.get(),
                          disposable.get(),
                          dropActionHandler.get(),
                          cleanUp.get(),
                          asTarget.get(),
                          asSource.get(),
                          asNativeTarget.get());
      }
    };
  }
}
