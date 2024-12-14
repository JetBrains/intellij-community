package org.jetbrains.jewel.samples.ideplugin

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.ui.Messages.showMessageDialog

internal class ActionSystemTestAction : AnAction() {
    private val logger = thisLogger()

    override fun actionPerformed(anActionEvent: AnActionEvent) {
        logger.debug(anActionEvent.getData(COMPONENT_DATA_KEY))

        showMessageDialog(anActionEvent.getData(COMPONENT_DATA_KEY), "Action System Test", null)
    }

    companion object {
        val COMPONENT_DATA_KEY = DataKey.create<String>("COMPONENT")
    }
}
