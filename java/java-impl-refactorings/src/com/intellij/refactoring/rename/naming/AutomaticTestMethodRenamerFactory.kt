// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.rename.naming

import com.intellij.codeInsight.TestFrameworks
import com.intellij.ide.projectView.impl.ProjectRootsUtil
import com.intellij.java.refactoring.JavaRefactoringBundle
import com.intellij.openapi.module.Module
import com.intellij.psi.PsiElement
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.PsiShortNamesCache
import com.intellij.util.containers.ContainerUtil
import java.util.regex.Pattern

abstract class AutomaticTestMethodRenamerFactory : AutomaticRenamerFactory {
  override fun isApplicable(element: PsiElement): Boolean {
    val file = element.containingFile
    return !ProjectRootsUtil.isInTestSource(file)
  }

  override fun getOptionName(): String = JavaRefactoringBundle.message("rename.test.method")

  protected class AutomaticTestMethodRenamer(oldMethodName: String?,
                                             className: String?,
                                             module: Module?,
                                             newMethodName: String) : AutomaticRenamer() {
    init {
      findMethodsToReplace(oldMethodName, className, module)
      suggestAllNames(oldMethodName, newMethodName)
    }

    private fun findMethodsToReplace(oldMethodName: String?, className: String?, module: Module?) {
      if (oldMethodName == null || className == null || module == null) return
      val moduleScope = GlobalSearchScope.moduleWithDependentsScope(module)

      val cache = PsiShortNamesCache.getInstance(module.getProject())

      val classPattern = Pattern.compile(".*$className.*")
      val methodPattern = Pattern.compile(".*$oldMethodName.*", Pattern.CASE_INSENSITIVE)

      var count = 0
      for (eachName in ContainerUtil.newHashSet(*cache.getAllClassNames())) {
        if (classPattern.matcher(eachName).matches()) {
          if (count ++ > 1000) break
          for (eachClass in cache.getClassesByName(eachName, moduleScope)) {
            if (TestFrameworks.detectFramework(eachClass) != null) {
              eachClass.methods.forEach {
                if (methodPattern.matcher(it.name).matches()) {
                  myElements.add(it)
                }
              }
            }
          }
        }
      }
    }

    override fun getDialogTitle(): String = JavaRefactoringBundle.message("rename.test.method.title")

    override fun getDialogDescription(): String = JavaRefactoringBundle.message("rename.test.method.description")

    override fun entityName(): String = JavaRefactoringBundle.message("rename.test.method.entity.name")
  }
}