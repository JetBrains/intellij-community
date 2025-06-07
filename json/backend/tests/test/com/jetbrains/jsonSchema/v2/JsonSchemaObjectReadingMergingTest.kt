// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("JsonSchemaCompliance")

package com.jetbrains.jsonSchema.v2

import com.intellij.json.psi.JsonProperty
import com.intellij.json.psi.JsonStringLiteral
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.application.ex.PathManagerEx
import com.intellij.openapi.application.ex.PathManagerEx.TestDataLookupStrategy
import com.intellij.openapi.diagnostic.LogLevel
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiRecursiveElementVisitor
import com.intellij.psi.util.parentOfType
import com.intellij.testFramework.UsefulTestCase
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.util.asSafely
import com.jetbrains.jsonSchema.impl.*
import com.jetbrains.jsonSchema.impl.JsonSchemaResolver.selectSchema
import com.jetbrains.jsonSchema.impl.tree.JsonSchemaNodeExpansionRequest
import org.intellij.lang.annotations.Language
import org.junit.Assert
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import kotlin.reflect.KCallable
import kotlin.reflect.jvm.isAccessible

private val myTestLogger by lazy {
  Logger.getInstance("JsonSchemaObjectReadingMergingTest").apply {
    setLevel(LogLevel.INFO)
  }
}

@Deprecated("Test exists only until JsonSchemaObjectImpl is removed")
internal class JsonSchemaObjectReadingMergingTest : BasePlatformTestCase() {

  private enum class Mode {
    OLD, NEW, OLD_OBJECT_NEW_MERGE
  }

  private fun newMergeOldObject() {
    throw IllegalStateException("The mode is no more supported and will be deleted along with JsonSchemaObjectImpl")
  }

  private fun oldMergeOldObject() {
    Registry.get("json.schema.object.v2").setValue(false)
  }

  private fun newMergeNewObject() {
    Registry.get("json.schema.object.v2").setValue(true)
  }

  private fun schema(text: String): VirtualFile {
    return myFixture.configureByText("schema${text.hashCode()}.json", text).virtualFile!!
  }

  private fun file(text: String): PsiFile {
    return myFixture.configureByText("test.json", text)
  }

  private fun VirtualFile.parseObject(mode: Mode): JsonSchemaObject {
    when (mode) {
      Mode.OLD -> oldMergeOldObject()
      Mode.NEW -> newMergeNewObject()
      Mode.OLD_OBJECT_NEW_MERGE -> newMergeOldObject()
    }
    return JsonSchemaReader.readFromFile(myFixture.project, this)
  }

  private fun checkBothNullable(inspectedText: String, old: JsonSchemaObject?, new: JsonSchemaObject?) {

    if (old == null && new == null) {
      myTestLogger.warn("Both objects are null for inspected element: '$inspectedText'")
    }
    else if (old != null && new != null) {
      myTestLogger.info("Both objects are not null for inspected element: '$inspectedText'")
    }
    else if (old == null) {
      myTestLogger.warn(
        "New object is not null for inspected element: '$inspectedText', but it is OK since old implementation tends to lose schema data")
    }
    else {
      Assert.fail("Nullability:   Old is $old and New is $new for inspected element: '$inspectedText'")
    }
  }

  private fun compareReading(old: JsonSchemaObject, new: JsonSchemaObject, functions: List<KCallable<*>> = getFunctionsToInspect()) {
    functions.forEach { inspectedFunction ->
      inspectedFunction.isAccessible = true
      val oldPointer = old.pointer
      val newPointer = new.pointer
      val oldResult = callInspectedFunctionSafe(inspectedFunction, old)
      val newResult = callInspectedFunctionSafe(inspectedFunction, new)
      doCompareReadPrimitiveTypes(inspectedFunction.name, oldPointer, newPointer, oldResult, newResult)
    }
  }


  private data object DeprecatedMethodResultToIgnore

