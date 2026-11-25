// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.images.ide

import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.awt.Image
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.DataFlavor.imageFlavor
import java.awt.datatransfer.Transferable
import java.awt.datatransfer.UnsupportedFlavorException
import java.awt.image.BaseMultiResolutionImage
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

class ImagePasteProviderTest : BasePlatformTestCase() {
  /** On macOS, AWT gives a Retina capture at its size in points. The PNG file of the pasteboard holds each pixel. */
  fun testPasteKeepsPngFileOfTransfer() {
    val png = pngFile(BufferedImage(8, 6, BufferedImage.TYPE_INT_ARGB))

    val pasted = paste(ImageTransferable(BufferedImage(4, 3, BufferedImage.TYPE_INT_ARGB), png))

    assertTrue(pasted.contentEquals(png))
  }

  fun testPasteKeepsLargestVariantOfMultiResolutionImage() {
    val image = BaseMultiResolutionImage(
      BufferedImage(4, 3, BufferedImage.TYPE_INT_ARGB),
      BufferedImage(8, 6, BufferedImage.TYPE_INT_ARGB),
    )

    val pasted = ImageIO.read(ByteArrayInputStream(paste(ImageTransferable(image, png = null))))

    assertEquals(8 to 6, pasted.width to pasted.height)
  }

  private fun paste(transferable: Transferable): ByteArray {
    val dir = myFixture.tempDirFixture.findOrCreateDir("paste")
    CopyPasteManager.getInstance().setContents(transferable)
    ImagePasteProvider().performPaste(SimpleDataContext.getSimpleContext(CommonDataKeys.VIRTUAL_FILE, dir))
    return checkNotNull(dir.findChild("img.png")).contentsToByteArray()
  }
}

private val PNG_FLAVOR = DataFlavor("image/png;class=java.io.InputStream", "PNG")

private fun pngFile(image: BufferedImage): ByteArray {
  val output = ByteArrayOutputStream()
  check(ImageIO.write(image, "png", output))
  return output.toByteArray()
}

private class ImageTransferable(private val image: Image, private val png: ByteArray?) : Transferable {
  override fun getTransferDataFlavors(): Array<DataFlavor> = if (png == null) arrayOf(imageFlavor) else arrayOf(imageFlavor, PNG_FLAVOR)

  override fun isDataFlavorSupported(flavor: DataFlavor): Boolean = flavor in transferDataFlavors

  override fun getTransferData(flavor: DataFlavor): Any = when {
    flavor == imageFlavor -> image
    flavor == PNG_FLAVOR && png != null -> ByteArrayInputStream(png)
    else -> throw UnsupportedFlavorException(flavor)
  }
}
