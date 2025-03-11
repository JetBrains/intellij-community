package org.jetbrains.jps.dependency.storage

interface EnumeratedStringDataTypeExternalizer<T : Any> {
  fun createStorage(size: Int): Array<T?>

  fun create(id: String): T

  fun getStringId(obj: T): String
}

interface StringEnumerator {
  fun enumerate(string: String): Int

  fun valueOf(id: Int): String
}