package com.intellij.codeInsight.template.impl;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.template.Template;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.application.ex.DecodeDefaultsUtil;
import com.intellij.openapi.components.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.options.SchemeProcessor;
import com.intellij.openapi.options.SchemesManager;
import com.intellij.openapi.util.DefaultJDOMExternalizer;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.util.io.FileUtil;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;


@State(
  name="TemplateSettings",
  storages= {
    @Storage(
      id="other",
      file = "$APP_CONFIG$/other.xml"
    )}
)
public class TemplateSettings implements PersistentStateComponent<Element>, ExportableComponent, SettingsSavingComponent {

  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.template.impl.TemplateSettings");

  public  @NonNls static final String USER_GROUP_NAME = "user";
  private @NonNls static final String TEMPLATE_SET = "templateSet";
  private @NonNls static final String GROUP = "group";
  private @NonNls static final String TEMPLATE = "template";

  private @NonNls static final String DELETED_TEMPLATES = "deleted_templates";
  private List<String> myDeletedTemplates = new ArrayList<String>();

  public static final char SPACE_CHAR = ' ';
  public static final char TAB_CHAR = '\t';
  public static final char ENTER_CHAR = '\n';
  public static final char DEFAULT_CHAR = 'D';

  private static final @NonNls String SPACE = "SPACE";
  private static final @NonNls String TAB = "TAB";
  private static final @NonNls String ENTER = "ENTER";

  private static final @NonNls String NAME = "name";
  private static final @NonNls String VALUE = "value";
  private static final @NonNls String DESCRIPTION = "description";
  private static final @NonNls String SHORTCUT = "shortcut";

  private static final @NonNls String VARIABLE = "variable";
  private static final @NonNls String EXPRESSION = "expression";
  private static final @NonNls String DEFAULT_VALUE = "defaultValue";
  private static final @NonNls String ALWAYS_STOP_AT = "alwaysStopAt";

  private static final @NonNls String CONTEXT = "context";
  private static final @NonNls String TO_REFORMAT = "toReformat";
  private static final @NonNls String TO_SHORTEN_FQ_NAMES = "toShortenFQNames";

  private static final @NonNls String DEFAULT_SHORTCUT = "defaultShortcut";
  private static final @NonNls String DEACTIVATED = "deactivated";

  @NonNls private static final String RESOURCE_BUNDLE = "resource-bundle";
  @NonNls private static final String KEY = "key";
  @NonNls private static final String ID = "id";

  private static final @NonNls String TEMPLATES_CONFIG_FOLDER = "templates";

  private final Map<String,Template> myTemplates = new LinkedHashMap<String,Template>();
  private final Map<String,Template> myTemplatesById = new LinkedHashMap<String,Template>();
  private final Map<String,TemplateImpl> myDefaultTemplates = new LinkedHashMap<String, TemplateImpl>();

  private int myMaxKeyLength = 0;
  private char myDefaultShortcutChar = TAB_CHAR;
  private String myLastSelectedTemplateKey;
  @NonNls
  public static final String XML_EXTENSION = ".xml";
  private final SchemesManager mySchemesManager;
  private final SchemeProcessor<TemplateGroup> myProcessor;
  private static final String FILE_SPEC = "$ROOT_CONFIG$/templates";

  public TemplateSettings(SchemesManager schemesManager) {
    mySchemesManager = schemesManager;

    myProcessor = new SchemeProcessor<TemplateGroup>() {
      public TemplateGroup readScheme(final Document schemeContent, final File file)
          throws InvalidDataException, IOException, JDOMException {
        String defGroupName = FileUtil.getNameWithoutExtension(file);
        return readTemplateFile(JDOMUtil.loadDocument(file), defGroupName, false);
      }


      public boolean shouldBeSaved(final TemplateGroup template) {
        for (TemplateImpl t : template.getTemplates()) {
          if (!t.equals(myDefaultTemplates.get(t.getKey()))) {
            return true;
          }
        }
        return false;
      }

      public Document writeScheme(final TemplateGroup template) throws WriteExternalException {
        Element templateSetElement = new Element(TEMPLATE_SET);
        templateSetElement.setAttribute(GROUP, template.getName());

        for (TemplateImpl t : template.getTemplates()) {
          if (!t.equals(myDefaultTemplates.get(t.getKey()))) {
            saveTemplate(t, templateSetElement);
          }
        }

        return new Document(templateSetElement);
      }

      public void showReadErrorMessage(final Exception e, final String schemeName, final String filePath) {
        LOG.warn(e);
      }

      public void showWriteErrorMessage(final Exception e, final String schemeName, final String filePath) {
        LOG.warn(e);
      }
    };

    loadTemplates();
  }

  @NotNull
  public File[] getExportFiles() {
    return new File[]{getTemplateDirectory(true),PathManager.getDefaultOptionsFile()};
  }

