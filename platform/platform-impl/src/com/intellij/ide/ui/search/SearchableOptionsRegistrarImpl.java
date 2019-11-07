// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.ide.ui.search;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.ide.plugins.PluginManagerConfigurable;
import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ex.ApplicationInfoEx;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurableGroup;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Couple;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.CollectConsumer;
import com.intellij.util.ObjectUtils;
import com.intellij.util.ResourceUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.io.URLUtil;
import com.intellij.util.text.ByteArrayCharSequence;
import com.intellij.util.text.CharSequenceHashingStrategy;
import gnu.trove.THashMap;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.event.DocumentEvent;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.*;
import java.util.function.Consumer;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.regex.Pattern;

@SuppressWarnings("Duplicates")
public class SearchableOptionsRegistrarImpl extends SearchableOptionsRegistrar {
  // option => array of packed OptionDescriptor
  private final Map<CharSequence, long[]> myStorage = new THashMap<>(20, 0.9f, CharSequenceHashingStrategy.CASE_SENSITIVE);

  private final Set<String> myStopWords = Collections.synchronizedSet(new THashSet<>());

  @NotNull
  private volatile Map<Couple<String>, Set<String>> myHighlightOptionToSynonym = Collections.emptyMap();

  private volatile boolean allTheseHugeFilesAreLoaded;

  private final IndexedCharsInterner myIdentifierTable = new IndexedCharsInterner() {
    @Override
    public synchronized int toId(@NotNull String name) {
      return super.toId(name);
    }

    @NotNull
    @Override
    public synchronized CharSequence fromId(int id) {
      return super.fromId(id);
    }
  };

  private static final Logger LOG = Logger.getInstance(SearchableOptionsRegistrarImpl.class);
  @NonNls
  private static final Pattern REG_EXP = Pattern.compile("[\\W&&[^-]]+");

  public SearchableOptionsRegistrarImpl() {
    if (ApplicationManager.getApplication().isCommandLine() ||
        ApplicationManager.getApplication().isUnitTestMode()) return;
    try {
      //stop words
      InputStream stream = ResourceUtil.getResourceAsStream(SearchableOptionsRegistrarImpl.class, "/search/", "ignore.txt");
      if (stream == null) throw new IOException("Broken installation: IDE does not provide /search/ignore.txt");

      String text = ResourceUtil.loadText(stream);
      final String[] stopWords = text.split("[\\W]");
      ContainerUtil.addAll(myStopWords, stopWords);
    }
    catch (IOException e) {
      LOG.error(e);
    }
  }

  private void loadHugeFilesIfNecessary() {
    if (allTheseHugeFilesAreLoaded) {
      return;
    }

    allTheseHugeFilesAreLoaded = true;
    try {
      //index
      final Set<URL> searchableOptions = findSearchableOptions();
      if (searchableOptions.isEmpty()) {
        LOG.info("No /search/searchableOptions.xml found, settings search won't work!");
        return;
      }

      SearchableOptionIndexLoader loader = new SearchableOptionIndexLoader(this, myStorage);
      loader.load(searchableOptions);
      myHighlightOptionToSynonym = loader.getHighlightOptionToSynonym();
    }
    catch (Exception e) {
      LOG.error(e);
    }

    ApplicationInfoEx applicationInfo = ApplicationInfoEx.getInstanceEx();
    for (IdeaPluginDescriptor plugin : PluginManagerCore.getPlugins()) {
      if (applicationInfo.isEssentialPlugin(plugin.getPluginId().getIdString())) {
        continue;
      }
      final String pluginName = plugin.getName();
      final Set<String> words = getProcessedWordsWithoutStemming(pluginName);
      final String description = plugin.getDescription();
      if (description != null) {
        words.addAll(getProcessedWordsWithoutStemming(description));
      }
      addOptions(words, null, pluginName, PluginManagerConfigurable.ID, IdeBundle.message("title.plugins"));
    }
  }

