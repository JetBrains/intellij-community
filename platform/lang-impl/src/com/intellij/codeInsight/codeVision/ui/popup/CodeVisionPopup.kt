package com.intellij.codeInsight.codeVision.ui.popup

import com.intellij.codeInsight.codeVision.CodeVisionEntry
import com.intellij.codeInsight.codeVision.ui.model.CodeVisionListData
import com.intellij.codeInsight.codeVision.ui.model.RangeCodeVisionModel
import com.intellij.codeInsight.codeVision.ui.popup.layouter.*
import com.intellij.codeInsight.codeVision.ui.renderers.CodeVisionRenderer
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.Inlay
import com.intellij.openapi.project.Project
import com.intellij.ui.popup.AbstractPopup

import com.jetbrains.rd.util.lifetime.Lifetime
import com.jetbrains.rd.util.lifetime.LifetimeDefinition
import com.jetbrains.rd.util.reactive.IProperty
import com.jetbrains.rd.util.reactive.map
import java.awt.Rectangle

class CodeVisionPopup {
  enum class Disposition constructor(val list: List<Anchoring2D>) {
    MOUSE_POPUP_DISPOSITION(listOf(
      Anchoring2D(Anchoring.FarOutside, Anchoring.NearOutside),
      Anchoring2D(Anchoring.NearOutside, Anchoring.NearOutside),
      Anchoring2D(Anchoring.FarOutside, Anchoring.FarOutside),
      Anchoring2D(Anchoring.NearOutside, Anchoring.FarOutside)
    )),

    CURSOR_POPUP_DISPOSITION(listOf(
      Anchoring2D(Anchoring.FarOutside, Anchoring.FarOutside),
      Anchoring2D(Anchoring.NearOutside, Anchoring.FarOutside),
      Anchoring2D(Anchoring.FarOutside, Anchoring.NearOutside),
      Anchoring2D(Anchoring.NearOutside, Anchoring.NearOutside)
    ));
  }

  companion object {
    private var ltd: LifetimeDefinition? = null

    fun createNested(lifetime: Lifetime? = null): LifetimeDefinition {
      ltd?.terminate()

      val lt = lifetime?.createNested() ?: LifetimeDefinition()
      ltd = lt

      return lt
    }


    fun showContextPopup(
      lifetime: Lifetime,
      inlay: Inlay<*>,
      entry: CodeVisionEntry,
      disposition: Disposition,
      model: CodeVisionListData,
      project: Project
    ) {
      showPopup(lifetime, inlay, entry, disposition, model.projectModel.lensPopupActive) {
        CodeVisionContextPopup.createLensList(entry, model, project)
      }
    }

    fun showMorePopup(
      lifetime: Lifetime,
      inlay: Inlay<*>,
      entry: CodeVisionEntry,
      disposition: Disposition,
      model: RangeCodeVisionModel,
      project: Project
    ) {
      showPopup(lifetime, inlay, entry, disposition, model.lensPopupActive()) {
        CodeVisionListPopup.createLensList(model, project)
      }
    }

    fun showMorePopup(
      lifetime: Lifetime,
      project: Project,
      editor: Editor,
      offset: Int,
      disposition: Disposition,
      model: RangeCodeVisionModel
    ) {
      val ltd = createNested(lifetime)

      val anchor = EditorAnchoringRect.createHorizontalSmartClipRect(ltd, offset, editor)

      showPopup(ltd, project, editor, model.lensPopupActive(), anchor, disposition.list) {
        CodeVisionListPopup.createLensList(model, project)
      }
    }

    private fun showPopup(
      ltd: LifetimeDefinition,
      project: Project,
      editor: Editor,
      lensPopupActive: IProperty<Boolean>,
      anchor: AnchoringRect,
      disposition: List<Anchoring2D>,
      popupFactory: (Lifetime) -> AbstractPopup
    ) {
      val popupLayouter = SimplePopupLayouterSource({ lt ->
                                                      DockingLayouter(
                                                        lt, anchor, disposition, project
                                                      )
                                                    }, this::class.java.simpleName).createLayouter(ltd)

      val pw = CodeVisionPopupWrapper(ltd, editor, popupFactory, popupLayouter, lensPopupActive)

      //pw.show()
      anchor.rectangle.map { it != null }.view(ltd) { lt, it ->
        if (it) {
          pw.show()
        }
        else {
          pw.hide(lt)
        }
      }
    }

    private fun showPopup(
      lifetime: Lifetime,
      inlay: Inlay<*>,
      entry: CodeVisionEntry,
      disposition: Disposition,
      lensPopupActive: IProperty<Boolean>,
      popupFactory: (Lifetime) -> AbstractPopup
    ) {
      val editor = inlay.editor
      val project = editor.project ?: return
      val offset = inlay.offset

      val ltd = createNested(lifetime)
      val shift = getPopupShift(inlay, entry) ?: return
      val anchor = EditorAnchoringRect(ltd, editor, offset, shiftDelegate(editor, shift))

      showPopup(ltd, project, editor, lensPopupActive, anchor, disposition.list, popupFactory)
    }


    private fun getPopupShift(inlay: Inlay<*>, entry: CodeVisionEntry): LensPopupLayoutingData? {
      val inlayXY = inlay.bounds ?: return null
      val editor = inlay.editor

      val renderer = inlay.renderer as CodeVisionRenderer
      val entryBounds = renderer.entryBounds(inlay, entry) ?: return null

      val offsetXY = editor.offsetToXY(inlay.offset)
      val shiftX = inlayXY.x - offsetXY.x + entryBounds.x - entryBounds.width
      val shiftY = offsetXY.y - inlayXY.y

      return LensPopupLayoutingData(shiftX, shiftY, entryBounds.width, inlayXY.height)
    }

    private class LensPopupLayoutingData(
      val horizontalShift: Int,
      val verticalShift: Int,
      val width: Int,
      val height: Int
    )

    private fun shiftDelegate(editor: Editor, shift: LensPopupLayoutingData): (Rectangle) -> Rectangle? = {
      val rect = it.map { Rectangle(it.x + shift.horizontalShift, it.y - shift.verticalShift, shift.width, shift.height) }
      if (rect == null) null else EditorAnchoringRect.horizontalSmartClipDelegate(editor).invoke(rect)
    }
  }
}