// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection

import com.intellij.codeInsight.CodeInsightBundle
import com.intellij.ui.dsl.builder.RightGap
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.panel

class NonAsciiCharactersInspectionFormUi(inspection: NonAsciiCharactersInspection) {
  val panel = panel {
    row {
      checkBox(CodeInsightBundle.message("non.ascii.chars.inspection.option.files.containing.bom.checkbox"))
        .bindSelected(inspection::CHECK_FOR_FILES_CONTAINING_BOM)
        .gap(RightGap.SMALL)
      contextHelp(CodeInsightBundle.message("non.ascii.chars.inspection.option.files.containing.bom.label"))
    }
    buttonsGroup(CodeInsightBundle.message("non.ascii.chars.inspection.non.ascii.top.label")) {
      row {
        checkBox(CodeInsightBundle.message("non.ascii.chars.inspection.option.characters.in.identifiers.checkbox"))
          .bindSelected(inspection::CHECK_FOR_NOT_ASCII_IDENTIFIER_NAME)
          .gap(RightGap.SMALL)
        contextHelp(CodeInsightBundle.message("non.ascii.chars.inspection.example.characters.in.identifiers.label"))
      }

      row {
        checkBox(CodeInsightBundle.message("non.ascii.chars.inspection.option.characters.in.strings.checkbox"))
          .bindSelected(inspection::CHECK_FOR_NOT_ASCII_STRING_LITERAL)
          .gap(RightGap.SMALL)
        contextHelp(CodeInsightBundle.message("non.ascii.chars.inspection.example.characters.in.strings.label"))
      }

      row {
        checkBox(CodeInsightBundle.message("non.ascii.chars.inspection.option.characters.in.comments.checkbox"))
          .bindSelected(inspection::CHECK_FOR_NOT_ASCII_COMMENT)
          .gap(RightGap.SMALL)
        contextHelp(CodeInsightBundle.message("non.ascii.chars.inspection.example.characters.in.comments.label"))
      }

      row {
        checkBox(CodeInsightBundle.message("non.ascii.chars.inspection.option.characters.in.any.other.word.checkbox"))
          .bindSelected(inspection::CHECK_FOR_NOT_ASCII_IN_ANY_OTHER_WORD)
          .gap(RightGap.SMALL)
        contextHelp(CodeInsightBundle.message("non.ascii.chars.inspection.example.characters.in.any.other.word.label"))
      }
    }
    buttonsGroup(CodeInsightBundle.message("non.ascii.chars.inspection.mixed.chars.top.label")) {
      row {
        checkBox(CodeInsightBundle.message("non.ascii.chars.inspection.option.mixed.languages.in.identifiers.checkbox"))
          .bindSelected(inspection::CHECK_FOR_DIFFERENT_LANGUAGES_IN_IDENTIFIER_NAME)
          .gap(RightGap.SMALL)
        contextHelp(CodeInsightBundle.message("non.ascii.chars.inspection.example.mixed.languages.in.identifiers.label"))
      }
      row {
        checkBox(CodeInsightBundle.message("non.ascii.chars.inspection.option.mixed.languages.in.strings.checkbox"))
          .bindSelected(inspection::CHECK_FOR_DIFFERENT_LANGUAGES_IN_STRING)
          .gap(RightGap.SMALL)
        contextHelp(CodeInsightBundle.message("non.ascii.chars.inspection.example.mixed.languages.in.string.label"))
      }
      row {
        checkBox(CodeInsightBundle.message("non.ascii.chars.inspection.option.mixed.languages.in.comments.checkbox"))
          .bindSelected(inspection::CHECK_FOR_DIFFERENT_LANGUAGES_IN_COMMENTS)
          .gap(RightGap.SMALL)
        contextHelp(CodeInsightBundle.message("non.ascii.chars.inspection.example.mixed.languages.in.comments.label"))
      }
      row {
        checkBox(CodeInsightBundle.message("non.ascii.chars.inspection.option.mixed.languages.in.any.other.word.checkbox"))
          .bindSelected(inspection::CHECK_FOR_DIFFERENT_LANGUAGES_IN_ANY_OTHER_WORD)
          .gap(RightGap.SMALL)
        contextHelp(CodeInsightBundle.message("non.ascii.chars.inspection.example.mixed.languages.in.any.other.word.label"))
      }
    }
  }
}