/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

import com.intellij.application.options.OptionsContainingConfigurable;
import com.intellij.ide.fileTemplates.FileTemplate;
import com.intellij.ide.fileTemplates.FileTemplateManager;
import com.intellij.ide.fileTemplates.impl.AllFileTemplatesConfigurable;
import com.intellij.ide.plugins.AvailablePluginsManagerMain;
import com.intellij.ide.plugins.PluginManagerConfigurable;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.impl.ActionManagerImpl;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ApplicationStarterEx;
import com.intellij.openapi.application.ex.ApplicationEx;
import com.intellij.openapi.keymap.impl.ui.KeymapPanel;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.options.UnnamedConfigurable;
import com.intellij.openapi.options.ex.ConfigurableWrapper;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.util.JDOMUtil;
import org.jdom.Document;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Set;
import java.util.TreeSet;

/**
 * Used by Installer's build buildSearchableOptions step. In order to run locally use TraverseUi configuration.
 * Pass corresponding -Didea.platform.prefix=YOUR_IDE_PREFIX to vm options and choose main_YOUR_IDE module
 */
@SuppressWarnings({"CallToPrintStackTrace", "SynchronizeOnThis"})
public class TraverseUIStarter extends ApplicationStarterEx {
  @NonNls private static final String OPTIONS = "options";
  @NonNls private static final String CONFIGURABLE = "configurable";
  @NonNls private static final String ID = "id";
  @NonNls private static final String CONFIGURABLE_NAME = "configurable_name";
  @NonNls private static final String OPTION = "option";
  @NonNls private static final String NAME = "name";
  @NonNls private static final String PATH = "path";
  @NonNls private static final String HIT = "hit";

  private String OUTPUT_PATH;

  @Override
  public boolean isHeadless() {
    return true;
  }

  @Override
  @NonNls
  public String getCommandName() {
    return "traverseUI";
  }

  @Override
  public void premain(String[] args) {
    OUTPUT_PATH = args[1];
  }

  @Override
  public void main(String[] args){
    System.out.println("Starting searchable options index builder");
    try {
      startup(OUTPUT_PATH);
      ((ApplicationEx)ApplicationManager.getApplication()).exit(true, true);
    }
    catch (Throwable e) {
      System.out.println("Searchable options index builder failed");
      e.printStackTrace();
      System.exit(-1);
    }
  }

  public static void startup(String outputPath) throws IOException {
    final HashMap<SearchableConfigurable, TreeSet<OptionDescription>> options =
      new HashMap<>();
    SearchUtil.processProjectConfigurables(ProjectManager.getInstance().getDefaultProject(), options);
    Element root = new Element(OPTIONS);
    for (SearchableConfigurable configurable : options.keySet()) {
      Element configurableElement = new Element(CONFIGURABLE);
      final String id = configurable.getId();
      if (id == null) continue;
      configurableElement.setAttribute(ID, id);
      configurableElement.setAttribute(CONFIGURABLE_NAME, configurable.getDisplayName());
      final TreeSet<OptionDescription> sortedOptions = options.get(configurable);
      writeOptions(configurableElement, sortedOptions);
      if (configurable instanceof ConfigurableWrapper) {
        final UnnamedConfigurable wrapped = ((ConfigurableWrapper)configurable).getConfigurable();
        if (wrapped instanceof SearchableConfigurable) {
          configurable = (SearchableConfigurable)wrapped;
        }
      }
      if (configurable instanceof KeymapPanel){
        processKeymap(configurableElement);
      } else if (configurable instanceof OptionsContainingConfigurable){
        processOptionsContainingConfigurable((OptionsContainingConfigurable)configurable, configurableElement);
      } else if (configurable instanceof PluginManagerConfigurable) {
        final TreeSet<OptionDescription> descriptions = wordsToOptionDescriptors(Collections.singleton(AvailablePluginsManagerMain.MANAGE_REPOSITORIES));
        for (OptionDescription description : descriptions) {
          append(null, AvailablePluginsManagerMain.MANAGE_REPOSITORIES, description.getOption(), configurableElement);
        }
      } else if (configurable instanceof AllFileTemplatesConfigurable) {
        processFileTemplates(configurableElement);
      }
      root.addContent(configurableElement);
      configurable.disposeUIResources();
    }
    final File file = new File(outputPath);
    if (!file.isFile()) {
      file.getParentFile().mkdirs();
      file.createNewFile();
    }
    JDOMUtil.writeDocument(new Document(root), outputPath, "\n");

    System.out.println("Searchable options index builder completed");
  }

