/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.ide.ui.search;

import com.intellij.application.options.SkipSelfSearchComponent;
import com.intellij.ide.actions.ShowSettingsUtilImpl;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurableGroup;
import com.intellij.openapi.options.MasterDetails;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.options.ex.GlassPanel;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.JBColor;
import com.intellij.ui.SimpleColoredComponent;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.TabbedPaneWrapper;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.TitledBorder;
import javax.swing.plaf.basic.BasicComboPopup;
import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author anna
 * @since 07.02.2006
 */
public class SearchUtil {
  private static final Pattern HTML_PATTERN = Pattern.compile("<[^<>]*>");
  private static final Pattern QUOTED = Pattern.compile("\"([^\"]+)\"");

  public static final String HIGHLIGHT_WITH_BORDER = "searchUtil.highlightWithBorder";
  public static final String STYLE_END = "</style>";

  private SearchUtil() { }

  public static void processProjectConfigurables(Project project, Map<SearchableConfigurable, Set<OptionDescription>> options) {
    processConfigurables(ShowSettingsUtilImpl.getConfigurables(project, true), options);
  }

  private static void processConfigurables(Configurable[] configurables, Map<SearchableConfigurable, Set<OptionDescription>> options) {
    for (Configurable configurable : configurables) {
      if (configurable instanceof SearchableConfigurable) {
        //ignore invisible root nodes
        if (configurable instanceof SearchableConfigurable.Parent && !((SearchableConfigurable.Parent)configurable).isVisible()) {
          continue;
        }

        Set<OptionDescription> configurableOptions = new TreeSet<>();
        options.put((SearchableConfigurable)configurable, configurableOptions);

        if (configurable instanceof MasterDetails) {
          final MasterDetails md = (MasterDetails)configurable;
          md.initUi();
          processComponent(configurable, configurableOptions, md.getMaster());
          processComponent(configurable, configurableOptions, md.getDetails().getComponent());
        }
        else {
          processComponent(configurable, configurableOptions, configurable.createComponent());
        }
      }
    }
  }

  private static void processComponent(Configurable configurable, Set<OptionDescription> configurableOptions, JComponent component) {
    if (component != null) {
      processUILabel(configurable.getDisplayName(), configurableOptions, null);
      processComponent(component, configurableOptions, null);
    }
  }

  private static void processComponent(JComponent component, Set<OptionDescription> configurableOptions, String path) {
    if (component instanceof SkipSelfSearchComponent) return;
    final Border border = component.getBorder();
    if (border instanceof TitledBorder) {
      final TitledBorder titledBorder = (TitledBorder)border;
      final String title = titledBorder.getTitle();
      if (title != null) {
        processUILabel(title, configurableOptions, path);
      }
    }
    String label = getLabelFromComponent(component);
    if (label != null) {
      processUILabel(label, configurableOptions, path);
    }
    else if (component instanceof JComboBox) {
      List<String> labels = getItemsFromComboBox((JComboBox)component);
      for (String each : labels) {
        processUILabel(each, configurableOptions, path);
      }
    }
    else if (component instanceof JTabbedPane) {
      final JTabbedPane tabbedPane = (JTabbedPane)component;
      final int tabCount = tabbedPane.getTabCount();
      for (int i = 0; i < tabCount; i++) {
        final String title = path != null ? path + '.' + tabbedPane.getTitleAt(i) : tabbedPane.getTitleAt(i);
        processUILabel(title, configurableOptions, title);
        final Component tabComponent = tabbedPane.getComponentAt(i);
        if (tabComponent instanceof JComponent) {
          processComponent((JComponent)tabComponent, configurableOptions, title);
        }
      }
    }
    else if (component instanceof TabbedPaneWrapper.TabbedPaneHolder) {
      final TabbedPaneWrapper tabbedPane = ((TabbedPaneWrapper.TabbedPaneHolder)component).getTabbedPaneWrapper();
      final int tabCount = tabbedPane.getTabCount();
      for (int i = 0; i < tabCount; i++) {
        String tabTitle = tabbedPane.getTitleAt(i);
        final String title = path != null ? path + '.' + tabTitle : tabTitle;
        processUILabel(title, configurableOptions, title);
        final JComponent tabComponent = tabbedPane.getComponentAt(i);
        if (tabComponent != null) {
          processComponent(tabComponent, configurableOptions, title);
        }
      }
    }
    else {
      final Component[] components = component.getComponents();
      if (components != null) {
        for (Component child : components) {
          if (child instanceof JComponent) {
            processComponent((JComponent)child, configurableOptions, path);
          }
        }
      }
    }
  }

