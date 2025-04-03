// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.javadoc

import com.intellij.java.JavaBundle
import com.intellij.java.syntax.parser.JavaKeywords
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.util.NlsSafe
import com.intellij.ui.dsl.builder.*
import com.intellij.ui.layout.selected
import javax.swing.JCheckBox
import javax.swing.JPanel
import javax.swing.JTextField

class JavadocGenerationAdditionalUi {
  lateinit var myIncludeLibraryCb: JCheckBox
  lateinit var myLinkToJdkDocs: JCheckBox

  lateinit var myTfOutputDir: TextFieldWithBrowseButton

  lateinit var myScopeCombo: ComboBox<@NlsSafe String>

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

  val panel: JPanel = panel {
    group(JavaBundle.message("javadoc.generate.options.separator")) {
      row {
        myIncludeLibraryCb = checkBox(JavaBundle.message("javadoc.generate.include.jdk.library.sources.in.sourcepath.option")).component
      }
      row {
        myLinkToJdkDocs = checkBox(JavaBundle.message("javadoc.generate.link.to.jdk.documentation.option")).component
      }
      row(JavaBundle.message("javadoc.generate.output.directory")) {
        myTfOutputDir = textFieldWithBrowseButton(FileChooserDescriptorFactory.createSingleFolderDescriptor().withTitle(JavaBundle.message("javadoc.generate.output.directory.browse")))
          .align(AlignX.FILL)
          .component
        bottomGap(BottomGap.MEDIUM)
      }
        .layout(RowLayout.INDEPENDENT)
      row(JavaBundle.message("javadoc.generate.scope.row")) {
        myScopeCombo = comboBox(listOf(JavaKeywords.PUBLIC, JavaKeywords.PROTECTED, JavaKeywords.PACKAGE, JavaKeywords.PRIVATE))
          .component
      }
        .layout(RowLayout.INDEPENDENT)
      row {
        panel {
          row {
            myHierarchy = checkBox(JavaBundle.message("javadoc.generate.options.hierarchy")).component
          }
          row {
            myNavigator = checkBox(JavaBundle.message("javadoc.generate.options.navigator")).component
          }
          row {
            myIndex = checkBox(JavaBundle.message("javadoc.generate.options.index")).component
          }
          indent {
            row {
              mySeparateIndex = checkBox(JavaBundle.message("javadoc.generate.options.index.per.letter"))
                .enabledIf(myIndex.selected)
                .component
            }
          }
        }
          .gap(RightGap.COLUMNS)
          .align(AlignY.TOP)
        panel {
          row {
            myTagUse = checkBox("@use").component
          }
          row {
            myTagAuthor = checkBox("@author").component
          }
          row {
            myTagVersion = checkBox("@version").component
          }
          row {
            myTagDeprecated = checkBox("@deprecated").component
          }
          indent {
            row {
              myDeprecatedList = checkBox(JavaBundle.message("javadoc.generate.tag.list.deprecated"))
                .enabledIf(myTagDeprecated.selected)
                .component
            }
          }
        }
          .align(AlignY.TOP)
        bottomGap(BottomGap.MEDIUM)
      }
      row(JavaBundle.message("javadoc.generate.locale")) {
        myLocaleTextField = textField()
          .align(AlignX.FILL)
          .component
      }
      row(JavaBundle.message("javadoc.generate.arguments")) {
        myOtherOptionsField = textField()
          .align(AlignX.FILL)
          .component
      }
      row(JavaBundle.message("javadoc.generate.heap.size")) {
        myHeapSizeField = intTextField(IntRange(0, Int.MAX_VALUE), 128)
          .gap(RightGap.SMALL)
          .component
        @Suppress("DialogTitleCapitalization")
        label(JavaBundle.message("megabytes.unit"))
      }
      row {
        myOpenInBrowserCheckBox = checkBox(JavaBundle.message("javadoc.generate.open.in.browser")).component
      }
    }
  }
}
