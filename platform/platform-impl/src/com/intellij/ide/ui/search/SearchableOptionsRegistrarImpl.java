// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.ui.search;

import com.intellij.ide.plugins.DynamicPluginListener;
import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurableGroup;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.util.text.Strings;
import com.intellij.util.CollectConsumer;
import com.intellij.util.ResourceUtil;
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
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.regex.Pattern;

@SuppressWarnings("Duplicates")
public final class SearchableOptionsRegistrarImpl extends SearchableOptionsRegistrar {
  private static final ExtensionPointName<SearchableOptionContributor> EP_NAME = new ExtensionPointName<>("com.intellij.search.optionContributor");

  // option => array of packed OptionDescriptor
  private volatile Map<CharSequence, long[]> storage = Collections.emptyMap();

  private final Set<String> stopWords;

  private volatile @NotNull Map<Pair<String, String>, Set<String>> highlightOptionToSynonym = Collections.emptyMap();

  private final AtomicBoolean isInitialized = new AtomicBoolean();

  private volatile IndexedCharsInterner identifierTable = new IndexedCharsInterner();

  private static final Logger LOG = Logger.getInstance(SearchableOptionsRegistrarImpl.class);
  @NonNls
  private static final Pattern REG_EXP = Pattern.compile("[^\\pL&&[^-]]+");

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
    EP_NAME.forEachExtensionSafe(contributor -> contributor.processOptions(processor));

    // index
    highlightOptionToSynonym = processor.computeHighlightOptionToSynonym();

    storage = processor.getStorage();
    identifierTable = processor.getIdentifierTable();
  }

  static void processSearchableOptions(@NotNull Predicate<String> fileNameFilter, @NotNull BiConsumer<String, Element> consumer) {
    Set<ClassLoader> visited = Collections.newSetFromMap(new IdentityHashMap<>());
    for (IdeaPluginDescriptor plugin : PluginManagerCore.getPluginSet().getRawListOfEnabledModules()) {
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
            throw new RuntimeException(e);
          }
        });
      }
      catch (IOException e) {
        throw new RuntimeException(e);
      }
    }
  }

  /**
   * @return XYZT:64 bits where X:16 bits - id of the interned groupName
   *                            Y:16 bits - id of the interned id
   *                            Z:16 bits - id of the interned hit
   *                            T:16 bits - id of the interned path
   */
  @SuppressWarnings("SpellCheckingInspection")
  static long pack(@NotNull String id, @Nullable String hit, @Nullable String path, @Nullable String groupName, @NotNull IndexedCharsInterner identifierTable) {
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
                                                   @Nullable Set<? extends Configurable> configurables,
                                                   @NotNull String option,
                                                   @Nullable Project project) {
    //noinspection unchecked
    return findConfigurables(groups, type, (Collection<Configurable>)configurables, option, project);
  }

  private @NotNull ConfigurableHit findConfigurables(@NotNull List<? extends ConfigurableGroup> groups,
                                                     DocumentEvent.EventType type,
                                                     @Nullable Collection<Configurable> configurables,
                                                     @NotNull String option,
                                                     @Nullable Project project) {
    if (configurables == null || configurables.isEmpty()) {
      configurables = null;
    }

    Collection<Configurable> effectiveConfigurables;
    if (configurables == null) {
      effectiveConfigurables = new LinkedHashSet<>();
      Consumer<Configurable> consumer = new CollectConsumer<>(effectiveConfigurables);
      for (ConfigurableGroup group : groups) {
        SearchUtil.processExpandedGroups(group, consumer);
      }
    }
    else {
      effectiveConfigurables = configurables;
    }

    String optionToCheck = Strings.toLowerCase(option.trim());
    Set<String> options = getProcessedWordsWithoutStemming(optionToCheck);

    Set<Configurable> nameHits = new LinkedHashSet<>();
    Set<Configurable> nameFullHits = new LinkedHashSet<>();

    for (Configurable each : effectiveConfigurables) {
      if (each.getDisplayName() == null) {
        continue;
      }
      final String displayName = Strings.toLowerCase(each.getDisplayName());
      final List<String> allWords = StringUtil.getWordsIn(displayName);
      if (displayName.contains(optionToCheck)) {
        nameFullHits.add(each);
        nameHits.add(each);
      }
      for (String eachWord : allWords) {
        if (eachWord.startsWith(optionToCheck)) {
          nameHits.add(each);
          break;
        }
      }

      if (options.isEmpty()) {
        nameHits.add(each);
        nameFullHits.add(each);
      }
    }

    Set<Configurable> currentConfigurables = type == DocumentEvent.EventType.CHANGE ? new HashSet<>(effectiveConfigurables) : null;
    // operate with substring
    if (options.isEmpty()) {
      String[] components = REG_EXP.split(optionToCheck);
      if (components.length > 0) {
        Collections.addAll(options, components);
      }
      else {
        options.add(option);
      }
    }

    Set<Configurable> contentHits;
    if (configurables == null) {
      contentHits = (Set<Configurable>)effectiveConfigurables;
    }
    else {
      contentHits = new LinkedHashSet<>(effectiveConfigurables);
    }

    Set<String> helpIds = null;
    for (String opt : options) {
      final Set<OptionDescription> optionIds = getAcceptableDescriptions(opt);
      if (optionIds == null) {
        contentHits.clear();
        return new ConfigurableHit(nameHits, nameFullHits, contentHits);
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

    if (helpIds != null) {
      for (Iterator<Configurable> it = contentHits.iterator(); it.hasNext();) {
        Configurable configurable = it.next();
        boolean needToRemove = true;
        if (configurable instanceof SearchableConfigurable && helpIds.contains(((SearchableConfigurable)configurable).getId())) {
          needToRemove = false;
        }
        if (configurable instanceof SearchableConfigurable.Merged) {
          final List<Configurable> mergedConfigurables = ((SearchableConfigurable.Merged)configurable).getMergedConfigurables();
          for (Configurable mergedConfigurable : mergedConfigurables) {
            if (mergedConfigurable instanceof SearchableConfigurable &&
                helpIds.contains(((SearchableConfigurable)mergedConfigurable).getId())) {
              needToRemove = false;
              break;
            }
          }
        }
        if (needToRemove) {
          it.remove();
        }
      }
    }

    if (type == DocumentEvent.EventType.CHANGE && configurables != null && currentConfigurables.equals(contentHits)) {
      return getConfigurables(groups, DocumentEvent.EventType.CHANGE, null, option, project);
    }
    return new ConfigurableHit(nameHits, nameFullHits, contentHits);
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

      if (resultSet.isEmpty())
        resultSet.add(theOnlyResult.getPath());
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
  public static void collectProcessedWordsWithoutStemming(@NotNull String text, @NotNull Set<? super String> result, @NotNull Set<String> stopWords) {
    for (String opt : REG_EXP.split(Strings.toLowerCase(text))) {
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
    final String[] options = REG_EXP.split(toLowerCase);
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
