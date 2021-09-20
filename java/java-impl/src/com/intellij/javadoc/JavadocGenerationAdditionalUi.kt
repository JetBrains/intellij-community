// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.javadoc

import com.intellij.java.JavaBundle
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.psi.PsiKeyword
import com.intellij.ui.Gray
import com.intellij.ui.JBColor
import com.intellij.ui.dsl.builder.BottomGap
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.gridLayout.HorizontalAlign
import com.intellij.ui.dsl.gridLayout.VerticalAlign
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.util.*
import javax.swing.*

class JavadocGenerationAdditionalUi {
  lateinit var myIncludeLibraryCb: JCheckBox
  lateinit var myLinkToJdkDocs: JCheckBox

  lateinit var myTfOutputDir: TextFieldWithBrowseButton

  lateinit var myScopeSlider: JSlider

  lateinit var myHierarchy: JCheckBox
  lateinit var myNavigator: JCheckBox
  lateinit var myIndex: JCheckBox
  lateinit var mySeparateIndex: JCheckBox

  lateinit var myTagUse: JCheckBox
  lateinit var myTagAuthor: JCheckBox
  lateinit var myTagVersion: JCheckBox
  lateinit var myTagDeprecated: JCheckBox
  lateinit var myDeprecatedList: JCheckBox

  lateinit var myLocaleTextField: JTextField
  lateinit var myOtherOptionsField: JTextField
  lateinit var myHeapSizeField: JTextField

  lateinit var myOpenInBrowserCheckBox: JCheckBox

  @SuppressWarnings("UseOfObsoleteCollectionType")
  private val mylabelTable = Hashtable<Int, JComponent>().apply {
    put(Integer.valueOf(1), JLabel(PsiKeyword.PUBLIC))
    put(Integer.valueOf(2), JLabel(PsiKeyword.PROTECTED))
    put(Integer.valueOf(3), JLabel(PsiKeyword.PACKAGE))
    put(Integer.valueOf(4), JLabel(PsiKeyword.PRIVATE))
  }

  val panel = panel {
    group(JavaBundle.message("javadoc.generate.options.separator")) {
      row {
        checkBox(JavaBundle.message("javadoc.generate.include.jdk.library.sources.in.sourcepath.option"))
          .applyToComponent { myIncludeLibraryCb = this }
      }
      row {
        checkBox(JavaBundle.message("javadoc.generate.link.to.jdk.documentation.option"))
          .applyToComponent { myLinkToJdkDocs = this }
      }
      row(JavaBundle.message("javadoc.generate.output.directory")) {
        textFieldWithBrowseButton(JavaBundle.message("javadoc.generate.output.directory.browse"),
          null,
          FileChooserDescriptorFactory.createSingleFolderDescriptor())
          .applyToComponent { myTfOutputDir = this }
          .horizontalAlign(HorizontalAlign.FILL)
        bottomGap(BottomGap.MEDIUM)
      }
      row {
        panel {
          row {
            cell(JSlider())
              .applyToComponent {
                myScopeSlider = this
                orientation = JSlider.VERTICAL
                maximum = 4; minimum = 1
                value = 1
                labelTable = mylabelTable
                putClientProperty(UIUtil.JSLIDER_ISFILLED, true)
                preferredSize = JBUI.size(84, 100)
                paintLabels = true
                snapToTicks = true
                addChangeListener { handleSlider() }
              }
              .horizontalAlign(HorizontalAlign.CENTER)
              .verticalAlign(VerticalAlign.FILL)
          }
        }
        panel {
          row {
            checkBox(JavaBundle.message("javadoc.generate.options.hierarchy")).applyToComponent { myHierarchy = this }
          }
          row {
            checkBox(JavaBundle.message("javadoc.generate.options.navigator")).applyToComponent { myNavigator = this }
          }
          row {
            checkBox(JavaBundle.message("javadoc.generate.options.index")).applyToComponent {
              myIndex = this
              addChangeListener { mySeparateIndex.isEnabled = myIndex.isSelected }
            }
          }
          row {
            checkBox(JavaBundle.message("javadoc.generate.options.index.per.letter")).applyToComponent { mySeparateIndex = this }
          }
        }
        panel {
          row {
            checkBox("@use").applyToComponent { myTagUse = this }
          }
          row {
            checkBox("@author").applyToComponent { myTagAuthor = this }
          }
          row {
            checkBox("@version").applyToComponent { myTagVersion = this }
          }
          row {
            checkBox("@deprecated").applyToComponent {
              myTagDeprecated = this
              addChangeListener { myDeprecatedList.isEnabled = myTagDeprecated.isSelected }
            }
          }
          row {
            checkBox(JavaBundle.message("javadoc.generate.tag.list.deprecated")).applyToComponent { myDeprecatedList = this }
          }
        }
          .horizontalAlign(HorizontalAlign.CENTER)
        bottomGap(BottomGap.MEDIUM)
      }
      row(JavaBundle.message("javadoc.generate.locale")) {
        textField()
          .applyToComponent { myLocaleTextField = this }
          .horizontalAlign(HorizontalAlign.FILL)
      }
      row(JavaBundle.message("javadoc.generate.arguments")) {
        textField()
          .applyToComponent { myOtherOptionsField = this }
          .horizontalAlign(HorizontalAlign.FILL)
      }
      row(JavaBundle.message("javadoc.generate.heap.size")) {
        intTextField(IntRange(0, Int.MAX_VALUE), 128)
          .applyToComponent { myHeapSizeField = this }
          .horizontalAlign(HorizontalAlign.FILL)
      }
      row {
        checkBox(JavaBundle.message("javadoc.generate.open.in.browser"))
          .applyToComponent { myOpenInBrowserCheckBox = this }
      }
    }
  }

  private fun handleSlider() {
    val value = myScopeSlider.value
    val enumeration: Enumeration<Int> = mylabelTable.keys()
    while (enumeration.hasMoreElements()) {
      val key = enumeration.nextElement()
      mylabelTable[key]?.foreground = if (key <= value) JBColor.BLACK else Gray._100
    }
  }

  fun setScope(scope: String) {
    myScopeSlider.value = when (scope) {
      PsiKeyword.PUBLIC -> 1
      PsiKeyword.PROTECTED -> 2
      PsiKeyword.PRIVATE -> 4
      else -> 3
    }
    handleSlider()
  }

  fun getScope(): String? = when (myScopeSlider.value) {
    1 -> PsiKeyword.PUBLIC
    2 -> PsiKeyword.PROTECTED
    3 -> PsiKeyword.PACKAGE
    4 -> PsiKeyword.PRIVATE
    else -> null
  }
}