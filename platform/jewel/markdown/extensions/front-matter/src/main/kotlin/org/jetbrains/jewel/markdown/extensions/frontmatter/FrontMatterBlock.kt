package org.jetbrains.jewel.markdown.extensions.frontmatter

import org.commonmark.node.CustomBlock

internal class FrontMatterBlock : CustomBlock() {
    /**
     * Whether a closing `---` delimiter was seen. Front matter is only rendered when its opening delimiter is matched
     * by a closing one; otherwise the collected content is not treated as front matter.
     */
    var sawClosingDelimiter: Boolean = false
}
