// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ml.impl.fus

import com.intellij.internal.statistic.eventLog.FeatureUsageData
import com.intellij.internal.statistic.eventLog.events.EventPair
import com.intellij.internal.statistic.eventLog.events.PrimitiveEventField
import com.intellij.internal.statistic.eventLog.events.StringEventField
import com.intellij.lang.Language
import com.intellij.openapi.util.Version
import com.intellij.platform.ml.impl.fus.ConverterObjectDescription.Companion.asIJObjectDescription
import com.intellij.platform.ml.impl.fus.ConverterOfEnum.Companion.toIJConverter
import com.intellij.platform.ml.impl.fus.IJEventPairConverter.Companion.typedBuild
import com.jetbrains.ml.api.logs.RuleBasedStringEventField
import com.intellij.internal.statistic.eventLog.events.BooleanEventField as IJBooleanEventField
import com.intellij.internal.statistic.eventLog.events.ClassEventField as IJClassEventField
import com.intellij.internal.statistic.eventLog.events.DoubleEventField as IJDoubleEventField
import com.intellij.internal.statistic.eventLog.events.EnumEventField as IJEnumEventField
import com.intellij.internal.statistic.eventLog.events.EventField as IJEventField
import com.intellij.internal.statistic.eventLog.events.EventFields as IJEventFields
import com.intellij.internal.statistic.eventLog.events.EventPair as IJEventPair
import com.intellij.internal.statistic.eventLog.events.FloatEventField as IJFloatEventField1
import com.intellij.internal.statistic.eventLog.events.FloatListEventField as IJFloatListEventField
import com.intellij.internal.statistic.eventLog.events.IntEventField as IJIntEventField
import com.intellij.internal.statistic.eventLog.events.IntListEventField as IJIntListEventField
import com.intellij.internal.statistic.eventLog.events.LongEventField as IJLongEventField
import com.intellij.internal.statistic.eventLog.events.LongListEventField as IJLongListEventField
import com.intellij.internal.statistic.eventLog.events.ObjectDescription as IJObjectDescription
import com.intellij.internal.statistic.eventLog.events.ObjectEventData as IJObjectEventData
import com.intellij.internal.statistic.eventLog.events.ObjectEventField as IJObjectEventField
import com.intellij.internal.statistic.eventLog.events.ObjectListEventField as IJObjectListEventField
import com.intellij.internal.statistic.eventLog.events.StringEventField as IJStringEventField
import com.jetbrains.ml.api.logs.BooleanEventField as MLBooleanEventField
import com.jetbrains.ml.api.logs.ClassEventField as MLClassEventField
import com.jetbrains.ml.api.logs.DoubleEventField as MLDoubleEventField
import com.jetbrains.ml.api.logs.EnumEventField as MLEnumEventField
import com.jetbrains.ml.api.logs.EventField as MLEventField
import com.jetbrains.ml.api.logs.EventPair as MLEventPair
import com.jetbrains.ml.api.logs.FloatEventField as MLFloatEventField
import com.jetbrains.ml.api.logs.FloatListEventField as MLFloatListEventField
import com.jetbrains.ml.api.logs.IntEventField as MLIntEventField
import com.jetbrains.ml.api.logs.IntListEventField as MLIntListEventField
import com.jetbrains.ml.api.logs.LongEventField as MLLongEventField
import com.jetbrains.ml.api.logs.LongListEventField as MLLongListEventField
import com.jetbrains.ml.api.logs.ObjectDescription as MLObjectDescription
import com.jetbrains.ml.api.logs.ObjectEventData as MLObjectEventData
import com.jetbrains.ml.api.logs.ObjectEventField as MLObjectEventField
import com.jetbrains.ml.api.logs.ObjectListEventField as MLObjectListEventField
import com.jetbrains.ml.api.logs.StringEventField as MLStringEventField


internal interface IJEventPairConverter<M, I> {
  val ijEventField: IJEventField<I>

  fun buildEventPair(mlEventPair: MLEventPair<M>): IJEventPair<I>

  companion object {
    fun <M, I> IJEventPairConverter<M, I>.typedBuild(mlEventPair: MLEventPair<*>): IJEventPair<I> {
      @Suppress("UNCHECKED_CAST")
      return buildEventPair(mlEventPair as MLEventPair<M>)
    }
  }
}


internal class ConverterObjectDescription(mlObjectDescription: MLObjectDescription) : IJObjectDescription() {
  private val toIJConverters: Map<MLEventField<*>, IJEventPairConverter<*, *>> = mlObjectDescription.getFields().associateWith { mlField ->
    val converter = createConverter(mlField)
    checkNotNull(converter) { "Please implement converter for $mlField from ML to IJ event fields" }
    field(converter.ijEventField)
    converter
  }

