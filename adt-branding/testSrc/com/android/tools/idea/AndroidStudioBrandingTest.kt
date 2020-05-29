/*
 * Copyright (C) 2019 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tools.idea

import com.android.testutils.TestUtils
import com.google.common.truth.Truth.assertThat
import org.apache.commons.imaging.ImageFormats
import org.apache.commons.imaging.Imaging
import org.jdom.input.SAXBuilder
import org.junit.Test
import java.io.File
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * Tests the implicit invariants around UX assets (splash screen, icons) defined in {@code AndroidStudioApplicationInfo.xml}
 *
 * The Windows native launcher embeds these ahead of time by using dummy placeholders, and the actual assets are injected at build time
 * by simply overwriting the specific byte range in the .exe file. Size mismatch between the expected and actual byte streams results in
 * unusable Windows builds (“This app can't run on your PC.”).
 */
class AndroidStudioBrandingTest {

  @Test
  fun splashScreen() {
    fun toBMP(f: InputStream) = Imaging.writeImageToBytes(Imaging.getBufferedImage(f), ImageFormats.BMP, HashMap())

    val splash = toBMP(File(TestUtils.getWorkspaceRoot(), "tools/idea/native/WinLauncher/WinLauncher/splash.bmp").inputStream())
    // Note: the splash image doesn't actually have to be in .bmp format, as long as it can be decoded by the commons-imaging library.
    // But the resulting bytes from converting it to bitmap need to match the canonical bitmap size exactly.
    val appInfoRoot = SAXBuilder()
      .build(this.javaClass.classLoader.getResourceAsStream("idea/AndroidStudioApplicationInfo.xml"))
      .rootElement
    val splashUrl = appInfoRoot.getChild("logo", appInfoRoot.namespace)?.getAttributeValue("url")?.removePrefix("/")
    val studioSplash = toBMP(this.javaClass.classLoader.getResourceAsStream(splashUrl)!!)

    assertThat(studioSplash.size).isEqualTo(splash.size)
  }

  @Test
  fun icons() {
    val canonicalIcon = File(TestUtils.getWorkspaceRoot(), "tools/idea/native/WinLauncher/WinLauncher/WinLauncher.ico")

    // Note: the paths below must match Studio's WindowsDistributionCustomizer from AndroidStudioProperties.groovy.
    assertIconsMatch(canonicalIcon, this.javaClass.classLoader.getResourceAsStream("artwork/androidstudio.ico")!!)
    assertIconsMatch(canonicalIcon, this.javaClass.classLoader.getResourceAsStream("artwork/preview/androidstudio.ico")!!)
  }

  private fun assertIconsMatch(canonicalIconFile: File, studioIconStream: InputStream) {
    val canonicalIcons = Imaging.getAllBufferedImages(canonicalIconFile)

    // Same workaround as in LauncherGenerator.injectIcon, because we can't tell it's an .ico just from the byte stream.
    val tmpFile = Files.createTempFile("studio", ".ico")
    Files.copy(studioIconStream, tmpFile, StandardCopyOption.REPLACE_EXISTING)
    val studioIcons = Imaging.getAllBufferedImages(tmpFile.toFile())

    assertThat(studioIcons.size).isEqualTo(canonicalIcons.size)
    for (i in 0 until canonicalIcons.size) {
      val canonicalIcon = canonicalIcons[i]
      val studioIcon = studioIcons[i]

      assertThat(studioIcon.width).isEqualTo(canonicalIcon.width)
      assertThat(studioIcon.height).isEqualTo(canonicalIcon.height)
      assertThat(studioIcon.colorModel).isEqualTo(canonicalIcon.colorModel)
    }
  }
}
