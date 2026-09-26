/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
import com.intellij.util.Function;

/**
 * Builds the drag-and-drop support for one Swing component.
 * <p>
 * Get a builder with {@link DnDSupport#createBuilder(javax.swing.JComponent)}, set the parts that you need,
 * and call {@link #install()}. Each setter returns this builder, so you can chain the calls.
 * A second call to the same setter replaces the previous value.
 * <p>
 * The component acts both as a drag source and as a drop target by default.
 * Disable the side that you do not need. A source also needs a bean provider, because
 * {@link #setBeanProvider} supplies the dragged object.
 * <p>
 * Example:
 * <pre>{@code
 * DnDSupport.createBuilder(myTree)
 *   .disableAsTarget()
 *   .setBeanProvider(info -> new DnDDragStartBean(getSelectedNodes()))
 *   .setDisposableParent(myDisposable)
 *   .install();
 * }</pre>
 *
 * @author Konstantin Bulenkov
 * @see DnDSupport
 * @see DnDManager
 */
public interface DnDSupportBuilder {
  /**
   * Makes the component a drag source only. It then accepts no drop.
   */
  DnDSupportBuilder disableAsTarget();

  /**
   * Makes the component a drop target only. It then starts no drag.
   */
  DnDSupportBuilder disableAsSource();

  /**
   * Lets the target also accept a drop from outside the IDE, for example a file from the OS file manager.
   * The drop handler then gets an event with a native transferable.
   *
   * @see DnDNativeTarget
   */
  DnDSupportBuilder enableAsNativeTarget();

  /**
   * Sets the provider of the image that follows the pointer during the drag.
   * The provider can return {@code null}, and the platform then draws no image.
   */
  DnDSupportBuilder setImageProvider(Function<? super DnDActionInfo, ? extends DnDImage> provider);

  /**
   * Sets the provider of the dragged object. The provider gets the drag action and the start point.
   * It must return {@code null} if the component cannot start a drag at that point.
   * A source without this provider starts no drag.
   */
  DnDSupportBuilder setBeanProvider(Function<? super DnDActionInfo, ? extends DnDDragStartBean> provider);

  /**
   * Sets the handler that accepts the drop. The platform runs the handler in a write intent read action,
   * and always reports the drop as successful.
   * Use {@link #setDropHandlerWithResult} if the handler must report a failure.
   */
  DnDSupportBuilder setDropHandler(DnDDropHandler handler);

  /**
   * Sets the handler that accepts the drop and reports the result.
   * The handler runs as is, so it must request a write intent read action itself if it needs one.
   * A target without a drop handler accepts every drop and does nothing.
   */
  DnDSupportBuilder setDropHandlerWithResult(DnDDropHandler.WithResult handler);

  /**
   * Sets the checker that decides if the current drag can drop on the component.
   * The checker also paints the drop feedback, for example a highlight or a drop line.
   * A target without a checker allows every drop.
   */
  DnDSupportBuilder setTargetChecker(DnDTargetChecker checker);

  /**
   * Sets the handler that the platform calls when the user changes the drag action with a modifier key,
   * for example when a move becomes a copy.
   */
  DnDSupportBuilder setDropActionHandler(DnDDropActionHandler handler);

  /**
   * Sets the callback that runs on the source after the drag ends. The drag can end with a drop or with a cancel.
   * Use it to clean up the source state.
   */
  DnDSupportBuilder setDropEndedCallback(Runnable callback);

  /**
   * Sets the callback that runs on the target when the drag leaves the component or ends over it.
   * Use it to remove the drop feedback that the target checker painted.
   */
  DnDSupportBuilder setCleanUpOnLeaveCallback(Runnable callback);

  /**
   * Sets the parent disposable that unregisters the support.
   * Always set it, unless the support must live as long as the application.
   */
  DnDSupportBuilder setDisposableParent(Disposable parent);

  /**
   * Registers the support on the component. Call it once, at the end of the chain.
   */
  void install();
}