  private fun callInspectedFunctionSafe(function: KCallable<*>, targetObject: JsonSchemaObject): Any? {
    return kotlin.runCatching {
      function.call(targetObject)
    }.getOrElse { throwable ->
      myTestLogger.warn("Caught exception during function \"${function.name}\" call")
      if (throwable is UnsupportedOperationException) return DeprecatedMethodResultToIgnore
      throw throwable
    }
  }

  private fun doCompareReadPrimitiveTypes(inspectedFunctionName: String,
                                          oldPointer: String,
                                          newPointer: String,
                                          oldResult: Any?,
                                          newResult: Any?) {
    val assertionMessage = "Function: ${inspectedFunctionName} at \n\told pointer `${oldPointer}`, \n\tnew pointer `${newPointer}`:\n\t\t"
    when {
      oldResult is JsonSchemaType -> {
        Assert.assertEquals(assertionMessage, oldResult, newResult)
      }
      oldResult is Map<*, *> -> {
        val newKeys = newResult.asSafely<Map<*, *>>()?.keys
        val oldKeys = oldResult.keys
        if (newKeys != null) {
          UsefulTestCase.assertSameElements(
            assertionMessage, oldKeys, newKeys)
        }
        val newSize = newResult.asSafely<Map<*, *>>()?.size
        val oldSize = oldResult.size
        Assert.assertEquals(assertionMessage, oldSize, newSize)
      }
      oldResult is Collection<*> -> {
        val newSize = newResult.asSafely<Collection<*>>()?.size
        val oldSize = oldResult.size
        Assert.assertEquals(assertionMessage, oldSize, newSize)
      }
      oldResult is String || oldResult is Number || oldResult is Boolean -> {
        Assert.assertEquals(assertionMessage, oldResult, newResult)
      }
      oldResult == null && newResult == null -> {
        myTestLogger.info("OK IGNORING result of $assertionMessage that returned null in both implementations")
      }
      else -> {
        myTestLogger.info("BAD IGNORING result of $assertionMessage with old return type: ${oldResult?.javaClass?.name}" +
                          " and new return type: ${newResult?.javaClass?.name}")
      }
    }
  }

  private fun getFunctionsToInspect(): List<KCallable<*>> {
    val functions = JsonSchemaObject::class.members
      .filter { function ->
        function.parameters.size == 1
        && function.name !in setOf("toString", "hashCode", "getDefinitionsMap", "getProperties", "getRootSchemaObject")
        && function.annotations.none { annotation -> annotation.annotationClass.simpleName == "Deprecated" }
      }
      .sortedBy { it.name }
      .sortedWith { o1, o2 ->
        if (o1.name == "getPointer") -1 else 1
      }
    myTestLogger.info("Inspecting ${functions.size} of ${JsonSchemaObject::class.members.size} members of JsonSchemaObject")
    myTestLogger.info("Inspected functions are ${functions.map { it.name }}")
    return functions
  }

