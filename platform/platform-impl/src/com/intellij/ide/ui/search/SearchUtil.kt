// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.ui.search

import com.intellij.BundleBase
import com.intellij.ide.ui.search.SearchUtil.ADDITIONAL_SEARCH_LABELS_KEY
import com.intellij.ide.ui.search.SearchUtil.SEARCH_SKIP_COMPONENT_KEY
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.ConfigurableGroup
import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.NlsSafe
import com.intellij.ui.*
import com.intellij.ui.SimpleTextAttributes.StyleAttributeConstant
import com.intellij.ui.TabbedPaneWrapper.TabbedPaneHolder
import com.intellij.util.IntPair
import it.unimi.dsi.fastutil.ints.Int2ObjectMaps
import it.unimi.dsi.fastutil.ints.Int2ObjectRBTreeMap
import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus.Internal
import java.awt.Color
import java.awt.Component
import java.util.regex.Pattern
import javax.swing.*
import javax.swing.border.TitledBorder
import javax.swing.plaf.basic.BasicComboPopup
import javax.swing.text.BadLocationException
import javax.swing.text.View

private val LOG = logger<SearchUtil>()

object SearchUtil {
  @JvmField
  @Internal
  val ADDITIONAL_SEARCH_LABELS_KEY: Key<List<String>> = Key.create("ADDITIONAL_SEARCH_LABELS")

  @JvmField
  @Internal
  val SEARCH_SKIP_COMPONENT_KEY: Key<Boolean> = Key.create("SEARCH_SKIP_COMPONENT_KEY")

  const val HIGHLIGHT_WITH_BORDER: String = "searchUtil.highlightWithBorder"
  private const val STYLE_END = "</style>"

  @JvmStatic
  fun processComponent(
    configurable: SearchableConfigurable,
    configurableOptions: MutableSet<OptionDescription>,
    component: JComponent?,
    i18n: Boolean,
  ) {
    if (component != null) {
      processUiLabel(title = configurable.displayName, configurableOptions = configurableOptions, path = null, i18n = i18n, rawList = null)
      collectSearchItemsForComponent(
        component = component,
        configurableOptions = configurableOptions,
        path = null,
        i18n = i18n,
        rawList = null,
      )
    }
  }

  @Internal
  fun lightOptions(configurable: SearchableConfigurable, component: JComponent, option: String?) {
    if (!traverseComponentsTree(configurable = configurable, rootComponent = component, option = option, force = true)) {
      traverseComponentsTree(configurable = configurable, rootComponent = component, option = option, force = false)
    }
  }

  private fun getSelection(tabIndex: String, tabCount: Int, titleGetter: (Int) -> String?): Int {
    val searchableOptionsRegistrar = SearchableOptionsRegistrar.getInstance()
    for (i in 0 until tabCount) {
      val pathWords = searchableOptionsRegistrar.getProcessedWords(tabIndex)
      val title = titleGetter(i) ?: ""
      if (!pathWords.isEmpty()) {
        val titleWords = searchableOptionsRegistrar.getProcessedWords(title)
        pathWords.removeAll(titleWords)
        if (pathWords.isEmpty()) {
          return i
        }
      }
      else if (tabIndex.equals(title, ignoreCase = true)) { //e.g., only stop words
        return i
      }
    }
    return -1
  }