  @Nullable
  private static String getLabelFromComponent(@Nullable Component component) {
    String label = null;
    if (component instanceof JLabel) {
      label = ((JLabel)component).getText();
    }
    else if (component instanceof JCheckBox) {
      label = ((JCheckBox)component).getText();
    }
    else if (component instanceof JRadioButton) {
      label = ((JRadioButton)component).getText();
    }
    else if (component instanceof JButton) {
      label = ((JButton)component).getText();
    }
    return StringUtil.nullize(label, true);
  }

  @NotNull
  private static List<String> getItemsFromComboBox(@NotNull JComboBox comboBox) {
    ListCellRenderer renderer = comboBox.getRenderer();
    if (renderer == null) renderer = new DefaultListCellRenderer();

    JList jList = new BasicComboPopup(comboBox).getList();

    List<String> result = new ArrayList<>();

    int count = comboBox.getItemCount();
    for (int i = 0; i < count; i++) {
      Object value = comboBox.getItemAt(i);
      //noinspection unchecked
      Component labelComponent = renderer.getListCellRendererComponent(jList, value, i, false, false);
      String label = getLabelFromComponent(labelComponent);
      if (label != null) result.add(label);
    }

    return result;
  }

  private static void processUILabel(String title, Set<OptionDescription> configurableOptions, String path) {
    title = HTML_PATTERN.matcher(title).replaceAll(" ");
    final Set<String> words = SearchableOptionsRegistrar.getInstance().getProcessedWordsWithoutStemming(title);
    final String regex = "[\\W&&[^\\p{Punct}\\p{Blank}]]";
    for (String option : words) {
      configurableOptions.add(new OptionDescription(option, title.replaceAll(regex, " "), path));
    }
  }

  public static Runnable lightOptions(SearchableConfigurable configurable, JComponent component, String option, GlassPanel glassPanel) {
    return () -> {
      if (!traverseComponentsTree(configurable, glassPanel, component, option, true)) {
        traverseComponentsTree(configurable, glassPanel, component, option, false);
      }
    };
  }

  private static int getSelection(String tabIdx, final JTabbedPane tabbedPane) {
    SearchableOptionsRegistrar searchableOptionsRegistrar = SearchableOptionsRegistrar.getInstance();
    for (int i = 0; i < tabbedPane.getTabCount(); i++) {
      final Set<String> pathWords = searchableOptionsRegistrar.getProcessedWords(tabIdx);
      final String title = tabbedPane.getTitleAt(i);
      if (!pathWords.isEmpty()) {
        final Set<String> titleWords = searchableOptionsRegistrar.getProcessedWords(title);
        pathWords.removeAll(titleWords);
        if (pathWords.isEmpty()) return i;
      }
      else if (tabIdx.equalsIgnoreCase(title)) { //e.g. only stop words
        return i;
      }
    }
    return -1;
  }

  public static int getSelection(String tabIdx, final TabbedPaneWrapper tabbedPane) {
    SearchableOptionsRegistrar searchableOptionsRegistrar = SearchableOptionsRegistrar.getInstance();
    for (int i = 0; i < tabbedPane.getTabCount(); i++) {
      final Set<String> pathWords = searchableOptionsRegistrar.getProcessedWords(tabIdx);
      final String title = tabbedPane.getTitleAt(i);
      final Set<String> titleWords = searchableOptionsRegistrar.getProcessedWords(title);
      pathWords.removeAll(titleWords);
      if (pathWords.isEmpty()) return i;
    }
    return -1;
  }

