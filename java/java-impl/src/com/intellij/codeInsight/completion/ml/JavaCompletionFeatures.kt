// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.completion.ml

import com.intellij.codeInsight.template.macro.MacroUtil
import com.intellij.internal.ml.WordsSplitter
import com.intellij.openapi.util.Key
import com.intellij.psi.*
import com.intellij.psi.CommonClassNames.JAVA_LANG_STRING
import com.intellij.psi.util.InheritanceUtil
import com.intellij.psi.util.TypeConversionUtil
import java.util.*

internal object JavaCompletionFeatures {
  private val VARIABLES_KEY: Key<VariablesInfo> = Key.create("java.ml.completion.variables")
  private val CHILD_CLASS_WORDS_KEY: Key<List<String>> = Key.create("java.ml.completion.child.class.name.words")

  private val wordsSplitter: WordsSplitter = WordsSplitter.Builder.identifiers().build()

  enum class JavaKeyword {
    ABSTRACT,
    BOOLEAN,
    BREAK,
    CASE,
    CATCH,
    CHAR,
    CLASS,
    CONST,
    CONTINUE,
    DOUBLE,
    ELSE,
    EXTENDS,
    FINAL,
    FINALLY,
    FLOAT,
    FOR,
    IF,
    IMPLEMENTS,
    IMPORT,
    INSTANCEOF,
    INT,
    INTERFACE,
    LONG,
    NEW,
    PRIVATE,
    PROTECTED,
    PUBLIC,
    RETURN,
    STATIC,
    SUPER,
    SWITCH,
    THIS,
    THROW,
    THROWS,
    TRY,
    VOID,
    WHILE,
    TRUE,
    FALSE,
    NULL,
    ANOTHER;

    companion object {
      private val ALL_VALUES: Map<String, JavaKeyword> = values().associateBy { it.toString() }

      fun getValue(text: String): JavaKeyword? = ALL_VALUES[text]
    }

    override fun toString(): String = name.toLowerCase(Locale.ENGLISH)
  }

  enum class JavaType {
    VOID,
    BOOLEAN,
    NUMERIC,
    STRING,
    CHAR,
    ENUM,
    ARRAY,
    COLLECTION,
    MAP,
    THROWABLE,
    ANOTHER;

    companion object {
      fun getValue(type: PsiType): JavaType {
        return when {
          type == PsiType.VOID -> VOID
          type == PsiType.CHAR -> CHAR
          type.equalsToText(JAVA_LANG_STRING) -> STRING
          TypeConversionUtil.isBooleanType(type) -> BOOLEAN
          TypeConversionUtil.isNumericType(type) -> NUMERIC
          type is PsiArrayType -> ARRAY
          InheritanceUtil.isInheritor(type, CommonClassNames.JAVA_UTIL_COLLECTION) -> COLLECTION
          InheritanceUtil.isInheritor(type, CommonClassNames.JAVA_UTIL_MAP) -> MAP
          InheritanceUtil.isInheritor(type, CommonClassNames.JAVA_LANG_THROWABLE) -> THROWABLE
          TypeConversionUtil.isEnumType(type) -> ENUM
          else -> ANOTHER
        }
      }
    }
  }

  fun asKeyword(text: String): JavaKeyword? = JavaKeyword.getValue(text)

  fun asJavaType(type: PsiType): JavaType = JavaType.getValue(type)

  fun calculateChildClassWords(environment: CompletionEnvironment, childClass: PsiElement) {
    val childClassWords = wordsSplitter.split(childClass.text)
    environment.putUserData(CHILD_CLASS_WORDS_KEY, childClassWords)
  }

  fun getChildClassTokensMatchingFeature(contextFeatures: ContextFeatures, baseClassName: String): MLFeatureValue? {
    contextFeatures.getUserData(CHILD_CLASS_WORDS_KEY)?.let { childClassTokens ->
      val baseClassTokens = wordsSplitter.split(baseClassName)
      if (baseClassTokens.isNotEmpty()) {
        return MLFeatureValue.numerical(baseClassTokens.count(childClassTokens::contains).toDouble() / baseClassTokens.size)
      }
    }
    return null
  }

  fun calculateVariables(environment: CompletionEnvironment) = try {
    val position = environment.parameters.position
    val variables = MacroUtil.getVariablesVisibleAt(position, "").toList()
    val names = variables.mapNotNull { it.name }.toSet()
    val types = variables.map { it.type }.toSet()
    val names2types = variables.mapNotNull { variable -> variable.name?.let { Pair(it, variable.type) } }.toSet()
    environment.putUserData(VARIABLES_KEY, VariablesInfo(names, types, names2types))
  } catch (ignored: PsiInvalidElementAccessException) {}

  fun getArgumentsVariablesMatchingFeatures(contextFeatures: ContextFeatures, method: PsiMethod): Map<String, MLFeatureValue> {
    val result = mutableMapOf<String, MLFeatureValue>()
    val parameters = method.parameterList.parameters
    result["args_count"] = MLFeatureValue.numerical(parameters.size)
    val names2types = parameters.map { Pair(it.name, it.type) }
    contextFeatures.getUserData(VARIABLES_KEY)?.let { variables ->
      result["args_vars_names_matches"] = MLFeatureValue.numerical(names2types.count { it.first in variables.names })
      result["args_vars_types_matches"] = MLFeatureValue.numerical(names2types.count { it.second in variables.types })
      result["args_vars_names_types_matches"] = MLFeatureValue.numerical(names2types.count { it in variables.names2types })
    }
    return result
  }

  fun isInQualifierExpression(environment: CompletionEnvironment): Boolean {
    val parentExpressions = mutableSetOf<PsiExpression>()
    var curParent = environment.parameters.position.context
    while (curParent is PsiExpression) {
      if (curParent is PsiReferenceExpression && parentExpressions.contains(curParent.qualifierExpression)) {
        return true
      }
      parentExpressions.add(curParent)
      curParent = curParent.parent
    }
    return false
  }

  fun isAfterMethodCall(environment: CompletionEnvironment): Boolean {
    val context = environment.parameters.position.context
    if (context is PsiReferenceExpression) {
      val qualifier = context.qualifierExpression
      return qualifier is PsiNewExpression || qualifier is PsiMethodCallExpression
    }
    return false
  }

  private data class VariablesInfo(
    val names: Set<String>,
    val types: Set<PsiType>,
    val names2types: Set<Pair<String, PsiType>>
  )
}