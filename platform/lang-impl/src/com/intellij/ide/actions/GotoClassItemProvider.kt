// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions

import com.intellij.ide.actions.searcheverywhere.FoundItemDescriptor
import com.intellij.ide.actions.searcheverywhere.footer.ClassHistoryManager
import com.intellij.ide.util.gotoByName.ChooseByNameViewModel
import com.intellij.ide.util.gotoByName.DefaultChooseByNameItemProvider
import com.intellij.openapi.module.ModuleUtil
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiQualifiedNamedElement
import com.intellij.util.Processor
import com.intellij.util.indexing.FindSymbolParameters

class GotoClassItemProvider(val context: PsiElement?) : DefaultChooseByNameItemProvider(context) {
  override fun fetchRecents(project: Project,
                            indicator: ProgressIndicator,
                            pattern: String,
                            base: ChooseByNameViewModel,
                            consumer: Processor<in FoundItemDescriptor<*>>): Boolean {
    if (StringUtil.isNotEmpty(pattern)) return false

    val state = ClassHistoryManager.getInstance(project).state
    val qname2Modules = state.qname2Modules
    val qname2Name = state.qname2Name
    val names = qname2Name.values.map {
      matches(base, "*", buildPatternMatcher(it, true), it)
    }

    val consumerFQN: Processor<in FoundItemDescriptor<*>> = Processor {
      val any = it.item
      if (any is PsiQualifiedNamedElement) {
        val qualifiedName = any.qualifiedName ?: return@Processor true
        if (!qname2Name.keys.contains(qualifiedName)) return@Processor true

        val module = ModuleUtil.findModuleForPsiElement(any) ?: return@Processor true
        if (qname2Modules[qualifiedName]?.any { moduleName -> moduleName == module.name } == true) {
          consumer.process(it)
          return@Processor true
        }
      }
      true
    }

    return processByNames(base, true, indicator, context, consumerFQN, names, FindSymbolParameters.simple(project, false))
  }
}