  private fun traverseComponentsTree(
    configurable: SearchableConfigurable,
    rootComponent: JComponent,
    option: String?,
    force: Boolean,
  ): Boolean {
    rootComponent.putClientProperty(HIGHLIGHT_WITH_BORDER, null)

    if (option.isNullOrBlank()) {
      return false
    }

    val searchableOptionsRegistrar = SearchableOptionsRegistrar.getInstance()
    val label = getLabelsFromComponent(rootComponent)
    if (!label.isEmpty()) {
      for (each in label) {
        if (isComponentHighlighted(
            text = each,
            option = option,
            force = force,
            configurable = configurable,
            searchableOptionsRegistrar = searchableOptionsRegistrar,
          )) {
          highlightComponent(rootComponent, option)
          // do not visit children of a highlighted component
          return true
        }
      }
    }
    else if (rootComponent is JComboBox<*>) {
      val labels = getItemsFromComboBox(rootComponent)
      if (labels.any {
        isComponentHighlighted(
          text = it,
          option = option,
          force = force,
          configurable = configurable,
          searchableOptionsRegistrar = searchableOptionsRegistrar,
        )
      }) {
        highlightComponent(rootComponent, option)
        // do not visit children of a highlighted component
        return true
      }
    }
    else if (rootComponent is JTabbedPane) {
      val paths = SearchableOptionsRegistrar.getInstance().getInnerPaths(configurable, option)
      for (path in paths) {
        val index = getSelection(path, rootComponent.tabCount) { i ->
          rootComponent.getTitleAt(i)
        }
        if (index > -1 && index < rootComponent.tabCount) {
          if (rootComponent.getTabComponentAt(index) is JComponent) {
            highlightComponent(rootComponent.getTabComponentAt(index) as JComponent, option)
          }
        }
      }
    }
    else if (rootComponent is TabbedPaneHolder) {
      val tabbedPaneWrapper = rootComponent.tabbedPaneWrapper
      val paths = SearchableOptionsRegistrar.getInstance().getInnerPaths(configurable, option)
      for (path in paths) {
        val index = getSelection(path, tabbedPaneWrapper.tabCount) { i ->
          tabbedPaneWrapper.getTitleAt(i)
        }
        if (index > -1 && index < tabbedPaneWrapper.tabCount) {
          highlightComponent(tabbedPaneWrapper.getTabComponentAt(index) as JComponent, option)
        }
      }
    }

    val border = rootComponent.border
    if (border is TitledBorder) {
      val title = border.title
      if (isComponentHighlighted(
          text = title,
          option = option,
          force = force,
          configurable = configurable,
          searchableOptionsRegistrar = searchableOptionsRegistrar,
        )) {
        highlightComponent(rootComponent, option)
        rootComponent.putClientProperty(HIGHLIGHT_WITH_BORDER, true)
        // do not visit children of a highlighted component
        return true
      }
    }
    return rootComponent.components.any {
      it is JComponent && traverseComponentsTree(configurable = configurable, rootComponent = it, option = option, force = force)
    }
  }

  @Internal
  fun isComponentHighlighted(
    text: String?,
    option: String?,
    force: Boolean,
    configurable: SearchableConfigurable?,
    searchableOptionsRegistrar: SearchableOptionsRegistrar,
  ): Boolean {
    if (text == null || option.isNullOrEmpty()) {
      return false
    }

    val words = searchableOptionsRegistrar.getProcessedWords(option)
    val options = if (configurable == null) words else searchableOptionsRegistrar.replaceSynonyms(words, configurable)
    if (options.isEmpty()) {
      return text.contains(option, ignoreCase = true)
    }

    val tokens = searchableOptionsRegistrar.getProcessedWords(text)
    if (!force) {
      options.retainAll(tokens)
      val highlight = !options.isEmpty()
      return highlight || text.contains(option, ignoreCase = true)
    }
    else {
      options.removeAll(tokens)
      return options.isEmpty()
    }
  }

  @JvmStatic
  fun markup(textToMarkup: String, filter: String?): String {
    return markup(textToMarkup, filter, JBColor(Gray._50, Gray._0),
                  JBColor.namedColor("SearchMatch.startBackground", ColorUtil.fromHex("1d5da7")))
  }

  @JvmStatic
  fun markup(textToMarkup: String, filter: String?, textColor: Color, backgroundColor: Color): String {
    @Suppress("NAME_SHADOWING")
    var textToMarkup = textToMarkup

    @Suppress("NAME_SHADOWING")
    var filter = filter
    if (filter.isNullOrEmpty()) {
      return textToMarkup
    }
    var bodyStart = textToMarkup.indexOf("<body>")
    val bodyEnd = textToMarkup.indexOf("</body>")
    val head: String
    val foot: String
    if (bodyStart >= 0) {
      bodyStart += "<body>".length
      head = textToMarkup.substring(0, bodyStart)
      foot = if (bodyEnd >= 0) {
        textToMarkup.substring(bodyEnd)
      }
      else {
        ""
      }
      textToMarkup = textToMarkup.substring(bodyStart, bodyEnd)
    }
    else {
      foot = ""
      head = ""
    }
    val insideHtmlTagPattern = Pattern.compile("[<[^<>]*>]*<[^<>]*")
    val registrar = SearchableOptionsRegistrar.getInstance()
    val quoted = HashSet<String>()
    filter = processFilter(quoteStrictOccurrences(textToMarkup, filter), quoted)
    val options = registrar.getProcessedWords(filter)
    val words = registrar.getProcessedWords(textToMarkup)
    for (option in options) {
      if (words.contains(option)) {
        textToMarkup = markup(textToMarkup, insideHtmlTagPattern, option, textColor, backgroundColor)
      }
    }
    for (stripped in quoted) {
      if (registrar.isStopWord(stripped)) {
        continue
      }
      textToMarkup = markup(textToMarkup, insideHtmlTagPattern, stripped, textColor, backgroundColor)
    }
    return head + textToMarkup + foot
  }

