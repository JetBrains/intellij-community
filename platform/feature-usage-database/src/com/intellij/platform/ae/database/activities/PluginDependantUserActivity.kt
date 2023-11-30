package com.intellij.platform.ae.database.activities

/**
 * A user activity that depends on some non-core functionality that is
 * not available in every IDE
 */
interface PluginDependantUserActivity {
  /**
   * The ID of the plugin, upon which this UserActivity depends.
   */
  val pluginId: String
}