  @Suppress("UNCHECKED_CAST")
  private fun <L> createConverter(mlEventField: MLEventField<L>): IJEventPairConverter<L, *> = when (mlEventField) {
    is MLObjectEventField -> ConverterOfObject(
      mlEventField.name,
      mlEventField.lazyDescription,
      mlEventField.objectDescription
    ) as IJEventPairConverter<L, *>
    is MLBooleanEventField -> ConverterOfPrimitiveType(mlEventField) { n, d -> IJBooleanEventField(n, d) } as IJEventPairConverter<L, *>
    is MLIntEventField -> ConverterOfPrimitiveType(mlEventField) { n, d -> IJIntEventField(n, d) } as IJEventPairConverter<L, *>
    is MLLongEventField -> ConverterOfPrimitiveType(mlEventField) { n, d -> IJLongEventField(n, d) } as IJEventPairConverter<L, *>
    is MLFloatEventField -> ConverterOfPrimitiveType(mlEventField) { n, d -> IJFloatEventField1(n, d) } as IJEventPairConverter<L, *>
    is MLEnumEventField<*> -> mlEventField.toIJConverter() as IJEventPairConverter<L, *>
    is MLClassEventField -> ConverterOfClass(mlEventField) as IJEventPairConverter<L, *>
    is MLObjectListEventField -> ConvertObjectList(mlEventField) as IJEventPairConverter<L, *>
    is MLDoubleEventField -> ConverterOfPrimitiveType(mlEventField) { n, d -> IJDoubleEventField(n, d) } as IJEventPairConverter<L, *>
    is MLStringEventField -> ConverterOfString(mlEventField) as IJEventPairConverter<L, *>
    is MLFloatListEventField -> object : IJEventPairConverter<List<Float>, List<Float>> {
      override val ijEventField: IJEventField<List<Float>> = IJFloatListEventField(mlEventField.name, mlEventField.lazyDescription())
      override fun buildEventPair(mlEventPair: MLEventPair<List<Float>>): IJEventPair<List<Float>> = ijEventField with mlEventPair.data
    } as IJEventPairConverter<L, *>
    is MLIntListEventField -> object : IJEventPairConverter<List<Int>, List<Int>> {
      override val ijEventField: IJEventField<List<Int>> = IJIntListEventField(mlEventField.name, mlEventField.lazyDescription())
      override fun buildEventPair(mlEventPair: MLEventPair<List<Int>>): IJEventPair<List<Int>> = ijEventField with mlEventPair.data
    } as IJEventPairConverter<L, *>
    is MLLongListEventField -> object : IJEventPairConverter<List<Long>, List<Long>> {
      override val ijEventField: IJEventField<List<Long>> = IJLongListEventField(mlEventField.name, mlEventField.lazyDescription())
      override fun buildEventPair(mlEventPair: MLEventPair<List<Long>>): IJEventPair<List<Long>> = ijEventField with mlEventPair.data
    } as IJEventPairConverter<L, *>

    is VersionEventField -> ConverterOfVersion(mlEventField) as IJEventPairConverter<L, *>
    is LanguageEventField -> ConverterOfLanguage(mlEventField) as IJEventPairConverter<L, *>
    is RuleBasedStringEventField<L> -> ConverterSensitiveString(mlEventField) as IJEventPairConverter<L, *>

    else -> throw NotImplementedError("Please implement converter for $mlEventField from ML to IJ event fields")
  }

  fun buildEventPairs(mlEventPairs: List<MLEventPair<*>>): List<IJEventPair<*>> {
    return mlEventPairs.map { mlEventPair ->
      require(mlEventPair.field in toIJConverters) {
        """
          Field ${mlEventPair.field} (name: ${mlEventPair.field.name}) was not found among
          the registered ones: ${toIJConverters.keys.map { it.name }}
        """.trimIndent()
      }
      val converter = requireNotNull(toIJConverters[mlEventPair.field])
      converter.typedBuild(mlEventPair)
    }
  }

  fun buildObjectEventData(mlObject: MLObjectEventData): IJObjectEventData {
    return IJObjectEventData(buildEventPairs(mlObject.values))
  }

  companion object {
    fun MLObjectDescription.asIJObjectDescription(): ConverterObjectDescription =
      ConverterObjectDescription(this)
  }
}


private class ConverterOfString(mlEventField: MLStringEventField) : IJEventPairConverter<String, String?> {
  override val ijEventField: IJEventField<String?> = IJStringEventField.ValidatedByAllowedValues(
    mlEventField.name,
    allowedValues = mlEventField.possibleValues,
    description = mlEventField.lazyDescription()
  )

  override fun buildEventPair(mlEventPair: MLEventPair<String>): IJEventPair<String?> {
    return ijEventField with mlEventPair.data
  }
}

private class ConverterOfLanguage(mlEventField: LanguageEventField) : IJEventPairConverter<Language, Language?> {
  override val ijEventField: IJEventField<Language?> = IJEventFields.Language(mlEventField.name, mlEventField.lazyDescription())