  private fun quoteStrictOccurrences(textToMarkup: String, filter: String): String {
    val cur = StringBuilder()
    val s = textToMarkup.lowercase()
    for (part in filter.split(' ').dropLastWhile { it.isEmpty() }) {
      if (s.contains(part)) {
        cur.append('"').append(part).append("\" ")
      }
      else {
        cur.append(part).append(' ')
      }
    }
    return cur.toString()
  }

  private fun markup(
    textToMarkup: String,
    insideHtmlTagPattern: Pattern,
    option: String,
    textColor: Color,
    backgroundColor: Color,
  ): String {
    val styleIdx = textToMarkup.indexOf("<style")
    val styleEndIdx = textToMarkup.indexOf("</style>")
    if (styleIdx < 0 || styleEndIdx < 0) {
      return markupInText(textToMarkup, insideHtmlTagPattern, option, textColor, backgroundColor)
    }
    return markup(textToMarkup.substring(0, styleIdx), insideHtmlTagPattern, option, textColor, backgroundColor) +
           markup(textToMarkup.substring(styleEndIdx + STYLE_END.length), insideHtmlTagPattern, option, textColor, backgroundColor)
  }

  private fun markupInText(
    textToMarkup: String,
    insideHtmlTagPattern: Pattern,
    option: String,
    textColor: Color,
    backgroundColor: Color,
  ): String {
    val result = StringBuilder()
    var beg = 0
    var idx: Int
    while ((textToMarkup.indexOf(option, beg, ignoreCase = true).also { idx = it }) != -1) {
      val prefix = textToMarkup.substring(beg, idx)
      val toMark = textToMarkup.substring(idx, idx + option.length)
      if (insideHtmlTagPattern.matcher(prefix).matches()) {
        val lastIdx = textToMarkup.indexOf('>', idx)
        result.append(prefix).append(textToMarkup, idx, lastIdx + 1)
        beg = lastIdx + 1
      }
      else {
        result.append(prefix)
          .append("<font color='#").append(ColorUtil.toHex(textColor)).append("' bgColor='#").append(
            ColorUtil.toHex(backgroundColor)).append("'>")
          .append(toMark).append("</font>")
        beg = idx + option.length
      }
    }
    result.append(textToMarkup.substring(beg))
    return result.toString()
  }

  fun appendRangedFragments(
    filter: String?,
    text: @NlsSafe String,
    matchingRanges: Array<IntPair>,
    @StyleAttributeConstant style: Int,
    foreground: Color?,
    background: Color?,
    textRenderer: SimpleColoredComponent,
  ) {
    if (matchingRanges.isEmpty()) {
      appendFragments(
        filter = filter,
        text = text,
        style = style,
        foreground = foreground,
        background = background,
        textRenderer = textRenderer,
      )
    }

    if (filter.isNullOrEmpty()) {
      textRenderer.setDynamicSearchMatchHighlighting(false)
      textRenderer.append(text, SimpleTextAttributes(background, foreground, JBColor.RED, style))
      return
    }

    textRenderer.setDynamicSearchMatchHighlighting(true)
    var index = 0
    for (range in matchingRanges) {
      val before: @NlsSafe String = text.substring(index, range.first)
      if (!before.isEmpty()) {
        textRenderer.append(before, SimpleTextAttributes(background, foreground, null, style))
      }
      index = range.second
      textRenderer.append(text.substring(range.first, range.second), SimpleTextAttributes(background,
                                                                                          foreground, null,
                                                                                          style or SimpleTextAttributes.STYLE_SEARCH_MATCH))
    }
    val after: @NlsSafe String = text.substring(index)
    if (!after.isEmpty()) {
      textRenderer.append(after, SimpleTextAttributes(background, foreground, null, style))
    }
  }