  private fun iterateOldAndNewSchemaNodes(oldSchema: JsonSchemaObject,
                                          newSchema: JsonSchemaObject): Sequence<Pair<JsonSchemaObject, JsonSchemaObject>> {
    return sequence {
      yield(oldSchema to newSchema)

      val oldProps = oldSchema.propertyNames.asSequence().map { name ->
        name to oldSchema.getPropertyByName(name)!!
      }.toMap()
      val newProps = newSchema.propertyNames.asSequence().map { name ->
        name to newSchema.getPropertyByName(name)!!
      }.toMap()
      bothMapsNullOrYield(oldSchema.pointer, "properties", oldProps, newProps)

      val oldDefs = oldSchema.definitionNames.asSequence()
        .filter { !it.startsWith("$") && it !in setOf("unevaluatedProperties", "dependentSchemas") }
        .map { name ->
          val definitionByName = newSchema.getDefinitionByName(name)
          Assert.assertNotNull("Name = $name, but OLD definition schema is null", definitionByName)
          name to definitionByName!!
        }.toMap()
      val newDefs = newSchema.definitionNames.asSequence()
        .filter { !it.startsWith("$") && it !in setOf("unevaluatedProperties", "dependentSchemas") }
        .map { name ->
          val definitionByName = newSchema.getDefinitionByName(name)
          Assert.assertNotNull("Name = $name, but NEW definition schema is null", definitionByName)
          name to definitionByName!!
        }.toMap()
      bothMapsNullOrYield(oldSchema.pointer, "definitions", oldDefs, newDefs)

      bothValuesAreNullOrYield(oldSchema.itemsSchema, newSchema.itemsSchema)
      bothValuesAreNullOrYield(oldSchema.containsSchema, newSchema.containsSchema)
      bothValuesAreNullOrYield(oldSchema.propertyNamesSchema, newSchema.propertyNamesSchema)
      bothValuesAreNullOrYield(oldSchema.additionalPropertiesSchema, newSchema.additionalPropertiesSchema)
      bothValuesAreNullOrYield(oldSchema.additionalItemsSchema, newSchema.additionalItemsSchema)
      //bothValuesAreNullOrYield(oldSchema.backReference, newSchema.backReference)
      bothValuesAreNullOrYield(oldSchema.not, newSchema.not)

      bothIfThenElseNullOrYield(oldSchema.pointer, oldSchema.ifThenElse, newSchema.ifThenElse)

      bothListsNullOrYield(oldSchema.pointer, "itemsSchemaList", oldSchema.itemsSchemaList, newSchema.itemsSchemaList)
      bothListsNullOrYield(oldSchema.pointer, "anyOf", oldSchema.anyOf, newSchema.anyOf)
      bothListsNullOrYield(oldSchema.pointer, "allOf", oldSchema.allOf, newSchema.allOf)
      bothListsNullOrYield(oldSchema.pointer, "oneOf", oldSchema.oneOf, newSchema.oneOf)
    }
  }

  private suspend fun SequenceScope<Pair<JsonSchemaObject, JsonSchemaObject>>.bothIfThenElseNullOrYield(
    pointer: String,
    oldIfThenElse: List<IfThenElse>?,
    newIfThenElse: List<IfThenElse>?) {
    Assert.assertEquals("ifthenelse sizes should be equal at ${pointer}", oldIfThenElse?.size,
                        newIfThenElse?.size)
    if (oldIfThenElse != null && newIfThenElse != null) {
      for (index in oldIfThenElse.indices) {
        bothValuesAreNullOrYield(oldIfThenElse[index].`if`, newIfThenElse[index].`if`)
        bothValuesAreNullOrYield(oldIfThenElse[index].then, newIfThenElse[index].then)
        bothValuesAreNullOrYield(oldIfThenElse[index].`else`, newIfThenElse[index].`else`)
      }
    }
  }

  private suspend fun SequenceScope<Pair<JsonSchemaObject, JsonSchemaObject>>.bothMapsNullOrYield(pointer: String,
                                                                                                  debugName: String,
                                                                                                  old: Map<String, JsonSchemaObject>?,
                                                                                                  new: Map<String, JsonSchemaObject>?) {
    if (old == null && new == null) return
    if (old == null && new != null) return
    if (new == null) {
      Assert.fail("The new map is null, but the old one is not")
    }

    Assert.assertTrue(
      "$debugName map sizes are not equal at `${pointer}`. Old size is ${old?.size}, new size is ${new?.size}; Old contents: $old; New contents: $new",
      old!!.size <= new!!.size)

    for (oldProperty in old) {
      Assert.assertTrue("Property with name `${oldProperty.key}` is not present at ${pointer}",
                        new[oldProperty.key] != null)
      val sameNameNewProperty = new[oldProperty.key]!!
      yieldAll(iterateOldAndNewSchemaNodes(oldProperty.value, sameNameNewProperty))
    }
  }

