package org.jetbrains.jewel.bridge.clipboard

import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.Clipboard
import androidx.compose.ui.platform.NativeClipboard
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.ide.CopyPasteManager
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable
import java.awt.datatransfer.UnsupportedFlavorException

/**
 * A [Clipboard] implementation, similar to Compose's internal `AwtPlatformClipboard`, that delegates to the IJP's
 * [CopyPasteManager] instead of to the AWT [`Clipboard`][java.awt.datatransfer.Clipboard].
 */
internal class JewelBridgeClipboard : Clipboard {
    private val logger = thisLogger()

    @Suppress("TooGenericExceptionCaught") // Just guarding against IJP/AWT weirdness
    private val copyPasteManager by lazy {
        logger.info("Initializing CopyPasteManager...")
        if (ApplicationManager.getApplication() == null) {
            logger.error("CopyPasteManager is not available when the IJP is not initialized.")
            return@lazy null
        }

        try {
            CopyPasteManager.getInstance()
        } catch (e: RuntimeException) {
            logger.error("CopyPasteManager is not available.", e)
            null
        }
    }

    override val nativeClipboard: NativeClipboard
        get() = copyPasteManager ?: error("CopyPasteManager is not available")

    override suspend fun getClipEntry(): ClipEntry? {
        logger.debug("getClipEntry called. CopyPasteManager available: ${copyPasteManager != null}")

        val transferable = copyPasteManager?.contents ?: return null
        val flavors = transferable.transferDataFlavors
        if (flavors?.size == 0) return null
        return ClipEntry(transferable)
    }

    override suspend fun setClipEntry(clipEntry: ClipEntry?) {
        logger.debug("setClipEntry called: $clipEntry. CopyPasteManager available: ${copyPasteManager != null}")
        val transferable = clipEntry?.nativeClipEntry as? Transferable
        copyPasteManager?.setContents(transferable ?: EmptyTransferable)
    }

    private object EmptyTransferable : Transferable {
        override fun getTransferDataFlavors(): Array<DataFlavor> = emptyArray()

        override fun isDataFlavorSupported(flavor: DataFlavor?): Boolean = false

        override fun getTransferData(flavor: DataFlavor?): Any {
            throw UnsupportedFlavorException(flavor)
        }
    }
}