  @NotNull
  private static Set<URL> findSearchableOptions() throws IOException, URISyntaxException {
    final Set<URL> urls = new HashSet<>();
    final Set<ClassLoader> visited = new HashSet<>();
    for (final IdeaPluginDescriptor plugin : PluginManagerCore.getPlugins()) {
      if (!plugin.isEnabled()) {
        continue;
      }
      final ClassLoader classLoader = plugin.getPluginClassLoader();
      if (visited.add(classLoader)) {
        final Enumeration<URL> resources = classLoader.getResources("search");
        while (resources.hasMoreElements()) {
          final URL url = resources.nextElement();
          if (URLUtil.JAR_PROTOCOL.equals(url.getProtocol())) {
            final Pair<String, String> parts = ObjectUtils.notNull(URLUtil.splitJarUrl(url.getFile()));
            final File file = new File(parts.first);
            try (final JarFile jar = new JarFile(file)) {
              final Enumeration<JarEntry> entries = jar.entries();
              while (entries.hasMoreElements()) {
                final String name = entries.nextElement().getName();
                if (name.startsWith("search/") && name.endsWith(SEARCHABLE_OPTIONS_XML) && StringUtil.countChars(name, '/') == 1) {
                  urls.add(URLUtil.getJarEntryURL(file, name));
                }
              }
            }
          }
          else {
            final File file = new File(url.toURI());
            if (file.isDirectory()) {
              final File[] files = file.listFiles((dir, name) -> name.endsWith(SEARCHABLE_OPTIONS_XML));
              if (files != null) {
                for (final File xml : files) {
                  if (xml.isFile()) {
                    urls.add(xml.toURI().toURL());
                  }
                }
              }
            }
          }
        }
      }
    }
    return urls;
  }

  /**
   * @return XYZT:64 bits where X:16 bits - id of the interned groupName
   *                            Y:16 bits - id of the interned id
   *                            Z:16 bits - id of the interned hit
   *                            T:16 bits - id of the interned path
   */
  @SuppressWarnings("SpellCheckingInspection")
  private long pack(@NotNull final String id, @Nullable String hit, @Nullable final String path, @Nullable String groupName) {
    long _id = myIdentifierTable.toId(id.trim());
    long _hit = hit == null ? Short.MAX_VALUE : myIdentifierTable.toId(hit.trim());
    long _path = path == null ? Short.MAX_VALUE : myIdentifierTable.toId(path.trim());
    long _groupName = groupName == null ? Short.MAX_VALUE : myIdentifierTable.toId(groupName.trim());
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

    String groupName = _groupName == Short.MAX_VALUE ? null : myIdentifierTable.fromId(_groupName).toString();
    String configurableId = myIdentifierTable.fromId(_id).toString();
    String hit = _hit == Short.MAX_VALUE ? null : myIdentifierTable.fromId(_hit).toString();
    String path = _path == Short.MAX_VALUE ? null : myIdentifierTable.fromId(_path).toString();

    return new OptionDescription(null, configurableId, hit, path, groupName);
  }

  static void putOptionWithHelpId(@NotNull String option,
                                  @NotNull String id,
                                  @Nullable String groupName,
                                  @Nullable String hit,
                                  @Nullable String path,
                                  @NotNull Map<CharSequence, long[]> storage,
                                  @NotNull SearchableOptionsRegistrarImpl registrar) {
    if (registrar.isStopWord(option)) return;
    String stopWord = PorterStemmerUtil.stem(option);
    if (stopWord == null) return;
    if (registrar.isStopWord(stopWord)) return;

    long[] configs = storage.get(option);
    long packed = registrar.pack(id, hit, path, groupName);
    if (configs == null) {
      configs = new long[]{packed};
    }
    else if (ArrayUtil.indexOf(configs, packed) == -1) {
      configs = ArrayUtil.append(configs, packed);
    }
    storage.put(ByteArrayCharSequence.convertToBytesIfPossible(option), configs);
  }

  @Override
  @NotNull
  public ConfigurableHit getConfigurables(@NotNull List<? extends ConfigurableGroup> groups,
                                          final DocumentEvent.EventType type,
                                          @Nullable Set<? extends Configurable> configurables,
                                          @NotNull String option,
                                          @Nullable Project project) {
    //noinspection unchecked
    return findConfigurables(groups, type, (Collection<Configurable>)configurables, option, project);
  }

  @NotNull
  private ConfigurableHit findConfigurables(@NotNull List<? extends ConfigurableGroup> groups,
                                            final DocumentEvent.EventType type,
                                            @Nullable Collection<Configurable> configurables,
                                            @NotNull String option,
                                            @Nullable Project project) {
    if (ContainerUtil.isEmpty(configurables)) {
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

    String optionToCheck = StringUtil.toLowerCase(option.trim());
    Set<String> options = getProcessedWordsWithoutStemming(optionToCheck);

    Set<Configurable> nameHits = new LinkedHashSet<>();
    Set<Configurable> nameFullHits = new LinkedHashSet<>();

    for (Configurable each : effectiveConfigurables) {
      if (each.getDisplayName() == null) continue;
      final String displayName = StringUtil.toLowerCase(each.getDisplayName());
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

    Set<Configurable> currentConfigurables = type == DocumentEvent.EventType.CHANGE ? new THashSet<>(effectiveConfigurables) : null;
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
        if (!(configurable instanceof SearchableConfigurable && helpIds.contains(((SearchableConfigurable)configurable).getId()))) {
          it.remove();
        }
      }
    }

    if (type == DocumentEvent.EventType.CHANGE && configurables != null && currentConfigurables.equals(contentHits)) {
      return getConfigurables(groups, DocumentEvent.EventType.CHANGE, null, option, project);
    }
    return new ConfigurableHit(nameHits, nameFullHits, contentHits);
  }