  private static boolean traverseComponentsTree(SearchableConfigurable configurable,
                                                GlassPanel glassPanel,
                                                JComponent rootComponent,
                                                String option,
                                                boolean force) {
    rootComponent.putClientProperty(HIGHLIGHT_WITH_BORDER, null);

    if (option == null || option.trim().length() == 0) return false;
    boolean highlight = false;

    String label = getLabelFromComponent(rootComponent);
    if (label != null) {
      if (isComponentHighlighted(label, option, force, configurable)) {
        highlight = true;
        glassPanel.addSpotlight(rootComponent);
      }
    }
    else if (rootComponent instanceof JComboBox) {
      List<String> labels = getItemsFromComboBox(((JComboBox)rootComponent));
      if (ContainerUtil.exists(labels, it -> isComponentHighlighted(it, option, force, configurable))) {
        highlight = true;
        glassPanel.addSpotlight(rootComponent);
      }
    }
    else if (rootComponent instanceof JTabbedPane) {
      final JTabbedPane tabbedPane = (JTabbedPane)rootComponent;
      final String path = SearchableOptionsRegistrar.getInstance().getInnerPath(configurable, option);
      if (path != null) {
        final int index = getSelection(path, tabbedPane);
        if (index > -1 && index < tabbedPane.getTabCount()) {
          if (tabbedPane.getTabComponentAt(index) instanceof JComponent) {
            glassPanel.addSpotlight((JComponent)tabbedPane.getTabComponentAt(index));
          }
        }
      }
    }
    else if (rootComponent instanceof TabbedPaneWrapper.TabbedPaneHolder) {
      final TabbedPaneWrapper tabbedPaneWrapper = ((TabbedPaneWrapper.TabbedPaneHolder)rootComponent).getTabbedPaneWrapper();
      final String path = SearchableOptionsRegistrar.getInstance().getInnerPath(configurable, option);
      if (path != null) {
        final int index = getSelection(path, tabbedPaneWrapper);
        if (index > -1 && index < tabbedPaneWrapper.getTabCount()) {
          glassPanel.addSpotlight((JComponent)tabbedPaneWrapper.getTabComponentAt(index));
        }
      }
    }

    final Component[] components = rootComponent.getComponents();
    for (Component component : components) {
      if (component instanceof JComponent) {
        final boolean innerHighlight = traverseComponentsTree(configurable, glassPanel, (JComponent)component, option, force);

        if (!highlight && !innerHighlight) {
          final Border border = rootComponent.getBorder();
          if (border instanceof TitledBorder) {
            final String title = ((TitledBorder)border).getTitle();
            if (isComponentHighlighted(title, option, force, configurable)) {
              highlight = true;
              glassPanel.addSpotlight(rootComponent);
              rootComponent.putClientProperty(HIGHLIGHT_WITH_BORDER, Boolean.TRUE);
            }
          }
        }


        if (innerHighlight) {
          highlight = true;
        }
      }
    }
    return highlight;
  }

  public static boolean isComponentHighlighted(String text, String option, boolean force, final SearchableConfigurable configurable) {
    if (text == null || option == null || option.length() == 0) return false;
    final SearchableOptionsRegistrar searchableOptionsRegistrar = SearchableOptionsRegistrar.getInstance();
    final Set<String> words = searchableOptionsRegistrar.getProcessedWords(option);
    final Set<String> options = configurable != null ? searchableOptionsRegistrar.replaceSynonyms(words, configurable) : words;
    if (options == null || options.isEmpty()) {
      return text.toLowerCase(Locale.US).contains(option.toLowerCase(Locale.US));
    }
    final Set<String> tokens = searchableOptionsRegistrar.getProcessedWords(text);
    if (!force) {
      options.retainAll(tokens);
      final boolean highlight = !options.isEmpty();
      return highlight || text.toLowerCase(Locale.US).contains(option.toLowerCase(Locale.US));
    }
    else {
      options.removeAll(tokens);
      return options.isEmpty();
    }
  }

