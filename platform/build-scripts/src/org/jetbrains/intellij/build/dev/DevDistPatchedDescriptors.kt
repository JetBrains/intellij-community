// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.dev

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.io.path.createDirectories

/**
 * Writes out what a dev-distribution assembly did to every plugin descriptor it put in a plugin's main jar.
 *
 * ### Why this is separate from the recipe
 *
 * [DevDistRecipe] records that a main jar took an in-memory source and stops there. It has to. The bytes of such a
 * source are computed, so the source has no label and no file to name. The recipe is a report of names.
 *
 * This report is those bytes, plus the text each stage of the patch produced. A stamp is a value a generator can
 * precompute. A lambda is not. This report separates the two per plugin, from the run rather than from the layout.
 *
 * ### Off unless asked for
 *
 * [start] arms the recording and nothing else does. Unarmed, [stagesOrNull] returns `null` on one volatile read, the
 * patcher runs the code it always ran, and no file is written.
 *
 * It is a process-wide object for [DevDistRecipe]'s reason. An assembly forks a build context per parallel task, so
 * context-held state would be recorded into whichever fork patched the descriptor.
 *
 * ### The stages are the vocabulary
 *
 * [DevDistDescriptorStage] is closed, and it names every step of `patchPluginXml` in the order the steps run. A report
 * of "the descriptor changed" would say nothing: what decides whether data can express a patch is *which* step changed
 * it. [DevDistDescriptorStage.RESERIALIZED] is there so that the XML round trip is not read as a patch.
 */
@ApiStatus.Internal
object DevDistPatchedDescriptors {
  @Volatile
  private var records: ConcurrentLinkedQueue<DevDistPluginDescriptor>? = null

  /** Starts recording the assembly about to run. */
  fun start() {
    records = ConcurrentLinkedQueue()
  }

  /**
   * Returns to the unarmed state.
   *
   * An assembly is one process and never needs this. A test does. The object is process-wide, so a test of the
   * unarmed path has to be able to reach that state after another test armed it.
   */
  @TestOnly
  internal fun stopForTest() {
    records = null
  }

  /**
   * A collector for one plugin's descriptor texts, or `null` when nothing is recording.
   *
   * The caller keeps the returned object across the whole patch and adds to it after each stage, then hands it to
   * [record]. A `null` means every one of those calls is a no-op on a nullable receiver.
   */
  internal fun stagesOrNull(): DevDistDescriptorStages? = if (records == null) null else DevDistDescriptorStages()

  /**
   * Records one plugin's descriptor.
   *
   * @param mainModule the plugin's main module, which is what the descriptor belongs to.
   * @param directoryName the plugin's directory under `plugins/`, so a reader can find the main jar in a distribution.
   * @param mainJar the main jar's name as the layout declares it, which is `PluginLayout.getMainJarName()`. It is not
   *   always where the descriptor landed. A plugin that declares its own main module as a content module gets a jar
   *   named by the content-module convention. Join through the recipe, not through this field.
   * @param embedsContentModules whether the content-module embedding stage was allowed to run at all. A layout that
   *   scrambles paths skips it. Such a descriptor keeps the `<module/>` elements the others have inlined.
   * @param stages the texts collected by [stagesOrNull].
   */
  internal fun record(
    mainModule: String,
    directoryName: String,
    mainJar: String,
    embedsContentModules: Boolean,
    stages: DevDistDescriptorStages,
  ) {
    val records = records ?: return
    records.add(
      stages.toRecord(
        mainModule = mainModule,
        directoryName = directoryName,
        mainJar = mainJar,
        embedsContentModules = embedsContentModules,
      )
    )
  }

  /**
   * Writes what was recorded, or nothing at all when [start] was never called.
   *
   * A plugin can be built more than once in one assembly. An OS-specific layout is that case, and it records its
   * descriptor once per build. Two identical records are one fact, so they collapse and the count is reported.
   *
   * Two *different* records for one module are two facts, and both are kept. The build states that such a descriptor is
   * the same every time. This report is where a run that broke that statement is visible.
   *
   * @return what was written, or `null` when nothing was recording and no file was written.
   */
  fun write(file: Path, fragment: String): DevDistDescriptorReport? {
    val records = records ?: return null
    val recorded = records.toList()
    // `distinct` and not an adjacency test, because outputs are recorded in parallel and two equal records need not be
    // neighbours. The third selector is the tiebreaker: two records `distinct` keeps can share a module and a final
    // text, and their queue order is not reproducible.
    val plugins = recorded.distinct().sortedWith(compareBy({ it.mainModule }, { it.patched }, { it.source }))
    val report = DevDistDescriptorReport(
      fragment = fragment,
      collapsedDuplicates = recorded.size - plugins.size,
      plugins = plugins,
    )
    file.parent?.createDirectories()
    Files.writeString(file, descriptorReportJson.encodeToString(DevDistDescriptorReport.serializer(), report))
    return report
  }
}

