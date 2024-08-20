// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ml.impl.tools.logs

import com.intellij.internal.statistic.eventLog.FeatureUsageData
import com.intellij.internal.statistic.eventLog.events.PrimitiveEventField
import com.intellij.internal.statistic.eventLog.events.VarargEventId
import com.intellij.lang.Language
import com.intellij.openapi.util.Version
import com.intellij.platform.ml.impl.tools.logs.ConverterObjectDescription.Companion.asIJObjectDescription
import com.intellij.platform.ml.impl.tools.logs.ConverterOfEnum.Companion.toIJConverter
import com.intellij.platform.ml.impl.tools.logs.IJEventPairConverter.Companion.typedBuild
import com.jetbrains.ml.logs.schema.EventField
import com.jetbrains.ml.logs.schema.EventPair
import org.jetbrains.annotations.ApiStatus
import com.intellij.internal.statistic.eventLog.EventLogGroup as IJEventLogGroup
import com.intellij.internal.statistic.eventLog.events.BooleanEventField as IJBooleanEventField
import com.intellij.internal.statistic.eventLog.events.ClassEventField as IJClassEventField
import com.intellij.internal.statistic.eventLog.events.DoubleEventField as IJDoubleEventField
import com.intellij.internal.statistic.eventLog.events.EnumEventField as IJEnumEventField
import com.intellij.internal.statistic.eventLog.events.EventField as IJEventField
import com.intellij.internal.statistic.eventLog.events.EventFields as IJEventFields
import com.intellij.internal.statistic.eventLog.events.EventPair as IJEventPair
import com.intellij.internal.statistic.eventLog.events.FloatEventField as IJFloatEventField1
import com.intellij.internal.statistic.eventLog.events.IntEventField as IJIntEventField
import com.intellij.internal.statistic.eventLog.events.LongEventField as IJLongEventField
import com.intellij.internal.statistic.eventLog.events.ObjectDescription as IJObjectDescription
import com.intellij.internal.statistic.eventLog.events.ObjectEventData as IJObjectEventData
import com.intellij.internal.statistic.eventLog.events.ObjectEventField as IJObjectEventField
import com.intellij.internal.statistic.eventLog.events.ObjectListEventField as IJObjectListEventField
import com.intellij.internal.statistic.eventLog.events.StringEventField as IJStringEventField
import com.jetbrains.ml.logs.schema.BooleanEventField as MLBooleanEventField
import com.jetbrains.ml.logs.schema.ClassEventField as MLClassEventField
import com.jetbrains.ml.logs.schema.DoubleEventField as MLDoubleEventField
import com.jetbrains.ml.logs.schema.EnumEventField as MLEnumEventField
import com.jetbrains.ml.logs.schema.EventField as MLEventField
import com.jetbrains.ml.logs.schema.EventPair as MLEventPair
import com.jetbrains.ml.logs.schema.FloatEventField as MLFloatEventField
import com.jetbrains.ml.logs.schema.IntEventField as MLIntEventField
import com.jetbrains.ml.logs.schema.LongEventField as MLLongEventField
import com.jetbrains.ml.logs.schema.ObjectDescription as MLObjectDescription
import com.jetbrains.ml.logs.schema.ObjectEventData as MLObjectEventData
import com.jetbrains.ml.logs.schema.ObjectEventField as MLObjectEventField
import com.jetbrains.ml.logs.schema.ObjectListEventField as MLObjectListEventField
import com.jetbrains.ml.logs.schema.StringEventField as MLStringEventField


@ApiStatus.Internal
class IntelliJFusEventRegister(private val baseEventGroup: IJEventLogGroup) : com.jetbrains.ml.logs.FusEventRegister {
  private class Logger(
    private val varargEventId: VarargEventId,
    private val objectDescription: ConverterObjectDescription
  ) : com.jetbrains.ml.logs.FusEventLogger {
    override fun log(eventPairs: List<MLEventPair<*>>) {
      val ijEventPairs = objectDescription.buildEventPairs(eventPairs)
      varargEventId.log(*ijEventPairs.toTypedArray())
    }
  }

