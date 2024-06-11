// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.jsonSchema.impl.nestedCompletions

import com.intellij.codeInsight.completion.InsertionContext
import com.intellij.json.pointer.JsonPointerPosition
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.*
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.psi.util.*
import com.jetbrains.jsonSchema.extension.JsonLikePsiWalker
import com.jetbrains.jsonSchema.extension.adapters.JsonObjectValueAdapter
import com.jetbrains.jsonSchema.extension.adapters.JsonPropertyAdapter
import com.jetbrains.jsonSchema.extension.adapters.JsonValueAdapter
import com.jetbrains.jsonSchema.impl.JsonSchemaObject
import com.jetbrains.jsonSchema.impl.JsonSchemaResolver
import com.jetbrains.jsonSchema.impl.JsonSchemaType
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


internal fun JsonLikePsiWalker.findChildBy(path: SchemaPath?, start: PsiElement): PsiElement =
  path?.let {
    findContainingObjectAdapter(start)
      ?.findChildBy(path.accessor(), offset = 0)
      ?.delegate
  } ?: start

private fun JsonLikePsiWalker.findContainingObjectAdapter(start: PsiElement) =
  start.parents(true).firstNotNullOfOrNull { createValueAdapter(it)?.asObject }

internal tailrec fun JsonObjectValueAdapter.findChildBy(path: List<String>, offset: Int): JsonValueAdapter? =
  if (offset > path.lastIndex) this
  else propertyList.firstOrNull { it.name == path[offset] }
    ?.values
    ?.firstOrNull()
    ?.asObject
    ?.findChildBy(path, offset + 1)

fun expandMissingPropertiesAndMoveCaret(context: InsertionContext, completionPath: SchemaPath?) {
  val path = completionPath?.accessor()
  if (path.isNullOrEmpty()) return
  val element = context.file.findElementAt(context.startOffset)?.parent ?: return
  val walker = JsonLikePsiWalker.getWalker(element) ?: return
  val parentObject = getOrCreateParentObject(element, walker, context, path) ?: return
  val newElement = doExpand(parentObject, path, walker, element, 0, null) ?: return
  cleanupWhitespacesAndDelete(walker.getParentPropertyAdapter(element)?.takeIf {
    it.nameValueAdapter?.delegate == element
  }?.delegate ?: element)
  // the inserted element might contain invalid psi and be re-invalidated after the document commit,
  // that's why we preserve the range instead and try restoring what was under
  val pointer = SmartPointerManager.getInstance(context.project).createSmartPsiFileRangePointer(newElement.containingFile, newElement.textRange)
  PsiDocumentManager.getInstance(context.project).doPostponedOperationsAndUnblockDocument(context.document)
  val e = context.file.findElementAt(pointer.range!!.startOffset)
  val psiElement = rewindToMeaningfulLeaf(e, walker)
  if (psiElement != null) {
    context.editor.caretModel.moveToOffset(psiElement.endOffset)
  }
}

private const val dummyString = "jsonRulezzz111"

private fun getOrCreateParentObject(element: PsiElement,
                                    walker: JsonLikePsiWalker,
                                    context: InsertionContext,
                                    path: List<String>): JsonObjectValueAdapter? {
  val container = element.parent ?: return null
  return walker.createValueAdapter(container)?.asObject
    ?: walker.createValueAdapter(container.parent)?.asObject?.takeIf {
      // the first condition is a hack for yaml, we need to invent a better solution here
      walker.defaultObjectValue.isNotBlank() && walker.getParentPropertyAdapter(container) != null
    }
    ?: replaceAtCaretAndGetParentObject(element, walker, context, path).let {
      walker.createValueAdapter(it)?.asObject
    }
}

fun rewindToMeaningfulLeaf(element: PsiElement?, walker: JsonLikePsiWalker): PsiElement? {
  var meaningfulLeaf = element?.lastLeaf()
  while (meaningfulLeaf is PsiWhiteSpace || meaningfulLeaf is PsiErrorElement ||
    meaningfulLeaf is LeafPsiElement && meaningfulLeaf.text == ",") {
    meaningfulLeaf = meaningfulLeaf.prevLeaf()
  }
  return meaningfulLeaf
}

