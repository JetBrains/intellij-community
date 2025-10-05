// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.completion.ml

import com.intellij.codeInsight.template.macro.MacroUtil
import com.intellij.internal.ml.WordsSplitter
import com.intellij.openapi.util.Key
import com.intellij.psi.*
import com.intellij.psi.CommonClassNames.JAVA_LANG_STRING
import com.intellij.psi.scope.util.PsiScopesUtil
import com.intellij.psi.util.InheritanceUtil
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.PsiUtil
import com.intellij.psi.util.TypeConversionUtil
import org.jetbrains.annotations.ApiStatus
import java.util.*

@ApiStatus.Internal
public object JavaCompletionFeatures {
  private val VARIABLES_KEY: Key<VariablesInfo> = Key.create("java.ml.completion.variables")
  private val PACKAGES_KEY: Key<PackagesInfo> = Key.create("java.ml.completion.packages")
  private val CHILD_CLASS_WORDS_KEY: Key<List<String>> = Key.create("java.ml.completion.child.class.name.words")

  private val wordsSplitter: WordsSplitter = WordsSplitter.Builder.identifiers().build()

  public enum class JavaKeyword {
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

    public companion object {
      private val ALL_VALUES: Map<String, JavaKeyword> = values().associateBy { it.toString() }

      public fun getValue(text: String): JavaKeyword? = ALL_VALUES[text]
    }

    override fun toString(): String = name.lowercase(Locale.ENGLISH)
  }

  public enum class JavaType {
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

    public companion object {
      public fun getValue(type: PsiType): JavaType {
        return when {
          type == PsiTypes.voidType() -> VOID
          type == PsiTypes.charType() -> CHAR
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

  public fun asKeyword(text: String): JavaKeyword? = JavaKeyword.getValue(text)

  public fun asJavaType(type: PsiType): JavaType = JavaType.getValue(type)

  public fun calculateChildClassWords(environment: CompletionEnvironment, childClass: PsiElement) {
    val childClassWords = wordsSplitter.split(childClass.text)
    environment.putUserData(CHILD_CLASS_WORDS_KEY, childClassWords)
  }

  public fun getChildClassTokensMatchingFeature(contextFeatures: ContextFeatures, baseClassName: String): MLFeatureValue? {
    contextFeatures.getUserData(CHILD_CLASS_WORDS_KEY)?.let { childClassTokens ->
      val baseClassTokens = wordsSplitter.split(baseClassName)
      if (baseClassTokens.isNotEmpty()) {
        return MLFeatureValue.numerical(baseClassTokens.count(childClassTokens::contains).toDouble() / baseClassTokens.size)
      }
    }
    return null
  }

  public fun calculateVariables(environment: CompletionEnvironment): Unit? = try {
    PsiTreeUtil.getParentOfType(environment.parameters.position, PsiMethod::class.java)?.let { enclosingMethod ->
      val variables = getVariablesInScope(environment.parameters.position, enclosingMethod)
      val names = variables.mapNotNull { it.name }.toSet()
      val types = variables.map { it.type }.toSet()
      val names2types = variables.mapNotNull { variable -> variable.name?.let { Pair(it, variable.type) } }.toSet()
      environment.putUserData(VARIABLES_KEY, VariablesInfo(names, types, names2types))
    }
  } catch (ignored: PsiInvalidElementAccessException) {}

  public fun getArgumentsVariablesMatchingFeatures(contextFeatures: ContextFeatures, method: PsiMethod): Map<String, MLFeatureValue> {
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

  public fun isInQualifierExpression(environment: CompletionEnvironment): Boolean {
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

  public fun isAfterMethodCall(environment: CompletionEnvironment): Boolean {
    val context = environment.parameters.position.context
    if (context is PsiReferenceExpression) {
      val qualifier = context.qualifierExpression
      return qualifier is PsiNewExpression || qualifier is PsiMethodCallExpression
    }
    return false
  }

  public fun collectPackages(environment: CompletionEnvironment) {
    val file = environment.parameters.originalFile as? PsiJavaFile ?: return
    val packageTrie = FqnTrie.withFqn(file.packageName)
    val importsTrie = FqnTrie.create()
    file.importList?.importStatements?.mapNotNull { it.qualifiedName }?.forEach { importsTrie.addFqn(it) }
    file.importList?.importStaticStatements?.mapNotNull { it.importReference?.qualifiedName }?.forEach { importsTrie.addFqn(it) }
    environment.putUserData(PACKAGES_KEY, PackagesInfo(packageTrie, importsTrie))
  }

  public fun getPackageMatchingFeatures(contextFeatures: ContextFeatures, psiClass: PsiClass): Map<String, MLFeatureValue> {
    val packagesInfo = contextFeatures.getUserData(PACKAGES_KEY) ?: return emptyMap()
    val packageName = PsiUtil.getPackageName(psiClass)
    if (packageName.isNullOrEmpty()) return emptyMap()
    val packagePartsCount = packageName.split(".").size
    val result = mutableMapOf<String, MLFeatureValue>()
    val packageMatchedParts = packagesInfo.packageName.matchedParts(packageName)
    if (packageMatchedParts != 0) {
      result["package_matched_parts"] = MLFeatureValue.numerical(packageMatchedParts)
      result["package_matched_parts_relative"] = MLFeatureValue.numerical(packageMatchedParts.toDouble() / packagePartsCount)
    }
    val maxImportMatchedParts = packagesInfo.importPackages.matchedParts(packageName)
    if (maxImportMatchedParts != 0) {
      result["imports_max_matched_parts"] = MLFeatureValue.numerical(maxImportMatchedParts)
      result["imports_max_matched_parts_relative"] = MLFeatureValue.numerical(maxImportMatchedParts.toDouble() / packagePartsCount)
    }
    return result
  }

  private fun getVariablesInScope(position: PsiElement, maxScope: PsiElement?, maxVariables: Int = 100): List<PsiVariable> {
    val variables = mutableListOf<PsiVariable>()
    val variablesProcessor = object : MacroUtil.VisibleVariablesProcessor(position, "", variables) {
      override fun execute(pe: PsiElement, state: ResolveState): Boolean {
        return variables.size < maxVariables && super.execute(pe, state)
      }
    }
    PsiScopesUtil.treeWalkUp(variablesProcessor, position, maxScope)
    return variables
  }

  private data class VariablesInfo(
    val names: Set<String>,
    val types: Set<PsiType>,
    val names2types: Set<Pair<String, PsiType>>
  )

  private data class PackagesInfo(
    val packageName: FqnTrie,
    val importPackages: FqnTrie
  )

  public class FqnTrie private constructor(private val level: Int) {
    public companion object {
      public fun create(): FqnTrie = FqnTrie(0)

      public fun withFqn(name: String): FqnTrie = create().also { it.addFqn(name) }
    }
    private val children = mutableMapOf<String, FqnTrie>()

    public fun addFqn(name: String) {
      if (name.isEmpty()) return
      val (prefix, postfix) = split(name)
      children.getOrPut(prefix) { FqnTrie(level + 1) }.addFqn(postfix)
    }

    public fun matchedParts(name: String): Int {
      if (name.isEmpty()) return level
      val (prefix, postfix) = split(name)
      return children[prefix]?.matchedParts(postfix) ?: return level
    }

    private fun split(value: String): Pair<String, String> {
      val index = value.indexOf('.')
      return if (index < 0) Pair(value, "") else Pair(value.substring(0, index), value.substring(index + 1))
    }
  }
}