  @JvmStatic
  fun appendFragments(
    filter: String?,
    text: @NlsSafe String?,
    @StyleAttributeConstant style: Int,
    foreground: Color?,
    background: Color?,
    textRenderer: SimpleColoredComponent,
  ) {
    @Suppress("NAME_SHADOWING")
    var filter = filter

    @Suppress("NAME_SHADOWING")
    var text = text ?: return

    if (filter.isNullOrEmpty()) {
      textRenderer.setDynamicSearchMatchHighlighting(false)
      textRenderer.append(text, SimpleTextAttributes(background, foreground, JBColor.RED, style))
    }
    else {
      textRenderer.setDynamicSearchMatchHighlighting(true)
      //markup
      val quoted = HashSet<String>()
      filter = processFilter(quoteStrictOccurrences(text, filter), quoted)
      val indexToString = Int2ObjectRBTreeMap<String>()
      for (stripped in quoted) {
        var beg = 0
        var idx: Int
        while ((text.indexOf(stripped, beg, ignoreCase = true).also { idx = it }) != -1) {
          indexToString.put(idx, text.substring(idx, idx + stripped.length))
          beg = idx + stripped.length
        }
      }

      val selectedWords = ArrayList<String>()
      var pos = 0
      for (entry in Int2ObjectMaps.fastIterable(indexToString)) {
        val stripped = entry.value
        val start = entry.intKey
        if (pos > start) {
          val highlighted = selectedWords[selectedWords.size - 1]
          if (highlighted.length < stripped.length) {
            selectedWords.remove(highlighted)
          }
          else {
            continue
          }
        }

        appendSelectedWords(text = text, selectedWords = selectedWords, pos = pos, end = start, filter = filter)
        selectedWords.add(stripped)
        pos = start + stripped.length
      }
      appendSelectedWords(text = text, selectedWords = selectedWords, pos = pos, end = text.length, filter = filter)

      var idx = 0
      for (word in selectedWords) {
        text = text.substring(idx)
        val before: @NlsSafe String = text.substring(0, text.indexOf(word))
        if (!before.isEmpty()) {
          textRenderer.append(before, SimpleTextAttributes(background, foreground, null, style))
        }
        idx = text.indexOf(word) + word.length
        textRenderer.append(text.substring(idx - word.length, idx), SimpleTextAttributes(background,
                                                                                         foreground, null,
                                                                                         style or SimpleTextAttributes.STYLE_SEARCH_MATCH))
      }
      val after: @NlsSafe String = text.substring(idx)
      if (!after.isEmpty()) {
        textRenderer.append(after, SimpleTextAttributes(background, foreground, null, style))
      }
    }
  }

  private fun appendSelectedWords(
    text: String,
    selectedWords: MutableList<in String>,
    pos: Int,
    end: Int,
    filter: String,
  ) {
    if (pos < end) {
      val filters = SearchableOptionsRegistrar.getInstance().getProcessedWords(filter)
      val words = text.substring(pos, end).split("[^\\pL&&[^-]]+".toRegex()).dropLastWhile { it.isEmpty() }
      for (word in words) {
        if (filters.contains(PorterStemmerUtil.stem(word.lowercase()))) {
          selectedWords.add(word)
        }
      }
    }
  }

  @JvmStatic
  fun findKeys(filter: String?, quoted: MutableSet<String>): List<Set<String>> {
    @Suppress("NAME_SHADOWING")
    val filter = processFilter(filter = (filter ?: "").lowercase(), quoted = quoted)
    val keySetList = ArrayList<Set<String>>()
    val optionsRegistrar = SearchableOptionsRegistrar.getInstance() as SearchableOptionsRegistrarImpl
    for (word in optionsRegistrar.getProcessedWords(filter)) {
      val descriptions = optionsRegistrar.findAcceptableDescriptions(word) ?: continue
      val keySet = HashSet<String>()
      for (description in descriptions) {
        description.path?.let {
          keySet.add(it)
        }
      }
      keySetList.add(keySet)
    }

    if (keySetList.isEmpty() && filter.isNotBlank()) {
      keySetList.add(setOf(filter))
    }
    return keySetList
  }

  @JvmStatic
  fun expand(groups: Array<ConfigurableGroup>): List<Configurable> {
    val result = ArrayList<Configurable>()
    for (group in groups) {
      processExpandedGroups(group, result)
    }
    return result
  }

  fun expandGroup(group: ConfigurableGroup): List<Configurable> {
    val result = ArrayList<Configurable>()
    processExpandedGroups(group = group, result)
    return result
  }
}

private val QUOTED: Pattern = Pattern.compile("\"([^\"]+)\"")

