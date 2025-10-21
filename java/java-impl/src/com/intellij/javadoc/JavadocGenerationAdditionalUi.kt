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

public class JavadocGenerationAdditionalUi {
  public lateinit var myIncludeLibraryCb: JCheckBox
  public lateinit var myLinkToJdkDocs: JCheckBox

  public lateinit var myTfOutputDir: TextFieldWithBrowseButton

  public lateinit var myScopeCombo: ComboBox<@NlsSafe String>

  public lateinit var myHierarchy: JCheckBox
  public lateinit var myNavigator: JCheckBox
  public lateinit var myIndex: JCheckBox
  public lateinit var mySeparateIndex: JCheckBox

  public lateinit var myTagUse: JCheckBox
  public lateinit var myTagAuthor: JCheckBox
  public lateinit var myTagVersion: JCheckBox
  public lateinit var myTagDeprecated: JCheckBox
  public lateinit var myDeprecatedList: JCheckBox

  public lateinit var myLocaleTextField: JTextField
  public lateinit var myOtherOptionsField: JTextField
  public lateinit var myHeapSizeField: JTextField

  public lateinit var myOpenInBrowserCheckBox: JCheckBox

  public val panel: JPanel = panel {
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
