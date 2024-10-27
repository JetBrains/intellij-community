package com.jetbrains.rhizomedb

/**
 * Exception type, which is raised by [Mut] implementation if something goes wrong.
 * */
class TxValidationException(message: String) : RuntimeException(message)

class OutOfDbContext :
  RuntimeException("Free entities require context db to be used. Open transaction with db.tx or setup db to read with asOf(db)")

class OutOfMutableDbContext :
  RuntimeException("Free entities require mutable context db to be updated. Open transaction with db.tx or setup db to read with asOf(db)")

class EntityDoesNotExistException(message: String) : RuntimeException(message)

class EntityAttributeIsNotInitialized(entityDisplay: String, attributeDisplay: String) :
  RuntimeException("Access to not initialized attribute $attributeDisplay of entity $entityDisplay")
