/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
public class DnDSupport implements DnDTarget, DnDSource, Disposable {
  private final JComponent myComponent;
  private final Function<DnDActionInfo, DnDDragStartBean> myBeanProvider;
  private final Function<DnDActionInfo, DnDImage> myImageProvider;
  private final @Nullable DnDDropHandler myHandler;
  private final @Nullable DnDTargetChecker myChecker;
  private final Runnable myDropEndedCallback;
  private final DnDDropActionHandler myDropActionHandler;
  private final Runnable myCleanUpCallback;
  private final boolean myAsTarget;
  private final boolean myAsSource;

  private DnDSupport(JComponent component,
                     Function<DnDActionInfo, DnDDragStartBean> beanProvider,
                     Function<DnDActionInfo, DnDImage> imageProvider,
                     DnDDropHandler handler,
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
  public void drop(DnDEvent aEvent) {
    if (myHandler != null) {
      myHandler.drop(aEvent);
    }
  }

  @Override
  public void cleanUpOnLeave() {
    if (myCleanUpCallback != null) {
      myCleanUpCallback.run();
    }
  }

  @Override
  public void updateDraggedImage(Image image, Point dropPoint, Point imageOffset) {
    //TODO[kb] Create DnDDraggedImageUpdater interface
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

  private static class DnDNativeTargetWrapper implements DnDNativeTarget {
    @NotNull private final DnDTarget myTarget;

    private DnDNativeTargetWrapper(@NotNull DnDTarget target) {
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
    public void drop(DnDEvent event) {
      myTarget.drop(event);
    }

    @Override
    public boolean update(DnDEvent event) {
      return myTarget.update(event);
    }
  }

  public static DnDSupportBuilder createBuilder(JComponent component) {
    final JComponent myComponent = component;
    final Ref<Boolean> asTarget = Ref.create(true);
    final Ref<Boolean> asSource = Ref.create(true);
    final Ref<Boolean> asNativeTarget = Ref.create(false);
    final Ref<Function<DnDActionInfo, DnDImage>> imageProvider = Ref.create(null);
    final Ref<Function<DnDActionInfo, DnDDragStartBean>> beanProvider = Ref.create(null);
    final Ref<Runnable> dropEnded = Ref.create(null);
    final Ref<Disposable> disposable = Ref.create(null);
    final Ref<DnDDropHandler> dropHandler = Ref.create(null);
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
