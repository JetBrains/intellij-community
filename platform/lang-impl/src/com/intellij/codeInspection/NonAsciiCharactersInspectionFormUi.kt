// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection

import com.intellij.codeInsight.CodeInsightBundle
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.ui.CheckBox


class NonAsciiCharactersInspectionFormUi(entry: InspectionProfileEntry) {
  val panel = panel {
    row {
      cell(
        CheckBox(
          CodeInsightBundle.message("non.ascii.chars.inspection.option.files.containing.bom.checkbox"),
          entry,
          "CHECK_FOR_FILES_CONTAINING_BOM"
          )
      )
    }
    buttonsGroup(CodeInsightBundle.message("non.ascii.chars.inspection.non.ascii.top.label")) {
      row {
        cell(
          CheckBox(
            CodeInsightBundle.message("non.ascii.chars.inspection.option.characters.in.identifiers.checkbox"),
            entry,
            "CHECK_FOR_NOT_ASCII_IDENTIFIER_NAME"
          )
        )
        comment(CodeInsightBundle.message("non.ascii.chars.inspection.example.characters.in.identifiers.label"))
      }

      row {
        cell(
          CheckBox(
            CodeInsightBundle.message("non.ascii.chars.inspection.option.characters.in.strings.checkbox"),
            entry,
            "CHECK_FOR_NOT_ASCII_STRING_LITERAL"
          )
        )
        comment(CodeInsightBundle.message("non.ascii.chars.inspection.example.characters.in.strings.label"))
      }

      row {
        cell(
          CheckBox(
            CodeInsightBundle.message("non.ascii.chars.inspection.option.characters.in.comments.checkbox"),
            entry,
            "CHECK_FOR_NOT_ASCII_COMMENT"
          )
        )
        comment(CodeInsightBundle.message("non.ascii.chars.inspection.example.characters.in.comments.label"))
      }

      row {
        cell(
          CheckBox(
            CodeInsightBundle.message("non.ascii.chars.inspection.option.characters.in.any.other.word.checkbox"),
            entry,
            "CHECK_FOR_NOT_ASCII_IN_ANY_OTHER_WORD"
          )
        )
        comment(CodeInsightBundle.message("non.ascii.chars.inspection.example.characters.in.any.other.word.label"))
      }
    }
    buttonsGroup(CodeInsightBundle.message("non.ascii.chars.inspection.mixed.chars.top.label")) {
      row {
        cell(
          CheckBox(
            CodeInsightBundle.message("non.ascii.chars.inspection.option.mixed.languages.in.identifiers.checkbox"),
            entry,
            "CHECK_FOR_DIFFERENT_LANGUAGES_IN_IDENTIFIER_NAME"
          )
        )
        comment(CodeInsightBundle.message("non.ascii.chars.inspection.example.mixed.languages.in.identifiers.label"))
      }
      row {
        cell(
          CheckBox(
            CodeInsightBundle.message("non.ascii.chars.inspection.option.mixed.languages.in.strings.checkbox"),
            entry,
            "CHECK_FOR_DIFFERENT_LANGUAGES_IN_STRING"
          )
        )
        comment(CodeInsightBundle.message("non.ascii.chars.inspection.example.mixed.languages.in.string.label"))
      }
      row {
        cell(
          CheckBox(
            CodeInsightBundle.message("non.ascii.chars.inspection.option.mixed.languages.in.comments.checkbox"),
            entry,
            "CHECK_FOR_DIFFERENT_LANGUAGES_IN_COMMENTS"
          )
        )
        comment(CodeInsightBundle.message("non.ascii.chars.inspection.example.mixed.languages.in.comments.label"))
      }
      row {
        cell(
          CheckBox(
            CodeInsightBundle.message("non.ascii.chars.inspection.option.mixed.languages.in.any.other.word.checkbox"),
            entry,
            "CHECK_FOR_DIFFERENT_LANGUAGES_IN_ANY_OTHER_WORD"
          )
        )
        comment(CodeInsightBundle.message("non.ascii.chars.inspection.example.mixed.languages.in.any.other.word.label"))
      }
    }
  }
}