  @Nullable
  public synchronized Set<OptionDescription> getAcceptableDescriptions(@Nullable String prefix) {
    if (prefix == null) {
      return null;
    }

    final String stemmedPrefix = PorterStemmerUtil.stem(prefix);
    if (StringUtil.isEmptyOrSpaces(stemmedPrefix)) {
      return null;
    }

    loadHugeFilesIfNecessary();

    Set<OptionDescription> result = null;
    for (Map.Entry<CharSequence, long[]> entry : myStorage.entrySet()) {
      final long[] descriptions = entry.getValue();
      final CharSequence option = entry.getKey();
      if (!StringUtil.startsWith(option, prefix) && !StringUtil.startsWith(option, stemmedPrefix)) {
        final String stemmedOption = PorterStemmerUtil.stem(option.toString());
        if (stemmedOption != null && !stemmedOption.startsWith(prefix) && !stemmedOption.startsWith(stemmedPrefix)) {
          continue;
        }
      }
      if (result == null) {
        result = new THashSet<>();
      }
      for (long description : descriptions) {
        OptionDescription desc = unpack(description);
        result.add(desc);
      }
    }
    return result;
  }

  @Override
  @Nullable
  public String getInnerPath(SearchableConfigurable configurable, @NonNls String option) {
    loadHugeFilesIfNecessary();
    Set<OptionDescription> path = null;
    final Set<String> words = getProcessedWordsWithoutStemming(option);
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
    if (path == null || path.isEmpty()) {
      return null;
    }
    else {
      OptionDescription result = null;
      for (OptionDescription description : path) {
        final String hit = description.getHit();
        if (hit != null) {
          boolean theBest = true;
          for (String word : words) {
            if (!hit.contains(word)) {
              theBest = false;
              break;
            }
          }
          if (theBest) return description.getPath();
        }
        result = description;
      }
      return result.getPath();
    }
  }

  @Override
  public boolean isStopWord(String word) {
    return myStopWords.contains(word);
  }

  @Override
  public synchronized void addOption(@NotNull String option, @Nullable String path, @NotNull String hit, @NotNull String configurableId, final String configurableDisplayName) {
    putOptionWithHelpId(option, configurableId, configurableDisplayName, hit, path, myStorage, this);
  }

  @Override
  public synchronized void addOptions(@NotNull Collection<String> words, @Nullable String path, String hit, @NotNull String configurableId, String configurableDisplayName) {
    for (String word : words) {
      putOptionWithHelpId(word, configurableId, configurableDisplayName, hit, path, myStorage, this);
    }
  }

  @Override
  public Set<String> getProcessedWordsWithoutStemming(@NotNull String text) {
    Set<String> result = new THashSet<>();
    for (String opt : REG_EXP.split(StringUtil.toLowerCase(text))) {
      if (isStopWord(opt)) {
        continue;
      }

      String processed = PorterStemmerUtil.stem(opt);
      if (isStopWord(processed)) {
        continue;
      }

      result.add(opt);
    }
    return result;
  }

  @Override
  public Set<String> getProcessedWords(@NotNull String text) {
    Set<String> result = new THashSet<>();
    String toLowerCase = StringUtil.toLowerCase(text);
    final String[] options = REG_EXP.split(toLowerCase);
    for (String opt : options) {
      if (isStopWord(opt)) continue;
      opt = PorterStemmerUtil.stem(opt);
      if (opt == null) continue;
      result.add(opt);
    }
    return result;
  }

  @NotNull
  @Override
  public Set<String> replaceSynonyms(@NotNull Set<String> options, @NotNull SearchableConfigurable configurable) {
    if (myHighlightOptionToSynonym.isEmpty()) {
      return options;
    }

    Set<String> result = new THashSet<>(options);
    loadHugeFilesIfNecessary();
    for (String option : options) {
      Set<String> synonyms = myHighlightOptionToSynonym.get(Couple.of(option, configurable.getId()));
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
