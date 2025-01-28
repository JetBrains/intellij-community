package com.intellij.database.datagrid

import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.util.Key
import java.util.*

class DataGridStartupActivity : ProjectActivity {
  override suspend fun execute(project: Project) {
    DataEditorConfigurator.configure(project)
  }

  object DataEditorConfigurator {
    private val LAUNCH_ID = Key.create<String>("DATABASE_PROJECT_LAUNCH_ID")
    private val SAFE_LOADING_KEY = Key.create<Boolean>("DATA_GRID_SAFE_LOADING_KEY")
    private val LOADING_DELAYED_KEY = Key.create<Boolean>("DATA_GRID_LOADING_DELAYED_KEY")

    fun configure(project: Project) {
      SAFE_LOADING_KEY[project] = true
      initLaunchId(project)
    }

    @JvmStatic
    fun isSafeToLoadData(project: Project): Boolean {
      return java.lang.Boolean.TRUE == SAFE_LOADING_KEY[project]
    }

    @JvmStatic
    fun isLoadingDelayed(grid: DataGrid): Boolean {
      return java.lang.Boolean.TRUE == LOADING_DELAYED_KEY[grid]
    }

    @JvmStatic
    fun delayLoading(grid: DataGrid) {
      LOADING_DELAYED_KEY[grid] = true
      grid.loadingDelayed()
    }

    @JvmStatic
    fun disableLoadingDelay(grid: DataGrid) {
      LOADING_DELAYED_KEY[grid] = null
      grid.loadingDelayDisabled()
    }

    private fun initLaunchId(project: Project): String {
      val restartId = UUID.randomUUID().toString()
      LAUNCH_ID[project] = restartId
      return restartId
    }

    @JvmStatic
    fun getLaunchId(project: Project): String {
      val launchId = LAUNCH_ID[project]
      return launchId ?: initLaunchId(project)
    }
  }
}