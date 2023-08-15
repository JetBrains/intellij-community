// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.ui.search;

import com.intellij.CommonBundle;
import com.intellij.ide.plugins.DynamicPluginListener;
import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurableGroup;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.options.ex.ConfigurableExtensionPointUtil;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.util.text.Strings;
import com.intellij.util.CollectConsumer;
import com.intellij.util.ResourceUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.lang.UrlClassLoader;
import kotlin.Pair;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.event.DocumentEvent;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.regex.Pattern;

@SuppressWarnings("Duplicates")
public final class SearchableOptionsRegistrarImpl extends SearchableOptionsRegistrar {
  private static final ExtensionPointName<SearchableOptionContributor> EP_NAME =
    new ExtensionPointName<>("com.intellij.search.optionContributor");

  private static final ExtensionPointName<AdditionalLocationProvider> LOCATION_EP_NAME =
    new ExtensionPointName<>("com.intellij.search.additionalOptionsLocation");

  // option => array of packed OptionDescriptor
  private volatile Map<CharSequence, long[]> storage = Collections.emptyMap();

  private final Set<String> stopWords;

  private volatile @NotNull Map<Pair<String, String>, Set<String>> highlightOptionToSynonym = Collections.emptyMap();

  private final AtomicBoolean isInitialized = new AtomicBoolean();

  private volatile IndexedCharsInterner identifierTable = new IndexedCharsInterner();

  private static final Logger LOG = Logger.getInstance(SearchableOptionsRegistrarImpl.class);
  private static final @NonNls Pattern WORD_SEPARATOR_CHARS = Pattern.compile("[^-\\pL\\d#+]+");

  public SearchableOptionsRegistrarImpl() {
    Application app = ApplicationManager.getApplication();
    if (app.isCommandLine() || app.isHeadlessEnvironment()) {
      stopWords = Collections.emptySet();
      return;
    }

    stopWords = loadStopWords();

    app.getMessageBus().connect().subscribe(DynamicPluginListener.TOPIC, new DynamicPluginListener() {
      @Override
      public void pluginLoaded(@NotNull IdeaPluginDescriptor pluginDescriptor) {
        dropStorage();
      }

      @Override
      public void pluginUnloaded(@NotNull IdeaPluginDescriptor pluginDescriptor, boolean isUpdate) {
        dropStorage();
      }
    });
  }

