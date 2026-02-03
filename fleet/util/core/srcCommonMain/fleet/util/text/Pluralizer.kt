/*
 * The original license from http://github.com/blakeembrey/pluralize:
 *
 * The MIT License (MIT)
 *
 * Copyright (c) 2013 Blake Embrey (hello@blakeembrey.com)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package fleet.util.text

import fleet.util.letIf
import kotlin.math.min

private data class InsensitiveString(private val original: String) {
  override fun hashCode(): Int {
    return original.fold(0) { sum, ch ->
      31 * sum + ch.lowercaseChar().code
    }
  }

  override fun equals(other: Any?): Boolean {
    return other == original || (other is InsensitiveString && other.original.equals(original, ignoreCase = true))
  }
}

private fun String.ignoringCase(): InsensitiveString = InsensitiveString(this)

/**
 * A kotlin multiplatform version of com.intellij.openapi.util.text.Pluralizer
 * which is a java version of http://github.com/blakeembrey/pluralize (rev de7b35e700caccfb4e74923d98ab9b40006eda04 - Nov 19, 2017)
 *
 * It tries to preserve the original structure for future sync.
 * @noinspection SpellCheckingInspection, HardCodedStringLiteral
 */
private object Pluralizer {
  private val irregularSingles = mutableMapOf<InsensitiveString, String>()
  private val irregularPlurals = mutableMapOf<InsensitiveString, String>()
  private val uncountables = mutableSetOf<InsensitiveString>()
  private val pluralRules = mutableListOf<Pair<Regex, String>>()
  private val singularRules = mutableListOf<Pair<Regex, String>>()

  /**
   * Sanitize a word by passing in the word and sanitization rules.
   */
  private fun sanitizeWord(word: String, rules: List<Pair<Regex, String>>): String? {
    if (word.isEmpty() || uncountables.contains(word.ignoringCase())) return word

    var len = rules.size

    while (--len > -1) {
      val (regex, replacement) = rules[len]

      if (regex.containsMatchIn(word)) {
        return regex.replaceFirst(word, replacement)
      }
    }
    return null
  }

  /**
   * Replace a word with the updated word.
   *
   * @return null if no applicable rules found
   */
  private fun replaceWord(
    word: String,
    replaceMap: Map<InsensitiveString, String>,
    keepMap: Map<InsensitiveString, String>,
    rules: List<Pair<Regex, String>>,
  ): String? {
    if (word.isEmpty()) return word

    // Get the correct token and case restoration functions.
    // Check against the keep object map.
    if (keepMap.containsKey(word.ignoringCase())) return word

    // Check against the replacement map for a direct word replacement.
    val replacement = replaceMap[word.ignoringCase()]
    if (replacement != null) {
      return replacement
    }

    // Run all the rules against the word.
    return sanitizeWord(word, rules)
  }

  /**
   * Pluralize or singularize a word based on the passed in count.
   */
  fun pluralize(word: String, count: Int, inclusive: Boolean): String {
    val pluralized = if (count == 1) singular(word) else plural(word)

    return (if (inclusive) "$count " else "") + (pluralized ?: word)
  }

  fun plural(word: String): String? {
    return restoreCase(word, replaceWord(word, irregularSingles, irregularPlurals, pluralRules))
  }

  fun singular(word: String): String? {
    return restoreCase(word, replaceWord(word, irregularPlurals, irregularSingles, singularRules))
  }

  private fun addPluralRule(rule: String, replacement: String) {
    pluralRules.add(sanitizeRule(rule) to replacement)
  }

  private fun addSingularRule(rule: String, replacement: String) {
    singularRules.add(sanitizeRule(rule) to replacement)
  }

  private fun addUncountableRule(word: String) {
    if (!word.startsWith("/")) {
      uncountables.add(word.ignoringCase())
    }
    else {
      // Set singular and plural references for the word.
      addPluralRule(word, "$0")
      addSingularRule(word, "$0")
    }
  }

  private fun addIrregularRule(single: String, plural: String) {
    irregularSingles.put(single.ignoringCase(), plural)
    irregularPlurals.put(plural.ignoringCase(), single)
  }

  /**
   * Pass in a word token to produce a function that can replicate the case on
   * another word.
   */
  fun restoreCase(word: String?, result: String?): String? {
    if (word == null || result == null || word === result) return result
    val len = min(result.length.toDouble(), word.length.toDouble()).toInt()
    if (len == 0) return result
    val chars = result.toCharArray()
    var i = 0
    while (i < len) {
      val wc = word.get(i)
      if (chars[i] == wc && i != len - 1) {
        i++
        continue
      }
      val uc = chars[i].uppercaseChar()
      val lc = chars[i].lowercaseChar()
      if (wc != lc && wc != uc) break
      chars[i] = wc
      i++
    }
    if (i > 0 && i < chars.size) {
      val wc = word[i - 1]
      val uc = wc.uppercaseChar()
      val lc = wc.lowercaseChar()
      if (uc != lc) {
        while (i < chars.size) {
          chars[i] = if (wc == uc) chars[i].uppercaseChar() else chars[i].lowercaseChar()
          i++
        }
      }
    }
    return chars.concatToString()
  }

