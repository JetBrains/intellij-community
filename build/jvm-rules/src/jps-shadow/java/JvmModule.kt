// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.dependency.java

import org.jetbrains.bazel.jvm.util.emptyList
import org.jetbrains.jps.dependency.GraphDataInput
import org.jetbrains.jps.dependency.GraphDataOutput
import org.jetbrains.jps.dependency.Usage
import org.jetbrains.jps.dependency.readList
import org.jetbrains.jps.dependency.writeCollection

class JvmModule : JVMClassNode<JvmModule, JvmModule.Diff> {
  val version: String

  private val requires: Collection<ModuleRequires>
  private val exports: Collection<ModulePackage>

  constructor(
    flags: JVMFlags,
    name: String,
    outFilePathHash: Long,
    version: String?,
    requires: Collection<ModuleRequires>,
    exports: Collection<ModulePackage>,
    usages: Collection<Usage>,
    metadata: Collection<JvmMetadata<*, *>>
  ) : super(
    flags = flags,
    signature = "",
    name = name,
    outFilePathHash = outFilePathHash,
    annotations = emptyList<ElementAnnotation>(),
    usages = usages,
    metadata = metadata,
  ) {
    this.version = version ?: ""
    this.requires = requires
    this.exports = exports
  }

  @Suppress("unused")
  constructor(input: GraphDataInput) : super(input) {
    version = input.readUTF()
    requires = input.readList { ModuleRequires(input) }
    exports = input.readList { ModulePackage(input) }
  }

  @Suppress("unused")
  fun getRequires(): Iterable<ModuleRequires> = requires

  @Suppress("unused")
  fun getExports(): Iterable<ModulePackage> = exports

  override fun write(out: GraphDataOutput) {
    super.write(out)

    out.writeUTF(version)
    out.writeCollection(requires) { it.write(this) }
    out.writeCollection(exports) { it.write(this) }
  }

  @Suppress("unused")
  fun requiresTransitively(requirementName: String): Boolean {
    for (require in requires) {
      if (require.name == requirementName) {
        return require.flags.isTransitive
      }
    }
    return false
  }

  override fun difference(past: JvmModule): Diff = Diff(past)

  inner class Diff internal constructor(past: JvmModule) : JVMClassNode<JvmModule, Diff>.Diff(past) {
    private val requiresDiff by lazy(LazyThreadSafetyMode.NONE) { deepDiff(myPast.requires, requires) }
    private val exportsDiff by lazy(LazyThreadSafetyMode.NONE) { deepDiff(myPast.exports, exports) }

    override fun unchanged(): Boolean {
      return super.unchanged() && !versionChanged() && requires().unchanged() && exports().unchanged()
    }

    fun requires() = requiresDiff

    fun exports() = exportsDiff

    fun versionChanged(): Boolean = myPast.version != version
  }
}