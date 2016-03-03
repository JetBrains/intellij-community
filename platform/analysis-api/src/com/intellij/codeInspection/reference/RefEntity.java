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
package com.intellij.codeInspection.reference;

import com.intellij.openapi.util.UserDataHolder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.List;

/**
 * Base class for nodes in the reference graph built during the global inspection pass.
 *
 * @author anna
 * @since 6.0
 * @see RefManager
 */
public interface RefEntity extends UserDataHolder {
  RefEntity[] EMPTY_ELEMENTS_ARRAY = new RefEntity[0];

  /**
   * Returns the name of the node.
   *
   * @return the name of the node.
   */
  @NotNull
  String getName();

  @NotNull
  String getQualifiedName();

  /**
   * Returns the list of children of the node.
   *
   * @return the list of children.
   */
  @Nullable
  List<RefEntity> getChildren();

  /**
   * Returns the parent of the node.
   *
   * @return the parent of the node.
   */
  RefEntity getOwner();

  /**
   * Accepts the specified visitor and passes self to one of its visit methods.
   *
   * @param refVisitor the visitor to accept.
   */
  void accept(@NotNull RefVisitor refVisitor);

  /**
   * Returns a user-readable name for the element corresponding to the node.
   *
   * @return the user-readable name.
   */
  String getExternalName();

  /**
   * Checks if the element corresponding to the node is valid.
   *
   * @return true if the element is valid, false otherwise.
   */
  boolean isValid();

  /**
   * Returns the reference graph manager for the node.
   *
   * @return the reference graph element for the instance.
   */
  @NotNull
  RefManager getRefManager();

  @Nullable
  Icon getIcon(boolean expanded);
}
