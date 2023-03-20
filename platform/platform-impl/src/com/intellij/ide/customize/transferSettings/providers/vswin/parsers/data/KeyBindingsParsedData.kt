package com.intellij.ide.customize.transferSettings.providers.vswin.parsers.data


import com.intellij.ide.customize.transferSettings.db.KnownPlugins
import com.intellij.ide.customize.transferSettings.models.KeyBinding
import com.intellij.openapi.actionSystem.KeyboardShortcut
import com.intellij.ide.customize.transferSettings.providers.vswin.mappings.KeyBindingsMappings.newTokens
import com.intellij.ide.customize.transferSettings.providers.vswin.mappings.KeyBindingsMappings.vsCommandToIdeaAction
import com.intellij.ide.customize.transferSettings.providers.vswin.utilities.VSHive
import com.intellij.openapi.diagnostic.logger
import org.jdom.Element
import javax.swing.KeyStroke

private class BadKeyBindingException(message: String, val isImportant: Boolean, cause: Throwable?) : Exception(message, cause) {
  constructor(full: String, token: String, isImportant: Boolean) : this("Can't parse: $full, token: $token", isImportant, null)
}

private class UnknownKeyboardActionException(val command: String) : Exception()

private val logger = logger<VisualStudioKeyboardShortcut>()
class VisualStudioKeyboardShortcut(val shortcut: String, val command: String) {
  private val splitItems: List<String?>

  init {
    logger.info("Started parsing $shortcut, cmd $command")
    if (vsCommandToIdeaAction(command) == null) {
      throw UnknownKeyboardActionException(command)
    }
    splitItems = split().map { processSingleShortcut(it) }

    if (splitItems.contains(null)) {
      throw BadKeyBindingException("got null somewhere", false, null)
    }
  }

  fun getParsed(): KeyboardShortcut {
    return KeyboardShortcut(
      KeyStroke.getKeyStroke(splitItems[0]),
      if (splitItems.size == 2) KeyStroke.getKeyStroke(splitItems[1]) else null
    )
  }

  private fun containsGoodChars(str: String): Boolean {
    return newTokens.containsKey(str) ||
           !str.any { char -> !(char in 'A'..'Z' || char in 'a'..'z' || char in '0'..'9' || char == ' ') }
  }

  // будем парсить по порядку =)

  private fun split(): List<String> {
    return shortcut.split(", ").also {
      logger.info("Got parts of shortcut: ${it.size}, $it")
    }
  }

  private fun processSingleShortcut(sc: String): String? {
    return sc.split('+').map {
      when {
        !containsGoodChars(it) -> return null
        newTokens.containsKey(it) -> newTokens[it]
        it.startsWith("Num") -> "NUMPAD" + it.substring(4)
        (it.startsWith('F') || it.startsWith('f')) && it.length > 1 && it[1] in '0'..'9' -> it
        //it.length == 1 -> it.toUpperCase()
        else -> it.uppercase()
      }
    }.joinToString(separator = " ")
  }
}

class KeyBindingsParsedData(majorVersion: Int, val scheme: String, userShortcuts: Element, hive: VSHive?) : VSParsedData {
  private val parsedKeyBindings = mutableListOf<KeyBinding>()

  companion object {
    const val globalScope = "Global"
    const val key = "Environment_KeyBindings"
    private val logger = logger<KeyBindingsParsedData>()
  }

  private val isReSharperInstalled = hive?.registry?.extensions?.contains(KnownPlugins.ReSharper) == true
  private val reSharperName = KnownPlugins.ReSharper.name.lowercase()

  init {
    // Hash map of Command, Pair<Scope (Global, etc..), Value>
    val alreadyParsed = mutableMapOf<String, MutableSet<Pair<String, Boolean>>>()

    logger.info("Started parsing shortcuts")
    for (shortcutTag in userShortcuts.children) {
      if (shortcutTag.name != "Shortcut") continue


      val scope = shortcutTag.getAttribute("Scope")?.value
      val command = shortcutTag.getAttribute("Command")?.value
      val keyboardShortcut = shortcutTag.value

      if (!isReSharperInstalled && command?.lowercase()?.startsWith(reSharperName) == true) {
        logger.info("ReSharper is not installed or disabled, skipping ReSharper shortcuts")
        continue
      }

      if (scope == null || command == null) {
        logger.warn("Invalid shortcut where scope or command are null")
        continue
      }

      logger.debug("Processing $command")
      if (vsCommandToIdeaAction(command) == null) {
        logger.debug("Unknown action")
        continue
      }

      val mapValue = alreadyParsed.getOrPut(command) { mutableSetOf() }
      if (!mapValue.contains(keyboardShortcut to true)) {
        mapValue.add(keyboardShortcut to (scope == "Global"))
      }
      if (scope == "Global") {
        mapValue.remove(keyboardShortcut to false)
      }
    }

    for ((command, shortcuts) in alreadyParsed) {
      val scArr = mutableListOf<KeyboardShortcut>()
      val convertedAction = vsCommandToIdeaAction(command)
      if (convertedAction == null) {
        logger.warn("convertedaction is null at late stage")
        continue
      }
      for ((shortcut, _) in shortcuts) {
        try {
          val sc = VisualStudioKeyboardShortcut(shortcut, command).getParsed()
          scArr.add(sc)
        }
        catch (t: BadKeyBindingException) {
          logger.info("parsing error might be not an error")
          logger.info(t)
        }
        catch (t: Throwable) {
          logger.error("Failed to parse shortcut")
          logger.error(t)
        }
      }
      parsedKeyBindings.add(KeyBinding(convertedAction, scArr))
    }
  }

  fun convertToSettingsFormat() = parsedKeyBindings
}