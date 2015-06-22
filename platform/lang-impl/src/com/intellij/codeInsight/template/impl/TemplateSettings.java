/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.codeInsight.template.impl;

import com.intellij.AbstractBundle;
import com.intellij.codeInsight.template.Template;
import com.intellij.openapi.application.ex.DecodeDefaultsUtil;
import com.intellij.openapi.components.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.options.BaseSchemeProcessor;
import com.intellij.openapi.options.SchemesManager;
import com.intellij.openapi.options.SchemesManagerFactory;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.util.SmartList;
import com.intellij.util.containers.MultiMap;
import com.intellij.util.xmlb.Converter;
import com.intellij.util.xmlb.annotations.OptionTag;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;

@State(
  name = "TemplateSettings",
  storages = {
    @Storage(file = StoragePathMacros.APP_CONFIG + "/templates.xml"),
    @Storage(file = StoragePathMacros.APP_CONFIG + "/other.xml", deprecated = true)
  },
  additionalExportFile = TemplateSettings.TEMPLATES_DIR_PATH
)
public class TemplateSettings implements PersistentStateComponent<TemplateSettings.State> {
  private static final Logger LOG = Logger.getInstance(TemplateSettings.class);

  @NonNls public static final String USER_GROUP_NAME = "user";
  @NonNls private static final String TEMPLATE_SET = "templateSet";
  @NonNls private static final String GROUP = "group";
  @NonNls private static final String TEMPLATE = "template";

  public static final char SPACE_CHAR = ' ';
  public static final char TAB_CHAR = '\t';
  public static final char ENTER_CHAR = '\n';
  public static final char DEFAULT_CHAR = 'D';
  public static final char CUSTOM_CHAR = 'C';

  @NonNls private static final String SPACE = "SPACE";
  @NonNls private static final String TAB = "TAB";
  @NonNls private static final String ENTER = "ENTER";
  @NonNls private static final String CUSTOM = "CUSTOM";

  @NonNls private static final String NAME = "name";
  @NonNls private static final String VALUE = "value";
  @NonNls private static final String DESCRIPTION = "description";
  @NonNls private static final String SHORTCUT = "shortcut";

  @NonNls private static final String VARIABLE = "variable";
  @NonNls private static final String EXPRESSION = "expression";
  @NonNls private static final String DEFAULT_VALUE = "defaultValue";
  @NonNls private static final String ALWAYS_STOP_AT = "alwaysStopAt";

  @NonNls private static final String CONTEXT = "context";
  @NonNls private static final String TO_REFORMAT = "toReformat";
  @NonNls private static final String TO_SHORTEN_FQ_NAMES = "toShortenFQNames";
  @NonNls private static final String USE_STATIC_IMPORT = "useStaticImport";

  @NonNls private static final String DEACTIVATED = "deactivated";

  @NonNls private static final String RESOURCE_BUNDLE = "resource-bundle";
  @NonNls private static final String KEY = "key";
  @NonNls private static final String ID = "id";

  static final String TEMPLATES_DIR_PATH = StoragePathMacros.ROOT_CONFIG + "/templates";

  private final MultiMap<String, TemplateImpl> myTemplates = MultiMap.createLinked();

  private final Map<String, Template> myTemplatesById = new LinkedHashMap<String, Template>();
  private final Map<TemplateKey, TemplateImpl> myDefaultTemplates = new LinkedHashMap<TemplateKey, TemplateImpl>();

  private int myMaxKeyLength = 0;
  private final SchemesManager<TemplateGroup, TemplateGroup> mySchemesManager;

  private State myState = new State();

  static final class ShortcutConverter extends Converter<Character> {
    @Nullable
    @Override
    public Character fromString(@NotNull String shortcut) {
      return TAB.equals(shortcut) ? TAB_CHAR :
             ENTER.equals(shortcut) ? ENTER_CHAR :
             CUSTOM.equals(shortcut) ? CUSTOM_CHAR :
             SPACE_CHAR;
    }

