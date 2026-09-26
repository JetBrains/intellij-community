// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ml.logs

import com.intellij.internal.statistic.eventLog.events.BooleanEventField
import com.intellij.internal.statistic.eventLog.events.ClassEventField
import com.intellij.internal.statistic.eventLog.events.DoubleEventField
import com.intellij.internal.statistic.eventLog.events.EnumEventField
import com.intellij.internal.statistic.eventLog.events.EventField
import com.intellij.internal.statistic.eventLog.events.EventPair
import com.intellij.internal.statistic.eventLog.events.FloatEventField
import com.intellij.internal.statistic.eventLog.events.FloatListEventField
import com.intellij.internal.statistic.eventLog.events.IntEventField
import com.intellij.internal.statistic.eventLog.events.IntListEventField
import com.intellij.internal.statistic.eventLog.events.LongEventField
import com.intellij.internal.statistic.eventLog.events.LongListEventField
import com.intellij.internal.statistic.eventLog.events.ObjectDescription
import com.intellij.internal.statistic.eventLog.events.ObjectEventData
import com.intellij.internal.statistic.eventLog.events.ObjectEventField
import com.intellij.internal.statistic.eventLog.events.ObjectListEventField
import com.intellij.internal.statistic.eventLog.events.StringEventField
import com.intellij.platform.ml.logs.IJEventPairConverter.Companion.typedBuild
import com.jetbrains.mlapi.feature.AbstractPrimitiveDeclaration
import com.jetbrains.mlapi.feature.ClassFeatureDeclaration
import com.jetbrains.mlapi.feature.EnumFeatureDeclaration
import com.jetbrains.mlapi.feature.Feature
import com.jetbrains.mlapi.feature.FeatureSignature
import com.jetbrains.mlapi.feature.NullableFeatureDeclaration
import com.jetbrains.mlapi.feature.PrimitiveFeatureDeclaration
import com.jetbrains.mlapi.feature.PrimitiveType
import com.jetbrains.mlapi.logs.ObjectArrayDeclaration
import com.jetbrains.mlapi.logs.ObjectDeclaration
import com.jetbrains.mlapi.logs.ObjectFeatureDeclaration


internal interface IJEventPairConverter<FeatureT : Feature, I> {
  val ijEventField: EventField<I>

  fun buildEventPair(feature: FeatureT): EventPair<I>

  companion object {
    fun <F: Feature, I> IJEventPairConverter<F, I>.typedBuild(feature: Feature): EventPair<I> {
      @Suppress("UNCHECKED_CAST")
      return buildEventPair(feature as F)
    }
  }
}


internal class ConverterObjectDescription(signatures: List<FeatureSignature>) : ObjectDescription() {
  private val toIJConverters: Map<FeatureSignature, IJEventPairConverter<*, *>> = signatures.associateWith { signature ->
    val converter = createConverter(signature)
    checkNotNull(converter) { "Please implement converter for $signature from ML to IJ event fields" }
    field(converter.ijEventField)
    converter
  }

  @Suppress("UNCHECKED_CAST")
  private fun createConverter(signature: FeatureSignature): IJEventPairConverter<*, *>? = when (signature) {
    is ObjectFeatureDeclaration -> ConverterOfObject(signature)
    is ObjectArrayDeclaration -> ConvertObjectList(signature)
    is PrimitiveFeatureDeclaration<*> -> {
      when (signature.type) {
        is PrimitiveType.Boolean -> ConverterOfBoolean(signature)
        is PrimitiveType.Int32 -> ConverterOfInt32(signature)
        is PrimitiveType.Int64 -> ConverterOfInt64(signature)
        is PrimitiveType.Float -> ConverterOfFloat(signature)
        is PrimitiveType.Double -> ConverterOfDouble(signature)

        is PrimitiveType.String -> ConverterOfString(signature)

        is PrimitiveType.EmbeddingInt32 -> ConverterOfIntList(signature)
        is PrimitiveType.EmbeddingInt64 -> ConverterOfLongList(signature)
        is PrimitiveType.EmbeddingFloat -> ConverterOfFloatList(signature)
        is PrimitiveType.EmbeddingDouble -> ConverterOfFloatList(signature)

        is PrimitiveType.Null -> null
      }
    }
    is ClassFeatureDeclaration -> ConverterOfClass(signature)
    is EnumFeatureDeclaration<*> -> ConverterOfEnum(signature)
    is NullableFeatureDeclaration<*> -> createConverter(signature.asNonNullable)

    else -> throw NotImplementedError("Please implement converter for $signature from ML to IJ event fields")
  }

