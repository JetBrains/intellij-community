// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.rhizomedb

/**
 * Exception type, which is raised by [Mut] implementation if something goes wrong.
 * */
class TxValidationException(message: String) : RuntimeException(message)

class OutOfDbContext :
  RuntimeException("""
    Free entities require context db to be used. Open transaction with db.tx or setup db to read with asOf(db).
    
    For IntelliJ: Most probably RhizomeDB context is not properly propagated. By default DB context is propagated for EDT and Services' coroutine scopes.
    If you created your own CoroutineScope or used `GlobalScope.childScope()`, then you have to use `withKernel { ... }` wrapper, so DB will be properly propagated there.
    But, please, avoid creating your own CoroutineScopes and don't use `GlobalScope`
  """.trimIndent())

class OutOfMutableDbContext :
  RuntimeException("Free entities require mutable context db to be updated. Open transaction with db.tx or setup db to read with asOf(db)")

class EntityDoesNotExistException(message: String) : RuntimeException(message)

class EntityAttributeIsNotInitialized(entityDisplay: String, attributeDisplay: String) :
  RuntimeException("Access to not initialized attribute $attributeDisplay of entity $entityDisplay")
