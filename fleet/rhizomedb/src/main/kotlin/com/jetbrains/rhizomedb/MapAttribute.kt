// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.rhizomedb

import fleet.util.logging.logger

data class MapAttribute(val attr: Attribute<*>,
                        val f: (Any) -> Any) : Instruction {
  companion object {
    val log = logger<MapAttribute>()
  }

  override val seed: Long = 0L

  override fun DbContext<Q>.expand(): InstructionExpansion =
    InstructionExpansion(
      queryIndex(IndexQuery.Column(attr)).flatMap { datom ->
        val deser = kotlin.runCatching {
          f(datom.value)
        }.getOrElse { x ->
          log.error(x) {
            "error mapping attribute ${displayAttribute(attr)} value ${datom.value}"
          }
          DeserializationProblem.Exception(throwable = x, datom = datom)
        }
        listOf(Op.Retract(datom.eid, datom.attr, datom.value),
               Op.AssertWithTX(datom.eid, datom.attr, deser, datom.tx))
      }
    )
}

sealed class DeserializationProblem {
  abstract val datom: Datom

  data class Exception(val throwable: Throwable,
                       override val datom: Datom) : DeserializationProblem()

  data class GotNull(override val datom: Datom) : DeserializationProblem()
  data class Unexpected(override val datom: Datom) : DeserializationProblem()
}

fun DbContext<Q>.deserializationProblems(attrs: Iterable<Attribute<*>>): List<DeserializationProblem> =
  attrs.flatMap { attr ->
    queryIndex(IndexQuery.Column(attr)).mapNotNull { (_, _, v) -> v as? DeserializationProblem }
  }
