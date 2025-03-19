// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion.commands.impl

import com.intellij.codeInsight.completion.PrefixMatcher
import com.intellij.codeInsight.completion.command.CommandCompletionProviderContext
import com.intellij.codeInsight.completion.command.CommandProvider
import com.intellij.codeInsight.completion.command.CompletionCommand
import com.intellij.codeInsight.completion.impl.CamelHumpMatcher
import com.intellij.codeInsight.daemon.QuickFixBundle
import com.intellij.codeInsight.intention.impl.ShowIntentionActionsHandler
import com.intellij.lang.java.actions.CreateMethodAction
import com.intellij.lang.java.request.generateActions
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry.Companion.`is`
import com.intellij.psi.*
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageEditorUtil
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.PsiUtil
import com.intellij.psi.util.parentOfType
import org.jetbrains.annotations.Nls
import javax.swing.Icon


internal class JavaCreateFromUsagesCommandProvider : CommandProvider {
  override fun getCommands(context: CommandCompletionProviderContext): List<CompletionCommand> {
    if (!`is`("java.command.completion.create.from.usages")) return emptyList()
    val editor = context.editor
    if (InjectedLanguageEditorUtil.getTopLevelEditor(editor) != editor) return emptyList()
    if (context.isReadOnly) return emptyList()
    val originalPsiFile = context.originalPsiFile
    val offset = context.offset
    var psiElement = originalPsiFile.findElementAt(offset)
    if (psiElement is PsiJavaToken || psiElement is PsiIdentifier) {
      psiElement = psiElement.parent
    }
    if (psiElement !is PsiReferenceExpression) return emptyList()
    val qualifier = psiElement.qualifier
    if (qualifier !is PsiExpression) return emptyList()
    val psiClass = PsiUtil.resolveClassInClassTypeOnly(qualifier.type) ?: return emptyList()
    if (!psiClass.isWritable) return emptyList()
    if (psiElement.referenceName != null && !PsiNameHelper.getInstance(context.project).isIdentifier(psiElement.referenceName)) return emptyList()
    return listOf(JavaCreateFromUsagesCompletionCommand(psiClass))
  }
}

internal class JavaCreateFromUsagesCompletionCommand(val psiClass: PsiClass) : CompletionCommand() {

  private val methodNames: Set<String> = psiClass.allMethods.map { it.name }.toSet()

  override val name: String
    get() = "Create method from usage"
  override val i18nName: @Nls String
    get() = QuickFixBundle.message("create.method.from.usage.family")
  override val icon: Icon?
    get() = null

  override fun execute(offset: Int, psiFile: PsiFile, editor: Editor?) {
    val fileDocument = psiFile.fileDocument
    var currentOffset = offset
    val previousElement = if (currentOffset >= 0) psiFile.findElementAt(currentOffset - 1) else null
    WriteAction.run<RuntimeException> {
      val addSemicolon = previousElement?.parentOfType<PsiExpressionStatement>() != null
      if (fileDocument.charsSequence[currentOffset - 1] == '.') {
        fileDocument.insertString(currentOffset, "method")
        currentOffset = currentOffset + 6
      }
      if (previousElement == null || PsiTreeUtil.nextLeaf(previousElement) !is PsiJavaToken) {
        fileDocument.insertString(currentOffset, "()")
        if (addSemicolon) {
          fileDocument.insertString(currentOffset + 2, ";")
        }
      }
      PsiDocumentManager.getInstance(psiFile.project).commitDocument(fileDocument)
    }
    val psiElement = psiFile.findElementAt(currentOffset) ?: return
    val expression = psiElement.parentOfType<PsiMethodCallExpression>() ?: return
    val action = generateActions(expression).firstOrNull { it is CreateMethodAction } ?: return
    ShowIntentionActionsHandler.chooseActionAndInvoke(psiFile, editor, action, name)
  }

  override fun customPrefixMatcher(prefix: String): PrefixMatcher? {
    return AlwaysMatchingCamelHumpMatcher(prefix, psiClass.project, methodNames)
  }

  override val priority: Int?
    get() = 500
}

private class AlwaysMatchingCamelHumpMatcher(
  prefix: String,
  private val project: Project,
  private val predefinedNames: Set<String>,
) : CamelHumpMatcher(prefix) {
  override fun prefixMatches(name: String): Boolean {
    return prefix == "" || PsiNameHelper.getInstance(project).isIdentifier(prefix) && !predefinedNames.contains(prefix)
  }

  override fun isStartMatch(name: String?) = name == null || prefixMatches(name)
  override fun cloneWithPrefix(prefix: String) = if (prefix == this.prefix) this else AlwaysMatchingCamelHumpMatcher(prefix, project, predefinedNames)
}