  private static @NotNull Set<String> loadStopWords() {
    try {
      // stop words
      InputStream stream = ResourceUtil.getResourceAsStream(SearchableOptionsRegistrarImpl.class.getClassLoader(), "search", "ignore.txt");
      if (stream == null) {
        throw new IOException("Broken installation: IDE does not provide /search/ignore.txt");
      }

      Set<String> result = new HashSet<>();
      try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
        String line;
        while ((line = reader.readLine()) != null) {
          if (!line.isEmpty()) {
            result.add(line);
          }
        }
      }
      return result;
    }
    catch (IOException e) {
      LOG.error(e);
      return Collections.emptySet();
    }
  }

  private synchronized void dropStorage() {
    storage = Collections.emptyMap();
    isInitialized.set(false);
  }

  public boolean isInitialized() {
    return isInitialized.get();
  }

  @ApiStatus.Internal
  public void initialize() {
    if (!isInitialized.compareAndSet(false, true)) {
      return;
    }

    MySearchableOptionProcessor processor = new MySearchableOptionProcessor(stopWords);
    try {
      EP_NAME.forEachExtensionSafe(contributor -> contributor.processOptions(processor));
    }
    catch (ProcessCanceledException e) {
      LOG.warn("=== Search storage init canceled ===");
      isInitialized.set(false);
      throw e;
    }

    // index
    highlightOptionToSynonym = processor.computeHighlightOptionToSynonym();

    storage = processor.getStorage();
    identifierTable = processor.getIdentifierTable();
  }

  static void processSearchableOptions(@NotNull Predicate<? super String> fileNameFilter,
                                       @NotNull BiConsumer<? super String, ? super Element> consumer) {
    Set<ClassLoader> visited = Collections.newSetFromMap(new IdentityHashMap<>());
    for (IdeaPluginDescriptor plugin : PluginManagerCore.INSTANCE.getPluginSet().getEnabledModules()) {
      ClassLoader classLoader = plugin.getPluginClassLoader();
      if (!(classLoader instanceof UrlClassLoader) || !visited.add(classLoader)) {
        continue;
      }

      try {
        ((UrlClassLoader)classLoader).processResources("search", fileNameFilter, (name, stream) -> {
          try {
            consumer.accept(name, JDOMUtil.load(stream));
          }
          catch (IOException | JDOMException e) {
            throw new RuntimeException(String.format("Can't parse searchable options '%s' for plugin '%s'",
                                                     name, plugin.getPluginId().getIdString()), e);
          }
        });
      }
      catch (IOException e) {
        throw new RuntimeException(e);
      }
    }

    // process additional locations
    LOCATION_EP_NAME.forEachExtensionSafe(provider -> {
      Path additionalLocation = provider.getAdditionalLocation();
      if (additionalLocation == null) {
        return;
      }
      if (Files.isDirectory(additionalLocation)) {
        try (var stream = Files.list(additionalLocation)) {
          stream
            .filter(path -> fileNameFilter.test(path.getFileName().toString()))
            .forEach(path -> {
              String fileName = path.getFileName().toString();
              try {
                consumer.accept(fileName, JDOMUtil.load(path));
              }
              catch (IOException | JDOMException e) {
                throw new RuntimeException(String.format("Can't parse searchable options '%s'", fileName), e);
              }
            });
        }
        catch (IOException e) {
          throw new RuntimeException(e);
        }
      }
    });
  }

  /**
   * @return XYZT:64 bits where
   * X:16 bits - id of the interned groupName
   * Y:16 bits - id of the interned id
   * Z:16 bits - id of the interned hit
   * T:16 bits - id of the interned path
   */
  @SuppressWarnings("SpellCheckingInspection")
  static long pack(@NotNull String id,
                   @Nullable String hit,
                   @Nullable String path,
                   @Nullable String groupName,
                   @NotNull IndexedCharsInterner identifierTable) {
    long _id = identifierTable.toId(id.trim());
    long _hit = hit == null ? Short.MAX_VALUE : identifierTable.toId(hit.trim());
    long _path = path == null ? Short.MAX_VALUE : identifierTable.toId(path.trim());
    long _groupName = groupName == null ? Short.MAX_VALUE : identifierTable.toId(groupName.trim());
    assert _id >= 0 && _id < Short.MAX_VALUE;
    assert _hit >= 0 && _hit <= Short.MAX_VALUE;
    assert _path >= 0 && _path <= Short.MAX_VALUE;
    assert _groupName >= 0 && _groupName <= Short.MAX_VALUE;
    return _groupName << 48 | _id << 32 | _hit << 16 | _path/* << 0*/;
  }

  private OptionDescription unpack(long data) {
    int _groupName = (int)(data >> 48 & 0xffff);
    int _id = (int)(data >> 32 & 0xffff);
    int _hit = (int)(data >> 16 & 0xffff);
    int _path = (int)(data & 0xffff);
    assert /*_id >= 0 && */_id < Short.MAX_VALUE;
    assert /*_hit >= 0 && */_hit <= Short.MAX_VALUE;
    assert /*_path >= 0 && */_path <= Short.MAX_VALUE;
    assert /*_groupName >= 0 && */_groupName <= Short.MAX_VALUE;

    String groupName = _groupName == Short.MAX_VALUE ? null : identifierTable.fromId(_groupName).toString();
    String configurableId = identifierTable.fromId(_id).toString();
    String hit = _hit == Short.MAX_VALUE ? null : identifierTable.fromId(_hit).toString();
    String path = _path == Short.MAX_VALUE ? null : identifierTable.fromId(_path).toString();

    return new OptionDescription(null, configurableId, hit, path, groupName);
  }

  @Override
  public @NotNull ConfigurableHit getConfigurables(@NotNull List<? extends ConfigurableGroup> groups,
                                                   DocumentEvent.EventType type,
                                                   @Nullable Set<? extends Configurable> previouslyFiltered,
                                                   @NotNull String option,
                                                   @Nullable Project project) {
    if (previouslyFiltered == null || previouslyFiltered.isEmpty()) {
      previouslyFiltered = null;
    }

    String optionToCheck = Strings.toLowerCase(option.trim());

    ConfigurableHit foundByPath = findGroupsByPath(groups, optionToCheck);
    if (foundByPath != null) {
      return foundByPath;
    }

    Set<Configurable> effectiveConfigurables = new LinkedHashSet<>();
    if (previouslyFiltered == null) {
      Consumer<Configurable> consumer = new CollectConsumer<>(effectiveConfigurables);
      for (ConfigurableGroup group : groups) {
        SearchUtil.processExpandedGroups(group, consumer);
      }
    }
    else {
      effectiveConfigurables.addAll(previouslyFiltered);
    }

    Set<Configurable> nameHits = new LinkedHashSet<>();
    Set<Configurable> nameFullHits = new LinkedHashSet<>();

    Set<String> options = getProcessedWordsWithoutStemming(optionToCheck);
    if (options.isEmpty()) {
      for (Configurable each : effectiveConfigurables) {
        if (each.getDisplayName() != null) {
          nameHits.add(each);
          nameFullHits.add(each);
        }
      }
    }
    else {
      for (Configurable each : effectiveConfigurables) {
        if (each.getDisplayName() == null) {
          continue;
        }
        final String displayName = Strings.toLowerCase(each.getDisplayName());
        if (displayName.contains(optionToCheck)) {
          nameFullHits.add(each);
          nameHits.add(each);
        }
      }
    }

    // operate with substring
    Set<String> descriptionOptions = new HashSet<>();
    if (options.isEmpty()) {
      String[] components = WORD_SEPARATOR_CHARS.split(optionToCheck);
      if (components.length > 0) {
        Collections.addAll(descriptionOptions, components);
      }
      else {
        descriptionOptions.add(option);
      }
    }
    else {
      descriptionOptions.addAll(options);
    }

    Set<String> foundIds = findConfigurablesByDescriptions(descriptionOptions);
    if (foundIds == null) {
      return new ConfigurableHit(nameHits, nameFullHits, Collections.emptySet(), option);
    }

    List<Configurable> contentHits = filterById(effectiveConfigurables, foundIds);

    if (type == DocumentEvent.EventType.CHANGE && previouslyFiltered != null && effectiveConfigurables.size() == contentHits.size()) {
      return getConfigurables(groups, DocumentEvent.EventType.CHANGE, null, option, project);
    }
    return new ConfigurableHit(nameHits, nameFullHits, new LinkedHashSet<>(contentHits), option);
  }

  private static @Nullable ConfigurableHit findGroupsByPath(@NotNull List<? extends ConfigurableGroup> groups, @NotNull String path) {
    List<String> split = parseSettingsPath(path);
    if (split == null || split.isEmpty()) return null;

    ConfigurableGroup root = ContainerUtil.getOnlyItem(groups);
    List<Configurable> topLevel;
    if (root instanceof SearchableConfigurable &&
        ((SearchableConfigurable)root).getId().equals(ConfigurableExtensionPointUtil.ROOT_CONFIGURABLE_ID)) {
      topLevel = Arrays.asList(root.getConfigurables());
    }
    else {
      topLevel = ContainerUtil.filterIsInstance(groups, Configurable.class);
    }

    List<Configurable> current = topLevel;
    Configurable lastMatched = null;
    int lastMatchedIndex = -1;

    for (int i = 0; i < split.size(); i++) {
      String option = split.get(i);
      Configurable matched = ContainerUtil.find(current, group -> StringUtil.equalsIgnoreCase(group.getDisplayName(), option));
      if (matched == null) break;

      lastMatched = matched;
      lastMatchedIndex = i;

      if (matched instanceof Configurable.Composite) {
        current = Arrays.asList(((Configurable.Composite)matched).getConfigurables());
      }
      else {
        break;
      }
    }
    if (lastMatched == null) return null;

    String spotlightFilter = lastMatchedIndex + 1 < split.size()
                             ? StringUtil.join(split.subList(lastMatchedIndex + 1, split.size()), " ")
                             : "";

    Set<Configurable> hits = Collections.singleton(lastMatched);
    return new ConfigurableHit(hits, hits, hits, spotlightFilter);
  }

  private static @Nullable List<String> parseSettingsPath(@NotNull String path) {
    if (!path.contains(SETTINGS_GROUP_SEPARATOR)) return null;

    @NotNull List<String> split = ContainerUtil.map(StringUtil.split(path, SETTINGS_GROUP_SEPARATOR), String::trim);
    List<@NlsSafe String> prefixes = Arrays.asList("IntelliJ IDEA",
                                                   ApplicationNamesInfo.getInstance().getFullProductName(),
                                                   "Settings",
                                                   "Preferences",
                                                   "File | Settings",
                                                   CommonBundle.message("action.settings.path"),
                                                   CommonBundle.message("action.settings.path.mac"),
                                                   CommonBundle.message("action.settings.path.macOS.ventura"));
    for (String prefix : prefixes) {
      split = skipPrefixIfNeeded(prefix, split);
    }
    return split;
  }

  private static @NotNull List<String> skipPrefixIfNeeded(@NotNull String prefix, @NotNull List<String> split) {
    if (split.isEmpty()) return split;

    List<String> prefixSplit = ContainerUtil.map(StringUtil.split(prefix, SETTINGS_GROUP_SEPARATOR), String::trim);
    if (split.size() < prefixSplit.size()) return split;

    for (int i = 0; i < prefixSplit.size(); i++) {
      if (!StringUtil.equalsIgnoreCase(split.get(i), prefixSplit.get(i))) return split;
    }

    return split.subList(prefixSplit.size(), split.size());
  }

  private static @NotNull List<Configurable> filterById(@NotNull Set<Configurable> configurables, @NotNull Set<String> configurableIds) {
    return ContainerUtil.filter(configurables, configurable -> {
      if (configurable instanceof SearchableConfigurable &&
          configurableIds.contains(((SearchableConfigurable)configurable).getId())) {
        return true;
      }
      if (configurable instanceof SearchableConfigurable.Merged) {
        final List<Configurable> mergedConfigurables = ((SearchableConfigurable.Merged)configurable).getMergedConfigurables();
        for (Configurable mergedConfigurable : mergedConfigurables) {
          if (mergedConfigurable instanceof SearchableConfigurable &&
              configurableIds.contains(((SearchableConfigurable)mergedConfigurable).getId())) {
            return true;
          }
        }
      }
      return false;
    });
  }

  private @Nullable Set<String> findConfigurablesByDescriptions(@NotNull Set<String> descriptionOptions) {
    Set<String> helpIds = null;
    for (String prefix : descriptionOptions) {
      final Set<OptionDescription> optionIds = getAcceptableDescriptions(prefix);
      if (optionIds == null) {
        return null;
      }

      final Set<String> ids = new HashSet<>();
      for (OptionDescription id : optionIds) {
        ids.add(id.getConfigurableId());
      }
      if (helpIds == null) {
        helpIds = ids;
      }
      helpIds.retainAll(ids);
    }
    return helpIds;
  }

  public synchronized @Nullable Set<OptionDescription> getAcceptableDescriptions(@Nullable String prefix) {
    if (prefix == null) {
      return null;
    }

    final String stemmedPrefix = PorterStemmerUtil.stem(prefix);
    if (StringUtil.isEmptyOrSpaces(stemmedPrefix)) {
      return null;
    }

    initialize();

    Set<OptionDescription> result = null;
    for (Map.Entry<CharSequence, long[]> entry : storage.entrySet()) {
      final long[] descriptions = entry.getValue();
      final CharSequence option = entry.getKey();
      if (!StringUtil.startsWith(option, prefix) && !StringUtil.startsWith(option, stemmedPrefix)) {
        final String stemmedOption = PorterStemmerUtil.stem(option.toString());
        if (stemmedOption != null && !stemmedOption.startsWith(prefix) && !stemmedOption.startsWith(stemmedPrefix)) {
          continue;
        }
      }
      if (result == null) {
        result = new HashSet<>();
      }
      for (long description : descriptions) {
        OptionDescription desc = unpack(description);
        result.add(desc);
      }
    }
    return result;
  }

  private @Nullable Set<OptionDescription> getOptionDescriptionsByWords(SearchableConfigurable configurable, Set<String> words) {
    Set<OptionDescription> path = null;

    for (String word : words) {
      Set<OptionDescription> configs = getAcceptableDescriptions(word);
      if (configs == null) return null;

      final Set<OptionDescription> paths = new HashSet<>();
      for (OptionDescription config : configs) {
        if (Comparing.strEqual(config.getConfigurableId(), configurable.getId())) {
          paths.add(config);
        }
      }
      if (path == null) {
        path = paths;
      }
      path.retainAll(paths);
    }
    return path;
  }

  @Override
  public @NotNull Set<String> getInnerPaths(SearchableConfigurable configurable, String option) {
    initialize();
    final Set<String> words = getProcessedWordsWithoutStemming(option);
    final Set<OptionDescription> path = getOptionDescriptionsByWords(configurable, words);

    Set<String> resultSet = new HashSet<>();
    if (path != null && !path.isEmpty()) {
      OptionDescription theOnlyResult = null;
      for (OptionDescription description : path) {
        final String hit = description.getHit();
        if (hit != null) {
          boolean theBest = true;
          for (String word : words) {
            if (!StringUtil.containsIgnoreCase(hit, word)) {
              theBest = false;
              break;
            }
          }
          if (theBest) {
            resultSet.add(description.getPath());
          }
        }
        theOnlyResult = description;
      }

      if (resultSet.isEmpty()) {
        resultSet.add(theOnlyResult.getPath());
      }
    }

    return resultSet;
  }

  @Override
  public boolean isStopWord(String word) {
    return stopWords.contains(word);
  }

  @Override
  public @NotNull Set<String> getProcessedWordsWithoutStemming(@NotNull String text) {
    Set<String> result = new HashSet<>();
    collectProcessedWordsWithoutStemming(text, result, stopWords);
    return result;
  }

  @ApiStatus.Internal
  public static void collectProcessedWordsWithoutStemming(@NotNull String text,
                                                          @NotNull Set<? super String> result,
                                                          @NotNull Set<String> stopWords) {
    for (String opt : WORD_SEPARATOR_CHARS.split(Strings.toLowerCase(text))) {
      if (stopWords.contains(opt)) {
        continue;
      }

      String processed = PorterStemmerUtil.stem(opt);
      if (stopWords.contains(processed)) {
        continue;
      }

      result.add(opt);
    }
  }

  @Override
  public Set<String> getProcessedWords(@NotNull String text) {
    Set<String> result = new HashSet<>();
    collectProcessedWords(text, result, stopWords);
    return result;
  }

  static void collectProcessedWords(@NotNull String text, @NotNull Set<? super String> result, @NotNull Set<String> stopWords) {
    String toLowerCase = StringUtil.toLowerCase(text);
    final String[] options = WORD_SEPARATOR_CHARS.split(toLowerCase);
    for (String opt : options) {
      if (stopWords.contains(opt)) {
        continue;
      }
      opt = PorterStemmerUtil.stem(opt);
      if (opt == null) {
        continue;
      }
      result.add(opt);
    }
  }

  @Override
  public @NotNull Set<String> replaceSynonyms(@NotNull Set<String> options, @NotNull SearchableConfigurable configurable) {
    if (highlightOptionToSynonym.isEmpty()) {
      return options;
    }

    Set<String> result = new HashSet<>(options);
    initialize();
    for (String option : options) {
      Set<String> synonyms = highlightOptionToSynonym.get(new Pair<>(option, configurable.getId()));
      if (synonyms != null) {
        result.addAll(synonyms);
      }
      else {
        result.add(option);
      }
    }
    return result;
  }
}
