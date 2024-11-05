package com.intellij.markdown.utils.doc.impl

import org.intellij.markdown.ast.ASTNode
import org.intellij.markdown.html.HtmlGenerator
import org.intellij.markdown.html.LinkGeneratingProvider

private val UNSAFE_LINK_REGEX = Regex("^(vbscript|javascript|file|data):", RegexOption.IGNORE_CASE)
/* We need to support svg for documentation rendering */
private val ALLOWED_DATA_LINK_REGEX = Regex("^data:image/(gif|png|jpeg|webp|svg)[+;=a-z0-9A-Z]*,", RegexOption.IGNORE_CASE)

fun makeXssSafeDestination(s: CharSequence): CharSequence = s.takeIf {
  !UNSAFE_LINK_REGEX.containsMatchIn(s.trim()) || ALLOWED_DATA_LINK_REGEX.containsMatchIn(s.trim())
} ?: "#"

fun LinkGeneratingProvider.makeXssSafe(useSafeLinks: Boolean = true): LinkGeneratingProvider {
  if (!useSafeLinks) return this

  return object : LinkGeneratingProvider(baseURI, resolveAnchors) {
    override fun renderLink(
      visitor: HtmlGenerator.HtmlGeneratingVisitor,
      text: String,
      node: ASTNode,
      info: RenderInfo
    ) {
      this@makeXssSafe.renderLink(visitor, text, node, info)
    }

    override fun getRenderInfo(text: String, node: ASTNode): RenderInfo? {
      return this@makeXssSafe.getRenderInfo(text, node)?.let {
        it.copy(destination = makeXssSafeDestination(it.destination))
      }
    }
  }
}