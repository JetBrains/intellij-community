// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection

import com.intellij.codeInsight.CodeInsightBundle
import com.intellij.codeInspection.ui.OptionAccessor
import com.intellij.ui.layout.*
import com.intellij.util.ui.CheckBox

class NonAsciiCharactersInspectionFormUi(entry: InspectionProfileEntry) {
  val panel = panel {
    row {
      CheckBox(
        CodeInsightBundle.message("checkbox.non.ascii.option.characters.in.identifiers"),
        entry,
        "CHECK_FOR_NOT_ASCII_IDENTIFIER_NAME"
      )()
      label(CodeInsightBundle.message("label.non.ascii.chars.example.characters.in.identifiers"))
    }
    row {
      CheckBox(
        CodeInsightBundle.message("checkbox.non.ascii.option.characters.in.comments"),
        entry,
        "CHECK_FOR_NOT_ASCII_STRING_LITERAL"
      )()
      label(CodeInsightBundle.message("label.non.ascii.chars.example.characters.in.comments"))
    }
    row {
      CheckBox(
        CodeInsightBundle.message("checkbox.non.ascii.option.characters.in.strings"),
        entry,
        "CHECK_FOR_NOT_ASCII_COMMENT"
      )()
      label(CodeInsightBundle.message("label.non.ascii.chars.example.characters.in.strings"))
    }
    row {
      CheckBox(
        CodeInsightBundle.message("checkbox.non.ascii.option.different.languages.in.identifiers"),
        entry,
        "CHECK_FOR_DIFFERENT_LANGUAGES_IN_IDENTIFIER_NAME"
      )()
      label(CodeInsightBundle.message("label.non.ascii.chars.example.different.languages.in.identifiers"))
    }
    row {
      CheckBox(
        CodeInsightBundle.message("checkbox.non.ascii.option.different.languages.in.string"),
        entry,
        "CHECK_FOR_DIFFERENT_LANGUAGES_IN_STRING"
      )()
      label(CodeInsightBundle.message("label.non.ascii.chars.example.different.languages.in.string"))
    }
    row {
      CheckBox(
        CodeInsightBundle.message("checkbox.non.ascii.option.files.containing.bom"),
        entry,
        "CHECK_FOR_FILES_CONTAINING_BOM"
      )()
      label(CodeInsightBundle.message("label.non.ascii.chars.example.files.containing.bom"))
    }
  }
}