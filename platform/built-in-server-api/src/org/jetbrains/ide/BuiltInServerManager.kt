/*
 * Copyright 2000-2013 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.ide

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.util.Url

import java.net.URLConnection

abstract class BuiltInServerManager {
  companion object {
    @JvmStatic
    fun getInstance(): BuiltInServerManager = ApplicationManager.getApplication().getComponent(BuiltInServerManager::class.java)
  }

  abstract val port: Int

  abstract val serverDisposable: Disposable?

  abstract fun waitForStart(): BuiltInServerManager

  abstract fun isOnBuiltInWebServer(url: Url?): Boolean

  abstract fun configureRequestToWebServer(connection: URLConnection)

  abstract fun addAuthToken(url: Url): Url
}