  @NotNull
  public String getPresentableName() {
    return CodeInsightBundle.message("templates.export.display.name");
  }

  public static TemplateSettings getInstance() {
    return ServiceManager.getService(TemplateSettings.class);
  }

  public void loadState(Element parentNode) {
    Element element = parentNode.getChild(DEFAULT_SHORTCUT);
    if (element != null) {
      String shortcut = element.getAttributeValue(SHORTCUT);
      if (TAB.equals(shortcut)) {
        myDefaultShortcutChar = TAB_CHAR;
      } else if (ENTER.equals(shortcut)) {
        myDefaultShortcutChar = ENTER_CHAR;
      } else {
        myDefaultShortcutChar = SPACE_CHAR;
      }
    }

    Element deleted = parentNode.getChild(DELETED_TEMPLATES);
    if (deleted != null) {
      List children = deleted.getChildren();
      for (final Object aChildren : children) {
        Element child = (Element)aChildren;
        myDeletedTemplates.add(child.getAttributeValue(NAME));
      }
    }

    loadTemplates();
  }

  public Element getState()  {
    Element parentNode = new Element("TemplateSettings");
    Element element = new Element(DEFAULT_SHORTCUT);
    if (myDefaultShortcutChar == TAB_CHAR) {
      element.setAttribute(SHORTCUT, TAB);
    } else if (myDefaultShortcutChar == ENTER_CHAR) {
      element.setAttribute(SHORTCUT, ENTER);
    } else {
      element.setAttribute(SHORTCUT, SPACE);
    }
    parentNode.addContent(element);

    if (myDeletedTemplates.size() > 0) {
      Element deleted = new Element(DELETED_TEMPLATES);
      for (final String myDeletedTemplate : myDeletedTemplates) {
        Element template = new Element(TEMPLATE);
        template.setAttribute(NAME, myDeletedTemplate);
        deleted.addContent(template);

      }
      parentNode.addContent(deleted);
    }
    return parentNode;
  }

  public String getLastSelectedTemplateKey() {
    return myLastSelectedTemplateKey;
  }

  public void setLastSelectedTemplateKey(String key) {
    myLastSelectedTemplateKey = key;
  }

  public TemplateImpl[] getTemplates() {
    return myTemplates.values().toArray(new TemplateImpl[myTemplates.size()]);
  }

  public char getDefaultShortcutChar() {
    return myDefaultShortcutChar;
  }

  public void setDefaultShortcutChar(char defaultShortcutChar) {
    myDefaultShortcutChar = defaultShortcutChar;
  }

  public TemplateImpl getTemplate(@NonNls String key) {
    return (TemplateImpl) myTemplates.get(key);
  }

  public Template getTemplateById(@NonNls String id) {
    return myTemplatesById.get(id);
  }

  public void addTemplateWithId(Template template) {
    final String id = template.getId();
    if (id != null) {
      myTemplatesById.put(id, template);
    }
  }

  public int getMaxKeyLength() {
    return myMaxKeyLength;
  }

  public void setTemplates(TemplateImpl[] newTemplates) {
    myTemplates.clear();
    myMaxKeyLength = 0;
    for (TemplateImpl template : newTemplates) {
      myTemplates.put(template.getKey(), template);
      myMaxKeyLength = Math.max(myMaxKeyLength, template.getKey().length());
      addTemplateWithId(template);
    }

    saveTemplates(newTemplates);
  }

  public void save() {
    saveTemplates(getTemplates());
  }

  public void addTemplate(Template template) {
    myTemplates.put(template.getKey(), template);
    addTemplateWithId(template);
    myMaxKeyLength = Math.max(myMaxKeyLength, template.getKey().length());
    saveTemplates(getTemplates());
  }

  public void removeTemplate(Template template) {
    myTemplates.remove(template.getKey());
    myTemplatesById.remove(template.getId());
    saveTemplates(getTemplates());
  }

  private TemplateImpl addTemplate(String key, String string, String group, String description, String shortcut, boolean isDefault,
                                   final String id) {
    TemplateImpl template = new TemplateImpl(key, string, group);
    template.setId(id);
    template.setDescription(description);
    if (TAB.equals(shortcut)) {
      template.setShortcutChar(TAB_CHAR);
    } else if (ENTER.equals(shortcut)) {
      template.setShortcutChar(ENTER_CHAR);
    } else if (SPACE.equals(shortcut)) {
      template.setShortcutChar(SPACE_CHAR);
    } else {
      template.setShortcutChar(DEFAULT_CHAR);
    }
    if (isDefault) {
      myDefaultTemplates.put(key, template);
      if (myTemplates.get(key) != null) return template;
    }
    myTemplates.put(key, template);
    myMaxKeyLength = Math.max(myMaxKeyLength, key.length());
    addTemplateWithId(template);
    return template;
  }

