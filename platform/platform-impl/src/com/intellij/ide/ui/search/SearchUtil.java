// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.ui.search;

import com.intellij.application.options.SkipSelfSearchComponent;
import com.intellij.ide.actions.ShowSettingsUtilImpl;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.options.*;
import com.intellij.openapi.options.ex.ConfigurableWrapper;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.JBColor;
import com.intellij.ui.SimpleColoredComponent;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.TabbedPaneWrapper;
import com.intellij.util.CollectConsumer;
import com.intellij.util.ReflectionUtil;
import com.intellij.util.containers.ContainerUtil;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMaps;
import it.unimi.dsi.fastutil.ints.Int2ObjectRBTreeMap;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.TitledBorder;
import javax.swing.plaf.basic.BasicComboPopup;
import java.awt.*;
import java.util.List;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class SearchUtil {
  private static final String DEBUGGER_CONFIGURABLE_CLASS = "com.intellij.xdebugger.impl.settings.DebuggerConfigurable";
  private static final Pattern HTML_PATTERN = Pattern.compile("<[^<>]*>");
  private static final Pattern QUOTED = Pattern.compile("\"([^\"]+)\"");
  private static final Pattern NON_WORD_PATTERN = Pattern.compile("[\\W&&[^\\p{Punct}\\p{Blank}]]");

  public static final String HIGHLIGHT_WITH_BORDER = "searchUtil.highlightWithBorder";
  private static final String STYLE_END = "</style>";

  private SearchUtil() { }

  public static void processProjectConfigurables(Project project, Map<SearchableConfigurable, Set<OptionDescription>> options) {
    processConfigurables(ShowSettingsUtilImpl.getConfigurables(project, true), options);
  }

  private static void processConfigurables(@NotNull List<? extends Configurable> configurables, Map<SearchableConfigurable, Set<OptionDescription>> options) {
    for (final Configurable configurable : configurables) {
      if (!(configurable instanceof SearchableConfigurable)) {
        continue;
      }
      //ignore invisible root nodes
      //noinspection deprecation
      if (configurable instanceof SearchableConfigurable.Parent && !((SearchableConfigurable.Parent)configurable).isVisible()) {
        continue;
      }

      final SearchableConfigurable searchableConfigurable = (SearchableConfigurable)configurable;

      Set<OptionDescription> configurableOptions = new TreeSet<>();
      options.put(searchableConfigurable, configurableOptions);

      for (TraverseUIHelper extension : TraverseUIHelper.helperExtensionPoint.getExtensions()) {
        extension.beforeConfigurable(searchableConfigurable, configurableOptions);
      }

      if (configurable instanceof MasterDetails) {
        final MasterDetails md = (MasterDetails)configurable;
        md.initUi();
        processComponent(searchableConfigurable, configurableOptions, md.getMaster());
        processComponent(searchableConfigurable, configurableOptions, md.getDetails().getComponent());
      }
      else {
        processComponent(searchableConfigurable, configurableOptions, configurable.createComponent());
        final Configurable unwrapped = unwrapConfigurable(configurable);
        if (unwrapped instanceof CompositeConfigurable) {
          unwrapped.disposeUIResources();
          final List<? extends UnnamedConfigurable> children = ((CompositeConfigurable<?>)unwrapped).getConfigurables();
          for (final UnnamedConfigurable child : children) {
            final Set<OptionDescription> childConfigurableOptions = new TreeSet<>();
            options.put(new SearchableConfigurableAdapter(searchableConfigurable, child), childConfigurableOptions);

            if (child instanceof SearchableConfigurable) {
              processUILabel(((SearchableConfigurable)child).getDisplayName(), childConfigurableOptions, null);
            }
            final JComponent component = child.createComponent();
            if (component != null) {
              processComponent(component, childConfigurableOptions, null);
            }

            configurableOptions.removeAll(childConfigurableOptions);
          }
        }
      }

      for (TraverseUIHelper extension : TraverseUIHelper.helperExtensionPoint.getExtensions()) {
        extension.afterConfigurable(searchableConfigurable, configurableOptions);
      }
    }
  }

  @NotNull
  private static Configurable unwrapConfigurable(@NotNull Configurable configurable) {
    if (configurable instanceof ConfigurableWrapper) {
      final UnnamedConfigurable wrapped = ((ConfigurableWrapper)configurable).getConfigurable();
      if (wrapped instanceof SearchableConfigurable) {
        configurable = (Configurable)wrapped;
      }
    }
    if (DEBUGGER_CONFIGURABLE_CLASS.equals(configurable.getClass().getName())) {
      final Class<?> clazz = ReflectionUtil.forName(DEBUGGER_CONFIGURABLE_CLASS);
      final Configurable rootConfigurable = ReflectionUtil.getField(clazz, configurable, Configurable.class, "myRootConfigurable");
      if (rootConfigurable != null) {
        return rootConfigurable;
      }
    }
    return configurable;
  }

  private static void processComponent(SearchableConfigurable configurable, Set<? super OptionDescription> configurableOptions, JComponent component) {
    if (component != null) {
      for (TraverseUIHelper extension : TraverseUIHelper.helperExtensionPoint.getExtensions()) {
        extension.beforeComponent(configurable, component, configurableOptions);
      }

      processUILabel(configurable.getDisplayName(), configurableOptions, null);
      processComponent(component, configurableOptions, null);

      for (TraverseUIHelper extension : TraverseUIHelper.helperExtensionPoint.getExtensions()) {
        extension.afterComponent(configurable, component, configurableOptions);
      }
    }
  }

  private static void processComponent(JComponent component, Set<? super OptionDescription> configurableOptions, String path) {
    if (component instanceof SkipSelfSearchComponent) {
      return;
    }
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
      List<String> labels = getItemsFromComboBox((JComboBox<?>)component);
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
  private static List<String> getItemsFromComboBox(@NotNull JComboBox<?> comboBox) {
    @SuppressWarnings("unchecked")
    ListCellRenderer<Object> renderer = (ListCellRenderer<Object>)comboBox.getRenderer();
    if (renderer == null) {
      renderer = new DefaultListCellRenderer();
    }

    JList<?> jList = new BasicComboPopup(comboBox).getList();

    List<String> result = new ArrayList<>();

    int count = comboBox.getItemCount();
    for (int i = 0; i < count; i++) {
      Object value = comboBox.getItemAt(i);
      Component labelComponent = renderer.getListCellRendererComponent(jList, value, i, false, false);
      String label = getLabelFromComponent(labelComponent);
      if (label != null) {
        result.add(label);
      }
    }

    return result;
  }

  private static void processUILabel(String title, Set<? super OptionDescription> configurableOptions, String path) {
    title = HTML_PATTERN.matcher(title).replaceAll(" ");
    final Set<String> words = SearchableOptionsRegistrar.getInstance().getProcessedWordsWithoutStemming(title);
    title = NON_WORD_PATTERN.matcher(title).replaceAll(" ");
    for (String option : words) {
      configurableOptions.add(new OptionDescription(option, title, path));
    }
  }

  public static void lightOptions(SearchableConfigurable configurable, JComponent component, String option) {
    if (!traverseComponentsTree(configurable, component, option, true)) {
      traverseComponentsTree(configurable, component, option, false);
    }
  }

  private static int getSelection(String tabIdx, int tabCount, Function<Integer,String> titleGetter) {
    SearchableOptionsRegistrar searchableOptionsRegistrar = SearchableOptionsRegistrar.getInstance();
    for (int i = 0; i < tabCount; i++) {
      final Set<String> pathWords = searchableOptionsRegistrar.getProcessedWords(tabIdx);
      final String title = titleGetter.apply(i);
      if (!pathWords.isEmpty()) {
        final Set<String> titleWords = searchableOptionsRegistrar.getProcessedWords(title);
        pathWords.removeAll(titleWords);
        if (pathWords.isEmpty()) {
          return i;
        }
      }
      else if (tabIdx.equalsIgnoreCase(title)) { //e.g. only stop words
        return i;
      }
    }
    return -1;
  }


  private static boolean traverseComponentsTree(SearchableConfigurable configurable,
                                                JComponent rootComponent,
                                                String option,
                                                boolean force) {
    rootComponent.putClientProperty(HIGHLIGHT_WITH_BORDER, null);

    if (option == null || option.trim().length() == 0) {
      return false;
    }
    String label = getLabelFromComponent(rootComponent);
    if (label != null) {
      if (isComponentHighlighted(label, option, force, configurable)) {
        highlightComponent(rootComponent, option);
        return true; // do not visit children of highlighted component
      }
    }
    else if (rootComponent instanceof JComboBox) {
      List<String> labels = getItemsFromComboBox(((JComboBox<?>)rootComponent));
      if (ContainerUtil.exists(labels, it -> isComponentHighlighted(it, option, force, configurable))) {
        highlightComponent(rootComponent, option);
        return true; // do not visit children of highlighted component
      }
    }
    else if (rootComponent instanceof JTabbedPane) {
      final JTabbedPane tabbedPane = (JTabbedPane)rootComponent;

      final Set<String> paths = SearchableOptionsRegistrar.getInstance().getInnerPaths(configurable, option);
      for (String path : paths) {
        if (path != null) {
          final int index = getSelection(path, tabbedPane.getTabCount(), i -> tabbedPane.getTitleAt(i));
          if (index > -1 && index < tabbedPane.getTabCount()) {
            if (tabbedPane.getTabComponentAt(index) instanceof JComponent) {
              highlightComponent((JComponent)tabbedPane.getTabComponentAt(index), option);
            }
          }
        }
      }
    }
    else if (rootComponent instanceof TabbedPaneWrapper.TabbedPaneHolder) {
      final TabbedPaneWrapper tabbedPaneWrapper = ((TabbedPaneWrapper.TabbedPaneHolder)rootComponent).getTabbedPaneWrapper();
      final Set<String> paths = SearchableOptionsRegistrar.getInstance().getInnerPaths(configurable, option);
      for (String path : paths) {
        if (path != null) {
          final int index = getSelection(path, tabbedPaneWrapper.getTabCount(), i -> tabbedPaneWrapper.getTitleAt(i));
          if (index > -1 && index < tabbedPaneWrapper.getTabCount()) {
            highlightComponent((JComponent)tabbedPaneWrapper.getTabComponentAt(index), option);
          }
        }
      }
    }

    Border border = rootComponent.getBorder();
    if (border instanceof TitledBorder) {
      String title = ((TitledBorder)border).getTitle();
      if (isComponentHighlighted(title, option, force, configurable)) {
        highlightComponent(rootComponent, option);
        rootComponent.putClientProperty(HIGHLIGHT_WITH_BORDER, Boolean.TRUE);
        return true; // do not visit children of highlighted component
      }
    }
    boolean highlight = false;
    for (Component component : rootComponent.getComponents()) {
      if (component instanceof JComponent && traverseComponentsTree(configurable, (JComponent)component, option, force)) {
        highlight = true;
      }
    }
    return highlight;
  }

  private static void highlightComponent(@NotNull JComponent rootComponent, @NotNull String searchString) {
    ApplicationManager.getApplication().getMessageBus().syncPublisher(ComponentHighlightingListener.TOPIC).highlight(rootComponent, searchString);
  }

  public static boolean isComponentHighlighted(String text, String option, boolean force, final SearchableConfigurable configurable) {
    if (text == null || option == null || option.length() == 0) {
      return false;
    }
    final SearchableOptionsRegistrar searchableOptionsRegistrar = SearchableOptionsRegistrar.getInstance();
    final Set<String> words = searchableOptionsRegistrar.getProcessedWords(option);
    final Set<String> options = configurable != null ? searchableOptionsRegistrar.replaceSynonyms(words, configurable) : words;
    if (options.isEmpty()) {
      return StringUtil.toLowerCase(text).contains(StringUtil.toLowerCase(option));
    }
    final Set<String> tokens = searchableOptionsRegistrar.getProcessedWords(text);
    if (!force) {
      options.retainAll(tokens);
      final boolean highlight = !options.isEmpty();
      return highlight || StringUtil.toLowerCase(text).contains(StringUtil.toLowerCase(option));
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
      if (registrar.isStopWord(stripped)) {
        continue;
      }
      textToMarkup = markup(textToMarkup, insideHtmlTagPattern, stripped);
    }
    return head + textToMarkup + foot;
  }

  private static String quoteStrictOccurrences(final String textToMarkup, final String filter) {
    StringBuilder cur = new StringBuilder();
    final String s = StringUtil.toLowerCase(textToMarkup);
    for (String part : filter.split(" ")) {
      if (s.contains(part)) {
        cur.append("\"").append(part).append("\" ");
      }
      else {
        cur.append(part).append(" ");
      }
    }
    return cur.toString();
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
    StringBuilder result = new StringBuilder();
    int beg = 0;
    int idx;
    while ((idx = StringUtil.indexOfIgnoreCase(textToMarkup, option, beg)) != -1) {
      final String prefix = textToMarkup.substring(beg, idx);
      final String toMark = textToMarkup.substring(idx, idx + option.length());
      if (insideHtmlTagPattern.matcher(prefix).matches()) {
        final int lastIdx = textToMarkup.indexOf(">", idx);
        result.append(prefix).append(textToMarkup, idx, lastIdx + 1);
        beg = lastIdx + 1;
      }
      else {
        result.append(prefix).append("<font color='#ffffff' bgColor='#1d5da7'>").append(toMark).append("</font>");
        beg = idx + option.length();
      }
    }
    result.append(textToMarkup.substring(beg));
    return result.toString();
  }

  public static void appendFragments(String filter,
                                     String text,
                                     @SimpleTextAttributes.StyleAttributeConstant int style,
                                     final Color foreground,
                                     final Color background,
                                     final SimpleColoredComponent textRenderer) {
    if (text == null) {
      return;
    }
    if (filter == null || filter.length() == 0) {
      textRenderer.append(text, new SimpleTextAttributes(background, foreground, JBColor.RED, style));
    }
    else {
      //markup
      ObjectOpenHashSet<String> quoted = new ObjectOpenHashSet<>();
      filter = processFilter(quoteStrictOccurrences(text, filter), quoted);
      final Int2ObjectRBTreeMap<String> indexToString = new Int2ObjectRBTreeMap<>();
      for (String stripped : quoted) {
        int beg = 0;
        int idx;
        while ((idx = StringUtil.indexOfIgnoreCase(text, stripped, beg)) != -1) {
          indexToString.put(idx, text.substring(idx, idx + stripped.length()));
          beg = idx + stripped.length();
        }
      }

      final List<String> selectedWords = new ArrayList<>();
      int pos = 0;
      for (Int2ObjectMap.Entry<String> entry : Int2ObjectMaps.fastIterable(indexToString)) {
        String stripped = entry.getValue();
        int start = entry.getIntKey();
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
        if (before.length() > 0) {
          textRenderer.append(before, new SimpleTextAttributes(background, foreground, null, style));
        }
        idx = text.indexOf(word) + word.length();
        textRenderer.append(text.substring(idx - word.length(), idx), new SimpleTextAttributes(background,
                                                                                               foreground, null,
                                                                                               style |
                                                                                               SimpleTextAttributes.STYLE_SEARCH_MATCH));
      }
      final String after = text.substring(idx);
      if (after.length() > 0) {
        textRenderer.append(after, new SimpleTextAttributes(background, foreground, null, style));
      }
    }
  }

  private static void appendSelectedWords(final String text,
                                          final List<? super String> selectedWords,
                                          final int pos,
                                          int end,
                                          final String filter) {
    if (pos < end) {
      final Set<String> filters = SearchableOptionsRegistrar.getInstance().getProcessedWords(filter);
      final String[] words = text.substring(pos, end).split("[\\W&&[^-]]+");
      for (String word : words) {
        if (filters.contains(PorterStemmerUtil.stem(StringUtil.toLowerCase(word)))) {
          selectedWords.add(word);
        }
      }
    }
  }

  public static @NotNull List<Set<String>> findKeys(String filter, Set<? super String> quoted) {
    filter = processFilter(StringUtil.toLowerCase(filter), quoted);
    List<Set<String>> keySetList = new ArrayList<>();
    SearchableOptionsRegistrarImpl optionsRegistrar = (SearchableOptionsRegistrarImpl)SearchableOptionsRegistrar.getInstance();
    for (String word : optionsRegistrar.getProcessedWords(filter)) {
      final Set<OptionDescription> descriptions = optionsRegistrar.getAcceptableDescriptions(word);
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

  private static String processFilter(String filter, Set<? super String> quoted) {
    StringBuilder withoutQuoted = new StringBuilder();
    int beg = 0;
    final Matcher matcher = QUOTED.matcher(filter);
    while (matcher.find()) {
      final int start = matcher.start(1);
      withoutQuoted.append(" ").append(filter, beg, start);
      beg = matcher.end(1);
      final String trimmed = filter.substring(start, beg).trim();
      if (trimmed.length() > 0) {
        quoted.add(trimmed);
      }
    }
    return withoutQuoted + " " + filter.substring(beg);
  }

  @NotNull
  public static List<Configurable> expand(ConfigurableGroup @NotNull [] groups) {
    List<Configurable> result = new ArrayList<>();
    CollectConsumer<Configurable> consumer = new CollectConsumer<>(result);
    for (ConfigurableGroup group : groups) {
      processExpandedGroups(group, consumer);
    }
    return result;
  }

  @NotNull
  public static List<Configurable> expandGroup(@NotNull ConfigurableGroup group) {
    List<Configurable> result = new ArrayList<>();
    processExpandedGroups(group, new CollectConsumer<>(result));
    return result;
  }

  public static void processExpandedGroups(@NotNull ConfigurableGroup group, @NotNull Consumer<? super Configurable> consumer) {
    Configurable[] configurables = group.getConfigurables();
    List<Configurable> result = new ArrayList<>();
    ContainerUtil.addAll(result, configurables);
    for (Configurable each : configurables) {
      addChildren(each, result);
    }

    for (Configurable configurable : result) {
      if (isAcceptable(configurable)) {
        consumer.accept(configurable);
      }
    }
  }

  public static boolean isAcceptable(@NotNull Configurable configurable) {
    //noinspection deprecation
    return !(configurable instanceof SearchableConfigurable.Parent) || ((SearchableConfigurable.Parent)configurable).isVisible();
  }

  private static void addChildren(@NotNull Configurable configurable, @NotNull List<? super Configurable> list) {
    if (configurable instanceof Configurable.Composite) {
      for (Configurable eachKid : ((Configurable.Composite)configurable).getConfigurables()) {
        list.add(eachKid);
        addChildren(eachKid, list);
      }
    }
  }

  private static final class SearchableConfigurableAdapter implements SearchableConfigurable {
    private final SearchableConfigurable myOriginal;
    private final UnnamedConfigurable myDelegate;

    private SearchableConfigurableAdapter(@NotNull final SearchableConfigurable original, @NotNull final UnnamedConfigurable delegate) {
      myOriginal = original;
      myDelegate = delegate;
    }

    @NotNull
    @Override
    public String getId() {
      return myOriginal.getId();
    }

    @Nls(capitalization = Nls.Capitalization.Title)
    @Override
    public String getDisplayName() {
      return myOriginal.getDisplayName();
    }

    @NotNull
    @Override
    public Class<?> getOriginalClass() {
      return myDelegate instanceof SearchableConfigurable ? ((SearchableConfigurable)myDelegate).getOriginalClass() : myDelegate.getClass();
    }

    @Nullable
    @Override
    public JComponent createComponent() {
      return null;
    }

    @Override
    public boolean isModified() {
      return false;
    }

    @Override
    public void apply() {
    }

    @Override
    public String toString() {
      return getDisplayName();
    }
  }
}