  override fun buildEventPair(mlEventPair: MLEventPair<Language>): IJEventPair<Language?> {
    return ijEventField with mlEventPair.data
  }
}


private class ConverterOfVersion(mlEventField: com.intellij.platform.ml.impl.fus.VersionEventField) : IJEventPairConverter<Version, Version?> {
  private class VersionEventField(override val name: String, override val description: String?) : PrimitiveEventField<Version?>() {
    override val validationRule: List<String>
      get() = listOf("{regexp#version}")

    override fun addData(fuData: FeatureUsageData, value: Version?) {
      fuData.addVersion(value)
    }
  }

  override val ijEventField: IJEventField<Version?> = VersionEventField(mlEventField.name, mlEventField.lazyDescription())

  override fun buildEventPair(mlEventPair: MLEventPair<Version>): IJEventPair<Version?> {
    return ijEventField with mlEventPair.data
  }
}


private class ConverterSensitiveString<T>(private val mlEventField: RuleBasedStringEventField<T>) : IJEventPairConverter<T, String?> {

  private class SensitiveStringEventField(
    name: String,
    ruleId: String,
    override val description: String? = null
  ) : StringEventField(name) {
    override val validationRule: List<String> = listOf("{util#${ruleId}}")
  }

  override val ijEventField: IJEventField<String?> =
    SensitiveStringEventField(mlEventField.name, mlEventField.ruleId!!, mlEventField.lazyDescription())

  override fun buildEventPair(mlEventPair: MLEventPair<T>): EventPair<String?> {
    return ijEventField with mlEventField.serialize(mlEventPair.data)
  }
}


private class ConvertObjectList(mlEventField: MLObjectListEventField) :
  IJEventPairConverter<List<MLObjectEventData>, List<IJObjectEventData>> {
  private val innerObjectConverter = ConverterOfObject(mlEventField.name, mlEventField.lazyDescription, mlEventField.internalObjectDescription)

  // FIXME: description is not passed
  override val ijEventField: IJEventField<List<IJObjectEventData>> = IJObjectListEventField(
    mlEventField.name,
    innerObjectConverter.ijObjectDescription
  )

  override fun buildEventPair(mlEventPair: MLEventPair<List<MLObjectEventData>>): IJEventPair<List<IJObjectEventData>> {
    return ijEventField with mlEventPair.data.map { innerObjectFieldsValues ->
      innerObjectConverter.buildObjectEventData(innerObjectFieldsValues)
    }
  }
}


private class ConverterOfEnum<T : Enum<*>>(mlEnumField: MLEnumEventField<T>) : IJEventPairConverter<T, T> {
  override val ijEventField: IJEventField<T> = IJEnumEventField(mlEnumField.name, mlEnumField.enumClass, mlEnumField.transform)

  override fun buildEventPair(mlEventPair: MLEventPair<T>): IJEventPair<T> {
    return ijEventField with mlEventPair.data
  }

  companion object {
    fun <T : Enum<*>> MLEnumEventField<T>.toIJConverter(): ConverterOfEnum<T> {
      return ConverterOfEnum(this)
    }
  }
}


private class ConverterOfObject(
  name: String,
  lazyDescription: () -> String,
  mlObjectDescription: MLObjectDescription,
) : IJEventPairConverter<MLObjectEventData, IJObjectEventData> {
  val ijObjectDescription = mlObjectDescription.asIJObjectDescription()

  override val ijEventField: IJEventField<IJObjectEventData> = IJObjectEventField(name, lazyDescription(), ijObjectDescription)

  fun buildObjectEventData(mlObject: MLObjectEventData): IJObjectEventData {
    return ijObjectDescription.buildObjectEventData(mlObject)
  }

  override fun buildEventPair(mlEventPair: MLEventPair<MLObjectEventData>): IJEventPair<IJObjectEventData> {
    return ijEventField with buildObjectEventData(mlEventPair.data)
  }
}


private class ConverterOfPrimitiveType<T>(
  mlEventField: MLEventField<T>,
  createIJField: (String, String?) -> IJEventField<T>
) : IJEventPairConverter<T, T> {
  override val ijEventField: IJEventField<T> = createIJField(mlEventField.name, mlEventField.lazyDescription())

  override fun buildEventPair(mlEventPair: MLEventPair<T>): IJEventPair<T> {
    return ijEventField with mlEventPair.data
  }
}


private class ConverterOfClass(
  mlEventField: MLClassEventField,
) : IJEventPairConverter<Class<*>, Class<*>?> {
  override val ijEventField: IJEventField<Class<*>?> = IJClassEventField(mlEventField.name, mlEventField.lazyDescription())

  override fun buildEventPair(mlEventPair: MLEventPair<Class<*>>): IJEventPair<Class<*>?> {
    return ijEventField with mlEventPair.data
  }
}