  private fun sanitizeRule(rule: String): Regex {
    return Regex(if (rule.startsWith("/")) rule.substring(1) else "^$rule$", RegexOption.IGNORE_CASE)
  }

  init {
    /*
     * Irregular rules.
     */
    listOf( // Pronouns.
      //{"I", "we"},
      //{"me", "us"},
      //{"he", "they"},
      //{"she", "they"},
      //{"them", "them"},
      //{"myself", "ourselves"},
      //{"yourself", "yourselves"},
      //{"itself", "themselves"},
      //{"herself", "themselves"},
      //{"himself", "themselves"},
      //{"themself", "themselves"},
      //{"is", "are"},
      //{"was", "were"},
      //{"has", "have"},
      "this" to "these",
      "that" to "those",  // Words ending in with a consonant and `o`.
      "echo" to "echoes",
      "dingo" to "dingoes",
      "volcano" to "volcanoes",
      "tornado" to "tornadoes",
      "torpedo" to "torpedoes",  // Ends with `us`.
      "genus" to "genera",
      "viscus" to "viscera",  // Ends with `ma`.
      "stigma" to "stigmata",
      "stoma" to "stomata",
      "dogma" to "dogmata",
      "lemma" to "lemmata",  //{"schema" to "schemata"},
      "anathema" to "anathemata",  // Other irregular rules.
      "ox" to "oxen",
      "axe" to "axes",
      "die" to "dice",
      "yes" to "yeses",
      "foot" to "feet",
      "eave" to "eaves",
      "goose" to "geese",
      "tooth" to "teeth",
      "quiz" to "quizzes",
      "human" to "humans",
      "proof" to "proofs",
      "carve" to "carves",
      "valve" to "valves",
      "looey" to "looies",
      "thief" to "thieves",
      "groove" to "grooves",
      "pickaxe" to "pickaxes",
      "whiskey" to "whiskies"
    ).forEach { o ->
      addIrregularRule(o.first, o.second)
    }

    /*
     * Pluralization rules.
     */
    listOf(
      "/s?$" to "s",
      "/([^aeiou]ese)$" to "$1",
      "/(ax|test)is$" to "$1es",
      "/(alias|[^aou]us|t[lm]as|gas|ris)$" to "$1es",
      "/(e[mn]u)s?$" to "$1s",
      "/([^l]ias|[aeiou]las|[ejzr]as|[iu]am)$" to "$1",
      "/(alumn|syllab|octop|vir|radi|nucle|fung|cact|stimul|termin|bacill|foc|uter|loc|strat)(?:us|i)$" to "$1i",
      "/(alumn|alg|vertebr)(?:a|ae)$" to "$1ae",
      "/(seraph|cherub)(?:im)?$" to "$1im",
      "/(her|at|gr)o$" to "$1oes",

      "/(agend|addend|millenni|medi|dat|extrem|bacteri|desiderat|strat|candelabr|errat|ov|symposi|curricul|automat|quor)(?:a|um)$" to
        "$1a",
      "/(apheli|hyperbat|periheli|asyndet|noumen|phenomen|criteri|organ|prolegomen|hedr|automat)(?:a|on)$" to "$1a",
      "/sis$" to "ses",
      "/(?:(kni|wi|li)fe|(ar|l|ea|eo|oa|hoo)f)$" to "$1$2ves",
      "/([^aeiouy]|qu)y$" to "$1ies",
      "/([^ch][ieo][ln])ey$" to "$1ies",
      "/(x|ch|ss|sh|zz)$" to "$1es",
      "/(matr|cod|mur|sil|vert|ind|append)(?:ix|ex)$" to "$1ices",
      "(m|l)(?:ice|ouse)" to "$1ice",
      "/(pe)(?:rson|ople)$" to "$1ople",
      "/(child)(?:ren)?$" to "$1ren",
      "/eaux$" to "$0",
      "/m[ae]n$" to "men",
    ).forEach { o ->
      addPluralRule(o.first, o.second)
    }

    /*
     * Singularization rules.
     */
    listOf(
      "/(.)s$" to "$1",
      "/([^aeiou]s)es$" to "$1",
      "/(wi|kni|(?:after|half|high|low|mid|non|night|[^\\w]|^)li)ves$" to "$1fe",
      "/(ar|(?:wo|[ae])l|[eo][ao])ves$" to "$1f",
      "/ies$" to "y",

      "/\\b([pl]|zomb|(?:neck|cross)?t|coll|faer|food|gen|goon|group|lass|talk|goal|cut)ies$" to "$1ie",
      "/\\b(mon|smil)ies$" to "$1ey",
      "(m|l)ice" to "$1ouse",
      "/(seraph|cherub)im$" to "$1",

      "/.(x|ch|ss|sh|zz|tto|go|cho|alias|[^aou]us|t[lm]as|gas|(?:her|at|gr)o|ris)(?:es)?$" to "$1",
      "/(analy|^ba|diagno|parenthe|progno|synop|the|empha|cri)(?:sis|ses)$" to "$1sis",
      "/(movie|twelve|abuse|e[mn]u)s$" to "$1",
      "/(test)(?:is|es)$" to "$1is",
      "/(x|ch|.ss|sh|zz|tto|go|cho|alias|[^aou]us|tlas|gas|(?:her|at|gr)o|ris)(?:es)?$" to "$1",
      "/(e[mn]u)s?$" to "$1",
      "/(cookie|movie|twelve)s$" to "$1",
      "/(cris|test|diagnos)(?:is|es)$" to "$1is",

      "/(alumn|syllab|octop|vir|radi|nucle|fung|cact|stimul|termin|bacill|foc|uter|loc|strat)(?:us|i)$" to
        "$1us",

      "/(agend|addend|millenni|dat|extrem|bacteri|desiderat|strat|candelabr|errat|ov|symposi|curricul|quor)a$" to
        "$1um",

      "/(apheli|hyperbat|periheli|asyndet|noumen|phenomen|criteri|organ|prolegomen|hedr|automat)a$" to
        "$1on",
      "/(alumn|alg|vertebr)ae$" to "$1a",
      "/(cod|mur|sil|vert|ind)ices$" to "$1ex",
      "/(matr|append)ices$" to "$1ix",
      "/(pe)(rson|ople)$" to "$1rson",
      "/(child)ren$" to "$1",
      "/(eau)x?$" to "$1",
      "/men$" to "man"
    ).forEach { o ->
      addSingularRule(o.first, o.second)
    }
    /*
     * Uncountable rules.
     */
    listOf( // Singular words with no plurals.
      "adulthood",
      "advice",
      "agenda",
      "aid",
      "alcohol",
      "ammo",
      "anime",
      "athletics",
      "audio",
      "bison",
      "blood",
      "bream",
      "buffalo",
      "butter",
      "carp",
      "cash",
      "chassis",
      "chess",
      "clothing",
      "cod",
      "commerce",
      "cooperation",
      "corps",
      "debris",
      "diabetes",
      "digestion",
      "elk",
      "energy",
      "equipment",
      "excretion",
      "expertise",
      "flounder",
      "fun",
      "gallows",
      "garbage",
      "graffiti",
      "headquarters",
      "health",
      "herpes",
      "highjinks",
      "homework",
      "housework",
      "information",
      "jeans",
      "justice",
      "kudos",
      "labour",
      "literature",
      "machinery",
      "mackerel",
      "mail",
      "media",
      "mews",
      "moose",
      "music",
      "news",
      "pike",
      "plankton",
      "pliers",
      "police",
      "pollution",
      "premises",
      "rain",
      "research",
      "rice",
      "salmon",
      "scissors",
      "series",
      "sewage",
      "shambles",
      "shrimp",
      "species",
      "staff",
      "swine",
      "tennis",
      "traffic",
      "transportation",
      "trout",
      "tuna",
      "wealth",
      "welfare",
      "whiting",
      "wildebeest",
      "wildlife",
      "you",  // Regexes.
      "/[^aeiou]ese$/i",  // "chinese", "japanese"
      "/deer$",  // "deer", "reindeer"
      "/fish$",  // "fish", "blowfish", "angelfish"
      "/measles$",
      "/o[iu]s$",  // "carnivorous"
      "/pox$",  // "chickpox", "smallpox"
      "/sheep$"
    ).forEach { s ->
      addUncountableRule(s)
    }
  }
}

fun String.pluralize(): String {
  val pluralized = Pluralizer.plural(this)
  return when {
    pluralized != null -> pluralized
    endsWith("s") -> Pluralizer.restoreCase(this, this + "es")!!
    else -> Pluralizer.restoreCase(this, this + "s")!!
  }
}

fun String.unpluralize(): String? {
  val singularized = Pluralizer.singular(this)
  return when {
    singularized != null -> singularized
    endsWith("es", ignoreCase = true) -> this.substring(0, this.length - 2).let { if (it.isEmpty()) null else it }
    endsWith("s", ignoreCase = true) -> this.substring(0, this.length - 1).let { if (it.isEmpty()) null else it }
    else -> null
  }
}

fun String.pluralizeIf(condition: Boolean): String = letIf(condition) { pluralize() }
