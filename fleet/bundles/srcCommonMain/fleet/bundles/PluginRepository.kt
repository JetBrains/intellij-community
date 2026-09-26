package fleet.bundles

/**
 * Could be backed by Marketplace, or Maven repository
 * Subject for trust??
 **/
interface PluginRepository {
  companion object {
    val Empty = object : PluginRepository {
      override suspend fun getLatestVersions(names: Set<PluginName>, shipVersion: PluginVersion): Map<PluginName, PluginVersion> = emptyMap()
      override suspend fun getPlugin(pluginName: PluginName, pluginVersion: PluginVersion): PluginDescriptor? = null
      override fun presentableName(): String = "Empty PluginRepository"
      override fun cacheKey(): String = "empty" // we should not attempt to re-resolve against the Empty repository if we already have a resolution result against it, it will lead to the same result
    }
  }

  /**
   * Should check signature of [fleet.bundles.PluginDescriptor] before returning it
   **/
  suspend fun getLatestVersions(names: Set<PluginName>, shipVersion: PluginVersion): Map<PluginName, PluginVersion>
  suspend fun getPlugin(pluginName: PluginName, pluginVersion: PluginVersion): PluginDescriptor?

  fun presentableName() : String = toString()

  /**
   * Cache invalidation looks at this identifier to know whether a resolution against that repository is stale or not.
   */
  fun cacheKey() : String
}

fun PluginSet.asPluginRepository(): PluginRepository {
  return object : PluginRepository {
    override suspend fun getLatestVersions(names: Set<PluginName>, shipVersion: PluginVersion): Map<PluginName, PluginVersion> =
      plugins.groupBy(PluginDescriptor::name).mapNotNull { (name, bs) ->
        bs.filter {
          it.compatibleShipVersionRange?.let { range ->
            shipVersion in range.from..range.to
          } ?: false
        }
          .maxByOrNull { it.version }
          ?.let { it.name to it.version }
      }.toMap()

    override suspend fun getPlugin(pluginName: PluginName, pluginVersion: PluginVersion): PluginDescriptor? =
      plugins.find { spec -> spec.name == pluginName && spec.version == pluginVersion }

    override fun presentableName(): String = "Plugin Repository from plugins set with ${this@asPluginRepository.plugins.size} plugins"
    override fun cacheKey(): String = buildString {
      shipVersions.sorted().forEach { shipVersion ->
        append(shipVersion)
      }
      plugins.sortedBy { descriptor -> descriptor.name.name }.forEach { descriptor ->
        append(descriptor.name.name)
        append(descriptor.version.versionString)
        descriptor.deps.entries.sortedBy { (name, _) -> name.name }.forEach { (name, version) ->
          append(name.name)
          append(version.version.versionString)
        }
      }
    }
  }
}

fun PluginRepository.compose(other: PluginRepository): PluginRepository {
  val self = this
  return object : PluginRepository {
    override suspend fun getLatestVersions(names: Set<PluginName>, shipVersion: PluginVersion): Map<PluginName, PluginVersion> =
      self.getLatestVersions(names, shipVersion).merge(other.getLatestVersions(names, shipVersion)) { _, v1, v2 ->
        max(v1, v2)
      }

    override suspend fun getPlugin(pluginName: PluginName, pluginVersion: PluginVersion): PluginDescriptor? {
      //TODO[jetzajac]: ambigs?
      return other.getPlugin(pluginName, pluginVersion) ?: self.getPlugin(pluginName, pluginVersion)
    }

    override fun presentableName(): String {
      return "Composed PluginRepository: ${self.presentableName()} -> ${other.presentableName()}"
    }

    override fun cacheKey(): String = "${self.cacheKey()}-${other.cacheKey()}"
  }
}

private fun<K, V> Map<K, V>.merge(other: Map<K, V>, f: (K, V, V) -> V = {_, _, v -> v}): Map<K, V> {
  val x = toMutableMap()
  other.forEach { (k, v) ->
    when {
      x.containsKey(k) -> {
        x[k] = f(k, x[k]!! as V, v)
      }
      else -> x[k] = v
    }
  }
  return x
}

private fun <T: Comparable<T>> max(c1: T, c2: T): T {
  val x = c1.compareTo(c2)
  return when  {
    x == 0 -> c1
    x < 0 -> c2
    else -> c1
  }
}
