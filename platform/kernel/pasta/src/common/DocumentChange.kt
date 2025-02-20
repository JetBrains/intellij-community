// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.pasta.common

import andel.operation.Operation
import andel.operation.isIdentity
import com.jetbrains.rhizomedb.DbContext
import com.jetbrains.rhizomedb.EID
import com.jetbrains.rhizomedb.Instruction
import com.jetbrains.rhizomedb.InstructionExpansion
import com.jetbrains.rhizomedb.Op
import com.jetbrains.rhizomedb.Q
import com.jetbrains.rhizomedb.entity
import com.jetbrains.rhizomedb.lookupSingle
import fleet.kernel.rebase.InstructionCoder
import fleet.kernel.rebase.InstructionDecodingContext
import fleet.kernel.rebase.InstructionEncodingContext
import fleet.kernel.rebase.SharedInstructionData
import fleet.kernel.rebase.sharedId
import fleet.kernel.rebase.sharedInstruction
import fleet.util.Random
import fleet.util.UID
import kotlinx.serialization.KSerializer
import org.jetbrains.annotations.ApiStatus.Experimental
import kotlin.reflect.KClass

@Experimental
data class ChangeDocument(
  val documentId: EID,
  val operationId: UID,
  val operation: Operation,
  override val seed: Long = Random.nextLong(),
) : Instruction {

  companion object : InstructionCoder<ChangeDocument, SharedChangeDocument> {
    override val instructionClass: KClass<ChangeDocument>
      get() = ChangeDocument::class
    override val serializer: KSerializer<SharedChangeDocument>
      get() = SharedChangeDocument.serializer()
    override val instructionName: String
      get() = "ChangeDocument"

    override fun DbContext<Q>.encode(
      serContext: InstructionEncodingContext,
      instruction: ChangeDocument,
    ): SharedInstructionData? {
      return sharedId(instruction.documentId, serContext.uidAttribute)?.let { documentUID ->
        SharedInstructionData(
          sharedInstruction(
            SharedChangeDocument(
              documentId = documentUID,
              operationId = instruction.operationId,
              operation = instruction.operation,
              seed = instruction.seed,
            )
          )
        )
      }
    }

    override fun DbContext<Q>.decode(
      deserContext: InstructionDecodingContext,
      sharedInstruction: SharedChangeDocument,
    ): List<Instruction> {
      val documentEID = lookupSingle(deserContext.uidAttribute, sharedInstruction.documentId)
      return listOf(ChangeDocument(
        documentId = documentEID,
        operationId = sharedInstruction.operationId,
        operation = sharedInstruction.operation,
        seed = sharedInstruction.seed,
      ))
    }
  }

  override fun DbContext<Q>.expand(): InstructionExpansion {
    return InstructionExpansion(
      when {
        operation.isIdentity() -> emptyList()
        else -> {
          val document = entity(documentId) as DocumentEntity
          val editLog = document.editLogEntity
          val textBefore = document.text
          val textAfter = textBefore.mutableView().apply { edit(operation) }.text()
          listOf(
            Op.Assert(
              eid = documentId,
              attribute = DocumentEntity.Companion.TextAttr.attr,
              value = textAfter,
            ),
            Op.Assert(
              eid = documentId,
              attribute = DocumentEntity.Companion.SharedAnchorStorageAttr.attr,
              value = document.sharedAnchorStorage.edit(textBefore, textAfter, operation),
            ),
            Op.Assert(
              eid = editLog.eid,
              attribute = EditLogEntity.Companion.EditLogAttr.attr,
              value = editLog.editLog.append(operationId, operation),
            ),
          )
        }
      }
    )
  }
}
