package org.jetbrains.jewel.detekt.rules

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.extensions.ExtensionPoint
import com.intellij.psi.impl.source.tree.TreeCopyHandler
import dev.detekt.api.Config
import dev.detekt.api.Rule
import dev.detekt.api.modifiedText
import java.net.URI
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtPsiFactory

/**
 * Base class for Jewel Detekt rules. Handles working-file isolation for autocorrect: changes are applied to a writable
 * copy of the PSI file, then written back to the original only if modifications were made.
 */
@Suppress("AbstractClassCanBeConcreteClass")
abstract class JewelBaseRule(config: Config, description: String, url: URI? = null) : Rule(config, description, url) {
    private lateinit var workingFile: KtFile

    override fun visit(root: KtFile) {
        val startingText = root.modifiedText ?: root.text
        workingFile =
            if (autoCorrect) {
                val extensionArea = ApplicationManager.getApplication().extensionArea
                val extensionName = TreeCopyHandler.EP_NAME.name
                if (!extensionArea.hasExtensionPoint(extensionName)) {
                    extensionArea.registerExtensionPoint(
                        extensionName,
                        TreeCopyHandler::class.java.name,
                        ExtensionPoint.Kind.INTERFACE,
                        true,
                    )
                }
                KtPsiFactory(root.project).createPhysicalFile(fileName = root.name, text = startingText)
            } else {
                root
            }

        super.visit(workingFile)

        if (autoCorrect && workingFile.text != startingText) {
            root.modifiedText = workingFile.text
        }
    }
}