  override fun registerEvent(name: String, eventFields: List<EventField<*>>): com.jetbrains.ml.logs.FusEventLogger {
    val objectDescription = ConverterObjectDescription(MLObjectDescription(eventFields))
    val varargEventId = baseEventGroup.registerVarargEvent(name, null, *objectDescription.getFields())
    return Logger(varargEventId, objectDescription)
  }
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
  is VersionEventField -> ConverterOfVersion(mlEventField) as IJEventPairConverter<L, *>
  is LanguageEventField -> ConverterOfLanguage(mlEventField) as IJEventPairConverter<L, *>
  is MLStringEventField -> ConverterOfString(mlEventField) as IJEventPairConverter<L, *>

  is IJSpecificEventField<*> -> {
    when (mlEventField) {
      is IJCustomEventField -> ConverterOfCustom(mlEventField)
      is LanguageEventField -> ConverterOfLanguage(mlEventField) as IJEventPairConverter<L, *>
      is VersionEventField -> ConverterOfVersion(mlEventField) as IJEventPairConverter<L, *>
    }
  }

  else -> throw IllegalArgumentException(
    """
    Conversion of ${mlEventField.javaClass.simpleName} is not possible.
    If you want to create your own field, you must add an inheritor of
    ${IJCustomEventField::class.qualifiedName}
    """.trimIndent()
  )
}

private class ConverterOfCustom<T>(mlEventField: IJCustomEventField<T>) : IJEventPairConverter<T, T> {
  override val ijEventField: IJEventField<T> = mlEventField.baseIJEventField

  override fun buildEventPair(mlEventPair: EventPair<T>): IJEventPair<T> {
    return ijEventField with mlEventPair.data
  }
}

private class ConverterOfString(mlEventField: MLStringEventField) : IJEventPairConverter<String, String?> {
  override val ijEventField: IJEventField<String?> = IJStringEventField.ValidatedByAllowedValues(
    mlEventField.name,
    allowedValues = mlEventField.possibleValues,
    description = mlEventField.lazyDescription()
  )

  override fun buildEventPair(mlEventPair: EventPair<String>): IJEventPair<String?> {
    return ijEventField with mlEventPair.data
  }
}

private class ConverterOfLanguage(mlEventField: LanguageEventField) : IJEventPairConverter<Language, Language?> {
  override val ijEventField: IJEventField<Language?> = IJEventFields.Language(mlEventField.name, mlEventField.lazyDescription())

  override fun buildEventPair(mlEventPair: MLEventPair<Language>): IJEventPair<Language?> {
    return ijEventField with mlEventPair.data
  }
}

private class ConverterOfVersion(mlEventField: com.intellij.platform.ml.impl.tools.logs.VersionEventField) : IJEventPairConverter<Version, Version?> {
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

private interface IJEventPairConverter<M, I> {
  val ijEventField: IJEventField<I>

  fun buildEventPair(mlEventPair: MLEventPair<M>): IJEventPair<I>

  companion object {
    fun <M, I> IJEventPairConverter<M, I>.typedBuild(mlEventPair: MLEventPair<*>): IJEventPair<I> {
      @Suppress("UNCHECKED_CAST")
      return buildEventPair(mlEventPair as MLEventPair<M>)
    }
  }
}

private class ConverterObjectDescription(mlObjectDescription: MLObjectDescription) : IJObjectDescription() {
  private val toIJConverters: Map<MLEventField<*>, IJEventPairConverter<*, *>> = mlObjectDescription.getFields().associateWith { mlField ->
    val converter = createConverter(mlField)
    field(converter.ijEventField)
    converter
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
    fun MLObjectDescription.asIJObjectDescription() = ConverterObjectDescription(this)
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
