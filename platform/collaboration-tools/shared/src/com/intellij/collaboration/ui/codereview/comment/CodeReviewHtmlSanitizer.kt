// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.ui.codereview.comment

import com.intellij.openapi.util.NlsSafe
import org.jsoup.Jsoup

/**
 * Makes the HTML of a review text safe to show in a Swing HTML pane.
 */
object CodeReviewHtmlSanitizer {
  private const val RESOURCE_ELEMENTS_QUERY =
    "style, link, object, applet, embed, iframe, frame, frameset, base, meta, script, isindex, input:not([type=checkbox])"

  private val SAFE_IMAGE_SCHEMES = setOf("http", "https", "data")
  private val SAFE_LINK_SCHEMES = setOf("http", "https", "mailto")
  private val URL_SCHEME_REGEX = Regex("""^([a-z][a-z0-9+.\-]*):""")
  private val NETWORK_PATH_REGEX = Regex("""^[/\\]{2}""")

  // No parentheses, quotes, slashes, backslashes or at signs: such a style cannot hold a URL, an escape, a comment or an import.
  private val SAFE_STYLE_REGEX = Regex("""[\w\s:;#.,%\-]*""")

  /**
   * Removes the HTML that makes Swing load a resource when it renders the [html], and the links to unsafe targets.
   * Use it for the HTML of a text that the review host does not sanitize, such as a comment converted from Markdown.
   *
   * The function removes these items:
   * - the elements that load a URL, such as `style`, `link`, `object` and `iframe`;
   * - the elements that open a URL in the pane when the user sends a form: `isindex` and all `input` elements except the checkboxes;
   * - the `img` elements with a `src` that is a network path or has a scheme other than `http`, `https` or `data`;
   * - the `background` attributes;
   * - the `style` attributes with a character other than a letter, a digit, a space, or one of `:;#.,%-_`.
   *
   * The function keeps the text of a link and removes the link itself when the `href` has one of these targets:
   * - a scheme other than `http`, `https`, `mailto` or one of the [customLinkSchemes];
   * - a network path, such as `//host/share` or `\\host\share`;
   * - one of the [customLinkSchemes] and then a network path, such as `glfilelink://host/share`.
   *
   * The function keeps the other markup, so the `color` styles of the code highlighting stay.
   *
   * @param customLinkSchemes the schemes of the links that the caller handles itself, such as `glmergerequest`
   */
  fun sanitize(html: @NlsSafe String, customLinkSchemes: Set<String> = emptySet()): @NlsSafe String {
    if (html.isBlank()) return html

    val document = Jsoup.parse(html)
    document.select(RESOURCE_ELEMENTS_QUERY).remove()
    document.select("img").forEach { image ->
      if (!isSafeImageUrl(image.attr("src"))) {
        image.remove()
      }
    }
    val lowercaseLinkSchemes = customLinkSchemes.mapTo(HashSet()) { it.lowercase() }
    document.select("a[href], area[href]").forEach { link ->
      if (!isSafeLink(link.attr("href"), lowercaseLinkSchemes)) {
        link.unwrap()
      }
    }
    document.select("[background]").removeAttr("background")
    document.select("[style]").forEach { element ->
      if (!SAFE_STYLE_REGEX.matches(element.attr("style"))) {
        element.removeAttr("style")
      }
    }

    document.outputSettings().prettyPrint(false)
    val body = document.body()
    return if (html.trimStart().startsWith("<body", ignoreCase = true)) body.outerHtml() else body.html()
  }

  private fun isSafeImageUrl(url: String): Boolean {
    if (isNetworkPath(url)) return false
    val scheme = urlScheme(url) ?: return true
    return scheme in SAFE_IMAGE_SCHEMES
  }

  private fun isSafeLink(url: String, customLinkSchemes: Set<String>): Boolean {
    if (isNetworkPath(url)) return false
    val scheme = urlScheme(url) ?: return true
    return when (scheme) {
      in SAFE_LINK_SCHEMES -> true
      in customLinkSchemes -> !isNetworkPath(url.trimUrl().substring(scheme.length + 1))
      else -> false
    }
  }

  private fun isNetworkPath(url: String): Boolean = NETWORK_PATH_REGEX.containsMatchIn(url.trimUrl())

  private fun urlScheme(url: String): String? = URL_SCHEME_REGEX.find(url.trimUrl().lowercase())?.groupValues?.get(1)

  // java.net.URL also ignores the leading control characters
  private fun String.trimUrl(): String = trim { it <= ' ' }
}