  fun buildEventPairs(features: List<Feature>): List<EventPair<*>> {
    return features.map { feature ->
      require(feature.signature in toIJConverters) {
        """
          Field ${feature.signature} (name: ${feature.signature.name}) was not found among
          the registered ones: ${toIJConverters.keys.map { it.name }}
        """.trimIndent()
      }
      val converter = requireNotNull(toIJConverters[feature.signature])
      converter.typedBuild(feature)
    }
  }

  fun buildObjectEventData(features: List<Feature>): ObjectEventData {
    return ObjectEventData(buildEventPairs(features))
  }

}


private class ConverterOfString(declaration: PrimitiveFeatureDeclaration<*>) : IJEventPairConverter<Feature.String, String?> {

  private class SensitiveStringEventField(
    name: String,
    ruleId: String,
    override val description: String? = null
  ) : StringEventField(name) {
    override val validationRule: List<String> = listOf("{util#${ruleId}}")
  }

  override val ijEventField: EventField<String?> =
    SensitiveStringEventField(
      declaration.name,
      requireNotNull(declaration.logsMetadata?.ruleId) { "Error for $declaration: it must have a validation rule" },
      declaration.logsMetadata?.lazyDescription?.invoke()
    )

  override fun buildEventPair(feature: Feature.String): EventPair<String?> =
    ijEventField with feature.stringValue

}

private abstract class ConverterOfPrimitiveType<FeatureT : Feature, T>(declaration: AbstractPrimitiveDeclaration<*, *>) : IJEventPairConverter<FeatureT, T> {
  abstract fun createEventField(name: String, description: String?): EventField<T>
  abstract fun getValue(feature: FeatureT): T
  
  override val ijEventField: EventField<T> by lazy { createEventField(declaration.name, declaration.logsMetadata?.lazyDescription?.invoke()) }
  override fun buildEventPair(feature: FeatureT): EventPair<T> = ijEventField with getValue(feature)
}

private class ConverterOfBoolean(declaration: PrimitiveFeatureDeclaration<*>) : ConverterOfPrimitiveType<Feature.Boolean, Boolean>(declaration) {
  override fun createEventField(name: String, description: String?): EventField<Boolean> = BooleanEventField(name, description)
  override fun getValue(feature: Feature.Boolean) = feature.booleanValue
}

private class ConverterOfFloat(declaration: PrimitiveFeatureDeclaration<*>) : ConverterOfPrimitiveType<Feature.Float, Float>(declaration) {
  override fun createEventField(name: String, description: String?): EventField<Float> = FloatEventField(name, description)
  override fun getValue(feature: Feature.Float) = feature.floatValue
}

private class ConverterOfDouble(declaration: PrimitiveFeatureDeclaration<*>) : ConverterOfPrimitiveType<Feature.Double, Double>(declaration) {
  override fun createEventField(name: String, description: String?): EventField<Double> = DoubleEventField(name, description)
  override fun getValue(feature: Feature.Double) = feature.doubleValue
}

private class ConverterOfInt32(declaration: PrimitiveFeatureDeclaration<*>) : ConverterOfPrimitiveType<Feature.Int32, Int>(declaration) {
  override fun createEventField(name: String, description: String?): EventField<Int> = IntEventField(name, description)
  override fun getValue(feature: Feature.Int32) = feature.int32Value
}

