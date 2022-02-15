package org.jetbrains.jewel

import androidx.compose.foundation.Indication
import androidx.compose.foundation.IndicationInstance
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.drawscope.ContentDrawScope

object NoIndication : Indication {
    private object NoIndicationInstance : IndicationInstance {

        override fun ContentDrawScope.drawIndication() = drawContent()
    }

    @Composable
    override fun rememberUpdatedInstance(interactionSource: InteractionSource): IndicationInstance =
        NoIndicationInstance
}