  public static String markup(@NotNull String textToMarkup, @Nullable String filter) {
    if (filter == null || filter.length() == 0) {
      return textToMarkup;
    }
    int bodyStart = textToMarkup.indexOf("<body>");
    final int bodyEnd = textToMarkup.indexOf("</body>");
    final String head;
    final String foot;
    if (bodyStart >= 0) {
      bodyStart += "<body>".length();
      head = textToMarkup.substring(0, bodyStart);
      if (bodyEnd >= 0) {
        foot = textToMarkup.substring(bodyEnd);
      }
      else {
        foot = "";
      }
      textToMarkup = textToMarkup.substring(bodyStart, bodyEnd);
    }
    else {
      foot = "";
      head = "";
    }
    final Pattern insideHtmlTagPattern = Pattern.compile("[<[^<>]*>]*<[^<>]*");
    final SearchableOptionsRegistrar registrar = SearchableOptionsRegistrar.getInstance();
    final HashSet<String> quoted = new HashSet<>();
    filter = processFilter(quoteStrictOccurrences(textToMarkup, filter), quoted);
    final Set<String> options = registrar.getProcessedWords(filter);
    final Set<String> words = registrar.getProcessedWords(textToMarkup);
    for (String option : options) {
      if (words.contains(option)) {
        textToMarkup = markup(textToMarkup, insideHtmlTagPattern, option);
      }
    }
    for (String stripped : quoted) {
      if (registrar.isStopWord(stripped)) continue;
      textToMarkup = markup(textToMarkup, insideHtmlTagPattern, stripped);
    }
    return head + textToMarkup + foot;
  }

  private static String quoteStrictOccurrences(final String textToMarkup, final String filter) {
    String cur = "";
    final String s = textToMarkup.toLowerCase(Locale.US);
    for (String part : filter.split(" ")) {
      if (s.contains(part)) {
        cur += "\"" + part + "\" ";
      }
      else {
        cur += part + " ";
      }
    }
    return cur;
  }

  private static String markup(String textToMarkup, final Pattern insideHtmlTagPattern, final String option) {
    final int styleIdx = textToMarkup.indexOf("<style");
    final int styleEndIdx = textToMarkup.indexOf("</style>");
    if (styleIdx < 0 || styleEndIdx < 0) {
      return markupInText(textToMarkup, insideHtmlTagPattern, option);
    }
    return markup(textToMarkup.substring(0, styleIdx), insideHtmlTagPattern, option) +
           markup(textToMarkup.substring(styleEndIdx + STYLE_END.length()), insideHtmlTagPattern, option);
  }

  private static String markupInText(String textToMarkup, Pattern insideHtmlTagPattern, String option) {
    String result = "";
    int beg = 0;
    int idx;
    while ((idx = StringUtil.indexOfIgnoreCase(textToMarkup, option, beg)) != -1) {
      final String prefix = textToMarkup.substring(beg, idx);
      final String toMark = textToMarkup.substring(idx, idx + option.length());
      if (insideHtmlTagPattern.matcher(prefix).matches()) {
        final int lastIdx = textToMarkup.indexOf(">", idx);
        result += prefix + textToMarkup.substring(idx, lastIdx + 1);
        beg = lastIdx + 1;
      }
      else {
        result += prefix + "<font color='#ffffff' bgColor='#1d5da7'>" + toMark + "</font>";
        beg = idx + option.length();
      }
    }
    result += textToMarkup.substring(beg);
    return result;
  }

  public static void appendFragments(String filter,
                                     String text,
                                     @SimpleTextAttributes.StyleAttributeConstant int style,
                                     final Color foreground,
                                     final Color background,
                                     final SimpleColoredComponent textRenderer) {
    if (text == null) return;
    if (filter == null || filter.length() == 0) {
      textRenderer.append(text, new SimpleTextAttributes(background, foreground, JBColor.RED, style));
    }
    else { //markup
      final HashSet<String> quoted = new HashSet<>();
      filter = processFilter(quoteStrictOccurrences(text, filter), quoted);
      final TreeMap<Integer, String> indx = new TreeMap<>();
      for (String stripped : quoted) {
        int beg = 0;
        int idx;
        while ((idx = StringUtil.indexOfIgnoreCase(text, stripped, beg)) != -1) {
          indx.put(idx, text.substring(idx, idx + stripped.length()));
          beg = idx + stripped.length();
        }
      }

      final List<String> selectedWords = new ArrayList<>();
      int pos = 0;
      for (Integer index : indx.keySet()) {
        final String stripped = indx.get(index);
        final int start = index.intValue();
        if (pos > start) {
          final String highlighted = selectedWords.get(selectedWords.size() - 1);
          if (highlighted.length() < stripped.length()) {
            selectedWords.remove(highlighted);
          }
          else {
            continue;
          }
        }
        appendSelectedWords(text, selectedWords, pos, start, filter);
        selectedWords.add(stripped);
        pos = start + stripped.length();
      }
      appendSelectedWords(text, selectedWords, pos, text.length(), filter);

      int idx = 0;
      for (String word : selectedWords) {
        text = text.substring(idx);
        final String before = text.substring(0, text.indexOf(word));
        if (before.length() > 0) textRenderer.append(before, new SimpleTextAttributes(background, foreground, null, style));
        idx = text.indexOf(word) + word.length();
        textRenderer.append(text.substring(idx - word.length(), idx), new SimpleTextAttributes(background,
                                                                                               foreground, null,
                                                                                               style |
                                                                                               SimpleTextAttributes.STYLE_SEARCH_MATCH));
      }
      final String after = text.substring(idx, text.length());
      if (after.length() > 0) textRenderer.append(after, new SimpleTextAttributes(background, foreground, null, style));
    }
  }