private fun processFilter(filter: String, quoted: MutableSet<String>): String {
  val withoutQuoted = StringBuilder()
  var beg = 0
  val matcher = QUOTED.matcher(filter)
  while (matcher.find()) {
    val start = matcher.start(1)
    withoutQuoted.append(" ").append(filter, beg, start)
    beg = matcher.end(1)
    val trimmed = filter.substring(start, beg).trim { it <= ' ' }
    if (!trimmed.isEmpty()) {
      quoted.add(trimmed)
    }
  }
  return withoutQuoted.toString() + " " + filter.substring(beg)
}

internal fun processExpandedGroups(group: ConfigurableGroup, result: MutableCollection<Configurable>) {
  val configurables = group.configurables
  for (configurable in configurables) {
    result.add(configurable)
  }

  for (configurable in configurables) {
    addChildren(configurable = configurable, result = result)
  }
}

private fun addChildren(configurable: Configurable, result: MutableCollection<Configurable>) {
  if (configurable is Configurable.Composite) {
    for (kid in configurable.configurables) {
      result.add(kid)
      addChildren(configurable = kid, result = result)
    }
  }
}

private fun highlightComponent(rootComponent: JComponent, searchString: String) {
  ApplicationManager.getApplication().messageBus.syncPublisher(ComponentHighlightingListener.TOPIC).highlight(rootComponent, searchString)
}

private val HTML_PATTERN: Pattern = Pattern.compile("<[^<>]*>")

@Internal
fun processUiLabel(
  title: String,
  configurableOptions: MutableSet<OptionDescription>?,
  path: String?,
  i18n: Boolean,
  rawList: MutableSet<SearchableOptionEntry>?,
) {
  if (title.isBlank()) {
    return
  }

  @Suppress("NAME_SHADOWING")
  var title = title
  val headStart = title.indexOf("<head>")
  val headEnd = if (headStart >= 0) title.indexOf("</head>") else -1
  if (headEnd > headStart) {
    title = title.substring(0, headStart) + title.substring(headEnd + "</head>".length)
  }

  title = HTML_PATTERN.matcher(title).replaceAll(" ")
  if (title.isBlank()) {
    return
  }

  if (configurableOptions == null) {
    rawList!!.add(SearchableOptionEntry(hit = title, path = path))
  }
  else {
    val words = WORD_SEPARATOR_CHARS.split(title.lowercase()).toSet()
    title = title.replace(BundleBase.MNEMONIC_STRING, "")
    title = getNonWordPattern(i18n).matcher(title).replaceAll(" ")
    for (word in words) {
      configurableOptions.add(OptionDescription(option = word, hit = title, path = path))
    }
  }
}

private fun getNonWordPattern(i18n: Boolean): Pattern {
  @Suppress("RegExpSimplifiable")
  return Pattern.compile("[" + (if (i18n) "^\\pL" else "\\W") + "&&[^\\p{Punct}\\p{Blank}]]")
}

@Internal
fun collectSearchItemsForComponent(
  component: JComponent,
  configurableOptions: MutableSet<OptionDescription>?,
  rawList: MutableSet<SearchableOptionEntry>?,
  path: String?,
  i18n: Boolean,
) {
  if (ClientProperty.isTrue(component, SEARCH_SKIP_COMPONENT_KEY)) {
    return
  }

  ClientProperty.get(component, ADDITIONAL_SEARCH_LABELS_KEY)?.let { additional ->
    for (each in additional) {
      processUiLabel(title = each, configurableOptions = configurableOptions, path = path, i18n = i18n, rawList = rawList)
    }
  }

  val border = component.border
  if (border is TitledBorder) {
    border.title?.let {
      processUiLabel(title = it, configurableOptions = configurableOptions, path = path, i18n = i18n, rawList = rawList)
    }
  }

  val label = getLabelsFromComponent(component)
  if (!label.isEmpty()) {
    for (each in label) {
      processUiLabel(title = each, configurableOptions = configurableOptions, path = path, i18n = i18n, rawList = rawList)
    }
  }
  else if (component is JComboBox<*>) {
    val labels = getItemsFromComboBox(component)
    for (each in labels) {
      processUiLabel(title = each, configurableOptions = configurableOptions, path = path, i18n = i18n, rawList = rawList)
    }
  }
  else if (component is JTabbedPane) {
    val tabCount = component.tabCount
    for (i in 0 until tabCount) {
      val title = if (path == null) component.getTitleAt(i) else path + '.' + component.getTitleAt(i)
      processUiLabel(title = title, configurableOptions = configurableOptions, path = title, i18n = i18n, rawList = rawList)
      val tabComponent = component.getComponentAt(i)
      if (tabComponent is JComponent) {
        collectSearchItemsForComponent(
          component = tabComponent,
          configurableOptions = configurableOptions,
          path = title,
          i18n = i18n,
          rawList = rawList,
        )
      }
    }
  }
  else if (component is TabbedPaneHolder) {
    val tabbedPane = component.tabbedPaneWrapper
    val tabCount = tabbedPane.tabCount
    for (i in 0 until tabCount) {
      val tabTitle = tabbedPane.getTitleAt(i)
      val title = if (path == null) tabTitle else "$path.$tabTitle"
      processUiLabel(title = title, configurableOptions = configurableOptions, path = title, i18n = i18n, rawList = rawList)
      tabbedPane.getComponentAt(i)?.let {
        collectSearchItemsForComponent(
          component = it,
          configurableOptions = configurableOptions,
          path = title,
          i18n = i18n,
          rawList = rawList,
        )
      }
    }
  }
  else {
    component.components?.let { components ->
      for (child in components) {
        if (child is JComponent) {
          collectSearchItemsForComponent(
            component = child,
            configurableOptions = configurableOptions,
            path = path,
            i18n = i18n,
            rawList = rawList,
          )
        }
      }
    }
  }
}