  private static void processFileTemplates(Element configurableElement) {
    final SearchableOptionsRegistrar optionsRegistrar = SearchableOptionsRegistrar.getInstance();
    TreeSet<OptionDescription> options = new TreeSet<>();

    FileTemplateManager fileTemplateManager = FileTemplateManager.getDefaultInstance();
    processTemplates(optionsRegistrar, options, fileTemplateManager.getAllTemplates());
    processTemplates(optionsRegistrar, options, fileTemplateManager.getAllPatterns());
    processTemplates(optionsRegistrar, options, fileTemplateManager.getAllCodeTemplates());
    processTemplates(optionsRegistrar, options, fileTemplateManager.getAllJ2eeTemplates());

    writeOptions(configurableElement, options);
  }

  private static void processTemplates(SearchableOptionsRegistrar optionsRegistrar,
                                       TreeSet<OptionDescription> options,
                                       FileTemplate[] templates) {
    for (FileTemplate template : templates) {
      collectOptions(optionsRegistrar, options, template.getName());
      //collectOptions(optionsRegistrar, options, template.getDescription());
    }
  }

  private static void collectOptions(SearchableOptionsRegistrar optionsRegistrar,
                                     TreeSet<OptionDescription> options,
                                     String text) {
    collectOptions(optionsRegistrar, options, text, null);
  }

  private static void collectOptions(SearchableOptionsRegistrar optionsRegistrar,
                                     TreeSet<OptionDescription> options,
                                     String text,
                                     String path) {
    final Set<String> strings = optionsRegistrar.getProcessedWordsWithoutStemming(text);
    for (String word : strings) {
      options.add(new OptionDescription(word, text, path));
    }
  }

  private static void processOptionsContainingConfigurable(final OptionsContainingConfigurable configurable,
                                                           final Element configurableElement) {
    final Set<String> optionsPath = configurable.processListOptions();
    final TreeSet<OptionDescription> result = wordsToOptionDescriptors(optionsPath);
    writeOptions(configurableElement, result);
  }

  private static TreeSet<OptionDescription> wordsToOptionDescriptors(Set<String> optionsPath) {
    SearchableOptionsRegistrar searchableOptionsRegistrar = SearchableOptionsRegistrar.getInstance();
    final TreeSet<OptionDescription> result = new TreeSet<>();
    for (String opt : optionsPath) {
      final Set<String> words = searchableOptionsRegistrar.getProcessedWordsWithoutStemming(opt);
      for (String word : words) {
        if (word != null){
          result.add(new OptionDescription(word, opt, null));
        }
      }
    }
    return result;
  }

  private static void processKeymap(final Element configurableElement){
    final ActionManager actionManager = ActionManager.getInstance();
    final String componentName = actionManager.getComponentName();
    final SearchableOptionsRegistrar searchableOptionsRegistrar = SearchableOptionsRegistrar.getInstance();
    final Set<String> ids = ((ActionManagerImpl)actionManager).getActionIds();
    final TreeSet<OptionDescription> options = new TreeSet<>();
    for (String id : ids) {
      final AnAction anAction = actionManager.getAction(id);
      final String text = anAction.getTemplatePresentation().getText();
      if (text != null) {
        collectOptions(searchableOptionsRegistrar, options, text, componentName);
      }
      final String description = anAction.getTemplatePresentation().getDescription();
      if (description != null) {
        collectOptions(searchableOptionsRegistrar, options, description, componentName);
      }
    }
    writeOptions(configurableElement, options);
  }

  private static void writeOptions(Element configurableElement, TreeSet<OptionDescription> options) {
    for (OptionDescription opt : options) {
      append(opt.getPath(), opt.getHit(), opt.getOption(), configurableElement);
    }
  }

  private static void append(String path, String hit, final String word, final Element configurableElement) {
    Element optionElement = new Element(OPTION);
    optionElement.setAttribute(NAME, word);
    if (path != null) {
      optionElement.setAttribute(PATH, path);
    }
    optionElement.setAttribute(HIT, hit);
    configurableElement.addContent(optionElement);
  }
}
