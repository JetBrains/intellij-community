// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.json.intentions

import com.intellij.codeInsight.intention.BaseElementAtCaretIntentionAction
import com.intellij.codeInsight.intention.LowPriorityAction
import com.intellij.ide.lightEdit.LightEditCompatible
import com.intellij.json.JsonBundle
import com.intellij.json.psi.JsonFile
import com.intellij.json.psi.JsonObject
import com.intellij.json.psi.JsonProperty
import com.intellij.json.psi.impl.JsonRecursiveElementVisitor
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.SelectionModel
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.psi.*
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.refactoring.util.CommonRefactoringUtil
import com.intellij.util.IncorrectOperationException
import org.jetbrains.annotations.Nls

open class JsonSortPropertiesIntention : BaseElementAtCaretIntentionAction(), LowPriorityAction, LightEditCompatible, DumbAware {
  override fun getText(): @Nls(capitalization = Nls.Capitalization.Sentence) String = JsonBundle.message("json.intention.sort.properties")

  override fun getFamilyName(): @Nls(capitalization = Nls.Capitalization.Sentence) String =
    JsonBundle.message("json.intention.sort.properties")

  override fun isAvailable(project: Project, editor: Editor, element: PsiElement): Boolean {
    return Session(editor, element).hasUnsortedObjects()
  }

  @Throws(IncorrectOperationException::class)
  override fun invoke(project: Project, editor: Editor, element: PsiElement) {
    if (!CommonRefactoringUtil.checkReadOnlyStatus(project, element)) {
      CommonRefactoringUtil.showErrorHint(project, editor, JsonBundle.message("file.is.readonly"),
                                          JsonBundle.message("cannot.sort.properties"), null)
      return
    }
    val session = Session(editor, element)
    if (session.rootObj != null) {
      session.sort()
      reformat(project, editor, session.rootObj)
    }
  }

  private fun reformat(project: Project, editor: Editor, obj: JsonObject) {
    val pointer = SmartPointerManager.createPointer<JsonObject>(obj)
    PsiDocumentManager.getInstance(project).doPostponedOperationsAndUnblockDocument(editor.document)
    val element = pointer.element ?: return
    val codeStyleManager = CodeStyleManager.getInstance(project)
    codeStyleManager.reformatText(element.containingFile, setOf(element.textRange))
  }

  override fun startInWriteAction(): Boolean = true

  private class Session(editor: Editor, private val contextElement: PsiElement) {
    private val selectionModel: SelectionModel = editor.selectionModel
    val rootObj: JsonObject?
    private val objects: Set<JsonObject>

    init {
      rootObj = findRootObject()
      objects = if (rootObj != null) collectObjects(rootObj) else emptySet()
    }

    private fun collectObjects(rootObj: JsonObject): Set<JsonObject> {
      val result: MutableSet<JsonObject> = LinkedHashSet()
      if (selectionModel.hasSelection()) {
        object : JsonRecursiveElementVisitor() {
          override fun visitObject(o: JsonObject) {
            super.visitObject(o)
            if (o.textRange?.intersects(selectionModel.selectionStart, selectionModel.selectionEnd) == true) {
              result.add(o)
            }
          }
        }.visitObject(rootObj)
      }
      result.add(rootObj)
      return result
    }

    private fun findRootObject(): JsonObject? {
      val initObj: JsonObject? = PsiTreeUtil.getParentOfType(contextElement, JsonObject::class.java) ?: run {
        val jsonFile = contextElement.containingFile as? JsonFile ?: return@run null
        return jsonFile.allTopLevelValues.filterIsInstance<JsonObject>().firstOrNull()
      }
      if (initObj == null || !selectionModel.hasSelection()) {
        return initObj
      }
      var obj: JsonObject = initObj
      while (obj.textRange?.containsRange(selectionModel.selectionStart, selectionModel.selectionEnd) == false) {
        obj = PsiTreeUtil.getParentOfType(obj, JsonObject::class.java) ?: break
      }
      return obj
    }

    fun hasUnsortedObjects(): Boolean = objects.any { !isSorted(it) }

    fun sort() {
      objects.forEach {
        if (!isSorted(it)) {
          cycleSortProperties(it)
        }
      }
    }

    private fun isSorted(obj: JsonObject): Boolean {
      return obj.propertyList.asSequence()
        .map { it.name }
        .zipWithNext()
        .all { (l, r) -> l <= r }
    }

    // cycle-sort performs the minimal amount of modifications, and we want to patch the tree as little as possible
    private fun cycleSortProperties(obj: JsonObject) {
      val properties: MutableList<JsonProperty> = obj.propertyList
      val size = properties.size
      for (cycleStart in 0 until size) {
        val item = properties[cycleStart]
        var pos = advance(properties, size, cycleStart, item)
        if (pos == -1) continue
        if (pos != cycleStart) {
          exchange(properties, pos, cycleStart)
        }
        while (pos != cycleStart) {
          pos = advance(properties, size, cycleStart, properties[cycleStart])
          if (pos == -1) break
          if (pos != cycleStart) {
            exchange(properties, pos, cycleStart)
          }
        }
      }
    }

    private fun advance(properties: List<JsonProperty>, size: Int, cycleStart: Int, item: JsonProperty): Int {
      var pos = cycleStart
      val itemName = item.name
      for (i in cycleStart + 1 until size) {
        if (properties[i].name < itemName) pos++
      }
      if (pos == cycleStart) return -1
      while (itemName == properties[pos].name) pos++
      return pos
    }

    private fun exchange(properties: MutableList<JsonProperty>, pos: Int, item: Int) {
      val propertyAtPos = properties[pos]
      val itemProperty = properties[item]
      properties[pos] = propertyAtPos.parent.addBefore(itemProperty, propertyAtPos) as JsonProperty
      properties[item] = itemProperty.parent.addBefore(propertyAtPos, itemProperty) as JsonProperty
      propertyAtPos.delete()
      itemProperty.delete()
    }
  }
}