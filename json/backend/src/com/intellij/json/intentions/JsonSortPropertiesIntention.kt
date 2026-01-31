// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.json.intentions

import com.intellij.codeInsight.intention.PriorityAction
import com.intellij.ide.lightEdit.LightEditCompatible
import com.intellij.json.JsonBundle
import com.intellij.json.psi.JsonFile
import com.intellij.json.psi.JsonObject
import com.intellij.json.psi.JsonProperty
import com.intellij.json.psi.impl.JsonRecursiveElementVisitor
import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.Presentation
import com.intellij.modcommand.PsiUpdateModCommandAction
import com.intellij.openapi.project.DumbAware
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.annotations.Nls

open class JsonSortPropertiesIntention : PsiUpdateModCommandAction<PsiFile>(PsiFile::class.java), LightEditCompatible, DumbAware {

  protected open fun createSession(context: ActionContext, file: PsiFile): AbstractSortPropertiesSession<out PsiElement, out PsiElement> {
    return JsonSortSession(context, file)
  }

  override fun getFamilyName(): @Nls(capitalization = Nls.Capitalization.Sentence) String =
    JsonBundle.message("json.intention.sort.properties")

  override fun getPresentation(context: ActionContext, element: PsiFile): Presentation? {
    val session = createSession(context, element)
    val root = session.rootElement ?: return null
    if (!session.hasUnsortedObjects()) return null
    return Presentation.of(familyName)
      .withPriority(PriorityAction.Priority.LOW)
      .withHighlighting(root.textRange)
  }

  override fun invoke(context: ActionContext, element: PsiFile, updater: ModPsiUpdater) {
    val session = createSession(context, element)
    val root = session.rootElement ?: return
    session.sort()
    CodeStyleManager.getInstance(context.project).reformatText(element, setOf(root.textRange))
  }

  private class JsonSortSession(context: ActionContext, file: PsiFile)
    : AbstractSortPropertiesSession<JsonObject, JsonProperty>(context, file) {

    override fun findRootObject(): JsonObject? {
      val offset = context.offset
      val initObj: JsonObject? = PsiTreeUtil.getParentOfType(file.findElementAt(offset), JsonObject::class.java) ?: run {
        val jsonFile = file as? JsonFile ?: return@run null
        return jsonFile.allTopLevelValues.filterIsInstance<JsonObject>().firstOrNull()
      }
      return adjustToSelectionContainer(initObj)
    }

    override fun collectObjects(rootObj: JsonObject): Set<JsonObject> = collectIntersectingObjects(rootObj)

    override fun getProperties(obj: JsonObject): MutableList<JsonProperty> = obj.propertyList

    override fun getPropertyName(prop: JsonProperty): String? = prop.name

    override fun getParentObject(obj: JsonObject): JsonObject? = PsiTreeUtil.getParentOfType(obj, JsonObject::class.java)

    override fun traverseObjects(root: JsonObject, visitor: (JsonObject) -> Unit) {
      object : JsonRecursiveElementVisitor() {
        override fun visitObject(o: JsonObject) {
          super.visitObject(o)
          visitor(o)
        }
      }.visitObject(root)
    }
  }
}