    @NotNull
    @Override
    public String toString(@NotNull Character shortcut) {
      return shortcut == TAB_CHAR ? TAB :
             shortcut == ENTER_CHAR ? ENTER :
             shortcut == CUSTOM_CHAR ? CUSTOM :
             SPACE;
    }
  }

  final static class State {
    @OptionTag(nameAttribute = "", valueAttribute = "shortcut", converter = ShortcutConverter.class)
    public char defaultShortcut = TAB_CHAR;

    public List<TemplateSettings.TemplateKey> deletedKeys = new SmartList<TemplateKey>();
  }

  public static class TemplateKey {
    private String groupName;
    private String key;

    @SuppressWarnings("UnusedDeclaration")
    public TemplateKey() {}

    private TemplateKey(String groupName, String key) {
      this.groupName = groupName;
      this.key = key;
    }

    public static TemplateKey keyOf(TemplateImpl template) {
      return new TemplateKey(template.getGroupName(), template.getKey());
    }

    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      TemplateKey that = (TemplateKey)o;
      return Comparing.equal(groupName, that.groupName) && Comparing.equal(key, that.key);
    }

    public int hashCode() {
      int result = groupName != null ? groupName.hashCode() : 0;
      result = 31 * result + (key != null ? key.hashCode() : 0);
      return result;
    }

    public String getGroupName() {
      return groupName;
    }

    @SuppressWarnings("UnusedDeclaration")
    public void setGroupName(String groupName) {
      this.groupName = groupName;
    }

    public String getKey() {
      return key;
    }

    public void setKey(String key) {
      this.key = key;
    }