  private suspend fun SequenceScope<Pair<JsonSchemaObject, JsonSchemaObject>>.bothListsNullOrYield(pointer: String,
                                                                                                   debugName: String,
                                                                                                   old: List<JsonSchemaObject>?,
                                                                                                   new: List<JsonSchemaObject>?) {
    Assert.assertEquals(
      "$debugName sizes are not equal at `${pointer}`. Old size is ${old?.size}, new size is ${new?.size}",
      old?.size, new?.size)

    if (old != null && new != null) {
      for (index in old.indices) {
        val oldItem = old[index]
        val newItem = new[index]
        yieldAll(iterateOldAndNewSchemaNodes(oldItem, newItem))
      }
    }
  }

  private suspend fun SequenceScope<Pair<JsonSchemaObject, JsonSchemaObject>>.bothValuesAreNullOrYield(old: JsonSchemaObject?,
                                                                                                       new: JsonSchemaObject?) {
    return when {
      old == null && new == null -> {
        myTestLogger.debug("OK, Both objects are null")
        return
      }
      old == null && new != null -> {
        myTestLogger.warn("OK, New object is not null, old is null - we just don't miss anything")
        return
      }
      old != null && new == null -> {
        Assert.fail("Old is not null at ${old.pointer}, but new is null")
      }
      else -> {
        yield(old!! to new!!)
      }
    }
  }


  private fun doTestReading(@Language("json") schemaText: String) {
    val schema = schema(schemaText)
    val nodes = iterateOldAndNewSchemaNodes(
      schema.parseObject(Mode.OLD),
      schema.parseObject(Mode.NEW)
    )
      .toList()
    myTestLogger.warn("Found ${nodes.size} json schema nodes to check")

    recordTotalProcessedNodesNumber { count ->
      nodes.forEach { (oldSchemaNode, newSchemaNode) ->
        count()
        compareReading(oldSchemaNode, newSchemaNode)
      }
    }
  }

  private fun recordTotalProcessedNodesNumber(test: (() -> Int) -> Unit) {
    val nodesProcessed = AtomicInteger(0)
    try {
      test(nodesProcessed::incrementAndGet)
    }
    finally {
      myTestLogger.warn("TOTAL NODES PROCESSED: ${nodesProcessed.get()}")
    }
  }

  private fun computeRootSchemaInMode(schema: VirtualFile, mode: Mode): JsonSchemaObject {
    when (mode) {
      Mode.OLD -> oldMergeOldObject()
      Mode.NEW -> newMergeNewObject()
      Mode.OLD_OBJECT_NEW_MERGE -> newMergeOldObject()
    }
    return JsonSchemaReader.readFromFile(project, schema)
  }

  private fun doTestMerging(@Language("json") schemaText: String,
                            @Language("json") jsonText: String,
                            oldMode: Mode,
                            newMode: Mode) {
    thisLogger().info("RESOLVED SCHEMA COMPARISON MODES:               [[OLD]] is $oldMode, [[NEW]] is $newMode")

    val schema = schema(schemaText)

    val oldRootSchema = computeRootSchemaInMode(schema, oldMode)
    val newRootSchema = computeRootSchemaInMode(schema, newMode)
    val jsonFile = file(jsonText)

    jsonFile.accept(object : PsiRecursiveElementVisitor() {
      override fun visitElement(element: PsiElement) {
        super.visitElement(element)
        if (element is JsonStringLiteral) {
          doCompareResolvedJsonObjectAtCaret(element, oldMode, newMode, oldRootSchema, newRootSchema)
        }
      }
    })
  }

  private fun doCompareResolvedJsonObjectAtCaret(inspectedElement: JsonStringLiteral,
                                                 oldMode: Mode,
                                                 newMode: Mode,
                                                 oldRootSchema: JsonSchemaObject,
                                                 newRootSchema: JsonSchemaObject) {
    val old = resolvedSchemaObjectWithMode(inspectedElement, oldMode, oldRootSchema)
    val new = resolvedSchemaObjectWithMode(inspectedElement, newMode, newRootSchema)
    checkBothNullable(inspectedElement.text, old, new)
    if (old == null || new == null) return
    try {
      compareReading(old, new)
    }
    catch (exception: Throwable) {
      thisLogger().warn("Caught exception while inspecting \"${inspectedElement.text}\" element")
      throw exception
    }
  }

