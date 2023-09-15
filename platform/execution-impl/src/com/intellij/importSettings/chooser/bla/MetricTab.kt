package com.intellij.importSettings.chooser.bla

import ChartsPanel
import com.intellij.ide.ui.laf.darcula.ui.OnboardingDialogButtons
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.ui.OnePixelSplitter
import net.miginfocom.swing.MigLayout
import java.awt.Color
import javax.swing.JPanel

class MetricTab: SimpleToolWindowPanel(false) {
    private val splitter: OnePixelSplitter
    private val chartsPanel = ChartsPanel()

    init {
        splitter = OnePixelSplitter(false, 0.5F).apply{
            firstComponent = JPanel(MigLayout("filly")).apply {
              add(OnboardingDialogButtons.createButton("bla", null, {
                chartsPanel.addPanel()

              }))
              background = Color.RED
            }
            secondComponent = chartsPanel
        }

        setContent(splitter)
    }
}
