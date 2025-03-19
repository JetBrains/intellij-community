// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.script

import com.intellij.ide.extensionResources.ExtensionsRootType
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.ExtensionNotApplicableException
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.util.Pair
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.openapi.util.text.Formats
import com.intellij.openapi.util.text.StringUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicBoolean
import java.util.stream.Collectors

private val LOG = logger<IdeStartupScripts>()
private const val SCRIPT_DIR = "startup"

internal class IdeStartupScripts : ProjectActivity {
  private val isActive = AtomicBoolean(true)

  init {
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      throw ExtensionNotApplicableException.create()
    }
  }

  override suspend fun execute(project: Project) {
    if (!isActive.compareAndSet(true, false)) {
      return
    }

    val scripts = getScripts()
    LOG.info("${scripts.size} startup script(s) found")
    if (!scripts.isEmpty()) {
      runAllScriptsImpl(project = project, result = prepareScriptsAndEngines(scripts), log = LOG)
    }
  }
}

internal fun runAllScriptsImpl(project: Project?, result: List<Pair<Path, IdeScriptEngine>>, log: Logger) {
  for (pair in result) {
    try {
      log.info(pair.first.toString())
      val scriptText = Files.readString(pair.first)
      IdeConsoleScriptBindings.ensureIdeIsBound(project, pair.second)
      val start = System.currentTimeMillis()
      try {
        pair.second.eval(scriptText)
      }
      catch (e: ProcessCanceledException) {
        log.warn("... cancelled")
      }
      finally {
        log.info("... completed in " + Formats.formatDuration(System.currentTimeMillis() - start))
      }
    }
    catch (e: Exception) {
      log.warn(e)
    }
  }
}

internal fun prepareScriptsAndEngines(scripts: List<Path>): List<Pair<Path, IdeScriptEngine>> {
  val scriptEngineManager = IdeScriptEngineManager.getInstance()
  val result = ArrayList<Pair<Path, IdeScriptEngine>>()
  for (script in scripts) {
    val extension = FileUtilRt.getExtension(script.fileName.toString())
    val engine = if (extension.isEmpty()) null else scriptEngineManager.getEngineByFileExtension(extension, null)
    if (engine == null) {
      LOG.warn("$script not supported (no script engine)")
      continue
    }
    result.add(Pair(script, engine))
  }
  return result
}

private suspend fun getScripts(): List<Path> {
  try {
    val directory = ExtensionsRootType.getInstance().findResourceDirectory(PluginManagerCore.CORE_ID, SCRIPT_DIR, false)
    return withContext(Dispatchers.IO) {
      Files.list(directory).use { stream ->
        stream
          .filter(ExtensionsRootType.regularFileFilter())
          .sorted(Comparator { f1, f2 ->
            val f1Name = f1?.fileName?.toString()
            val f2Name = f2?.fileName?.toString()
            StringUtil.compare(f1Name, f2Name, false)
          })
          .collect(Collectors.toList())
      }
    }
  }
  catch (ignore: NoSuchFileException) {
  }
  catch (e: IOException) {
    LOG.error(e)
  }
  return emptyList()
}