/**
 * This method tries to extract a user-visible text (as opposed to an HTML markup string) from a Swing text component.
 */
private fun getLabelFromTextView(component: JComponent): String? {
  val view = component.getClientProperty("html")
  if (view !is View) {
    return null
  }

  val document = view.document ?: return null
  val length = document.length
  try {
    return document.getText(0, length)
  }
  catch (e: BadLocationException) {
    LOG.error(e)
    return null
  }
}

private fun getLabelFromComponent(label: JLabel): String? = getLabelFromTextView(label) ?: label.text

private fun getLabelFromComponent(button: AbstractButton): String? = getLabelFromTextView(button) ?: button.text

private fun getLabelsFromComponent(component: Component?): List<String> {
  var label: String? = when (component) {
    is JLabel -> getLabelFromComponent(component)
    is JCheckBox -> getLabelFromComponent(component)
    is JRadioButton -> getLabelFromComponent(component)
    is JButton -> getLabelFromComponent(component)
    else -> null
  }
  label = label?.takeIf { it.isNotBlank() }

  val labels = ClientProperty.get(component, ADDITIONAL_SEARCH_LABELS_KEY) ?: return listOfNotNull(label)
  val list = ArrayList<String>(labels)
  if (label != null) {
    list.add(label)
  }
  return list
}

private fun getItemsFromComboBox(comboBox: JComboBox<*>): List<String> {
  @Suppress("UNCHECKED_CAST")
  val renderer = comboBox.renderer as? ListCellRenderer<Any?> ?: DefaultListCellRenderer()
  @Suppress("UNCHECKED_CAST")
  val jList = BasicComboPopup(comboBox as JComboBox<Any?>).list

  val result = ArrayList<String>()
  val count = comboBox.itemCount
  for (i in 0 until count) {
    val value = comboBox.getItemAt(i)
    val labelComponent = renderer.getListCellRendererComponent(jList, value, i, false, false)
    result.addAll(getLabelsFromComponent(labelComponent))
  }
  return result
}

@Serializable
@Internal
data class SearchableOptionEntry(
  @JvmField val hit: String,
  @JvmField val path: String? = null,
) : Comparable<SearchableOptionEntry> {
  init {
    if (hit.isBlank()) {
      LOG.error("SearchableOptionEntry with empty hit")
    }
  }

  override fun compareTo(other: SearchableOptionEntry): Int {
    val pathComparison = (path ?: "").compareTo(other.path ?: "")
    return if (pathComparison == 0) hit.compareTo(other.hit) else pathComparison
  }
}

@Internal
fun collectSearchItemsForComponentWithLabel(
  configurable: SearchableConfigurable,
  configurableOptions: MutableSet<SearchableOptionEntry>,
  component: JComponent,
) {
  processUiLabel(title = configurable.displayName, configurableOptions = null, rawList = configurableOptions, path = null, i18n = false)
  collectSearchItemsForComponent(
    component = component,
    configurableOptions = null,
    path = null,
    i18n = false,
    rawList = configurableOptions,
  )
}