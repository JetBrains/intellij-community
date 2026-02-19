// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.ui

import com.intellij.internal.statistic.eventLog.util.StringUtil
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.editor.ex.EditorGutterComponentEx
import com.intellij.openapi.fileEditor.impl.FileDocumentManagerBase
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.testFramework.LightVirtualFile
import com.intellij.ui.BrowserHyperlinkListener
import com.intellij.ui.OnePixelSplitter
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.HTMLEditorKitBuilder
import com.intellij.util.ui.UIUtil
import org.intellij.lang.annotations.Language
import java.util.prefs.Preferences
import javax.swing.Action
import javax.swing.JComponent
import javax.swing.JEditorPane

internal class JEditorPaneDemoAction : DumbAwareAction("HTML Rendering Playground") {

  private val PREFERENCE_KEY = "HTML_RENDERING_PLAYGROUND"

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun actionPerformed(e: AnActionEvent) {
    object : DialogWrapper(e.project, null, true, IdeModalityType.IDE, false) {
      val myView = JEditorPane()
      val myEditor: Editor

      init {
        title = "HTML Rendering Playground"
        isResizable = true
        myView.contentType = "text/html"
        myView.isEditable = false
        myView.editorKit = HTMLEditorKitBuilder.simple()
        myView.addHyperlinkListener(BrowserHyperlinkListener.INSTANCE)
        UIUtil.doNotScrollToCaret(myView)

        var html = Preferences.userRoot().get(PREFERENCE_KEY, "")
        if (StringUtil.isEmpty(html)) html = DEFAULT_HTML

        val editorFactory: EditorFactory? = EditorFactory.getInstance()
        val editorDocument: Document = editorFactory!!.createDocument(html)

        val htmlFileType = FileTypeManager.getInstance().getFileTypeByExtension("html")
        FileDocumentManagerBase.registerDocument(editorDocument, LightVirtualFile("$title.html", htmlFileType, html))
        myEditor = editorFactory.createEditor(editorDocument, e.project, htmlFileType, false)
        val settings = myEditor.settings
        settings.isLineNumbersShown = false
        settings.isWhitespacesShown = true
        settings.isLineMarkerAreaShown = false
        settings.isIndentGuidesShown = false
        settings.additionalColumnsCount = 0
        settings.additionalLinesCount = 0
        settings.isRightMarginShown = false
        settings.setRightMargin(60)
        settings.setGutterIconsShown(false)
        settings.isIndentGuidesShown = false
        (myEditor.gutter as EditorGutterComponentEx?)!!.isPaintBackground = false

        editorDocument.addDocumentListener(object : DocumentListener {
          override fun documentChanged(event: com.intellij.openapi.editor.event.DocumentEvent) {
            try {
              myView.text = event.document.text
              Preferences.userRoot().put(PREFERENCE_KEY, event.document.text)
            }
            catch (ignored: Exception) {
            }
          }
        })
        myView.text = myEditor.document.text
        init()
      }

      override fun getStyle(): DialogStyle {
        return DialogStyle.COMPACT
      }

      override fun getDimensionServiceKey(): String {
        return "HTMLRenderingPlayground"
      }

      override fun createCenterPanel(): JComponent {
        val splitter = OnePixelSplitter(false, .33F, .18F, .82F)
        splitter.splitterProportionKey = "HTMLRenderingPlayground.Splitter"
        splitter.firstComponent = JBScrollPane(myEditor.component)
        splitter.secondComponent = JBScrollPane(myView)
        return splitter
      }

      override fun createActions() = emptyArray<Action>()

    }.show()
  }

  @Language("HTML")
  private val DEFAULT_HTML = """<html>
<head>
    <style type="text/css">
        body {
            margin: 10px;
            background: #C0C0C0;
            color: #333333;
        }

        code {
            background-color: #eeee11;
            margin: 4px;
        }

        pre {
            padding: 10px;
        }
    </style>
</head>
<body>
<h1>This is HTML rendering demo.</h1>
Some key features are supported:
<ol>
    <li>Of course <i>Italic</i> & <b>bold</b></li>
    <li>Not so often used <sub>subscript</sub> <strike>strikethrough</strike> and <sup>superscript</sup>
    <li>Tags <small>small</small> and <strong>strong</strong>. And <u>underlined</u> too.</li>
    <li><a href="https://www.jetbrains.com/">External links</a></li>
    <li>This is <code>@Code</code> tag to be <code>highlighted</code></li>
    <li>Emoji etc. if you are lucky enough to see it <span style="color:red;">[&#9829;]</span>[&#128512;]</li>
</ol>
The tag below is &lt;pre&gt;:
<pre style="background-color:white;color:black;">
class HelloWorld {
    public static void main(String[] args) {
        System.out.println("Hello World!");
    }
}</pre>
<ul>
    <li>User-friendly link <a href="https://docs.oracle.com/en/java/javase/11/docs/api/java.desktop/javax/swing/text/html/CSS.html">
        CSS support in Java engine</a></li>
    <li>Not so user-friendly link <a href="https://docs.oracle.com/en/java/javase/11/docs/api/java.desktop/javax/swing/text/html/CSS.html">
        https://docs.oracle.com/en/java/javase/11/docs/api/java.desktop/javax/swing/text/html/CSS.html</a></li>
</ul>
<p>
<div style="color:white;background-color:#909090; border:solid 1px #fedcba; text-align:center;">Outer div
    <div style="background-color:#606060; width:50%; margin:20px;padding:11px; border:solid 1px orange;">Inner div with width 50%</div>
</div>
</p><br>
<p>
    Well, we can do something with it...</p>
</body>
</html>"""
}