  @Nullable
  private static File getTemplateDirectory(boolean toCreate) {
    String directoryPath = PathManager.getConfigPath() + File.separator + TEMPLATES_CONFIG_FOLDER;
    File directory = new File(directoryPath);
    if (!directory.exists()) {
      if (!toCreate) {
        return null;
      }
      if (!directory.mkdir()) {
        if (LOG.isDebugEnabled()) {
          LOG.debug("cannot create directory: " + directory.getAbsolutePath());
        }
        return null;
      }
    }
    return directory;
  }

  private void loadTemplates() {

    mySchemesManager.loadSchemes(FILE_SPEC, myProcessor, RoamingType.PER_USER);


    try {
      for(DefaultLiveTemplatesProvider provider: Extensions.getExtensions(DefaultLiveTemplatesProvider.EP_NAME)) {
        for (String defTemplate : provider.getDefaultLiveTemplateFiles()) {
          String templateName = getDefaultTemplateName(defTemplate);
          readDefTemplateFile(DecodeDefaultsUtil.getDefaultsInputStream(this, defTemplate), templateName);
        }
      }
    } catch (Exception e) {
      LOG.error(e);
    }
  }

  public static String getDefaultTemplateName(String defTemplate) {
    return defTemplate.substring(defTemplate.lastIndexOf("/") + 1);
  }

  public void readDefTemplateFile(InputStream inputStream, String defGroupName) throws JDOMException, InvalidDataException, IOException {
    readTemplateFile(JDOMUtil.loadDocument(inputStream), defGroupName, true);
  }

  public TemplateGroup readTemplateFile(Document document, @NonNls String defGroupName, boolean isDefault) throws InvalidDataException {
    if (document == null) {
      throw new InvalidDataException();
    }
    Element root = document.getRootElement();
    if (root == null || !TEMPLATE_SET.equals(root.getName())) {
      throw new InvalidDataException();
    }

    String groupName = root.getAttributeValue(GROUP);
    if (groupName == null || groupName.length() == 0) groupName = defGroupName;

    TemplateGroup result = new TemplateGroup(groupName);

    for (final Object o1 : root.getChildren(TEMPLATE)) {
      Element element = (Element)o1;

      String name = element.getAttributeValue(NAME);
      String value = element.getAttributeValue(VALUE);
      String description;
      String resourceBundle = element.getAttributeValue(RESOURCE_BUNDLE);
      String key = element.getAttributeValue(KEY);
      String id = element.getAttributeValue(ID);
      if (resourceBundle != null && key != null) {
        ResourceBundle bundle = ResourceBundle.getBundle(resourceBundle);
        description = bundle.getString(key);
      }
      else {
        description = element.getAttributeValue(DESCRIPTION);
      }
      String shortcut = element.getAttributeValue(SHORTCUT);
      if (isDefault && myDeletedTemplates.contains(name)) continue;
      TemplateImpl template = addTemplate(name, value, groupName, description, shortcut, isDefault, id);
      template.setToReformat(Boolean.parseBoolean(element.getAttributeValue(TO_REFORMAT)));
      template.setToShortenLongNames(Boolean.parseBoolean(element.getAttributeValue(TO_SHORTEN_FQ_NAMES)));
      template.setDeactivated(Boolean.parseBoolean(element.getAttributeValue(DEACTIVATED)));


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
        DefaultJDOMExternalizer.readExternal(template.getTemplateContext(), context);
      }

      result.addTemplate(template);

    }

    return result;

  }

  private void saveTemplates(final TemplateImpl[] templates) {
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        List<String> templateNames = new ArrayList<String>();
        for (TemplateImpl template1 : templates) {
          templateNames.add(template1.getKey());
        }
        myDeletedTemplates.clear();
        for (String defTemplateName : myDefaultTemplates.keySet()) {
          if (!templateNames.contains(defTemplateName)) {
            myDeletedTemplates.add(defTemplateName);
          }
        }

        try {
          mySchemesManager.saveSchemes(buildGroupList(templates), FILE_SPEC, myProcessor, RoamingType.PER_USER);
        }
        catch (WriteExternalException e) {
          LOG.error(e);
        }

      }
    });
  }

  private Collection<TemplateGroup> buildGroupList(final TemplateImpl[] templates) {
    Map<String, TemplateGroup> result = new LinkedHashMap<String, TemplateGroup>();

    for (TemplateImpl template : templates) {
      final String name = template.getGroupName();
      if (!result.containsKey(name)) {
        result.put(name, new TemplateGroup(name));
      }

      result.get(name).addTemplate(template);
    }

    return result.values();
  }

  private static void saveTemplate(TemplateImpl template, Element templateSetElement) {
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
      DefaultJDOMExternalizer.writeExternal(template.getTemplateContext(), contextElement);
      element.addContent(contextElement);
    } catch (WriteExternalException e) {
    }
    templateSetElement.addContent(element);
  }

}