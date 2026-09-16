package org.jetbrains.intellij.build.impl

import org.jetbrains.annotations.ApiStatus
import org.jetbrains.intellij.build.BuildContext

@ApiStatus.Internal
class DescriptorPluginVersion(@JvmField val suffix: String) : PluginVersionEvaluator {
  fun evaluateSource(pluginXml: String, ideBuildVersion: String): PluginVersionEvaluatorResult {
    if (pluginXml.indexOf("<version>") != -1) {
      val declaredVersion = pluginXml.substring(pluginXml.indexOf("<version>") + "<version>".length, pluginXml.indexOf("</version>"))
      return PluginVersionEvaluatorResult(pluginVersion = "$declaredVersion.$ideBuildVersion$suffix")
    }
    else {
      return PluginVersionEvaluatorResult(pluginVersion = "$ideBuildVersion$suffix")
    }
  }

  override fun evaluate(
    pluginXmlSupplier: () -> String,
    ideBuildVersion: String,
    context: BuildContext,
  ): PluginVersionEvaluatorResult = evaluateSource(pluginXmlSupplier(), ideBuildVersion)
}
