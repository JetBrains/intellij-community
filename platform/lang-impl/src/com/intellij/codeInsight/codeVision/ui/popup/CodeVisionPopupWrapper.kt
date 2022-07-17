package com.intellij.codeInsight.codeVision.ui.popup

import com.intellij.codeInsight.codeVision.ui.popup.layouter.DockingLayouter
import com.intellij.codeInsight.codeVision.ui.popup.layouter.location
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.ui.popup.JBPopupListener
import com.intellij.openapi.ui.popup.LightweightWindowEvent
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.popup.AbstractPopup
import com.jetbrains.rd.swing.escPressedSource
import com.jetbrains.rd.swing.mouseOrKeyReleased
import com.jetbrains.rd.util.getLogger
import com.jetbrains.rd.util.lifetime.Lifetime
import com.jetbrains.rd.util.lifetime.LifetimeDefinition
import com.jetbrains.rd.util.lifetime.isAlive
import com.jetbrains.rd.util.lifetime.onTermination
import com.jetbrains.rd.util.reactive.IProperty
import com.jetbrains.rd.util.warn
import java.awt.Dimension

class CodeVisionPopupWrapper(
  val mainLTD: LifetimeDefinition,
  val editor: Editor,
  val popupFactory: (Lifetime) -> AbstractPopup,
  val popupLayouter: DockingLayouter,
  val lensPopupActive: IProperty<Boolean>
) {
  private val logger = getLogger<CodeVisionPopupWrapper>()

  var popup: AbstractPopup? = null
  var ltd: LifetimeDefinition? = null
  var processing = false

  init {
    escPressedSource().advise(mainLTD) {
      mainLTD.terminate()
    }

    mainLTD.onTermination {
      close()
    }
  }

  fun show() {
    if (mainLTD.isAlive) {
      createPopup(mainLTD.createNested())
    }
  }

  fun hide(lt: Lifetime) {
    processing = true

    close()

    mouseOrKeyReleased().advise(lt) {
      mainLTD.terminate()
    }

  }

  private fun close() {
    ltd?.terminate()
    ltd = null
    lensPopupActive.set(false)
  }

  private fun createPopup(lifetimeDefinition: LifetimeDefinition) {
    lensPopupActive.set(true)

    ltd = lifetimeDefinition
    val pp = popupFactory(lifetimeDefinition)
    popup = pp

    pp.pack(true, true)

    val listener = object : JBPopupListener {
      override fun onClosed(event: LightweightWindowEvent) {
        if (event.isOk || !processing) {
          mainLTD.terminate(true)
        }

        processing = false
      }
    }
    pp.addListener(listener)

    lifetimeDefinition.onTermination {
      val cancelingPopup = popup ?: return@onTermination
      if (cancelingPopup.canClose()) {
        if (cancelingPopup.isDisposed)
          cancelingPopup.removeListener(listener)
        cancelingPopup.cancel()
      }

      popup = null
    }

    val possibleSize = pp.sizeForPositioning
    val probablyRealSize = Dimension(possibleSize.width, possibleSize.height - pp.headerPreferredSize.height)
    popupLayouter.size.set(probablyRealSize)
    val location = popupLayouter.layout.value?.location

    popupLayouter.layout.advise(lifetimeDefinition) {
      if (it != null) {
        pp.setLocation(it.location)
      }
    }

    if (location != null) {
      pp.show(RelativePoint(location))
    }
    else {
      logger.warn { "location == null, this can't be right" }
      pp.showInBestPositionFor(editor)
      popupLayouter.size.set(pp.size)
    }
  }
}

