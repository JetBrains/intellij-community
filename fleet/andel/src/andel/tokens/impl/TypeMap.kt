// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package andel.tokens.impl

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap

internal interface TypeMap<T> {
  operator fun get(typeId: Int): T
  fun builder(): TypeMapBuilder<T>
}

internal class TypeMapBuilder<T>(
  var typeIdToType: Int2ObjectOpenHashMap<T>,
  var typeToId: Object2IntOpenHashMap<T>,
  var nextId: Int,
) : TypeMap<T> {

  private var mappingCopied = false

  fun typeId(type: T): Int {
    val i = typeToId.getInt(type)
    return when {
      i != 0 -> i
      else -> {
        val mappingCopied = mappingCopied

        val typeIdToType =
          if (mappingCopied) {
            typeIdToType
          }
          else {
            Int2ObjectOpenHashMap(typeIdToType).also {
              typeIdToType = it
            }
          }

        val typeToId =
          if (mappingCopied) {
            typeToId
          }
          else {
            Object2IntOpenHashMap(typeToId).also {
              typeToId = it
            }
          }

        val id = nextId++
        typeIdToType[id] = type
        typeToId[type] = id
        this.mappingCopied = true
        id
      }
    }
  }

  fun build(): TypeMap<T> =
    TypeMapBuilder(
      typeIdToType = typeIdToType,
      typeToId = typeToId,
      nextId = nextId
    )

  override fun get(typeId: Int): T =
    typeIdToType[typeId]!!

  override fun builder(): TypeMapBuilder<T> =
    TypeMapBuilder(
      typeIdToType = typeIdToType,
      typeToId = typeToId,
      nextId = nextId
    )
}