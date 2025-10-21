package org.jetbrains.jewel.bridge.clipboard

import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.Clipboard
import androidx.compose.ui.platform.NativeClipboard
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.ide.CopyPasteManager
import java.awt.datatransfer.ClipboardOwner
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

    // This _needs_ to return an AWT Clipboard instance, or
    // androidx.compose.foundation.text.input.internal.selection.ClipboardPasteState will fail to update
    // itself. Hence, we build a facade Clipboard that delegates to the IntelliJ CopyPasteManager.
    override val nativeClipboard: NativeClipboard by lazy { JewelAwtClipboardBridge(copyPasteManager) }

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

    private class JewelAwtClipboardBridge(private val copyPasteManager: CopyPasteManager?) :
        java.awt.datatransfer.Clipboard("JewelAwtClipboardBridge") {
        private val manager
            get() = copyPasteManager ?: error("CopyPasteManager is not available")

        override fun setContents(transferable: Transferable?, clipboardOwner: ClipboardOwner?) {
            manager.setContents(transferable ?: EmptyTransferable)
        }

        override fun getContents(requestor: Any?): Transferable? = manager.contents

        override fun getAvailableDataFlavors(): Array<out DataFlavor?>? = manager.contents?.transferDataFlavors

        override fun getData(flavor: DataFlavor?): Any? = flavor?.let { manager.getContents(it) }
    }
}