private class ConverterOfInt64(declaration: PrimitiveFeatureDeclaration<*>) : ConverterOfPrimitiveType<Feature.Int64, Long>(declaration) {
  override fun createEventField(name: String, description: String?): EventField<Long> = LongEventField(name, description)
  override fun getValue(feature: Feature.Int64) = feature.int64Value
}

private class ConverterOfIntList(declaration: PrimitiveFeatureDeclaration<*>) : ConverterOfPrimitiveType<Feature.Int32Embedding, List<Int>>(declaration) {
  override fun createEventField(name: String, description: String?): EventField<List<Int>> = IntListEventField(name, description)
  override fun getValue(feature: Feature.Int32Embedding): List<Int> = feature.int32Array.toList()
}

private class ConverterOfLongList(declaration: PrimitiveFeatureDeclaration<*>) : ConverterOfPrimitiveType<Feature.Int64Embedding, List<Long>>(declaration) {
  override fun createEventField(name: String, description: String?): EventField<List<Long>> = LongListEventField(name, description)
  override fun getValue(feature: Feature.Int64Embedding): List<Long> = feature.int64Array.toList()
}

private class ConverterOfFloatList(declaration: PrimitiveFeatureDeclaration<*>) : ConverterOfPrimitiveType<Feature.FloatEmbedding, List<Float>>(declaration) {
  override fun createEventField(name: String, description: String?): EventField<List<Float>> = FloatListEventField(name, description)
  override fun getValue(feature: Feature.FloatEmbedding): List<Float> = feature.floatArray.toList()
}

private class ConverterOfClass(declaration: ClassFeatureDeclaration) : ConverterOfPrimitiveType<Feature.Class, Class<*>?>(declaration) {
  override fun createEventField(name: String, description: String?): EventField<Class<*>?> = ClassEventField(name, description)
  override fun getValue(feature: Feature.Class): Class<*> = feature.classValue
}

private class ConverterOfEnum<E : Enum<*>>(
  private val enumDeclaration: EnumFeatureDeclaration<E>
) : ConverterOfPrimitiveType<Feature.Enum<E>, E>(enumDeclaration) {
  override fun createEventField(name: String, description: String?): EventField<E> =
    EnumEventField(name, enumDeclaration.enumClass, description = description, transform = { it.name })
  override fun getValue(feature: Feature.Enum<E>): E = feature.enumValue
}


private class ConverterOfObject(
  objectDeclaration: ObjectDeclaration<*>,
) : IJEventPairConverter<Feature.Object, ObjectEventData> {
  val ijObjectDescription = ConverterObjectDescription(objectDeclaration.signatures)

  override val ijEventField: EventField<ObjectEventData> =
    ObjectEventField(objectDeclaration.name, objectDeclaration.logsMetadata?.lazyDescription?.invoke(), ijObjectDescription)

  fun buildObjectEventData(features: List<Feature>): ObjectEventData {
    return ijObjectDescription.buildObjectEventData(features)
  }

  override fun buildEventPair(feature: Feature.Object): EventPair<ObjectEventData> {
    return ijEventField with buildObjectEventData(feature.values)
  }
}


private class ConvertObjectList(declaration: ObjectArrayDeclaration) : IJEventPairConverter<Feature.ObjectArray, List<ObjectEventData>> {

  private val innerObjectConverter = ConverterOfObject(declaration.elementSignature)

  // FIXME: description is not passed
  override val ijEventField: EventField<List<ObjectEventData>> = ObjectListEventField(
    declaration.name,
    innerObjectConverter.ijObjectDescription,
  )

  override fun buildEventPair(feature: Feature.ObjectArray): EventPair<List<ObjectEventData>> {
    return ijEventField with feature.values.map { objectFeature ->
      innerObjectConverter.buildObjectEventData(objectFeature.values)
    }
  }
}
