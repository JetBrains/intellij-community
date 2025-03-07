package org.jetbrains.jps.dependency.java

import org.jetbrains.jps.dependency.NodeBuilder
import org.jetbrains.org.objectweb.asm.ModuleVisitor
import org.jetbrains.org.objectweb.asm.Opcodes
import org.jetbrains.org.objectweb.asm.Opcodes.API_VERSION

internal class ModuleCrawler(
  private val moduleRequires: MutableSet<ModuleRequires>,
  private val moduleExports: MutableSet<ModulePackage>,
  private val nodeBuilder: NodeBuilder,
) : ModuleVisitor(API_VERSION) {
  override fun visitMainClass(mainClass: String) {
    nodeBuilder.addUsage(ClassUsage(mainClass))
  }

  override fun visitRequire(module: String?, access: Int, version: String?) {
    if (isExplicit(access)) {
      // collect non-synthetic dependencies only
      moduleRequires.add(ModuleRequires(JVMFlags(access), module, version))
    }
  }

  override fun visitExport(packaze: String?, access: Int, vararg modules: String?) {
    if (isExplicit(access)) {
      // collect non-synthetic dependencies only
      @Suppress("UNNECESSARY_SAFE_CALL")
      moduleExports.add(ModulePackage(packaze, modules?.asList() ?: emptyList()))
    }
  }

  override fun visitUse(service: String) {
    nodeBuilder.addUsage(ClassUsage(service))
  }

  override fun visitProvide(service: String, vararg providers: String) {
    nodeBuilder.addUsage(ClassUsage(service))
    @Suppress("SENSELESS_COMPARISON")
    if (providers != null) {
      for (provider in providers) {
        nodeBuilder.addUsage(ClassUsage(provider))
      }
    }
  }

  fun isExplicit(access: Int): Boolean {
    return (access and (Opcodes.ACC_SYNTHETIC or Opcodes.ACC_MANDATED)) == 0
  }
}
