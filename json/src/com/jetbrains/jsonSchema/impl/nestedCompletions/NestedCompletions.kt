// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.jsonSchema.impl.nestedCompletions

import com.intellij.codeInsight.completion.InsertionContext
import com.intellij.json.pointer.JsonPointerPosition
import com.intellij.openapi.project.Project
import com.intellij.psi.*
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.psi.util.*
import com.jetbrains.jsonSchema.extension.JsonLikePsiWalker
import com.jetbrains.jsonSchema.extension.adapters.JsonObjectValueAdapter
import com.jetbrains.jsonSchema.extension.adapters.JsonPropertyAdapter
import com.jetbrains.jsonSchema.extension.adapters.JsonValueAdapter
import com.jetbrains.jsonSchema.impl.JsonSchemaObject
import com.jetbrains.jsonSchema.impl.JsonSchemaResolver
import com.jetbrains.jsonSchema.impl.light.nodes.isNotBlank

/**
 * Collects nested completions for a JSON schema object.
 * If `[node] == null`, it will just call collector once.
 *
 * @param project The project where the JSON schema is being used.
 * @param node A tree structure that represents a path through which we want nested completions.
 * @param completionPath The path of the completion in the schema.
 * @param collector The callback function to collect the nested completions.
 */
internal fun JsonSchemaObject.collectNestedCompletions(
  project: Project,
  node: NestedCompletionsNode?,
  completionPath: SchemaPath?,
  collector: (path: SchemaPath?, schema: JsonSchemaObject) -> Unit,
) {
  collector(completionPath, this) // Breadth first

  node
    ?.children
    ?.filterIsInstance<ChildNode.OpenNode>()
    ?.forEach { (name, childNode) ->
      for (subSchema in findSubSchemasByName(project, name)) {
        subSchema.collectNestedCompletions(project, childNode, completionPath / name, collector)
      }
    }
}

private fun JsonSchemaObject.findSubSchemasByName(project: Project, name: String): Iterable<JsonSchemaObject> =
  JsonSchemaResolver(project, this, JsonPointerPosition().apply { addFollowingStep(name) }).resolve()

internal fun CharSequence.mark(index: Int): String = substring(0, index) + "|" + substring(index)  // TODO: Remove, debugging only!
internal fun CharSequence.mark(vararg indices: Int): String = indices.sortedDescending()  // TODO: Remove, debugging only!
  .fold(toString()) { acc, mark -> acc.mark(mark) } // TODO: Remove, debugging only!


internal fun JsonLikePsiWalker.findChildBy(path: SchemaPath?, start: PsiElement): PsiElement =
  path?.let {
    findContainingObjectAdapter(start)
      ?.findChildBy(path.accessor(), offset = 0)
      ?.delegate
  } ?: start

private fun JsonLikePsiWalker.findContainingObjectAdapter(start: PsiElement) =
  start.parents(true).firstNotNullOfOrNull { createValueAdapter(it)?.asObject }

internal tailrec fun JsonObjectValueAdapter.findChildBy(path: List<String>, offset: Int): JsonValueAdapter? =
  if(offset > path.lastIndex) this
  else childByName(path[offset])
    ?.asObject
    ?.findChildBy(path, offset + 1)

private fun JsonObjectValueAdapter.childByName(name: String): JsonValueAdapter? =
  propertyList.firstOrNull { it.name == name }
    ?.values
    ?.firstOrNull()

fun expandMissingPropertiesAndMoveCaret(context: InsertionContext, completionPath: SchemaPath?) {
  if (completionPath == null) return
  val element = context.file.findElementAt(context.startOffset)?.parent ?: return
  val walker = JsonLikePsiWalker.getWalker(element) ?: return
  val container = element.parent ?: return
  val path = completionPath.accessor()
  if (path.isNotEmpty()) {
    val parentObject = walker.createValueAdapter(container)?.asObject
      ?: walker.createValueAdapter(container.parent)?.asObject?.takeIf {
        // the first condition is a hack for yaml, we need to invent a better solution here
        walker.defaultObjectValue.isNotBlank() && walker.getParentPropertyAdapter(container) != null
      }
      ?: replaceAtCaretAndGetParentObject(element, walker, context, path).let {
        walker.createValueAdapter(it)?.asObject
      } ?: return
    val newElement = doExpand(parentObject, path, walker, element, 0, null) ?: return
    val pointer = SmartPointerManager.createPointer(newElement)
    cleanupWhitespacesAndDelete(element)
    PsiDocumentManager.getInstance(context.project).doPostponedOperationsAndUnblockDocument(context.document)
    val psiElement = rewindToMeaningfulLeaf(pointer.element)
    if (psiElement != null) {
      context.editor.caretModel.moveToOffset(psiElement.endOffset)
    }
  }
}

fun rewindToMeaningfulLeaf(element: PsiElement?): PsiElement? {
  var meaningfulLeaf = element?.lastLeaf()
  while (meaningfulLeaf is PsiWhiteSpace || meaningfulLeaf is PsiErrorElement) {
    meaningfulLeaf = meaningfulLeaf.prevLeaf()
  }
  return meaningfulLeaf
}

