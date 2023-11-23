// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.ui.search;

import com.intellij.application.options.OptionsContainingConfigurable;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.actions.ShowSettingsUtilImpl;
import com.intellij.ide.fileTemplates.FileTemplate;
import com.intellij.ide.fileTemplates.FileTemplateManager;
import com.intellij.ide.fileTemplates.impl.AllFileTemplatesConfigurable;
import com.intellij.ide.fileTemplates.impl.BundledFileTemplate;
import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.ide.plugins.PluginManagerConfigurable;
import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.ex.ActionManagerEx;
import com.intellij.openapi.actionSystem.impl.ActionManagerImpl;
import com.intellij.openapi.application.ApplicationStarter;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.keymap.impl.ui.KeymapPanel;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.options.UnnamedConfigurable;
import com.intellij.openapi.options.ex.ConfigurableWrapper;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.PathUtil;
import com.intellij.util.io.URLUtil;
import com.intellij.util.ui.EdtInvocationManager;
import org.jdom.Element;
import org.jdom.IllegalDataException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Used in installer's "build searchable options" step.
 *
 * In order to run locally, use "TraverseUi" run configuration (pass corresponding "idea.platform.prefix" property via VM options,
 * and choose correct main module).
 *
 * Pass {@code true} as the second parameter to have searchable options split by modules.
 */
@SuppressWarnings({"CallToPrintStackTrace", "UseOfSystemOutOrSystemErr"})
public final class TraverseUIStarter implements ApplicationStarter {
  private static final @NonNls String OPTIONS = "options";
  private static final @NonNls String CONFIGURABLE = "configurable";
  private static final @NonNls String ID = "id";
  private static final @NonNls String CONFIGURABLE_NAME = "configurable_name";
  private static final @NonNls String OPTION = "option";
  private static final @NonNls String NAME = "name";
  private static final @NonNls String PATH = "path";
  private static final @NonNls String HIT = "hit";

  private static final String ROOT_ACTION_MODULE = "intellij.platform.ide";

  private String OUTPUT_PATH;
  private boolean SPLIT_BY_RESOURCE_PATH;
  private boolean I18N_OPTION;

  @Override
  public String getCommandName() {
    return "traverseUI";
  }

  @Override
  public int getRequiredModality() {
    return NOT_IN_EDT;
  }

  @Override
  public void premain(@NotNull List<String> args) {
    OUTPUT_PATH = args.get(1);
    SPLIT_BY_RESOURCE_PATH = args.size() > 2 && Boolean.parseBoolean(args.get(2));
    I18N_OPTION = Boolean.getBoolean("intellij.searchableOptions.i18n.enabled");
  }

  @Override
  public void main(@NotNull List<String> args) {
    try {
      startup(Path.of(OUTPUT_PATH), SPLIT_BY_RESOURCE_PATH, I18N_OPTION);
      System.out.println("Searchable options index builder completed");
      System.exit(0);
    }
    catch (Throwable e) {
      try {
        Logger.getInstance(getClass()).error("Searchable options index builder failed", e);
      }
      catch (Throwable ignored) {
      }
      System.exit(-1);
    }
  }

  public static void startup(@NotNull Path outputPath, boolean splitByResourcePath) throws IOException {
    startup(outputPath, splitByResourcePath, false);
  }

  public static void startup(@NotNull Path outputPath, boolean splitByResourcePath, boolean i18n) throws IOException {
    Map<SearchableConfigurable, Set<OptionDescription>> options = new LinkedHashMap<>();
    Map<String, Element> roots = new HashMap<>();
    try {
      EdtInvocationManager.invokeAndWaitIfNeeded(() -> {
        for (TraverseUIHelper extension : TraverseUIHelper.helperExtensionPoint.getExtensionList()) {
          extension.beforeStart();
        }

        SearchUtil.processConfigurables(ShowSettingsUtilImpl.getConfigurables(ProjectManager.getInstance().getDefaultProject(), true, false), options, i18n);

        for (TraverseUIHelper extension : TraverseUIHelper.helperExtensionPoint.getExtensionList()) {
          extension.afterTraversal(options);
        }
      });

      System.out.println("Found " + options.size() + " configurables");

      for (SearchableConfigurable configurable : options.keySet()) {
        try {
          addOptions(configurable, options, roots, splitByResourcePath);
        }
        catch (IllegalDataException jdomValidationException) {
          var exception = new IllegalStateException(
            "Unable to process configurable '" + configurable.getId() +
            "', please check strings used in class: " + configurable.getOriginalClass().getCanonicalName()
          );
          exception.addSuppressed(jdomValidationException);
          throw exception;
        }
      }
    }
    finally {
      EdtInvocationManager.invokeAndWaitIfNeeded(() -> {
        for (SearchableConfigurable configurable : options.keySet()) {
          configurable.disposeUIResources();
        }
      });
    }

    saveResults(outputPath, roots);
  }

