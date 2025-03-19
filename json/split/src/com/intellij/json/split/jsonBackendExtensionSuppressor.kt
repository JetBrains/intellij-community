package com.intellij.json.split

import com.intellij.idea.AppMode

/**
 * Checks whether the application is running in backend mode.
 * The function is used to suppress extensions available both on the backend and frontend sides in remote development.
 *
 * @return true if the application is in backend mode, false otherwise.
 */
internal fun shouldDoNothingInBackendMode(): Boolean {
  return AppMode.isRemoteDevHost()
}