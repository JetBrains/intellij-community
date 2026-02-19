package fleet.buildtool.codecache

import java.lang.module.ModuleDescriptor
import java.lang.module.ModuleFinder
import java.nio.file.Path

data class HashedJar(
  val hash: String,
  val file: Path,
  val moduleDescriptor: String?,
) {
  companion object {
    fun fromFile(hash: String, file: Path, jdkVersionFeature: Int): HashedJar = HashedJar(
      hash = hash,
      file = file,
      moduleDescriptor = findModuleDescriptorOrNull(file)?.serialize(jdkVersionFeature),
    )
  }
}

fun findModuleDescriptorOrNull(jarFile: Path): ModuleDescriptor? {
  val allModules = ModuleFinder.of(jarFile).findAll()
  return when {
    allModules.isEmpty() -> null
    allModules.size > 1 -> error("One jar file should have only one module: $jarFile")
    else -> allModules.single().descriptor()
  }
}

fun findModuleDescriptor(jarFile: Path): ModuleDescriptor = findModuleDescriptorOrNull(jarFile)
                                                            ?: error("a module descriptor must be able to be derived from '$jarFile'")
