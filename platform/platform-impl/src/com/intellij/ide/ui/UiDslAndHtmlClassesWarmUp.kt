// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.ui

import com.intellij.diagnostic.rethrowControlFlowException
import com.intellij.ide.ApplicationActivity
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.logger
import org.jetbrains.annotations.VisibleForTesting
import java.io.StringReader
import javax.swing.text.html.HTMLEditorKit
import javax.swing.text.html.StyleSheet

private val LOG = logger<UiDslAndHtmlClassesWarmUp>()

/**
 * Loads and initializes, on a background thread at start-up, the classes that the first Kotlin UI DSL panel and the first HTML text need,
 * so the EDT does not load them while it builds the first frame content, such as the Projects tab of the welcome screen.
 * No Swing component is created: the DSL classes are only loaded, and the HTML is parsed into a document that no component shows.
 */
internal class UiDslAndHtmlClassesWarmUp : ApplicationActivity {
  override suspend fun execute() {
    if (ApplicationManager.getApplication().isHeadlessEnvironment) {
      return
    }

    try {
      warmUpUiDslAndHtmlClasses()
    }
    catch (e: Throwable) {
      rethrowControlFlowException(e)
      LOG.warn("Cannot warm up the UI DSL and HTML classes", e)
    }
  }
}

@VisibleForTesting
internal fun warmUpUiDslAndHtmlClasses() {
  val classLoader = UiDslAndHtmlClassesWarmUp::class.java.classLoader
  for (className in UI_DSL_AND_HTML_CLASS_NAMES) {
    try {
      Class.forName(className, true, classLoader)
    }
    catch (e: ClassNotFoundException) {
      LOG.debug("Cannot warm up $className: $e")
    }
  }

  // the parser loads its DTD; a document that no component shows needs no EDT
  val editorKit = OwnStyleSheetHtmlEditorKit()
  val document = editorKit.createDefaultDocument()
  editorKit.read(StringReader("<html><body><p>a <b>b</b></p></body></html>"), document, 0)
}

/**
 * The classes that the first `panel {}` loads beyond the interfaces and superclasses that loading a class brings,
 * as the class loading log of the welcome screen start shows them, and the views of the first HTML label.
 */
@VisibleForTesting
internal val UI_DSL_AND_HTML_CLASS_NAMES: List<String> = classNames(
  "com.intellij.openapi.ui.", "DialogPanel",
) + classNames(
  "com.intellij.ui.dsl.builder.",
  "BuilderKt", "IntelliJSpacingConfiguration", "SpacingConfigurationKt", "EmptySpacingConfiguration", "DslComponentProperty",
  "RowLayout", "LabelPosition", "RightGap", "TopGap", "BottomGap", "HyperlinkEventAction", "AlignKt", "AlignBoth",
  "AlignX\$FILL", "AlignX\$LEFT", "AlignX\$CENTER", "AlignX\$RIGHT", "AlignY\$FILL", "AlignY\$TOP", "AlignY\$CENTER", "AlignY\$BOTTOM",
) + classNames(
  "com.intellij.ui.dsl.builder.impl.",
  "KotlinUiDslServiceImpl", "KotlinUiDslServiceImplKt", "DialogPanelConfig", "Context", "PanelImpl", "PanelContext", "RowImpl",
  "CellImpl", "PlaceholderImpl", "PlaceholderCellData", "PanelBuilder", "CellValidationImpl", "OnChangeManager", "UtilsKt",
  "InternalUtilsKt", "DslComponentPropertyInternal", "DslGridFormKt", "GridFormKt", "GridFormRow", "GridFormComponentCell",
  "GridFormPanelCell", "GridFormDeferredCell",
) + classNames(
  "com.intellij.ui.dsl.builder.components.",
  "DslLabel", "DslLabelKt", "DslLabelType",
) + classNames(
  "com.intellij.ui.dsl.gridLayout.",
  "GridLayout", "GridLayoutComponentProperty", "Constraints", "HorizontalAlign", "VerticalAlign", "UnscaledGapsKt", "UnscaledGapsImpl",
  "EmptyGaps", "UnscaledGapsXKt", "UnscaledGapsXImpl", "EmptyGapsX", "UnscaledGapsYKt", "UnscaledGapsYImpl", "EmptyGapsY",
) + classNames(
  "com.intellij.ui.dsl.gridLayout.builders.", "RowsGridBuilder",
) + classNames(
  "com.intellij.ui.dsl.gridLayout.impl.",
  "GridImpl", "GridImplKt", "LayoutData", "ColumnsSizeCalculator", "ColumnsSizeCalculatorKt", "BaselineData", "RowBaselineData",
  "Gaps", "ComponentCell", "GridCell", "PreCalculationData", "LayoutCellData", "ColumnInfo", "SizeInfo", "SizeConstrainsData",
) + classNames(
  "javax.swing.plaf.basic.", "BasicHTML", "BasicHTML\$BasicEditorKit", "BasicHTML\$BasicDocument", "BasicHTML\$Renderer",
) + classNames(
  "javax.swing.text.html.", "BlockView", "ParagraphView", "InlineView", "ImageView", "HTMLEditorKit\$HTMLFactory",
)

private fun classNames(packagePrefix: String, vararg simpleNames: String): List<String> = simpleNames.map { packagePrefix + it }

/**
 * Keeps the warm-up away from the default style sheet, which all HTML kits share.
 * The JDK publishes that sheet before it loads the rules, and the IDE replaces it on the EDT at start-up,
 * so a first use from the warm-up could race with the replacement.
 */
private class OwnStyleSheetHtmlEditorKit : HTMLEditorKit() {
  private val ownStyleSheet = StyleSheet()

  override fun getStyleSheet(): StyleSheet = ownStyleSheet
}
