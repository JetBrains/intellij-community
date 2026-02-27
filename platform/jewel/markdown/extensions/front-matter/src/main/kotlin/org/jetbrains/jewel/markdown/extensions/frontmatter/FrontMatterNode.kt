package org.jetbrains.jewel.markdown.extensions.frontmatter

import org.commonmark.node.CustomNode

internal class FrontMatterNode(val key: String, val values: List<String>) : CustomNode()
