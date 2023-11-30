// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet")

package com.intellij.ide.plugins

import com.intellij.core.CoreBundle
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.util.BuildNumber
import com.intellij.util.xml.dom.XmlInterner
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CopyOnWriteArrayList
import java.util.function.Supplier

@ApiStatus.Internal
class DescriptorListLoadingContext(
  private val customDisabledPlugins: Set<PluginId>? = null,
  private val customExpiredPlugins: Set<PluginId>? = null,
  private val customBrokenPluginVersions: Map<PluginId, Set<String?>>? = null,
  @JvmField val productBuildNumber: () -> BuildNumber = { PluginManagerCore.buildNumber },
  override val isMissingIncludeIgnored: Boolean = false,
  @JvmField val isMissingSubDescriptorIgnored: Boolean = false,
  checkOptionalConfigFileUniqueness: Boolean = false,
  @JvmField val transient: Boolean = false
) : AutoCloseable, ReadModuleContext {
  val disabledPlugins by lazy { customDisabledPlugins ?: DisabledPluginsState.getDisabledIds() }
  val expiredPlugins by lazy { customExpiredPlugins ?: ExpiredPluginsState.expiredPluginIds }
  private val brokenPluginVersions by lazy { customBrokenPluginVersions ?: getBrokenPluginVersions() }
  
  @JvmField
  internal val globalErrors: CopyOnWriteArrayList<Supplier<String>> = CopyOnWriteArrayList<Supplier<String>>()

  internal fun copyGlobalErrors(): MutableList<Supplier<String>> = ArrayList(globalErrors)

  private val toDispose = ConcurrentLinkedQueue<Array<MyXmlInterner?>>()
  // synchronization will ruin parallel loading, so, string pool is local for thread
  private val threadLocalXmlFactory = ThreadLocal.withInitial(Supplier {
    val factory = MyXmlInterner()
    val ref = arrayOf<MyXmlInterner?>(factory)
    toDispose.add(ref)
    ref
  })

  @Volatile var defaultVersion: String? = null
    get() {
      var result = field
      if (result == null) {
        result = productBuildNumber().asStringWithoutProductCode()
        field = result
      }
      return result
    }
    private set

  private val optionalConfigNames: MutableMap<String, PluginId>? = if (checkOptionalConfigFileUniqueness) ConcurrentHashMap() else null

  internal fun reportCannotLoad(file: Path, e: Throwable?) {
    PluginManagerCore.logger.warn("Cannot load $file", e)
    globalErrors.add(Supplier {
      CoreBundle.message("plugin.loading.error.text.file.contains.invalid.plugin.descriptor", pluginPathToUserString(file))
    })
  }

  fun isPluginDisabled(id: PluginId): Boolean {
    return PluginManagerCore.CORE_ID != id && disabledPlugins.contains(id)
  }

  fun isBroken(id: PluginId, descriptor: IdeaPluginDescriptorImpl): Boolean {
    val set = brokenPluginVersions.get(id) ?: return false
    return set.contains(descriptor.version)
  }

  fun isBroken(descriptor: IdeaPluginDescriptorImpl): Boolean {
    return (brokenPluginVersions.get(descriptor.pluginId) ?: return false).contains(descriptor.version)
  }

  override val interner: XmlInterner
    get() = threadLocalXmlFactory.get()[0]!!

  override fun close() {
    for (ref in toDispose) {
      ref[0] = null
    }
  }

  val visitedFiles: MutableList<String>
    get() = threadLocalXmlFactory.get()[0]!!.visitedFiles

  fun checkOptionalConfigShortName(configFile: String, descriptor: IdeaPluginDescriptor): Boolean {
    val configNames = optionalConfigNames
    if (configNames == null || configFile.startsWith("intellij.")) {
      return false
    }

    val pluginId = descriptor.pluginId
    val oldPluginId = configNames.put(configFile, pluginId)
    if (oldPluginId == null || oldPluginId == pluginId) {
      return false
    }

    PluginManagerCore.logger.error("Optional config file with name $configFile already registered by $oldPluginId. " +
              "Please rename to ensure that lookup in the classloader by short name returns correct optional config. " +
              "Current plugin: $descriptor.")
    return true
  }

  @JvmField
  internal val debugData: PluginDescriptorsDebugData? =
    if (System.getProperty("intellij.platform.plugins.record.debug.data.for.descriptors").toBoolean()) {
      PluginDescriptorsDebugData()
    }
    else {
      null
    }
}

// doesn't make sense to intern class name since it is unique
private val CLASS_NAMES = ReferenceOpenHashSet(arrayOf(
  "implementation", "implementationClass", "builderClass",
  "serviceImplementation", "class", "className", "beanClass",
  "serviceInterface", "interface", "interfaceClass", "instance", "implementation-class",
  "qualifiedName"))

@Suppress("ReplaceJavaStaticMethodWithKotlinAnalog")
private val EXTRA_STRINGS = Arrays.asList(
  "id", "order", "os", PluginManagerCore.VENDOR_JETBRAINS, PluginManagerCore.VENDOR_JETBRAINS_SRO,
  "com.intellij.applicationService",
  "com.intellij.projectService",
  "com.intellij.moduleService",
  "com.intellij.postStartupActivity",
  "com.intellij",
  "com.intellij.java",
  "com.intellij.modules.java",
  "Docker",
  "intellij.clouds.docker.file",
  "intellij.clouds.docker.remoteRun",
)

private class MyXmlInterner : XmlInterner {
  @Suppress("SSBasedInspection")
  private val strings = ObjectOpenHashSet<String>(256)

  @JvmField val visitedFiles = ArrayList<String>(3)

  init {
    strings.addAll(CLASS_NAMES)
    strings.addAll(EXTRA_STRINGS)
  }

  override fun name(value: String): String = strings.addOrGet(value)

  override fun value(name: String, value: String): String {
    // doesn't make sense to intern a long texts (JdomInternFactory doesn't intern CDATA, but plugin description can be simply Text)
    return if (value.length > 64 || CLASS_NAMES.contains(name)) value else strings.addOrGet(value)
  }
}
