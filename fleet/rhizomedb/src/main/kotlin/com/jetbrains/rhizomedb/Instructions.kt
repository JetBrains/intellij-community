// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.rhizomedb

import fleet.fastutil.ints.IntArrayList
import fleet.fastutil.ints.IntOpenHashSet
import fleet.fastutil.ints.isNotEmpty
import fleet.fastutil.ints.mapNotNull

data class CreateEntity(val eid: EID,
                        val entityTypeEid: EID,
                        val attributes: List<Pair<Attribute<*>, Any>>,
                        override val seed: TX) : Instruction {

  override fun DbContext<Q>.expand(): InstructionExpansion = InstructionExpansion(
    buildList {
      @Suppress("UNCHECKED_CAST")
      add(Op.Assert(eid, Entity.Type.attr as Attribute<EID>, entityTypeEid))
      if (entityTypeEid != EntityType.eid
          && entityTypeEid != EntityAttribute.eid
          && getOne(eid, Entity.EntityObject.attr) == null) {
        @Suppress("UNCHECKED_CAST")
        getOne(entityTypeEid, Entity.EntityObject.attr as Attribute<Entity>)?.let { et ->
          add(Op.Assert(eid, Entity.EntityObject.attr, (et as EntityType<*>).reify(eid)))
        }
      }

      attributes.forEach { (attr, value) ->
        add(Op.Assert(eid, attr, value))
      }
    }
  )
}

data class AtomicComposite(val instructions: List<Instruction>,
                           override val seed: Long) : Instruction {
  override fun DbContext<Q>.expand(): InstructionExpansion = run {
    val expansions = instructions.map { it.run { expand() } }
    InstructionExpansion(expansions.flatMap(InstructionExpansion::ops),
                         expansions.flatMap(InstructionExpansion::effects))
  }
}

data class EffectInstruction(val effect: DbContext<Mut>.() -> Unit) : Instruction {
  override val seed: Long get() = 0L

  override fun DbContext<Q>.expand(): InstructionExpansion =
    InstructionExpansion(emptyList(), listOf(InstructionEffect(this@EffectInstruction, effect)))
}

/**
 * When [EntityType] is loaded, all entities of this type should get their [Entity] object
 * i.e. Entities which we load from disk, or get from the internet could not have EntityType loaded, however
 * we are still able to store and modify them
 * */
data class ReifyEntities(val entityTypeEID: EID,
                         override val seed: TX) : Instruction {
  override fun DbContext<Q>.expand(): InstructionExpansion = InstructionExpansion(buildList {
    @Suppress("UNCHECKED_CAST")
    getOne(entityTypeEID, Entity.EntityObject.attr as Attribute<Entity>)?.let { et ->
      queryIndex(IndexQuery.LookupMany(Entity.Type.attr as Attribute<EID>, entityTypeEID)).forEach { (eid) ->
        if (getOne(eid, Entity.EntityObject.attr) == null) {
          add(Op.Assert(eid, Entity.EntityObject.attr, (et as EntityType<*>).reify(eid)))
        }
      }
    }
  })
}

data class Add<V : Any>(val eid: EID,
                        val attribute: Attribute<V>,
                        val value: V,
                        override val seed: Long) : Instruction {
  override fun DbContext<Q>.expand(): InstructionExpansion = run {
    impl.assertEntityExists(eid, attribute, null)
    InstructionExpansion(listOf(Op.Assert(eid, attribute, value)))
  }
}

data class Remove<V : Any>(val eid: EID,
                           val attribute: Attribute<V>,
                           val value: V,
                           override val seed: Long) : Instruction {

  override fun DbContext<Q>.expand(): InstructionExpansion =
    InstructionExpansion(listOf(Op.Retract(eid, attribute, value)))
}

data class RetractAttribute(val eid: EID,
                            val attribute: Attribute<*>,
                            override val seed: Long) : Instruction {

  override fun DbContext<Q>.expand(): InstructionExpansion =
    InstructionExpansion(buildList {
      queryIndex(IndexQuery.GetMany(eid, attribute)).forEach { datom ->
        if (datom.attr.schema.required) {
          throw TxValidationException("Retract required attribute ${displayAttribute(attribute)} of $eid")
        }
        add(Op.Retract(eid, attribute, datom.value))
      }
    })
}

data class RetractEntityInPartition(val eid: EID,
                                    override val seed: Long) : Instruction {

  override fun DbContext<Q>.expand(): InstructionExpansion {
    val res = ArrayList<Op>()
    val retractedEntities = IntOpenHashSet()
    val entitiesToRetract = IntArrayList()
    entitiesToRetract.add(eid)
    while (entitiesToRetract.isNotEmpty()) {
      val nextEID = entitiesToRetract.removeAt(entitiesToRetract.size - 1)
      if (retractedEntities.add(nextEID)) {
        queryIndex(IndexQuery.Entity(nextEID)).forEach { datom ->
          if (datom.attr.schema.cascadeDelete) {
            val value = datom.value as EID
            if (partition(value) == partition(eid)) {
              entitiesToRetract.add(value)
            }
          }
          res.add(Op.Retract(datom.eid, datom.attr, datom.value))
        }
        queryIndex(IndexQuery.RefsTo(nextEID)).forEach { datom ->
          when {
            datom.attr.schema.cascadeDeleteBy || datom.attr.schema.required -> {
              if (partition(datom.eid) == partition(eid)) {
                entitiesToRetract.add(datom.eid)
              }
            }
          }
          res.add(Op.Retract(datom.eid, datom.attr, datom.value))
        }
      }
    }
    return InstructionExpansion(
      ops = res,
      effects = retractedEntities.mapNotNull {
        (entity(it) as? RetractableEntity)?.onRetract()?.let { cb ->
          InstructionEffect(this@RetractEntityInPartition) {
            with(cb) {
              withChangeScope { afterRetract() }
            }
          }
        }
      })
  }
}