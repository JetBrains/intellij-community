// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion.commands.impl

import com.intellij.codeInsight.CodeInsightBundle
import com.intellij.codeInsight.completion.command.CommandCompletionProviderContext
import com.intellij.codeInsight.completion.command.CommandProvider
import com.intellij.codeInsight.completion.command.CompletionCommand
import com.intellij.codeInsight.completion.command.getCommandContext
import com.intellij.codeInsight.daemon.impl.quickfix.AddDefaultConstructorFix
import com.intellij.codeInsight.daemon.impl.quickfix.AddMethodFix
import com.intellij.codeInsight.generation.ClassMember
import com.intellij.codeInsight.generation.GenerateConstructorHandler
import com.intellij.codeInsight.generation.GenerationInfo
import com.intellij.codeInsight.generation.PsiMethodMember
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo
import com.intellij.java.JavaBundle
import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModCommandExecutor
import com.intellij.modcommand.PsiUpdateModCommandAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.psi.*
import com.intellij.psi.util.TypeConversionUtil
import com.intellij.psi.util.parentOfType
import org.jetbrains.annotations.Nls

internal class JavaGenerateConstructorCompletionCommandProvider : CommandProvider {
  private fun findContext(context: CommandCompletionProviderContext): PsiClass? {
    val element = getCommandContext(context.offset, context.psiFile) ?: return null
    val containingClass = element.parentOfType<PsiClass>() ?: return null
    if (containingClass.isInterface || containingClass.isRecord || containingClass is PsiImplicitClass) return null
    if (element is PsiIdentifier && element.parent is PsiClass) return element.parent as PsiClass
    if (!(element is PsiWhiteSpace && element.text.contains("\n"))) return null
    return element.parentOfType<PsiClass>()
  }

  override fun getCommands(context: CommandCompletionProviderContext): List<CompletionCommand> {
    val clazz = findContext(context) ?: return emptyList()
    val result = mutableListOf<CompletionCommand>()
    val actionContext = ActionContext.from(context.editor, context.psiFile)
    if (clazz.constructors.none { it.parameters.isEmpty() }) {
      val fix = AddDefaultConstructorFix(clazz, PsiModifier.PUBLIC)
      result.add(GenerateConstructorCompletionCommand(fix,
                                                      actionContext,
                                                      CodeInsightBundle.message("command.completion.generate.text", JavaBundle.message("command.completion.generate.no.args.constructor.text")),
                                                      listOf("generate default constructor", "generate no args constructor")))
    }

    val handler = object : GenerateConstructorHandler() {
      public override fun getAllOriginalMembers(aClass: PsiClass?): Array<out ClassMember?>? {
        return super.getAllOriginalMembers(aClass)
      }

      public override fun generateMemberPrototypes(aClass: PsiClass?, members: Array<out ClassMember?>?): List<GenerationInfo?> {
        return super.generateMemberPrototypes(aClass, members)
      }
    }
    var members = handler.getAllOriginalMembers(clazz) ?: return result
    if (members.isEmpty()) return result

    //let's simplify condition, because it can be quite tricky to find out whether there is a constructor with the same arguments or
    //which super constructor should be called
    val superClass = clazz.superClass
    if ((clazz.constructors.none { it.parameters.size == members.size } &&
         (superClass == null ||
          superClass.constructors.isEmpty() ||
          superClass.constructors.any { it.parameters.isEmpty() })) ||
        canUseSuperConstructor(superClass, clazz, members)) {
      val superConstructors = superClass?.constructors
      if (superConstructors?.size == 1) {
        val substitutor = TypeConversionUtil.getSuperClassSubstitutor(superClass, clazz, PsiSubstitutor.EMPTY)
        val superConstructor = PsiMethodMember(superConstructors[0], substitutor)
        members = arrayOf(superConstructor, *members)
      }
      val prototypes = handler.generateMemberPrototypes(clazz, members)
      if (prototypes.size == 1) {
        val generationInfo = prototypes[0]
        val text = (generationInfo?.psiMember as? PsiMethod)?.text ?: return result
        val fix = AddMethodFix(text, clazz)

        result.add(GenerateConstructorCompletionCommand(fix,
                                                        actionContext,
                                                        CodeInsightBundle.message("command.completion.generate.text", JavaBundle.message("command.completion.generate.all.args.constructor.text")),
                                                        listOf("generate all args constructor")))
      }
    }

    return result
  }

  private fun canUseSuperConstructor(
    superClass: PsiClass?,
    clazz: PsiClass,
    members: Array<out ClassMember?>,
  ): Boolean {
    if (superClass == null) return false
    if (superClass.constructors.size != 1) return false
    val method = superClass.constructors[0]
    val targetSize = members.size + method.parameters.size
    return clazz.constructors.none {
      it.parameters.size == targetSize
    }
  }

  class GenerateConstructorCompletionCommand(
    val fix: PsiUpdateModCommandAction<*>,
    val actionContext: ActionContext,
    @param:Nls override val presentableName: String,
    additionalSynonyms: List<String>,
  ) : CompletionCommand() {

    override val synonyms: List<String> = mutableListOf("generate constructor") + additionalSynonyms

    override val additionalInfo: String?
      get() {
        val shortcutText = KeymapUtil.getFirstKeyboardShortcutText("Generate")
        if (shortcutText.isNotEmpty()) {
          return shortcutText
        }
        return null
      }

    override fun execute(offset: Int, psiFile: PsiFile, editor: Editor?) {
      val actionContext = ActionContext.from(editor, psiFile)
      ModCommandExecutor.executeInteractively(actionContext, presentableName, editor) {
        fix.perform(actionContext)
      }
    }

    override fun getPreview(): IntentionPreviewInfo {
      return fix.generatePreview(actionContext)
    }
  }
}