private fun replaceAtCaretAndGetParentObject(element: PsiElement,
                                             walker: JsonLikePsiWalker,
                                             context: InsertionContext,
                                             path: List<String>): PsiElement {
  val newProperty = walker.getSyntaxAdapter(context.project).createProperty(path.first(), dummyString, context.project)
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

private fun addNewPropertyWithObjectValue(parentObject: JsonObjectValueAdapter, propertyName: String, walker: JsonLikePsiWalker, element: PsiElement, fakeProperty: PsiElement?): JsonPropertyAdapter {
  val project = parentObject.delegate.project
  val syntaxAdapter = walker.getSyntaxAdapter(project)
  return syntaxAdapter.createProperty(propertyName, dummyString, project).also {
    walker.getParentPropertyAdapter(it)!!.values.single().delegate.replace(element.copy())
  }.let {
    addBeforeOrAfter(parentObject, it, element, fakeProperty)
  }.let { walker.getParentPropertyAdapter(it)!! }
}

private tailrec fun doExpand(parentObject: JsonObjectValueAdapter,
                     completionPath: List<String>,
                     walker: JsonLikePsiWalker,
                     element: PsiElement,
                     index: Int,
                     fakeProperty: PsiElement?): PsiElement? {
  val property = parentObject.propertyList.firstOrNull { it.name == completionPath[index] }
                  ?: addNewPropertyWithObjectValue(parentObject, completionPath[index], walker, element, fakeProperty)
  fakeProperty?.let {
    cleanupWhitespacesAndDelete(it)
  }
  val value = property.values.singleOrNull()
  if (value == null) return null
  if (index + 1 < completionPath.size) {
    val project = parentObject.delegate.project
    val fake = walker.getSyntaxAdapter(project).createProperty(dummyString, dummyString, project)
    val newValue = if (value.isObject) value.delegate else value.delegate.replace(fake.parent)
    switchToObjectSeparator(walker, property.delegate)
    val newValueAsObject = walker.createValueAdapter(newValue)!!.asObject!!
    return doExpand(newValueAsObject, completionPath, walker, element, index + 1,
                    if (value.isObject) null
                    else newValueAsObject.propertyList.single().delegate)
  }
  else {
    val movedElement =
      if (value.isObject) {
        val elementToAdd = walker.getParentPropertyAdapter(element)?.takeIf {
          it.nameValueAdapter?.delegate == element
        }?.delegate ?: if (walker.createValueAdapter(element)?.isStringLiteral == true) {
          walker.getSyntaxAdapter(element.project).createProperty(
            StringUtil.unquoteString(element.text), dummyString, element.project
          ).also { removePropertyValue(walker, it) }
        } else element.copy()
        addBeforeOrAfter(value, elementToAdd, element, fakeProperty)
      }
      else {
        val newElement = replaceValueForNesting(walker, value, element)
        if (walker.defaultObjectValue.isBlank()) {
          newElement.parent.addBefore(createLeaf("\n", newElement)!!, newElement)
        }
        switchToObjectSeparator(walker, property.delegate)
        newElement
      }
    return movedElement
  }
}

private fun switchToObjectSeparator(walker: JsonLikePsiWalker, node: PsiElement) {
  val nonObjectSeparator = walker.getPropertyValueSeparator(null).trim()
  val objectSeparator = walker.getPropertyValueSeparator(JsonSchemaType._object).trim()
  if (nonObjectSeparator != objectSeparator) {
    node.childLeafs().filter { it.parent == node }.firstOrNull { it.text == nonObjectSeparator }?.let {
      if (objectSeparator.isBlank()) {
        deleteWithWsAround(it, deleteBefore = false)
      }
      else it.replace(createLeaf(objectSeparator, it)!!)
    }
  }
}

private fun removePropertyValue(walker: JsonLikePsiWalker, it: PsiElement) {
  walker.getParentPropertyAdapter(it)!!.values.singleOrNull()?.delegate?.delete()
  it.childLeafs().firstOrNull { it.text == walker.getPropertyValueSeparator(null).trim() }?.let {
    deleteWithWsAround(it)
  }
}

private fun deleteWithWsAround(it: PsiElement, deleteBefore: Boolean = true) {
  if (deleteBefore) it.prevSibling?.takeIf { it.text.isBlank() }?.delete()
  it.nextSibling?.takeIf { it.text.isBlank() }?.delete()
  it.delete()
}

private fun addBeforeOrAfter(value: JsonValueAdapter,
                             elementToAdd: PsiElement,
                             element: PsiElement,
                             fakeProperty: PsiElement?): PsiElement {
  val properties = value.asObject?.propertyList.orEmpty()
  val firstProperty = properties.firstOrNull()
  val lastProperty = properties.lastOrNull()
  return if (lastProperty != null && element.startOffset >= lastProperty.delegate.endOffset) {
    val newElement = value.delegate.addAfter(elementToAdd, lastProperty.delegate)
    if (lastProperty.delegate != fakeProperty) {
      JsonLikePsiWalker.getWalker(newElement)?.getSyntaxAdapter(newElement.project)?.ensureComma(
        lastProperty.delegate, newElement
      )
    }
    newElement
  }
  else {
    val newElement = value.delegate.addBefore(elementToAdd, firstProperty?.delegate)
    firstProperty?.delegate?.takeIf { it != fakeProperty }?.let {
      JsonLikePsiWalker.getWalker(newElement)?.getSyntaxAdapter(newElement.project)?.ensureComma(
        newElement, it
      )
    }
    newElement
  }
}

private fun replaceValueForNesting(walker: JsonLikePsiWalker,
                                   value: JsonValueAdapter,
                                   element: PsiElement): PsiElement {
  return if (walker.defaultObjectValue.isNotBlank()) {
    value.delegate.replace(
      walker.getSyntaxAdapter(value.delegate.project).createProperty(dummyString, dummyString,
                                                                     value.delegate.project).parent.also {
        walker.createValueAdapter(it)!!.asObject!!.propertyList.single().let {
          it.nameValueAdapter!!.delegate.replace(element.copy())
          removePropertyValue(walker, it.delegate)
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