  private static void addOptions(SearchableConfigurable configurable,
                                 Map<SearchableConfigurable, Set<OptionDescription>> options,
                                 Map<String, Element> roots,
                                 boolean splitByResourcePath) {
    Element configurableElement = createConfigurableElement(configurable);
    writeOptions(configurableElement, options.get(configurable));

    if (configurable instanceof ConfigurableWrapper) {
      UnnamedConfigurable wrapped = ((ConfigurableWrapper)configurable).getConfigurable();
      if (wrapped instanceof SearchableConfigurable) {
        configurable = (SearchableConfigurable)wrapped;
      }
    }
    if (configurable instanceof KeymapPanel) {
      for (Map.Entry<String, Set<OptionDescription>> entry : processKeymap(splitByResourcePath).entrySet()) {
        Element entryElement = createConfigurableElement(configurable);
        writeOptions(entryElement, entry.getValue());
        addElement(roots, entryElement, entry.getKey());
      }
    }
    else if (configurable instanceof OptionsContainingConfigurable) {
      processOptionsContainingConfigurable((OptionsContainingConfigurable)configurable, configurableElement);
    }
    else if (configurable instanceof PluginManagerConfigurable) {
      TreeSet<OptionDescription> optionDescriptions = new TreeSet<>();
      wordsToOptionDescriptors(Collections.singleton(IdeBundle.message("plugin.manager.repositories")), null, optionDescriptions);
      for (OptionDescription description : optionDescriptions) {
        configurableElement.addContent(
          createOptionElement(null, IdeBundle.message("plugin.manager.repositories"), description.getOption()));
      }
    }
    else if (configurable instanceof AllFileTemplatesConfigurable) {
      for (Map.Entry<String, Set<OptionDescription>> entry : processFileTemplates(splitByResourcePath).entrySet()) {
        Element entryElement = createConfigurableElement(configurable);
        writeOptions(entryElement, entry.getValue());
        addElement(roots, entryElement, entry.getKey());
      }
    }

    String module = splitByResourcePath ? getModuleByClass(configurable.getOriginalClass()) : "";
    addElement(roots, configurableElement, module);
  }

  private static void saveResults(@NotNull Path outputPath, Map<String, Element> roots) throws IOException {
    for (Map.Entry<String, Element> entry : roots.entrySet()) {
      String module = entry.getKey();
      Path output;
      if (module.isEmpty()) {
        output = outputPath.resolve(SearchableOptionsRegistrar.getSearchableOptionsXmlName());
      }
      else {
        Path moduleDir = outputPath.resolve(module);
        Files.deleteIfExists(moduleDir.resolve("classpath.index"));
        output = moduleDir.resolve("search/" + module + '.' + SearchableOptionsRegistrar.getSearchableOptionsXmlName());
      }
      JDOMUtil.write(entry.getValue(), output);
      System.out.println("Output written to " + output);
    }

    for (TraverseUIHelper extension : TraverseUIHelper.helperExtensionPoint.getExtensionList()) {
      extension.afterResultsAreSaved();
    }
  }

  private static @NotNull Element createConfigurableElement(@NotNull SearchableConfigurable configurable) {
    Element configurableElement = new Element(CONFIGURABLE);
    configurableElement.setAttribute(ID, configurable.getId());
    configurableElement.setAttribute(CONFIGURABLE_NAME, configurable.getDisplayName());
    return configurableElement;
  }

  private static void addElement(@NotNull Map<String, Element> roots, @NotNull Element element, @NotNull String module) {
    roots.computeIfAbsent(module, __ -> new Element(OPTIONS)).addContent(element);
  }

  private static @NotNull Map<String, Set<OptionDescription>> processFileTemplates(boolean splitByResourcePath) {
    SearchableOptionsRegistrar optionsRegistrar = SearchableOptionsRegistrar.getInstance();
    Map<String, Set<OptionDescription>> options = new HashMap<>();
    FileTemplateManager fileTemplateManager = FileTemplateManager.getDefaultInstance();
    processTemplates(optionsRegistrar, options, fileTemplateManager.getAllTemplates(), splitByResourcePath);
    processTemplates(optionsRegistrar, options, fileTemplateManager.getAllPatterns(), splitByResourcePath);
    processTemplates(optionsRegistrar, options, fileTemplateManager.getAllCodeTemplates(), splitByResourcePath);
    processTemplates(optionsRegistrar, options, fileTemplateManager.getAllJ2eeTemplates(), splitByResourcePath);
    return options;
  }

  private static void processTemplates(SearchableOptionsRegistrar registrar,
                                       Map<String, Set<OptionDescription>> options,
                                       FileTemplate[] templates,
                                       boolean splitByResourcePath) {
    for (FileTemplate template : templates) {
      String module =
        splitByResourcePath && template instanceof BundledFileTemplate ? getModuleByTemplate((BundledFileTemplate)template) : "";
      collectOptions(registrar, options.computeIfAbsent(module, __ -> new TreeSet<>()), template.getName(), null);
    }
  }

  private static @NotNull String getModuleByTemplate(@NotNull BundledFileTemplate template) {
    final String url = template.toString();
    String path = StringUtil.substringBefore(url, "fileTemplates");
    assert path != null : "Template URL doesn't contain 'fileTemplates' directory.";
    if (path.startsWith(URLUtil.JAR_PROTOCOL)) {
      path = StringUtil.trimEnd(path, URLUtil.JAR_SEPARATOR);
    }
    return PathUtil.getFileName(path);
  }