/** One step of the main-jar descriptor patch, in the order the steps run. */
@Serializable
@ApiStatus.Internal
enum class DevDistDescriptorStage {
  /** The descriptor as the plugin's main module output holds it, before anything ran. */
  @SerialName("source")
  SOURCE,

  /** After `PluginLayout.rawPluginXmlPatcher`: a per-layout lambda over the text, so code and not data. */
  @SerialName("rawTextPatcher")
  RAW_TEXT_PATCHER,

  /**
   * The text the XML reader and writer produce, with only [RAW_TEXT_PATCHER] ahead of it.
   *
   * Not a step of the patch. It is here so that the next steps are read against it. The round trip rewrites whitespace,
   * attribute quoting and CDATA on every descriptor. A diff of the source against the final text carries all of that,
   * whether or not anything was patched.
   */
  @SerialName("reserialized")
  RESERIALIZED,

  /** After `doPatchPluginXml`: `since-build`, `until-build`, `<version>`, `<product-descriptor>` and the CDATA repair. */
  @SerialName("stamps")
  STAMPS,

  /** After `resolveIncludes`: every `xi:include` replaced by what it names. */
  @SerialName("includes")
  INCLUDES,

  /**
   * After `filterAndProcessContentModules`.
   *
   * A kept `<module/>` receives the module's own descriptor as CDATA. A filtered-out one is dropped. So a change at
   * this stage does not prove an embedding on its own.
   */
  @SerialName("contentModules")
  CONTENT_MODULES,

  /** After `PluginLayout.pluginXmlPatcher`: a per-layout lambda over the serialized text, so code and not data. */
  @SerialName("textPatcher")
  TEXT_PATCHER,
}

@Serializable
@ApiStatus.Internal
data class DevDistDescriptorStep(
  @JvmField val stage: DevDistDescriptorStage,
  @JvmField val bytes: Int,
  /** Whether this step changed the text the step before it produced. Absent for [DevDistDescriptorStage.SOURCE]. */
  @JvmField val changed: Boolean? = null,
)

@Serializable
@ApiStatus.Internal
data class DevDistPluginDescriptor(
  @JvmField val mainModule: String,
  @JvmField val directoryName: String,
  /**
   * What the layout declares. For 7 of this product's plugins it is not where the descriptor landed. See
   * [DevDistPatchedDescriptors.record].
   */
  @JvmField val mainJar: String,
  @JvmField val embedsContentModules: Boolean,
  @JvmField val steps: List<DevDistDescriptorStep>,
  /** The text of [DevDistDescriptorStage.SOURCE], so that the steps can be checked by an independent reader. */
  @JvmField val source: String,
  /** The text the main jar received, which is the text of the last step. */
  @JvmField val patched: String,
)

@Serializable
@ApiStatus.Internal
data class DevDistDescriptorReport(
  @JvmField val fragment: String,
  /** How many records were dropped as an exact repeat of another. See [DevDistPatchedDescriptors.write]. */
  @JvmField val collapsedDuplicates: Int,
  @JvmField val plugins: List<DevDistPluginDescriptor>,
)

/**
 * The descriptor text after each stage of one plugin's patch.
 *
 * It holds the texts rather than a comparison of them, because a stage's own text is what the next stage is measured
 * against. The first text and the last are also the report's two full texts.
 */
internal class DevDistDescriptorStages {
  private val texts = ArrayList<Pair<DevDistDescriptorStage, String>>(DevDistDescriptorStage.entries.size)

  fun add(stage: DevDistDescriptorStage, text: String) {
    texts.add(stage to text)
  }

  fun toRecord(
    mainModule: String,
    directoryName: String,
    mainJar: String,
    embedsContentModules: Boolean,
  ): DevDistPluginDescriptor {
    require(texts.firstOrNull()?.first == DevDistDescriptorStage.SOURCE) {
      "The descriptor of '$mainModule' was not recorded from its source: ${texts.map { it.first }}"
    }
    return DevDistPluginDescriptor(
      mainModule = mainModule,
      directoryName = directoryName,
      mainJar = mainJar,
      embedsContentModules = embedsContentModules,
      steps = texts.mapIndexed { index, (stage, text) ->
        DevDistDescriptorStep(
          stage = stage,
          // the descriptor goes into the jar as UTF-8, so a character count would not be the size of anything
          bytes = text.toByteArray(StandardCharsets.UTF_8).size,
          changed = if (index == 0) null else text != texts.get(index - 1).second,
        )
      },
      source = texts.first().second,
      patched = texts.last().second,
    )
  }
}

private val descriptorReportJson = Json {
  prettyPrint = true
  prettyPrintIndent = "  "
}
