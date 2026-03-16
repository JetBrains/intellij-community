package com.jetbrains.fleet.rpc.plugin.ir.remoteKind

import com.jetbrains.fleet.rpc.plugin.ir.FileContext
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.FqName
import com.jetbrains.fleet.rpc.plugin.ir.util.name
import org.jetbrains.kotlin.ir.symbols.IrSimpleFunctionSymbol
import org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI

@UnsafeDuringIrConstructionAPI
internal fun FileContext.getBuiltInSerializer(fqName: FqName?): IrSimpleFunctionSymbol? =
  when (fqName) {
    LIST_FQN -> referenceFunctions(CallableId(
      packageName = FqName.fromSegments(listOf("kotlinx", "serialization", "builtins")),
      className = null,
      callableName = "ListSerializer".name
    )).first()

    SET_FQN -> referenceFunctions(CallableId(
      packageName = FqName.fromSegments(listOf("kotlinx", "serialization", "builtins")),
      className = null,
      callableName = "SetSerializer".name
    )).first()

    MAP_FQN -> referenceFunctions(CallableId(
      packageName = FqName.fromSegments(listOf("kotlinx", "serialization", "builtins")),
      className = null,
      callableName = "MapSerializer".name
    )).first()

    PAIR_FQN -> referenceFunctions(CallableId(
      packageName = FqName.fromSegments(listOf("kotlinx", "serialization", "builtins")),
      className = null,
      callableName = "PairSerializer".name
    )).first()

    MAP_ENTRY_FQN -> referenceFunctions(CallableId(
      packageName = FqName.fromSegments(listOf("kotlinx", "serialization", "builtins")),
      className = null,
      callableName = "MapEntrySerializer".name
    )).first()

    TRIPLE_FQN -> referenceFunctions(CallableId(
      packageName = FqName.fromSegments(listOf("kotlinx", "serialization", "builtins")),
      className = null,
      callableName = "TripleSerializer".name
    )).first()

    CHAR_ARRAY_FQN -> referenceFunctions(CallableId(
      packageName = FqName.fromSegments(listOf("kotlinx", "serialization", "builtins")),
      className = null,
      callableName = "CharArraySerializer".name
    )).first()

    BYTE_ARRAY_FQN -> referenceFunctions(CallableId(
      packageName = FqName.fromSegments(listOf("kotlinx", "serialization", "builtins")),
      className = null,
      callableName = "ByteArraySerializer".name
    )).first()

    U_BYTE_ARRAY_FQN -> referenceFunctions(CallableId(
      packageName = FqName.fromSegments(listOf("kotlinx", "serialization", "builtins")),
      className = null,
      callableName = "UByteArraySerializer".name
    )).first()

    SHORT_ARRAY_FQN -> referenceFunctions(CallableId(
      packageName = FqName.fromSegments(listOf("kotlinx", "serialization", "builtins")),
      className = null,
      callableName = "ShortArraySerializer".name
    )).first()

    U_SHORT_ARRAY_FQN -> referenceFunctions(CallableId(
      packageName = FqName.fromSegments(listOf("kotlinx", "serialization", "builtins")),
      className = null,
      callableName = "UShortArraySerializer".name
    )).first()

    INT_ARRAY_FQN -> referenceFunctions(CallableId(
      packageName = FqName.fromSegments(listOf("kotlinx", "serialization", "builtins")),
      className = null,
      callableName = "IntArraySerializer".name
    )).first()

    U_INT_ARRAY_FQN -> referenceFunctions(CallableId(
      packageName = FqName.fromSegments(listOf("kotlinx", "serialization", "builtins")),
      className = null,
      callableName = "UIntArraySerializer".name
    )).first()

    LONG_ARRAY_FQN -> referenceFunctions(CallableId(
      packageName = FqName.fromSegments(listOf("kotlinx", "serialization", "builtins")),
      className = null,
      callableName = "LongArraySerializer".name
    )).first()

    U_LONG_ARRAY_FQN -> referenceFunctions(CallableId(
      packageName = FqName.fromSegments(listOf("kotlinx", "serialization", "builtins")),
      className = null,
      callableName = "ULongArraySerializer".name
    )).first()

    FLOAT_ARRAY_FQN -> referenceFunctions(CallableId(
      packageName = FqName.fromSegments(listOf("kotlinx", "serialization", "builtins")),
      className = null,
      callableName = "FloatArraySerializer".name
    )).first()

    DOUBLE_ARRAY_FQN -> referenceFunctions(CallableId(
      packageName = FqName.fromSegments(listOf("kotlinx", "serialization", "builtins")),
      className = null,
      callableName = "DoubleArraySerializer".name
    )).first()

    BOOLEAN_ARRAY_FQN -> referenceFunctions(CallableId(
      packageName = FqName.fromSegments(listOf("kotlinx", "serialization", "builtins")),
      className = null,
      callableName = "BooleanArraySerializer".name
    )).first()

    ARRAY_FQN -> referenceFunctions(CallableId(
      packageName = FqName.fromSegments(listOf("kotlinx", "serialization", "builtins")),
      className = null,
      callableName = "ArraySerializer".name
    )).first { it.owner.parameters.size == 1 }

    else -> null
  }

private val LIST_FQN = FqName.fromSegments(listOf("kotlin", "collections", "List"))
private val SET_FQN = FqName.fromSegments(listOf("kotlin", "collections", "Set"))
private val MAP_FQN = FqName.fromSegments(listOf("kotlin", "collections", "Map"))
private val PAIR_FQN = FqName.fromSegments(listOf("kotlin", "Pair"))
private val MAP_ENTRY_FQN = FqName.fromSegments(listOf("kotlin", "collections", "Map", "Entry"))
private val TRIPLE_FQN = FqName.fromSegments(listOf("kotlin", "Triple"))
private val CHAR_ARRAY_FQN = FqName.fromSegments(listOf("kotlin", "CharArray"))
private val BYTE_ARRAY_FQN = FqName.fromSegments(listOf("kotlin", "ByteArray"))
private val U_BYTE_ARRAY_FQN = FqName.fromSegments(listOf("kotlin", "UByteArray"))
private val SHORT_ARRAY_FQN = FqName.fromSegments(listOf("kotlin", "ShortArray"))
private val U_SHORT_ARRAY_FQN = FqName.fromSegments(listOf("kotlin", "UShortArray"))
private val INT_ARRAY_FQN = FqName.fromSegments(listOf("kotlin", "IntArray"))
private val U_INT_ARRAY_FQN = FqName.fromSegments(listOf("kotlin", "UIntArray"))
private val LONG_ARRAY_FQN = FqName.fromSegments(listOf("kotlin", "LongArray"))
private val U_LONG_ARRAY_FQN = FqName.fromSegments(listOf("kotlin", "ULongArray"))
private val FLOAT_ARRAY_FQN = FqName.fromSegments(listOf("kotlin", "FloatArray"))
private val DOUBLE_ARRAY_FQN = FqName.fromSegments(listOf("kotlin", "DoubleArray"))
private val BOOLEAN_ARRAY_FQN = FqName.fromSegments(listOf("kotlin", "BooleanArray"))
private val ARRAY_FQN = FqName.fromSegments(listOf("kotlin", "Array"))
