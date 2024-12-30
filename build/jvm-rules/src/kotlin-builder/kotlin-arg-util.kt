package org.jetbrains.bazel.jvm.kotlin

import org.jetbrains.kotlin.cli.common.arguments.*
import java.lang.reflect.Field

// we do not have kotlin reflection
internal fun <T : CommonToolArguments> toArgumentStrings(
  thisArgs: T,
  shortArgumentKeys: Boolean = false,
  compactArgumentValues: Boolean = true,
): List<String> {
  val result = ArrayList<String>()
  val defaultArguments = K2JVMCompilerArguments()
  for (field in getAllFields()) {
    val argAnnotation = field.getAnnotation(Argument::class.java) ?: continue
    field.setAccessible(true)
    val rawPropertyValue = field.get(thisArgs)
    val rawDefaultValue = field.get(defaultArguments)
    if (rawPropertyValue == rawDefaultValue) {
      continue
    }

    val argumentStringValues = when {
      field.type == java.lang.Boolean.TYPE -> {
        listOf(rawPropertyValue?.toString() ?: false.toString())
      }
      field.type.isArray -> {
        getArgumentStringValue(
          argAnnotation = argAnnotation,
          values = rawPropertyValue as Array<*>?,
          compactArgValues = compactArgumentValues,
        )
      }
      field.type == java.util.List::class.java -> {
        getArgumentStringValue(
          argAnnotation = argAnnotation,
          values = (rawPropertyValue as List<*>?)?.toTypedArray(),
          compactArgValues = compactArgumentValues,
        )
      }
      else -> listOf(rawPropertyValue.toString())
    }

    val argumentName = if (shortArgumentKeys && argAnnotation.shortName.isNotEmpty()) argAnnotation.shortName else argAnnotation.value

    for (argumentStringValue in argumentStringValues) {
      when {
        /* We can just enable the flag by passing the argument name like -myFlag: Value not required */
        rawPropertyValue is Boolean && rawPropertyValue -> {
          result.add(argumentName)
        }

        /* Advanced (e.g. -X arguments) or boolean properties need to be passed using the '=' */
        argAnnotation.isAdvanced || field.type == java.lang.Boolean.TYPE -> {
          result.add("$argumentName=$argumentStringValue")
        }

        else -> {
          result.add(argumentName)
          result.add(argumentStringValue)
        }
      }
    }
  }

  result.addAll(thisArgs.freeArgs)
  result.addAll(thisArgs.internalArguments.map { it.stringRepresentation })
  return result
}

private fun getAllFields(): Sequence<Field> {
  return sequence {
    var aClass: Class<*>? = K2JVMCompilerArguments::class.java
    while (aClass != null) {
      yieldAll(aClass.declaredFields.asSequence())
      aClass = aClass.superclass.takeIf { it != Any::class.java }
    }
  }
}

private fun getArgumentStringValue(argAnnotation: Argument, values: Array<*>?, compactArgValues: Boolean): List<String> {
  if (values.isNullOrEmpty()) {
    return emptyList()
  }

  val delimiter = argAnnotation.resolvedDelimiter
  return if (delimiter.isNullOrEmpty() || !compactArgValues) values.map { it.toString() } else listOf(values.joinToString(delimiter))
}