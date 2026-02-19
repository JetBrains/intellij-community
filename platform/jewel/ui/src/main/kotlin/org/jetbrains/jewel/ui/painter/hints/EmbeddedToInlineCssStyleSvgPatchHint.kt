// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.ui.painter.hints

import androidx.compose.runtime.Immutable
import javax.xml.xpath.XPathConstants
import javax.xml.xpath.XPathFactory
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.jewel.foundation.ExperimentalJewelApi
import org.jetbrains.jewel.ui.painter.PainterProviderScope
import org.jetbrains.jewel.ui.painter.PainterSvgPatchHint
import org.w3c.dom.Element
import org.w3c.dom.NodeList

/**
 * SVG patch hint that converts embedded CSS styles to inline style attributes.
 *
 * This hint transforms CSS rules defined in `<style>` blocks into inline `style` attributes on elements, then removes
 * the `<style>` blocks.
 *
 * ## Supported Features
 * - Simple class selectors (`.st0`, `.cls-1`)
 * - Multiple selectors per rule (`.st0, .st1 { ... }`)
 * - Multiple classes per element (`class="st0 st9"`)
 * - Minified CSS (no whitespace)
 * - CSS comments (`/* ... */`)
 * - CDATA sections (`<![CDATA[...]]>`)
 * - Inline style preservation (inline styles take precedence over class styles)
 * - URL references (`fill:url(#gradient)`)
 *
 * ## CSS Cascade Rules
 * When multiple styles apply to the same property, the following precedence is used:
 * 1. Inline styles (highest priority)
 * 2. Later classes override earlier classes
 * 3. Non-conflicting properties are merged
 *
 * ## Limitations
 * - Only class selectors (`.class`) are supported
 * - ID selectors (`#id`), element selectors (`circle`), and attribute selectors (`[attr]`) are ignored
 * - `!important` is ignored
 * - Data URLs with embedded semicolons are not supported
 */
@ExperimentalJewelApi
@ApiStatus.Experimental
@Immutable
public object EmbeddedToInlineCssStyleSvgPatchHint : PainterSvgPatchHint {

    public override fun PainterProviderScope.patch(element: Element) {
        // Early exit if no style elements
        val styleElements = element.getElementsByTagName("style")
        if (styleElements.length == 0) return

        val parser = CssParser()

        // Parse all CSS rules into the cache
        val cache = CssClassAttributesCache.from(styleElements, parser)

        if (cache.isEmpty()) {
            // If cache is empty, this means we have an empty style dom element
            element.removeAllStyleElements()
            element.removeOrphanedClassAttributes()
            return
        }

        // Inline styles into elements with class attributes
        element.inlineStyleDeclarations(cache, parser)
    }

    /**
     * Finds all elements with class attributes and inlines their CSS styles. Uses XPath for efficient element
     * selection.
     *
     * This function processes each element by:
     * 1. Parsing class attribute into individual class names
     * 2. Merging CSS rules from all classes (the later classes override earlier ones)
     * 3. Merging with existing inline styles (inline styles take precedence)
     * 4. Setting the final merged style attribute
     * 5. Removing the class attribute
     *
     * CSS cascade specificity rules:
     * - Multiple classes: Later classes override earlier classes for conflicting properties
     * - Inline styles: Always override class styles (highest specificity)
     * - Non-conflicting properties: All properties are preserved and merged
     *
     * ### Single class conversion
     *
     * ```xml
     * <!-- Input -->
     * <circle class="st0" cx="10"/>
     *
     * <!-- CSS -->
     * <style>.st0 { fill: red; opacity: 0.5; }</style>
     *
     * <!-- Output -->
     * <circle style="fill:red;opacity:0.5" cx="10"/>
     * ```
     *
     * ### Multiple classes with override
     *
     * ```xml
     * <!-- Input -->
     * <path class="st0 st9"/>
     *
     * <!-- CSS -->
     * <style>
     *   .st0 { fill: red; opacity: 0.5; }
     *   .st9 { opacity: 0.8; }
     * </style>
     *
     * <!-- Output -->
     * <path style="fill:red;opacity:0.8"/>
     * <!-- Note: st9's opacity overrides st0's opacity -->
     * ```
     *
     * ### Inline styles take precedence
     *
     * ```xml
     * <!-- Input -->
     * <circle class="st0" style="opacity: 0.9"/>
     *
     * <!-- CSS -->
     * <style>.st0 { fill: red; opacity: 0.5; }</style>
     *
     * <!-- Output -->
     * <circle style="fill:red;opacity:0.9"/>
     * <!-- Note: inline opacity overrides class opacity -->
     * ```
     *
     * @param cache Pre-built cache containing CSS class-to-rule mappings
     */
    private fun Element.inlineStyleDeclarations(cache: CssClassAttributesCache, parser: CssParser) {
        val classAttributeName = "class"
        val styleAttributeName = "style"

        // Use XPath to efficiently find only elements with a class attribute
        for (element in getElementsWithAttribute(classAttributeName)) {
            if (!element.hasAttribute(classAttributeName)) continue

            val classAttribute = element.getAttribute(classAttributeName)
            if (classAttribute.isBlank()) {
                element.removeAttribute(classAttributeName)
                continue
            }

            // Handle multiple classes: "st0 st9" or single class: "st0"
            val classNames = classAttribute.trim().split(Regex("\\s+"))
            val classStyleProperties = mergeStylePropertiesFromCssClasses(classNames, cache)

            if (classStyleProperties.isEmpty()) {
                // No styles found, just remove the class attribute
                element.removeAttribute(classAttributeName)
                continue
            }

            // Merge with existing inline styles (inline takes precedence)
            val existingDomStyleProperties = mutableMapOf<String, String>()
            val existingDomStyle = element.getAttribute(styleAttributeName)
            if (existingDomStyle.isNotEmpty()) {
                existingDomStyleProperties.putAll(parser.parseInlineStyle(existingDomStyle))
            }

            // Set the merged styles
            val classStyleValue = classStyleProperties.entries.joinToString(";") { "${it.key}:${it.value}" }
            val existingDomStyleValue = existingDomStyleProperties.entries.joinToString(";") { "${it.key}:${it.value}" }
            val styleValue = listOf(classStyleValue, existingDomStyleValue).filter { it.isNotBlank() }.joinToString(";")

            element.setAttribute(styleAttributeName, styleValue)

            // Remove class attribute after inlining
            element.removeAttribute(classAttributeName)
        }

        // Remove all <style> elements from the document
        removeAllStyleElements()
    }

    private fun mergeStylePropertiesFromCssClasses(
        classNames: List<String>,
        cache: CssClassAttributesCache,
    ): MutableMap<String, String> {
        // Resolve and merge styles from all classes
        val mergedStyles = mutableMapOf<String, String>()
        for (className in classNames) {
            val classStyleProperties: Map<String, String>? = cache.getCssPropertiesFor(className)
            if (classStyleProperties != null) {
                // Later classes override earlier ones
                mergedStyles.putAll(classStyleProperties)
            }
        }
        return mergedStyles
    }

    /** Uses XPath to efficiently query elements with a specific attribute. */
    private fun Element.getElementsWithAttribute(attributeName: String): List<Element> {
        val xPath = XPathFactory.newInstance().newXPath()

        val eligibleNodes = xPath.evaluate("//*[@$attributeName]", this, XPathConstants.NODESET) as NodeList

        return buildList {
            for (i in 0 until eligibleNodes.length) {
                eligibleNodes.item(i).let { node -> if (node is Element) add(node) }
            }
        }
    }

    /** Removes all <style> elements from the document. */
    private fun Element.removeAllStyleElements() {
        val styleElements = getElementsByTagName("style")
        // Remove in reverse to avoid index shifting issues
        for (i in styleElements.length - 1 downTo 0) {
            val styleElement = styleElements.item(i)
            styleElement.parentNode?.removeChild(styleElement)
        }
    }

    private fun Element.removeOrphanedClassAttributes() {
        val classAttributeName = "class"
        for (element in getElementsWithAttribute(classAttributeName)) {
            if (!element.hasAttribute(classAttributeName)) continue
            element.removeAttribute(classAttributeName)
        }
    }
}

/**
 * CSS rule representation.
 *
 * @property selector The CSS selector (e.g., ".st0")
 * @property properties Map of CSS property names to values (e.g., {"fill": "red", "opacity": "0.5"})
 */
private data class CssRule(val selector: String, val properties: Map<String, String>)

/**
 * Focused CSS parser for SVG styles. Supports simple class selectors as exported by Adobe Illustrator and similar
 * tools.
 */
private class CssParser {

    companion object {
        // Pre-compiled regex patterns for performance
        private val COMMENT_PATTERN = Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL)

        private val RULE_PATTERN =
            Regex("""([^{]+)\{([^}]+)\}""", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.MULTILINE))

        private val PROPERTY_PATTERN = Regex("""([^:]+):([^;]+);?""")

        // Matches simple class selectors: .st0, .cls-1, .my-class
        private val CLASS_SELECTOR_PATTERN = Regex("""^\.[a-zA-Z0-9_-]+$""")
    }

    /**
     * Parses CSS text and returns a map of class selectors to rules.
     *
     * Supports:
     * - Single class selectors: .st0 { ... }
     * - Multiple selectors: .st0, .st1 { ... }
     * - CSS comments: /* ... */
     * - Minified CSS
     *
     * @param cssText Raw CSS text from <style> element
     * @return Map of selector string (e.g., ".st0") to CssRule
     */
    fun parseCssBlock(cssText: String): Map<String, CssRule> {
        // Step 1: Normalize CSS text
        val normalizedCssText = cssText.removeComments().removeCDATAWrapper()
        if (normalizedCssText.isBlank()) return emptyMap()

        val rules = mutableMapOf<String, CssRule>()

        // Step 2: Find all CSS rules
        RULE_PATTERN.findAll(normalizedCssText).forEach { match ->
            val (selectorPart, propertiesPart) = match.destructured

            // Step 3: Split by comma for multiple selectors
            // Example: ".st0, .st1 { ... }" -> [".st0", ".st1"]
            val selectors = selectorPart.split(',').map { it.trim() }
            val properties = parseProperties(propertiesPart)

            // Step 4: Store each selector
            selectors.forEach { selector ->
                // Only process simple class selectors
                if (selector.matches(CLASS_SELECTOR_PATTERN)) {
                    rules[selector] = CssRule(selector = selector, properties = properties)
                }
            }
        }

        return rules
    }

    /**
     * Parses inline style attribute into a property map. Example: "fill:red;opacity:0.5" -> {"fill": "red", "opacity":
     * "0.5"}
     */
    fun parseInlineStyle(style: String): Map<String, String> =
        style
            .split(';')
            .mapNotNull { declaration ->
                val parts = declaration.split(':', limit = 2)
                if (parts.size != 2) return@mapNotNull null

                val name = parts[0].trim()
                val value = parts[1].trim()
                if (name.isNotEmpty() && value.isNotEmpty()) {
                    name to value
                } else {
                    null
                }
            }
            .toMap()

    /**
     * Removes CSS comments from the string.
     *
     * This function identifies and removes all occurrences of CSS comments (e.g., `
     */
    private fun String.removeComments(): String = replace(COMMENT_PATTERN, "")

    /**
     * Removes CDATA section markers (`<![CDATA[` and `]]>`) from the beginning and end of the string, if present, and
     * trims any extraneous whitespace from the resulting string.
     *
     * @return A new string with CDATA markers removed and whitespace trimmed.
     */
    private fun String.removeCDATAWrapper(): String =
        replace(Regex("^\\s*<!\\[CDATA\\["), "").replace(Regex("]]>\\s*$"), "").trim()

    /** Parses CSS property declarations. Example: "fill:red;opacity:0.5" -> {"fill": "red", "opacity": "0.5"} */
    private fun parseProperties(propertiesText: String): Map<String, String> {
        val properties = mutableMapOf<String, String>()

        PROPERTY_PATTERN.findAll(propertiesText).forEach { match ->
            val (name, value) = match.destructured
            val normalizedName = name.trim()
            val normalizedValue = value.trim()

            if (normalizedName.isNotEmpty() && normalizedValue.isNotEmpty()) {
                properties[normalizedName] = normalizedValue
            }
        }

        return properties
    }
}