  private static void appendSelectedWords(final String text,
                                          final List<String> selectedWords,
                                          final int pos,
                                          int end,
                                          final String filter) {
    if (pos < end) {
      final Set<String> filters = SearchableOptionsRegistrar.getInstance().getProcessedWords(filter);
      final String[] words = text.substring(pos, end).split("[\\W&&[^-]]+");
      for (String word : words) {
        if (filters.contains(PorterStemmerUtil.stem(word.toLowerCase(Locale.US)))) {
          selectedWords.add(word);
        }
      }
    }
  }

  public static List<Set<String>> findKeys(String filter, Set<String> quoted) {
    filter = processFilter(filter.toLowerCase(Locale.US), quoted);
    final List<Set<String>> keySetList = new ArrayList<>();
    final SearchableOptionsRegistrar optionsRegistrar = SearchableOptionsRegistrar.getInstance();
    final Set<String> words = optionsRegistrar.getProcessedWords(filter);
    for (String word : words) {
      final Set<OptionDescription> descriptions = ((SearchableOptionsRegistrarImpl)optionsRegistrar).getAcceptableDescriptions(word);
      Set<String> keySet = new HashSet<>();
      if (descriptions != null) {
        for (OptionDescription description : descriptions) {
          keySet.add(description.getPath());
        }
      }
      keySetList.add(keySet);
    }
    if (keySetList.isEmpty() && !StringUtil.isEmptyOrSpaces(filter)) {
      keySetList.add(Collections.singleton(filter));
    }
    return keySetList;
  }

  public static String processFilter(String filter, Set<String> quoted) {
    String withoutQuoted = "";
    int beg = 0;
    final Matcher matcher = QUOTED.matcher(filter);
    while (matcher.find()) {
      final int start = matcher.start(1);
      withoutQuoted += " " + filter.substring(beg, start);
      beg = matcher.end(1);
      final String trimmed = filter.substring(start, beg).trim();
      if (trimmed.length() > 0) {
        quoted.add(trimmed);
      }
    }
    return withoutQuoted + " " + filter.substring(beg);
  }

  public static List<Configurable> expand(ConfigurableGroup[] groups) {
    final ArrayList<Configurable> result = new ArrayList<>();
    for (ConfigurableGroup eachGroup : groups) {
      result.addAll(expandGroup(eachGroup));
    }
    return result;
  }

  public static List<Configurable> expandGroup(final ConfigurableGroup group) {
    final Configurable[] configurables = group.getConfigurables();
    List<Configurable> result = new ArrayList<>();
    ContainerUtil.addAll(result, configurables);
    for (Configurable each : configurables) {
      addChildren(each, result);
    }

    return ContainerUtil.filter(result, configurable -> !(configurable instanceof SearchableConfigurable.Parent) ||
                                                        ((SearchableConfigurable.Parent)configurable).isVisible());
  }

  private static void addChildren(Configurable configurable, List<Configurable> list) {
    if (configurable instanceof Configurable.Composite) {
      final Configurable[] kids = ((Configurable.Composite)configurable).getConfigurables();
      for (Configurable eachKid : kids) {
        list.add(eachKid);
        addChildren(eachKid, list);
      }
    }
  }
}