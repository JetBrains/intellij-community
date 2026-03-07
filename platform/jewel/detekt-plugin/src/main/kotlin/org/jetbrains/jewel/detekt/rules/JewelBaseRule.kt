package org.jetbrains.jewel.detekt.rules

import dev.detekt.api.Config
import dev.detekt.api.Rule
import dev.detekt.api.modifiedText
import java.net.URI
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtPsiFactory

abstract class JewelBaseRule(config: Config, description: String, url: URI? = null) : Rule(config, description, url) {
    private lateinit var workingFile: KtFile
    private lateinit var originalFile: KtFile

    override fun visit(root: KtFile) {
        if (autoCorrect) {
            // Create a writable copy of the file for autocorrect
            val fileCopy =
                KtPsiFactory(root.project)
                    .createPhysicalFile(fileName = root.name, text = root.modifiedText ?: root.text)

            workingFile = fileCopy
            originalFile = root
        } else {
            workingFile = root
            originalFile = root
        }

        super.visit(workingFile)

        if (autoCorrect && workingFile.modificationStamp > 0) {
            originalFile.modifiedText = workingFile.text
        }
    }
}