/** Cache for storing CSS class name to CssRule mapping. Responsible for building itself from style elements. */
private class CssClassAttributesCache private constructor(private val cache: Map<String, CssRule>) {
    /**
     * Gets the CSS rule for a given class name.
     *
     * @param className Class name without the dot (e.g., "st0", not ".st0")
     * @return CssRule if found, null otherwise
     */
    fun getCssRuleFor(className: String): CssRule? = cache[className]

    /**
     * Gets the CSS properties for a given class name.
     *
     * @param className Class name without the dot (e.g., "st0", not ".st0")
     * @return Map of properties if found, null otherwise
     */
    fun getCssPropertiesFor(className: String): Map<String, String>? = cache[className]?.properties

    fun isEmpty(): Boolean = cache.isEmpty()

    companion object {
        /**
         * Builds a cache from style elements in the document.
         *
         * @param styleElements NodeList of <style> elements
         * @param parser CssParser instance to use for parsing CSS rules.
         * @return CssClassAttributesCache populated with CSS rules
         */
        fun from(styleElements: NodeList, parser: CssParser): CssClassAttributesCache {
            val builder = CacheBuilder(parser)

            for (i in 0 until styleElements.length) {
                val styleElement = styleElements.item(i) as? Element ?: continue
                builder.addStyleElement(styleElement)
            }

            return builder.build()
        }
    }

    /**
     * Builder for constructing the cache.
     *
     * Encapsulates parsing logic.
     *
     * @param parser CssParser instance to use for parsing CSS rules.
     */
    private class CacheBuilder(private val parser: CssParser = CssParser()) {
        private val cache = mutableMapOf<String, CssRule>()

        fun addStyleElement(styleElement: Element) {
            // Only process CSS styles (type="text/css" or no type attribute)
            val type = styleElement.getAttribute("type")
            if (type.isNotEmpty() && type != "text/css") return

            val cssText = styleElement.textContent ?: return

            // Parse CSS and add to cache
            val rules = parser.parseCssBlock(cssText)
            for ((selector, rule) in rules) {
                // Extract class name from selector: ".st0" -> "st0"
                val className = selector.removePrefix(".")
                // Store the entire CssRule, not just properties
                cache[className] = rule
            }
        }

        fun build(): CssClassAttributesCache = CssClassAttributesCache(cache.toMap())
    }
}
