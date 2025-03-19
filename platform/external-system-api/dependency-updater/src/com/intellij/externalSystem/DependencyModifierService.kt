package com.intellij.externalSystem

import com.intellij.buildsystem.model.DeclaredDependency
import com.intellij.buildsystem.model.unified.UnifiedDependency
import com.intellij.buildsystem.model.unified.UnifiedDependencyRepository
import com.intellij.openapi.components.Service
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.ApiStatus


/**
 * @see ExternalDependencyModificator
 */
@Service(Service.Level.PROJECT)
@ApiStatus.Experimental
class DependencyModifierService(private val myProject: Project) {

  fun addRepository(module: Module, repository: UnifiedDependencyRepository) = module.modify {
    it.addRepository(module, repository)
  }

  fun deleteRepository(module: Module, repository: UnifiedDependencyRepository) = module.modify {
    it.deleteRepository(module, repository)
  }

  fun declaredDependencies(module: Module): List<DeclaredDependency> = read(module) {
    it.declaredDependencies(module)
  }

  fun declaredRepositories(module: Module): List<UnifiedDependencyRepository> = read(module) {
    it.declaredRepositories(module)
  }

  fun supports(module: Module): Boolean =
    ExternalDependencyModificator.EP_NAME.getExtensionList(myProject)
      .any { it.supports(module) }

  fun addDependency(module: Module, descriptor: UnifiedDependency) = module.modify {
    it.addDependency(module, descriptor)
  }

  fun updateDependency(module: Module,
                       oldDescriptor: UnifiedDependency,
                       newDescriptor: UnifiedDependency) = module.modify {
    it.updateDependency(module, oldDescriptor, newDescriptor)
  }

  fun removeDependency(module: Module, descriptor: UnifiedDependency) = module.modify {
    it.removeDependency(module, descriptor)
  }

  private fun Module.modify(modifier: (ExternalDependencyModificator) -> Unit) =
    ExternalDependencyModificator.EP_NAME.getExtensionList(myProject)
      .firstOrNull { it.supports(this) }
      ?.let(modifier)
    ?: error(DependencyUpdaterBundle.message("cannot.modify.module", name))

  private fun <T> read(module: Module, reader: (ExternalDependencyModificator) -> List<T>): List<T> =
    ExternalDependencyModificator.EP_NAME.getExtensionList(myProject)
      .filter { it.supports(module) }
      .flatMap { reader(it) }

  companion object {
    @JvmStatic
    fun getInstance(project: Project): DependencyModifierService = project.getService(DependencyModifierService::class.java)
  }
}
