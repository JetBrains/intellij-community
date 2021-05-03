// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.util.SafeJdomFactory
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet
import org.jdom.Attribute
import org.jdom.Element
import org.jdom.Namespace
import org.jdom.Text
import org.jetbrains.annotations.ApiStatus
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.function.Supplier

private val unitTestWithBundledPlugins = java.lang.Boolean.getBoolean("idea.run.tests.with.bundled.plugins")

private val LOG: Logger
  get() = PluginManagerCore.getLogger()

@ApiStatus.Internal
class DescriptorListLoadingContext constructor(
  @JvmField val disabledPlugins: Set<PluginId>,
  @JvmField @Suppress("EXPOSED_PROPERTY_TYPE_IN_CONSTRUCTOR") val result: PluginLoadingResult = PluginManagerCore.createLoadingResult(null),
  override val isMissingIncludeIgnored: Boolean = false,
  @JvmField val isMissingSubDescriptorIgnored: Boolean = false,
  checkOptionalConfigFileUniqueness: Boolean = false,
) : AutoCloseable, ReadModuleContext {
  private val toDispose = ConcurrentLinkedQueue<Array<PluginXmlFactory?>>()
  // synchronization will ruin parallel loading, so, string pool is local for thread
  private val threadLocalXmlFactory = ThreadLocal.withInitial(Supplier {
    val factory = PluginXmlFactory()
    val ref = arrayOf<PluginXmlFactory?>(factory)
    toDispose.add(ref)
    ref
  })

  @Volatile var defaultVersion: String? = null
    get() {
      var result = field
      if (result == null) {
        result = this.result.productBuildNumber.get().asStringWithoutProductCode()
        field = result
      }
      return result
    }
    private set

  @JvmField var usePluginClassLoader = !PluginManagerCore.isUnitTestMode || unitTestWithBundledPlugins

  private val optionalConfigNames: MutableMap<String, PluginId>? = if (checkOptionalConfigFileUniqueness) ConcurrentHashMap() else null

  fun isPluginDisabled(id: PluginId): Boolean {
    return PluginManagerCore.CORE_ID != id && disabledPlugins.contains(id)
  }

  override val jdomFactory: SafeJdomFactory
    get() = threadLocalXmlFactory.get()[0]!!

  override fun close() {
    for (ref in toDispose) {
      ref[0] = null
    }
  }

  fun internString(string: String): String {
    return threadLocalXmlFactory.get()[0]!!.intern(string)
  }

  val visitedFiles: MutableList<String>
    get() = threadLocalXmlFactory.get()[0]!!.visitedFiles

  fun checkOptionalConfigShortName(configFile: String, descriptor: IdeaPluginDescriptor): Boolean {
    val pluginId = descriptor.pluginId ?: return false
    val configNames = optionalConfigNames
    if (configNames == null || configFile.startsWith("intellij.")) {
      return false
    }

    val oldPluginId = configNames.put(configFile, pluginId)
    if (oldPluginId == null || oldPluginId == pluginId) {
      return false
    }

    LOG.error("Optional config file with name $configFile already registered by $oldPluginId. " +
              "Please rename to ensure that lookup in the classloader by short name returns correct optional config. " +
              "Current plugin: $descriptor.")
    return true
  }
}

// doesn't make sense to intern class name since it is unique
// ouch, do we really cannot agree how to name implementation class attribute?
private val CLASS_NAMES = ReferenceOpenHashSet(arrayOf(
  "implementation", "implementationClass", "builderClass",
  "serviceImplementation", "class", "className", "beanClass",
  "serviceInterface", "interface", "interfaceClass", "instance", "implementation-class",
  "qualifiedName"))

@Suppress("ReplaceJavaStaticMethodWithKotlinAnalog")
private val EXTRA_STRINGS = Arrays.asList("id", PluginManagerCore.VENDOR_JETBRAINS)

// don't intern CDATA because in most cases it is used for some unique large text (e.g. plugin description)
internal class PluginXmlFactory : SafeJdomFactory.BaseSafeJdomFactory() {
  @Suppress("SSBasedInspection")
  private val strings = ObjectOpenHashSet<String>(CLASS_NAMES.size + EXTRA_STRINGS.size)

  @JvmField val visitedFiles = ArrayList<String>(3)

  init {
    strings.addAll(CLASS_NAMES)
    strings.addAll(EXTRA_STRINGS)
  }

  // doesn't make any sense to intern long texts (JdomInternFactory doesn't intern CDATA, but plugin description can be simply Text)
  fun intern(string: String): String = if (string.length < 64) strings.addOrGet(string) else string

  override fun element(name: String, namespace: Namespace?) = super.element(intern(name), namespace)

  override fun attribute(name: String, value: String, namespace: Namespace?): Attribute {
    val internedName = intern(name)
    return if (CLASS_NAMES.contains(internedName)) {
      super.attribute(internedName, value, namespace)
    }
    else {
      super.attribute(internedName, intern(value), namespace)
    }
  }

  override fun text(text: String, parentElement: Element): Text {
    return Text(if (CLASS_NAMES.contains(parentElement.name)) text else intern(text))
  }
}