    @Override
    public String toString() {
      return getKey() + "@" + getGroupName();
    }
  }

  private TemplateKey myLastSelectedTemplate;

  public TemplateSettings(SchemesManagerFactory schemesManagerFactory) {
    mySchemesManager = schemesManagerFactory.createSchemesManager(TEMPLATES_DIR_PATH, new BaseSchemeProcessor<TemplateGroup>() {
      @Nullable
      @Override
      public TemplateGroup readScheme(@NotNull Element element) throws InvalidDataException {
        return readTemplateFile(element, element.getAttributeValue("group"), false, false,
                                getClass().getClassLoader());
      }


      @NotNull
      @Override
      public State getState(@NotNull TemplateGroup template) {
        for (TemplateImpl t : template.getElements()) {
          if (differsFromDefault(t)) {
            return State.POSSIBLY_CHANGED;
          }
        }
        return State.NON_PERSISTENT;
      }

      @Override
      public Element writeScheme(@NotNull TemplateGroup template) {
        Element templateSetElement = new Element(TEMPLATE_SET);
        templateSetElement.setAttribute(GROUP, template.getName());

        for (TemplateImpl t : template.getElements()) {
          if (differsFromDefault(t)) {
            saveTemplate(t, templateSetElement);
          }
        }

        return templateSetElement;
      }

      @Override
      public void initScheme(@NotNull final TemplateGroup scheme) {
        for (TemplateImpl template : scheme.getElements()) {
          addTemplateImpl(template);
        }
      }

      @Override
      public void onSchemeAdded(@NotNull final TemplateGroup scheme) {
        for (TemplateImpl template : scheme.getElements()) {
          addTemplateImpl(template);
        }
      }

      @Override
      public void onSchemeDeleted(@NotNull final TemplateGroup scheme) {
        for (TemplateImpl template : scheme.getElements()) {
          removeTemplate(template);
        }
      }
    }, RoamingType.PER_USER);

    for (TemplateGroup group : mySchemesManager.loadSchemes()) {
      for (TemplateImpl template : group.getElements()) {
        addTemplateImpl(template);
      }
    }

    loadDefaultLiveTemplates();
  }

  public static TemplateSettings getInstance() {
    return ServiceManager.getService(TemplateSettings.class);
  }

  private boolean differsFromDefault(TemplateImpl t) {
    TemplateImpl def = getDefaultTemplate(t);
    return def == null || !t.equals(def) || !t.contextsEqual(def);
  }

  @Nullable
  public TemplateImpl getDefaultTemplate(TemplateImpl t) {
    return myDefaultTemplates.get(TemplateKey.keyOf(t));
  }

  @Override
  public State getState() {
    return myState;
  }

  @Override
  public void loadState(State state) {
    myState = state;

    applyNewDeletedTemplates();
  }

  void applyNewDeletedTemplates() {
    for (TemplateKey templateKey : myState.deletedKeys) {
      if (templateKey.groupName == null) {
        for (TemplateImpl template : new ArrayList<TemplateImpl>(myTemplates.get(templateKey.key))) {
          removeTemplate(template);
        }
      }
      else {
        TemplateImpl toDelete = getTemplate(templateKey.key, templateKey.groupName);
        if (toDelete != null) {
          removeTemplate(toDelete);
        }
      }
    }
  }

  @Nullable
  public String getLastSelectedTemplateKey() {
    return myLastSelectedTemplate != null ? myLastSelectedTemplate.key : null;
  }

  @Nullable
  public String getLastSelectedTemplateGroup() {
    return myLastSelectedTemplate != null ? myLastSelectedTemplate.groupName : null;
  }

  public void setLastSelectedTemplate(@Nullable String group, @Nullable String key) {
    myLastSelectedTemplate = group == null ? null : new TemplateKey(group, key);
  }

  @SuppressWarnings("unused")
  public Collection<? extends TemplateImpl> getTemplatesAsList() {
    return myTemplates.values();
  }

  public TemplateImpl[] getTemplates() {
    final Collection<? extends TemplateImpl> all = myTemplates.values();
    return all.toArray(new TemplateImpl[all.size()]);
  }

  public char getDefaultShortcutChar() {
    return myState.defaultShortcut;
  }

  public void setDefaultShortcutChar(char defaultShortcutChar) {
    myState.defaultShortcut = defaultShortcutChar;
  }

  public Collection<TemplateImpl> getTemplates(@NonNls String key) {
    return myTemplates.get(key);
  }

  @Nullable
  public TemplateImpl getTemplate(@NonNls String key, String group) {
    final Collection<TemplateImpl> templates = myTemplates.get(key);
    for (TemplateImpl template : templates) {
      if (template.getGroupName().equals(group)) {
        return template;
      }
    }
    return null;
  }

  public Template getTemplateById(@NonNls String id) {
    return myTemplatesById.get(id);
  }

  public int getMaxKeyLength() {
    return myMaxKeyLength;
  }

  public void addTemplate(Template template) {
    clearPreviouslyRegistered(template);
    addTemplateImpl(template);

    TemplateImpl templateImpl = (TemplateImpl)template;
    String groupName = templateImpl.getGroupName();
    TemplateGroup group = mySchemesManager.findSchemeByName(groupName);
    if (group == null) {
      group = new TemplateGroup(groupName);
      mySchemesManager.addScheme(group);
    }
    group.addElement(templateImpl);
  }

  private void clearPreviouslyRegistered(final Template template) {
    TemplateImpl existing = getTemplate(template.getKey(), ((TemplateImpl) template).getGroupName());
    if (existing != null) {
      LOG.info("Template with key " + template.getKey() + " and id " + template.getId() + " already registered");
      TemplateGroup group = mySchemesManager.findSchemeByName(existing.getGroupName());
      if (group != null) {
        group.removeElement(existing);
        if (group.isEmpty()) {
          mySchemesManager.removeScheme(group);
        }
      }
      myTemplates.remove(template.getKey(), existing);
    }
  }

  private void addTemplateImpl(@NotNull Template template) {
    TemplateImpl templateImpl = (TemplateImpl)template;
    if (getTemplate(templateImpl.getKey(), templateImpl.getGroupName()) == null) {
      myTemplates.putValue(template.getKey(), templateImpl);
    }

    myMaxKeyLength = Math.max(myMaxKeyLength, template.getKey().length());
    myState.deletedKeys.remove(TemplateKey.keyOf((TemplateImpl)template));
  }

  private void addTemplateById(Template template) {
    if (!myTemplatesById.containsKey(template.getId())) {
      final String id = template.getId();
      if (id != null) {
        myTemplatesById.put(id, template);
      }
    }
  }

  public void removeTemplate(@NotNull Template template) {
    myTemplates.remove(template.getKey(), (TemplateImpl)template);

    TemplateGroup group = mySchemesManager.findSchemeByName(((TemplateImpl)template).getGroupName());
    if (group != null) {
      group.removeElement((TemplateImpl)template);
      if (group.isEmpty()) {
        mySchemesManager.removeScheme(group);
      }
    }
  }

  private TemplateImpl addTemplate(String key, String string, String group, String description, String shortcut, boolean isDefault,
                                   final String id) {
    TemplateImpl template = new TemplateImpl(key, string, group);
    template.setId(id);
    template.setDescription(description);
    if (TAB.equals(shortcut)) {
      template.setShortcutChar(TAB_CHAR);
    }
    else if (ENTER.equals(shortcut)) {
      template.setShortcutChar(ENTER_CHAR);
    }
    else if (SPACE.equals(shortcut)) {
      template.setShortcutChar(SPACE_CHAR);
    }
    else {
      template.setShortcutChar(DEFAULT_CHAR);
    }
    if (isDefault) {
      myDefaultTemplates.put(TemplateKey.keyOf(template), template);
    }
    return template;
  }

  private void loadDefaultLiveTemplates() {
    try {
      for (DefaultLiveTemplatesProvider provider : DefaultLiveTemplatesProvider.EP_NAME.getExtensions()) {
        for (String defTemplate : provider.getDefaultLiveTemplateFiles()) {
          readDefTemplate(provider, defTemplate, true);
        }
        try {
          String[] hidden = provider.getHiddenLiveTemplateFiles();
          if (hidden != null) {
            for (String s : hidden) {
              readDefTemplate(provider, s, false);
            }
          }
        }
        catch (AbstractMethodError ignore) {
        }
      }
    }
    catch (Exception e) {
      LOG.error(e);
    }
  }

  private void readDefTemplate(DefaultLiveTemplatesProvider provider, String defTemplate, boolean registerTemplate) throws JDOMException, InvalidDataException, IOException {
    InputStream inputStream = DecodeDefaultsUtil.getDefaultsInputStream(provider, defTemplate);
    if (inputStream != null) {
      TemplateGroup group = readTemplateFile(JDOMUtil.load(inputStream), getDefaultTemplateName(defTemplate), true, registerTemplate, provider.getClass().getClassLoader());
      if (group != null && group.getReplace() != null) {
        for (TemplateImpl template : myTemplates.get(group.getReplace())) {
          removeTemplate(template);
        }
      }
    }
  }

  private static String getDefaultTemplateName(String defTemplate) {
    return defTemplate.substring(defTemplate.lastIndexOf('/') + 1);
  }

  @Nullable
  private TemplateGroup readTemplateFile(@NotNull Element element, @NonNls String defGroupName, boolean isDefault, boolean registerTemplate, ClassLoader classLoader) throws InvalidDataException {
    if (!TEMPLATE_SET.equals(element.getName())) {
      throw new InvalidDataException();
    }

    String groupName = element.getAttributeValue(GROUP);
    if (groupName == null || groupName.isEmpty()) groupName = defGroupName;

    TemplateGroup result = new TemplateGroup(groupName, element.getAttributeValue("REPLACE"));

    Map<String, TemplateImpl> created = new LinkedHashMap<String,  TemplateImpl>();

    for (Element child : element.getChildren(TEMPLATE)) {
      TemplateImpl template = readTemplateFromElement(isDefault, groupName, child, classLoader);
      TemplateImpl existing = getTemplate(template.getKey(), template.getGroupName());
      boolean defaultTemplateModified = isDefault && (myState.deletedKeys.contains(TemplateKey.keyOf(template)) ||
                                                      myTemplatesById.containsKey(template.getId()) ||
                                                      existing != null);

      if(!defaultTemplateModified) {
        created.put(template.getKey(), template);
      }
      if (isDefault && existing != null) {
        existing.getTemplateContext().setDefaultContext(template.getTemplateContext());
      }
    }

    if (registerTemplate) {
      TemplateGroup existingScheme = mySchemesManager.findSchemeByName(result.getName());
      if (existingScheme != null) {
        result = existingScheme;
      }
    }

    for (TemplateImpl template : created.values()) {
      if (registerTemplate) {
        clearPreviouslyRegistered(template);
        addTemplateImpl(template);
      }
      addTemplateById(template);

      result.addElement(template);
    }

    if (registerTemplate) {
      TemplateGroup existingScheme = mySchemesManager.findSchemeByName(result.getName());
      if (existingScheme == null && !result.isEmpty()) {
        mySchemesManager.addNewScheme(result, false);
      }
    }

    return result.isEmpty() ? null : result;
  }

  private TemplateImpl readTemplateFromElement(final boolean isDefault,
                                               final String groupName,
                                               final Element element,
                                               ClassLoader classLoader) throws InvalidDataException {
    String name = element.getAttributeValue(NAME);
    String value = element.getAttributeValue(VALUE);
    String description;
    String resourceBundle = element.getAttributeValue(RESOURCE_BUNDLE);
    String key = element.getAttributeValue(KEY);
    String id = element.getAttributeValue(ID);
    if (resourceBundle != null && key != null) {
      if (classLoader == null) {
        classLoader = getClass().getClassLoader();
      }
      ResourceBundle bundle = AbstractBundle.getResourceBundle(resourceBundle, classLoader);
      description = bundle.getString(key);
    }
    else {
      description = element.getAttributeValue(DESCRIPTION);
    }
    String shortcut = element.getAttributeValue(SHORTCUT);
    TemplateImpl template = addTemplate(name, value, groupName, description, shortcut, isDefault, id);

    template.setToReformat(Boolean.parseBoolean(element.getAttributeValue(TO_REFORMAT)));
    template.setToShortenLongNames(Boolean.parseBoolean(element.getAttributeValue(TO_SHORTEN_FQ_NAMES)));
    template.setDeactivated(Boolean.parseBoolean(element.getAttributeValue(DEACTIVATED)));

    String useStaticImport = element.getAttributeValue(USE_STATIC_IMPORT);
    if (useStaticImport != null) {
      template.setValue(TemplateImpl.Property.USE_STATIC_IMPORT_IF_POSSIBLE, Boolean.parseBoolean(useStaticImport));
    }

    for (final Object o : element.getChildren(VARIABLE)) {
      Element e = (Element)o;
      String variableName = e.getAttributeValue(NAME);
      String expression = e.getAttributeValue(EXPRESSION);
      String defaultValue = e.getAttributeValue(DEFAULT_VALUE);
      boolean isAlwaysStopAt = Boolean.parseBoolean(e.getAttributeValue(ALWAYS_STOP_AT));
      template.addVariable(variableName, expression, defaultValue, isAlwaysStopAt);
    }

    Element context = element.getChild(CONTEXT);
    if (context != null) {
      template.getTemplateContext().readTemplateContext(context);
    }

    return template;
  }

  private void saveTemplate(TemplateImpl template, Element templateSetElement) {
    Element element = new Element(TEMPLATE);
    final String id = template.getId();
    if (id != null) {
      element.setAttribute(ID, id);
    }
    element.setAttribute(NAME, template.getKey());
    element.setAttribute(VALUE, template.getString());
    if (template.getShortcutChar() == TAB_CHAR) {
      element.setAttribute(SHORTCUT, TAB);
    } else if (template.getShortcutChar() == ENTER_CHAR) {
      element.setAttribute(SHORTCUT, ENTER);
    } else if (template.getShortcutChar() == SPACE_CHAR) {
      element.setAttribute(SHORTCUT, SPACE);
    }
    if (template.getDescription() != null) {
      element.setAttribute(DESCRIPTION, template.getDescription());
    }
    element.setAttribute(TO_REFORMAT, Boolean.toString(template.isToReformat()));
    element.setAttribute(TO_SHORTEN_FQ_NAMES, Boolean.toString(template.isToShortenLongNames()));
    if (template.getValue(Template.Property.USE_STATIC_IMPORT_IF_POSSIBLE)
        != Template.getDefaultValue(Template.Property.USE_STATIC_IMPORT_IF_POSSIBLE))
    {
      element.setAttribute(USE_STATIC_IMPORT, Boolean.toString(template.getValue(Template.Property.USE_STATIC_IMPORT_IF_POSSIBLE)));
    }
    if (template.isDeactivated()) {
      element.setAttribute(DEACTIVATED, Boolean.toString(true));
    }

    for (int i = 0; i < template.getVariableCount(); i++) {
      Element variableElement = new Element(VARIABLE);
      variableElement.setAttribute(NAME, template.getVariableNameAt(i));
      variableElement.setAttribute(EXPRESSION, template.getExpressionStringAt(i));
      variableElement.setAttribute(DEFAULT_VALUE, template.getDefaultValueStringAt(i));
      variableElement.setAttribute(ALWAYS_STOP_AT, Boolean.toString(template.isAlwaysStopAt(i)));
      element.addContent(variableElement);
    }

    try {
      Element contextElement = new Element(CONTEXT);
      TemplateImpl def = getDefaultTemplate(template);
      template.getTemplateContext().writeTemplateContext(contextElement, def == null ? null : def.getTemplateContext());
      element.addContent(contextElement);
    } catch (WriteExternalException ignore) {
    }
    templateSetElement.addContent(element);
  }

  public void setTemplates(@NotNull List<TemplateGroup> newGroups) {
    myTemplates.clear();
    myState.deletedKeys.clear();
    for (TemplateImpl template : myDefaultTemplates.values()) {
      myState.deletedKeys.add(TemplateKey.keyOf(template));
    }
    myMaxKeyLength = 0;
    List<TemplateGroup> schemes = new SmartList<TemplateGroup>();
    for (TemplateGroup group : newGroups) {
      if (!group.isEmpty()) {
        schemes.add(group);
        for (TemplateImpl template : group.getElements()) {
          clearPreviouslyRegistered(template);
          addTemplateImpl(template);
        }
      }
    }
    mySchemesManager.setSchemes(schemes);
  }

  public List<TemplateGroup> getTemplateGroups() {
    return mySchemesManager.getAllSchemes();
  }

  public List<TemplateImpl> collectMatchingCandidates(String key, @Nullable Character shortcutChar, boolean hasArgument) {
    final Collection<TemplateImpl> templates = getTemplates(key);
    List<TemplateImpl> candidates = new ArrayList<TemplateImpl>();
    for (TemplateImpl template : templates) {
      if (template.isDeactivated()) {
        continue;
      }
      if (shortcutChar != null && getShortcutChar(template) != shortcutChar) {
        continue;
      }
      if (hasArgument && !template.hasArgument()) {
        continue;
      }
      candidates.add(template);
    }
    return candidates;
  }

  public char getShortcutChar(TemplateImpl template) {
    char c = template.getShortcutChar();
    return c == DEFAULT_CHAR ? getDefaultShortcutChar() : c;
  }

  public List<TemplateKey> getDeletedTemplates() {
    return myState.deletedKeys;
  }

  public void reset() {
    myState.deletedKeys.clear();
    loadDefaultLiveTemplates();
  }
}
