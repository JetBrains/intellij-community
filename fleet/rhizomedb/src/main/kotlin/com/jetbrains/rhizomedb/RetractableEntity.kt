// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.rhizomedb

/**
 * An entity with auxiliary retraction logic
 */
interface RetractableEntity : Entity {
  fun interface Callback {
    fun ChangeScope.afterRetract()
  }

  /**
   * The method is called before retracting a graph of entities defined by [CascadeDelete] and [CascadeDeleteBy].
   * It's guaranteed that the graph contains the entity and none of the graph's entities are retracted.
   * You could read/modify existing entities inside the returned lambda which will be invoked after current instruction is finished.
   *
   * @throws TxValidationException if the method tries to add a datom which refers to an element of the graph.
   *
   * if you need to perform some action after entity retraction, please consider using Entity#lifetime
   */
  fun onRetract(): Callback? = null
}