private fun replaceAtCaretAndGetParentObject(element: PsiElement,
                                             walker: JsonLikePsiWalker,
                                             context: InsertionContext,
                                             path: List<String>): PsiElement {
  val newProperty = walker.getSyntaxAdapter(context.project).createProperty(path.first(), "foo", context.project)
  walker.getParentPropertyAdapter(newProperty)!!.values.single().delegate.replace(element.copy())

  val parentAdapter = element.parent?.let { walker.getParentPropertyAdapter(it) }
  if (parentAdapter != null && parentAdapter.nameValueAdapter?.delegate == element) {
    return element.parent.replace(newProperty).parent
  }

  return element.replace(newProperty.parent)
}

private fun cleanupWhitespacesAndDelete(it: PsiElement) {
  // cleanup redundant whitespace
  var next = it.nextSibling
  while (next != null && next.text.isBlank()) {
    val n = next
    next = next.nextSibling
    n.delete()
  }
  var prev = it.prevSibling
  while (prev != null && prev.text.isBlank()) {
    val n = prev
    prev = prev.prevSibling
    n.delete()
  }
  it.delete()
}

private fun addNewPropertyWithObjectValue(parentObject: JsonObjectValueAdapter, propertyName: String, walker: JsonLikePsiWalker, element: PsiElement): JsonPropertyAdapter {
  val project = parentObject.delegate.project
  val syntaxAdapter = walker.getSyntaxAdapter(project)
  return syntaxAdapter.createProperty(propertyName, "f", project).also {
    walker.getParentPropertyAdapter(it)!!.values.single().delegate.replace(element.copy())
  }.let {
    if (element.parent == parentObject.delegate) {
      parentObject.delegate.addAfter(it, element.copy())
    }
    else {
      addBeforeOrAfter(parentObject, it, element)
    }
  }.let { walker.getParentPropertyAdapter(it)!! }
}

private fun doExpand(parentObject: JsonObjectValueAdapter,
                     completionPath: List<String>,
                     walker: JsonLikePsiWalker,
                     element: PsiElement,
                     index: Int,
                     fakeProperty: PsiElement?): PsiElement? {
  val property: JsonPropertyAdapter = parentObject.propertyList.firstOrNull { it.name == completionPath[index] }
                                      ?: addNewPropertyWithObjectValue(parentObject, completionPath[index], walker, element)
  fakeProperty?.let {
    cleanupWhitespacesAndDelete(it)
  }
  val value = property.values.singleOrNull()
  if (value == null) return null
  if (index + 1 < completionPath.size) {
    val project = parentObject.delegate.project
    val fake = walker.getSyntaxAdapter(project).createProperty("f", "f", project)
    val newValue = if (value.isObject) value.delegate else value.delegate.replace(fake.parent)
    val newValueAsObject = walker.createValueAdapter(newValue)!!.asObject!!
    return doExpand(newValueAsObject, completionPath, walker, element, index + 1,
                    if (value.isObject) null
                    else newValueAsObject.propertyList.single().delegate)
  }
  else {
    val movedElement =
      if (value.isObject) {
        addBeforeOrAfter(value, element.copy(), element)
      }
      else {
        val newElement = replaceValueForNesting(walker, value, element)
        if (walker.defaultObjectValue.isBlank()) {
          newElement.parent.addBefore(createLeaf("\n", newElement)!!, newElement)
        }
        newElement
      }
    return movedElement
  }
}

private fun addBeforeOrAfter(value: JsonValueAdapter,
                             elementToAdd: PsiElement,
                             element: PsiElement): PsiElement {
  val properties = value.asObject?.propertyList.orEmpty()
  val firstProperty = properties.firstOrNull()
  val lastProperty = properties.lastOrNull()
  return if (lastProperty != null && element.startOffset >= lastProperty.delegate.endOffset) {
    val newElement = value.delegate.addAfter(elementToAdd, lastProperty.delegate)
    newElement.parent.addBefore(createLeaf("\n", newElement)!!, newElement)
    newElement
  }
  else {
    val newElement = value.delegate.addBefore(elementToAdd, firstProperty?.delegate)
    newElement.parent.addAfter(createLeaf("\n", newElement)!!, newElement)
    newElement
  }
}

private fun replaceValueForNesting(walker: JsonLikePsiWalker,
                                   value: JsonValueAdapter,
                                   element: PsiElement): PsiElement {
  return if (walker.defaultObjectValue.isNotBlank()) {
    value.delegate.replace(
      walker.getSyntaxAdapter(value.delegate.project).createProperty("f", "f",
                                                                     value.delegate.project).parent.also {
        walker.createValueAdapter(it)!!.asObject!!.propertyList.single().let {
          it.nameValueAdapter!!.delegate.replace(element.copy())
          it.values.forEach { it.delegate.delete() }
        }
      }
    ).let {
      walker.createValueAdapter(it)!!.asObject!!.propertyList.single().delegate
    }
  }
  else if (value.delegate.text == element.text) value.delegate else value.delegate.replace(element.copy())
}

private fun createLeaf(content: String, context: PsiElement): LeafPsiElement? {
  val psiFileFactory = PsiFileFactory.getInstance(context.project)
  return psiFileFactory.createFileFromText("dummy." + context.containingFile.virtualFile.extension,
                                           context.containingFile.fileType, content)
    .descendantsOfType<LeafPsiElement>().firstOrNull { it.text == content }
}