  private fun resolvedSchemaObjectWithMode(inspectedElement: JsonStringLiteral,
                                           mode: Mode,
                                           schemaForMode: JsonSchemaObject): JsonSchemaObject? {
    when (mode) {
      Mode.OLD -> oldMergeOldObject()
      Mode.NEW -> newMergeNewObject()
      Mode.OLD_OBJECT_NEW_MERGE -> newMergeOldObject()
    }
    return resolvedSchemaObject(inspectedElement, schemaForMode)
  }

  private fun resolvedSchemaObject(inspectedElement: JsonStringLiteral, schemaObject: JsonSchemaObject): JsonSchemaObject? {
    val jsonValue = inspectedElement.parentOfType<JsonProperty>()!!.value!!

    val steps = JsonOriginalPsiWalker.INSTANCE.findPosition(inspectedElement, true)!!
    val positionAdapter = JsonOriginalPsiWalker.INSTANCE.createValueAdapter(inspectedElement)

    val node = JsonSchemaVariantsTreeBuilder.buildTree(myFixture.project, JsonSchemaNodeExpansionRequest(positionAdapter, true), schemaObject, steps, true)
    return selectSchema(node, jsonValue, steps.isEmpty)
  }

  private fun readResourceAsString(@Suppress("SameParameterValue") path: String): String {
    return File(path).readText()
  }

  override fun getTestDataPath(): String {
    val strategy = PathManagerEx.guessTestDataLookupStrategy()
    if (strategy == TestDataLookupStrategy.COMMUNITY) {
      return PathManager.getHomePath() + "/json/backend/tests/testData/jsonSchema/v2"
    }
    return PathManager.getHomePath() + "/community/json/backend/tests/testData/jsonSchema/v2"
  }

  private fun propertyDependencySchema(): String {
    return readResourceAsString("$testDataPath/propertyDependencySchema.json")
  }

  private fun schemaDependencySchema(): String {
    return readResourceAsString("$testDataPath/schemaDependencySchema.json")
  }

  private fun openapi3schema(): String {
    return readResourceAsString("$testDataPath/openapi_3_0_0.json")
  }

  private fun openapi31schema(): String {
    return readResourceAsString("$testDataPath/openapi_3_1_0.json")
  }

  private fun openapi3schemaCut(): String {
    return readResourceAsString("$testDataPath/openapi_3_0_0_cut.json")
  }

  private fun petstore30json(): String {
    return readResourceAsString("$testDataPath/specs/petstore30.json")
  }

  private fun petstore31json(): String {
    return readResourceAsString("$testDataPath/specs/petstore31.json")
  }

  private fun yamlspec8k(): String {
    return readResourceAsString("$testDataPath/specs/largespec8k.yaml")
  }


  private fun petstore30jsonCut(): String {
    return readResourceAsString("$testDataPath/specs/petstore30cut.json")
  }

  fun `test equal reading results`() {
    doTestReading(propertyDependencySchema())
    doTestReading(schemaDependencySchema())
    doTestReading(openapi3schema())
    doTestReading(openapi3schemaCut())
    doTestReading(openapi31schema())
  }

  fun `disabled test equal merging results`() {
    doTestMerging(
      openapi31schema(),
      petstore31json(),
      Mode.OLD,
      Mode.NEW
    )
    doTestMerging(
      openapi3schemaCut(),
      petstore30jsonCut(),
      Mode.OLD,
      Mode.NEW
    )
    doTestMerging(
      openapi3schema(),
      petstore30json(),
      Mode.OLD,
      Mode.NEW
    )
  }

  fun `test measure performance here`() {
    myFixture.configureByText("openapi.yaml", yamlspec8k())
    myFixture.checkHighlighting()
  }
}