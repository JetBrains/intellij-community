// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.kernel.rebase

import com.jetbrains.rhizomedb.*
import com.jetbrains.rhizomedb.impl.EidGen
import com.jetbrains.rhizomedb.impl.generateSeed
import fleet.kernel.*
import fleet.util.UID
import kotlinx.collections.immutable.persistentListOf

internal fun <T> ChangeScope.sharedImpl(
  eidMemoizer: Memoizer<EID>,
  uidMemoizer: Memoizer<UID>,
  mutableNovelty: (Datom) -> Unit,
  instructionEncoder: InstructionEncoder,
  idMappings: List<Map<EID, UID>>,
  f: SharedChangeScope.() -> T,
): Pair<T, List<InstructionsPair>> =
  let { changeScope ->
    val uidAttribute = uidAttribute()
    eidMemoizer.nextEpoch()
    uidMemoizer.nextEpoch()
    val serContext = InstructionEncodingContext(
      uidAttribute = uidAttribute,
      encoder = instructionEncoder
    )
    val instructions = ArrayList<InstructionsPair>()
    val mut = context.impl
    val t = context.alter(
      mut.mutableDb
        .queryRecording(
          serContext = serContext,
          recorder = instructions::add,
          eidToUid = { eid ->
            when {
              partition(eid) == SharedPart ->
                getOne(eid, uidAttribute) ?: idMappings.firstNotNullOfOrNull { mapping -> mapping[eid] }
              else -> null
            }
          }
        )
        .instructionsRecording(
          serContext = serContext,
          recorder = instructions::add
        )
        .collectingNovelty(mutableNovelty)
        .preventReadsFromLocal()
        .preventRefsFromShared()
        .executingEffects(DbContext(mut, null))
        .withDefaultPart(SharedPart)
        .withIdMemoization(eidMemoizer, uidMemoizer)
    ) {
      SharedChangeScope(changeScope).f()
    }
    t to instructions
  }

internal fun Mut.withIdMemoization(
  eidMemo: Memoizer<EID>,
  uidMemo: Memoizer<UID>
): Mut = let { mut ->
  object : Mut by mut {
    override fun createEntity(
      pipeline: DbContext<Mut>,
      entityTypeEid: EID,
      initials: List<Pair<Attribute<*>, Any>>
    ): EID = run {
      val key = (pipeline.impl.meta[KeyStack] ?: persistentListOf()).add(entityTypeEid)
      val defaultPart = pipeline.impl.defaultPart
      val eid = eidMemo.memo(true, key) { EidGen.freshEID(defaultPart) }
      val uid = uidMemo.memo(true, key) { UID.random() }
      pipeline.mutate(
        CreateEntity(
          eid = eid,
          entityTypeEid = entityTypeEid,
          attributes = initials.filter { it.first != uidAttribute() } + (uidAttribute() to uid),
          seed = generateSeed()
        )
      )
      eid
    }
  }
}

internal fun Mut.instructionsRecording(
  serContext: InstructionEncodingContext,
  recorder: (InstructionsPair) -> Unit
): Mut = let { mut ->
  data class SharedInstructionWithEffects(val sharedInstruction: SharedInstruction?,
                                          val sharedEffects: List<InstructionEffect>)

  object : Mut by mut {
    override fun expand(pipeline: DbContext<Q>, instruction: Instruction): Expansion =
      pipeline.alter(pipeline.impl.original) {
        val pipeline = this
        when (val sharedInstructionData = serContext.encoder.run { pipeline.encode(serContext, instruction) }) {
          null -> mut.expand(pipeline, instruction)
          else -> {
            val expansion = when {
              sharedInstructionData.sharedInstruction != null ->
                expandInstructionWithReadTracking(pipeline, mut, instruction)
              else -> mut.expand(pipeline, instruction)
            }

            // we should execute local effects right away, because when we replay instruction on shared partition in rebase loop,
            // we will forget local effects
            val (sharedEffects, localEffects) = expansion.effects.partition { effect ->
              // encoder returns not null if instruction is shared, thus it's effects has to be delayed:
              effect.origin == instruction || serContext.encoder.run { pipeline.encode(serContext, effect.origin) } != null
            }

            expansion.copy(
              effects = localEffects,
              sharedInstruction = SharedInstructionWithEffects(
                sharedInstruction = sharedInstructionData.sharedInstruction,
                sharedEffects = sharedEffects
              )
            )
          }
        }
      }

    override fun mutate(pipeline: DbContext<Mut>, expansion: Expansion): Novelty {
      val novelty = mut.mutate(pipeline, expansion)
      (expansion.sharedInstruction as SharedInstructionWithEffects?)?.let { (sharedInstruction, sharedEffects) ->
        recorder(
          InstructionsPair(
            localInstruction = expansion.instruction,
            sharedInstruction = sharedInstruction,
            sharedEffects = sharedEffects,
            sharedNovelty = novelty.filter { d -> partition(d.eid) == SharedPart }.toNovelty()
          )
        )
      }
      return novelty
    }
  }
}
