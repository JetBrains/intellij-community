/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package org.jetbrains.intellij.build.images

import com.intellij.openapi.util.text.StringUtil
import java.awt.Dimension
import java.io.File
import javax.imageio.ImageIO

internal val File.children: List<File> get() = if (isDirectory) listFiles().toList() else emptyList()

internal fun isImage(file: File, iconsOnly: Boolean): Boolean {
  if (!isImage(file.name)) return false
  return !iconsOnly || isIcon(file)
}

internal fun isIcon(file: File): Boolean {
  if (!isImage(file.name)) return false
  val size = imageSize(file) ?: return false
  return size.height == size.width || size.height <= 100 && size.width <= 100
}

private fun isImage(name: String) = name.endsWith(".png") || name.endsWith(".gif")

internal fun imageSize(file: File): Dimension? {
  val image = ImageIO.read(file) ?: return null
  val width = image.getWidth(null)
  val height = image.getHeight(null)
  return Dimension(width, height)
}

internal enum class ImageType(private val suffix: String) {
  BASIC(""), RETINA("@2x"), DARCULA("_dark"), RETINA_DARCULA("@2x_dark");

  fun getBasicName(name: String): String = StringUtil.trimEnd(name, suffix)

  companion object {
    fun fromName(name: String): ImageType {
      if (name.endsWith(RETINA_DARCULA.suffix)) return RETINA_DARCULA
      if (name.endsWith(RETINA.suffix)) return RETINA
      if (name.endsWith(DARCULA.suffix)) return DARCULA
      return BASIC
    }

    fun stripSuffix(name: String): String {
      val type = fromName(name)
      return name.removeSuffix(type.suffix)
    }
  }
}