  private static void collectOptions(SearchableOptionsRegistrar registrar, Set<? super OptionDescription> options, @NotNull String text, String path) {
    for (@NlsSafe String word : registrar.getProcessedWordsWithoutStemming(text)) {
      options.add(new OptionDescription(word, text, path));
    }
  }

  private static void processOptionsContainingConfigurable(OptionsContainingConfigurable configurable, Element configurableElement) {
    Set<String> optionsPath = configurable.processListOptions();
    Set<OptionDescription> result = new TreeSet<>();
    wordsToOptionDescriptors(optionsPath, null, result);
    Map<String, Set<String>> optionsWithPaths = configurable.processListOptionsWithPaths();
    for (String path : optionsWithPaths.keySet()) {
      wordsToOptionDescriptors(optionsWithPaths.get(path), path, result);
    }
    writeOptions(configurableElement, result);
  }

  private static void wordsToOptionDescriptors(@NotNull Set<String> optionsPath,
                                               @Nullable String path,
                                               @NotNull Set<? super OptionDescription> result) {
    SearchableOptionsRegistrar registrar = SearchableOptionsRegistrar.getInstance();
    for (String opt : optionsPath) {
      for (@NlsSafe String word : registrar.getProcessedWordsWithoutStemming(opt)) {
        if (word != null) {
          result.add(new OptionDescription(word, opt, path));
        }
      }
    }
  }

  private static @NotNull Map<String, Set<OptionDescription>> processKeymap(boolean splitByResourcePath) {
    Map<String, Set<OptionDescription>> map = new LinkedHashMap<>();
    ActionManagerImpl actionManager = (ActionManagerImpl)ActionManager.getInstance();
    Map<String, PluginId> actionToPluginId = splitByResourcePath ? getActionToPluginId() : Collections.emptyMap();
    String componentName = "ActionManager";
    SearchableOptionsRegistrar searchableOptionsRegistrar = SearchableOptionsRegistrar.getInstance();
    for (Iterator<AnAction> iterator = actionManager.actions(false).iterator(); iterator.hasNext(); ) {
      AnAction action = iterator.next();
      String module = splitByResourcePath ? getModuleByAction(action, actionToPluginId) : "";
      Set<OptionDescription> options = map.computeIfAbsent(module, __ -> new TreeSet<>());
      String text = action.getTemplatePresentation().getText();
      if (text != null) {
        collectOptions(searchableOptionsRegistrar, options, text, componentName);
      }

      String description = action.getTemplatePresentation().getDescription();
      if (description != null) {
        collectOptions(searchableOptionsRegistrar, options, description, componentName);
      }
    }
    return map;
  }

  @NotNull
  private static Map<String, PluginId> getActionToPluginId() {
    ActionManagerEx actionManager = ActionManagerEx.getInstanceEx();
    Map<String, PluginId> actionToPluginId = new HashMap<>();
    for (PluginId id : PluginId.getRegisteredIds()) {
      for (String action : actionManager.getPluginActions(id)) {
        actionToPluginId.put(action, id);
      }
    }
    return actionToPluginId;
  }

  @NotNull
  private static String getModuleByAction(@NotNull final AnAction rootAction, @NotNull final Map<String, PluginId> actionToPluginId) {
    final Deque<AnAction> actions = new ArrayDeque<>();
    actions.add(rootAction);
    while (!actions.isEmpty()) {
      final AnAction action = actions.remove();
      final String module = getModuleByClass(action.getClass());
      if (!ROOT_ACTION_MODULE.equals(module)) {
        return module;
      }
      if (action instanceof ActionGroup) {
        Collections.addAll(actions, ((ActionGroup)action).getChildren(null));
      }
    }
    final ActionManager actionManager = ActionManager.getInstance();
    final PluginId id = actionToPluginId.get(actionManager.getId(rootAction));
    if (id != null) {
      final IdeaPluginDescriptor plugin = PluginManagerCore.getPlugin(id);
      if (plugin != null && !plugin.getName().equals(PluginManagerCore.SPECIAL_IDEA_PLUGIN_ID.getIdString())) {
        return PathUtil.getFileName(plugin.getPluginPath().toString());
      }
    }
    return ROOT_ACTION_MODULE;
  }

  @NotNull
  private static String getModuleByClass(@NotNull final Class<?> aClass) {
    return PathUtil.getFileName(PathUtil.getJarPathForClass(aClass));
  }

  private static void writeOptions(@NotNull Element configurableElement, @NotNull Set<? extends OptionDescription> options) {
    for (OptionDescription opt : options) {
      configurableElement.addContent(createOptionElement(opt.getPath(), opt.getHit(), opt.getOption()));
    }
  }

  private static @NotNull Element createOptionElement(String path, String hit, String word) {
    Element optionElement = new Element(OPTION);
    optionElement.setAttribute(NAME, word);
    if (path != null) {
      optionElement.setAttribute(PATH, path);
    }
    optionElement.setAttribute(HIT, hit);
    return optionElement;
  }
}