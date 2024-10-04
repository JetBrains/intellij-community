// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.json.editor

import com.intellij.json.JsonBundle
import com.intellij.openapi.options.BeanConfigurable
import com.intellij.openapi.options.SearchableConfigurable

private class JsonSmartKeysConfigurable : BeanConfigurable<Unit>(Unit), SearchableConfigurable {
  init {
    JsonEditorOptions.getInstance()?.let { settings ->
      checkBox(JsonBundle.message("settings.smart.keys.insert.missing.comma.on.enter"), settings::COMMA_ON_ENTER)
      checkBox(JsonBundle.message("settings.smart.keys.insert.missing.comma.after.matching.braces.and.quotes"),
               settings::COMMA_ON_MATCHING_BRACES)
      checkBox(JsonBundle.message("settings.smart.keys.automatically.manage.commas.when.pasting.json.fragments"),
               settings::COMMA_ON_PASTE)
      checkBox(JsonBundle.message("settings.smart.keys.escape.text.on.paste.in.string.literals"), settings::ESCAPE_PASTED_TEXT)
      checkBox(JsonBundle.message("settings.smart.keys.automatically.add.quotes.to.property.names.when.typing.comma"),
               settings::AUTO_QUOTE_PROP_NAME)
      checkBox(JsonBundle.message("settings.smart.keys.automatically.add.whitespace.when.typing.comma.after.property.names"),
               settings::AUTO_WHITESPACE_AFTER_COLON)
      checkBox(JsonBundle.message("settings.smart.keys.automatically.move.comma.after.the.property.name.if.typed.inside.quotes"),
               settings::COLON_MOVE_OUTSIDE_QUOTES)
      checkBox(
        JsonBundle.message("settings.smart.keys.automatically.move.comma.after.the.property.value.or.array.element.if.inside.quotes"),
        settings::COMMA_MOVE_OUTSIDE_QUOTES)
    }
  }

  override fun getDisplayName(): String {
    return JsonBundle.message("configurable.JsonSmartKeysConfigurable.display.name")
  }

  override fun getId(): String {
    return "editor.preferences.jsonOptions"
  }

  override fun getHelpTopic() = "reference.settings.editor.smart.keys.json"
}
