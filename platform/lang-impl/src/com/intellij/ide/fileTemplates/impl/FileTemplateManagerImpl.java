/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

package com.intellij.ide.fileTemplates.impl;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.fileTemplates.FileTemplate;
import com.intellij.ide.fileTemplates.FileTemplateManager;
import com.intellij.ide.fileTemplates.InternalTemplateBean;
import com.intellij.ide.plugins.IdeaPluginDescriptorImpl;
import com.intellij.ide.plugins.cl.PluginClassLoader;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.components.ExportableComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.openapi.fileTypes.ex.FileTypeManagerEx;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.SystemProperties;
import com.intellij.util.text.DateFormatUtil;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.text.MessageFormat;
import java.util.*;

/**
 * @author MYakovlev
 *         Date: Jul 24
 * @author 2002
 *
 * locking policy: if the class needs to take a read or write action, the LOCK lock must be taken
 * _inside_, not outside of the read action
 */
public class FileTemplateManagerImpl extends FileTemplateManager implements ExportableComponent, JDOMExternalizable {
  private static final Logger LOG = Logger.getInstance("#com.intellij.ide.fileTemplates.impl.FileTemplateManagerImpl");

  private static final String TEMPLATES_DIR = "fileTemplates";
  private static final String DEFAULT_TEMPLATES_ROOT = TEMPLATES_DIR;
  private static final String INTERNAL_DIR = "internal";
  private static final String INCLUDES_DIR = "includes";
  private static final String CODETEMPLATES_DIR = "code";
  private static final String J2EE_TEMPLATES_DIR = "j2ee";
  private static final String ROOT_DIR = ".";
  
  public static final String  DESCRIPTION_FILE_EXTENSION = "html";
  private static final String DESCRIPTION_EXTENSION_SUFFIX = "." + DESCRIPTION_FILE_EXTENSION;
  private static final String DESCRIPTION_FILE_NAME = "default." + DESCRIPTION_FILE_EXTENSION;

  private final RecentTemplatesManager myRecentList = new RecentTemplatesManager();
  private final FTManager myDefaultTemplatesManager;
  private final FTManager myInternalTemplatesManager;
  private final FTManager myPatternsManager;
  private final FTManager myCodeTemplatesManager;
  private final FTManager myJ2eeTemplatesManager;
  
  private final Map<String, FTManager> myDirToManagerMap = new HashMap<String, FTManager>();

  private static final String ELEMENT_DELETED_TEMPLATES = "deleted_templates";
  private static final String ELEMENT_DELETED_INCLUDES = "deleted_includes";
  private static final String ELEMENT_RECENT_TEMPLATES = "recent_templates";
  private static final String ELEMENT_TEMPLATES = "templates";
  private static final String ELEMENT_INTERNAL_TEMPLATE = "internal_template";
  private static final String ELEMENT_TEMPLATE = "template";
  private static final String ATTRIBUTE_NAME = "name";
  private static final String ATTRIBUTE_REFORMAT = "reformat";
  private static final String ATTRIBUTE_ENABLED = "enabled";

  private final FTManager[] myAllManagers;
  private final FileTypeManagerEx myTypeManager;

  public static FileTemplateManagerImpl getInstanceImpl() {
    return (FileTemplateManagerImpl)ServiceManager.getService(FileTemplateManager.class);
  }

  public FileTemplateManagerImpl(@NotNull FileTypeManagerEx typeManager) {
    myTypeManager = typeManager;
    myDefaultTemplatesManager = new FTManager(DEFAULT_TEMPLATES_CATEGORY, ROOT_DIR);
    myInternalTemplatesManager = new FTManager(INTERNAL_TEMPLATES_CATEGORY, INTERNAL_DIR);
    myPatternsManager = new FTManager(INCLUDES_TEMPLATES_CATEGORY, INCLUDES_DIR);
    myCodeTemplatesManager = new FTManager(CODE_TEMPLATES_CATEGORY, CODETEMPLATES_DIR);
    myJ2eeTemplatesManager = new FTManager(J2EE_TEMPLATES_CATEGORY, J2EE_TEMPLATES_DIR);
    
    myDirToManagerMap.put("", myDefaultTemplatesManager);
    myDirToManagerMap.put(INTERNAL_DIR + "/", myInternalTemplatesManager);
    myDirToManagerMap.put(INCLUDES_DIR + "/", myPatternsManager);
    myDirToManagerMap.put(CODETEMPLATES_DIR + "/", myCodeTemplatesManager);
    myDirToManagerMap.put(J2EE_TEMPLATES_DIR + "/", myJ2eeTemplatesManager);
    
    myAllManagers = new FTManager[]{myDefaultTemplatesManager, myInternalTemplatesManager, myPatternsManager, myCodeTemplatesManager, myJ2eeTemplatesManager};

    loadDefaultTemplates();
    for (FTManager child : myAllManagers) {
      loadCustomizedContent(child);
    }
    
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      for (String tname : Arrays.asList("Class", "AnnotationType", "Enum", "Interface")) {
        for (FileTemplate template : myInternalTemplatesManager.getAllTemplates(true)) {
          if (tname.equals(template.getName())) {
            myInternalTemplatesManager.removeTemplate(((FileTemplateBase)template).getQualifiedName());
            break;
          }
        }
        final FileTemplateBase template = myInternalTemplatesManager.addTemplate(tname, "java");
        template.setText(normalizeText(getTestClassTemplateText(tname)));
      }
    }
    
  }

  private void loadDefaultTemplates() {
    final Set<URL> processedUrls = new HashSet<URL>();
    for (PluginDescriptor plugin : ApplicationManager.getApplication().getPlugins()) {
      if (plugin instanceof IdeaPluginDescriptorImpl && ((IdeaPluginDescriptorImpl)plugin).isEnabled()) {
        final ClassLoader loader = plugin.getPluginClassLoader();
        if (loader instanceof PluginClassLoader && ((PluginClassLoader)loader).getUrls().isEmpty()) {
          continue; // development mode, when IDEA_CORE's loader contains all the classpath
        }
        try {
          final Enumeration<URL> systemResources = loader.getResources(DEFAULT_TEMPLATES_ROOT);
          if (systemResources != null && systemResources.hasMoreElements()) {
            while (systemResources.hasMoreElements()) {
              final URL url = systemResources.nextElement();
              if (processedUrls.contains(url)) {
                continue;
              }
              processedUrls.add(url);
              loadDefaultsFromRoot(url);
            }
          }
        }
        catch (IOException e) {
          LOG.error(e);
        }
      }
    }
  }

  private void loadDefaultsFromRoot(final URL root) throws IOException {
    final List<String> children = UrlUtil.getChildrenRelativePaths(root);
    if (children.isEmpty()) {
      return;
    }
    final Set<String> descriptionPaths = new HashSet<String>();
    for (String path : children) {
      if (path.endsWith(DESCRIPTION_EXTENSION_SUFFIX)) {
        descriptionPaths.add(path);
      }
    }
    for (final String path : children) {
      for (Map.Entry<String, FTManager> entry : myDirToManagerMap.entrySet()) {
        final String prefix = entry.getKey();
        if (matchesPrefix(path, prefix)) {
          if (path.endsWith(FTManager.TEMPLATE_EXTENSION_SUFFIX)) {
            final String filename = path.substring(prefix.length(), path.length() - FTManager.TEMPLATE_EXTENSION_SUFFIX.length());
            final String extension = myTypeManager.getExtension(filename);
            final String templateName = filename.substring(0, filename.length() - extension.length() - 1); 
            final URL templateUrl = new URL(root.toExternalForm() + "/" +path);
            final String descriptionPath = getDescriptionPath(prefix, templateName, extension, descriptionPaths);
            final URL descriptionUrl = descriptionPath != null? new URL(root.toExternalForm() + "/" + descriptionPath) : null;
            entry.getValue().addDefaultTemplate(new DefaultTemplate(templateName, extension, templateUrl, descriptionUrl));
          }
          break; // FTManagers loop
        }
      }
    }
  }

  private void loadCustomizedContent(FTManager manager) {
    final File configRoot = manager.getConfigRoot(false);
    File[] configFiles = configRoot.listFiles();
    if (configFiles == null) {
      return;
    }

    final List<File> templateWithDefaultExtension = new ArrayList<File>();
    final Set<String> processedNames = new HashSet<String>();

    for (File file : configFiles) {
      if (file.isDirectory() || myTypeManager.isFileIgnored(file.getName()) || file.isHidden()) {
        continue;
      }
      final String name = file.getName();
      if (name.endsWith(FTManager.TEMPLATE_EXTENSION_SUFFIX)) {
        templateWithDefaultExtension.add(file);
      }
      else {
        processedNames.add(name);
        addTemplateFromFile(manager, name, file);
      }

    }

    for (File file : templateWithDefaultExtension) {
      String name = file.getName();
      // cut default template extension
      name = name.substring(0, name.length() - FTManager.TEMPLATE_EXTENSION_SUFFIX.length());
      if (!processedNames.contains(name)) {
        addTemplateFromFile(manager, name, file);
      }
      FileUtil.delete(file);
    }
  }

  private void addTemplateFromFile(FTManager manager, String templateQName, File file) {
    final String extension = myTypeManager.getExtension(templateQName);
    templateQName = templateQName.substring(0, templateQName.length() - extension.length() - 1);
    if (templateQName.length() == 0) {
      return;
    }
    try {
      final String text = FileUtil.loadFile(file, FTManager.CONTENT_ENCODING);
      manager.addTemplate(templateQName, extension).setText(text);
    }
    catch (IOException e) {
      LOG.error(e);
    }
  }

  //Example: templateName="NewClass"   templateExtension="java"
  @Nullable
  private static String getDescriptionPath(String pathPrefix, String templateName, String templateExtension, Set<String> descriptionPaths) {
    final Locale locale = Locale.getDefault();
    
    String descName = MessageFormat.format("{0}.{1}_{2}_{3}" + DESCRIPTION_EXTENSION_SUFFIX, templateName, templateExtension,
                                           locale.getLanguage(), locale.getCountry());
    String descPath = pathPrefix.length() > 0? pathPrefix + descName : descName;
    if (descriptionPaths.contains(descPath)) {
      return descPath;
    }

    descName = MessageFormat.format("{0}.{1}_{2}" + DESCRIPTION_EXTENSION_SUFFIX, templateName, templateExtension, locale.getLanguage());
    descPath = pathPrefix.length() > 0? pathPrefix + descName : descName;
    if (descriptionPaths.contains(descPath)) {
      return descPath;
    }

    descName = templateName + "." + templateExtension + DESCRIPTION_EXTENSION_SUFFIX;
    descPath = pathPrefix.length() > 0? pathPrefix + descName : descName;
    if (descriptionPaths.contains(descPath)) {
      return descPath;
    }
    return null;
  }

  private static boolean matchesPrefix(String path, String prefix) {
    if (prefix.length() == 0) {
      return !path.contains("/");
    }
    return FileUtil.startsWith(path, prefix) && !path.substring(prefix.length()).contains("/");
  }

  @NotNull
  public File[] getExportFiles() {
    return new File[]{myDefaultTemplatesManager.getConfigRoot(false), PathManager.getDefaultOptionsFile()};
  }

  @NotNull
  public String getPresentableName() {
    return IdeBundle.message("item.file.templates");
  }

  @NotNull
  public FileTemplate[] getAllTemplates() {
    final Collection<FileTemplateBase> templates = myDefaultTemplatesManager.getAllTemplates(false);
    return templates.toArray(new FileTemplate[templates.size()]);
  }

  public FileTemplate getTemplate(@NotNull String templateName) {
    return myDefaultTemplatesManager.findTemplateByName(templateName);
  }

  @NotNull
  public FileTemplate addTemplate(@NotNull String name, @NotNull String extension) {
    return myDefaultTemplatesManager.addTemplate(name, extension);
  }

  public void removeTemplate(@NotNull FileTemplate template) {
    final String qName = ((FileTemplateBase)template).getQualifiedName();
    for (FTManager manager : myAllManagers) {
      manager.removeTemplate(qName);
    }
  }

  @TestOnly
  public FileTemplate addInternal(@NotNull String name, @NotNull String extension) {
    return myInternalTemplatesManager.addTemplate(name, extension);
  }

  @NotNull
  public Properties getDefaultProperties() {
    @NonNls Properties props = new Properties();

    Calendar calendar = Calendar.getInstance();
    props.setProperty("DATE", DateFormatUtil.formatDate(calendar.getTime()));
    props.setProperty("TIME", DateFormatUtil.formatTime(calendar.getTime()));
    props.setProperty("YEAR", Integer.toString(calendar.get(Calendar.YEAR)));
    props.setProperty("MONTH", getCalendarValue(calendar, Calendar.MONTH));
    props.setProperty("DAY", getCalendarValue(calendar, Calendar.DAY_OF_MONTH));
    props.setProperty("HOUR", getCalendarValue(calendar, Calendar.HOUR_OF_DAY));
    props.setProperty("MINUTE", getCalendarValue(calendar, Calendar.MINUTE));

    props.setProperty("USER", SystemProperties.getUserName());
    props.setProperty("PRODUCT_NAME", ApplicationNamesInfo.getInstance().getFullProductName());

    return props;
  }

  private static String getCalendarValue(final Calendar calendar, final int field) {
    int val = calendar.get(field);
    if (field == Calendar.MONTH) val++;
    final String result = Integer.toString(val);
    if (result.length() == 1) {
      return "0" + result;
    }
    return result;
  }

  @NotNull
  public Collection<String> getRecentNames() {
    validateRecentNames(); // todo: no need to do it lazily
    return myRecentList.getRecentNames(RECENT_TEMPLATES_SIZE);
  }

  public void addRecentName(@NotNull @NonNls String name) {
    myRecentList.addName(name);
  }

  public void readExternal(Element element) throws InvalidDataException {
    final Element recentElement = element.getChild(ELEMENT_RECENT_TEMPLATES);
    if (recentElement != null) {
      myRecentList.readExternal(recentElement);
    }

    // support older format
    final DeletedTemplatesManager deletedDefaults = new DeletedTemplatesManager();
    Element deletedTemplatesElement = element.getChild(ELEMENT_DELETED_TEMPLATES);
    if (deletedTemplatesElement != null) {
      deletedDefaults.readExternal(deletedTemplatesElement);
    }
    
    final DeletedTemplatesManager deletedIncludes = new DeletedTemplatesManager();
    Element deletedIncludesElement = element.getChild(ELEMENT_DELETED_INCLUDES);
    if (deletedIncludesElement != null) {
      deletedIncludes.readExternal(deletedIncludesElement);
    }
    
    final Set<String> templateNamesWithReformatOff = new HashSet<String>();
    final Element templatesElement = element.getChild(ELEMENT_TEMPLATES);
    if (templatesElement != null) {
      final List children = templatesElement.getChildren();
      for (final Object child : children) {
        final Element childElement = (Element)child;
        boolean reformat = Boolean.TRUE.toString().equals(childElement.getAttributeValue(ATTRIBUTE_REFORMAT));
        if (!reformat) {
          final String name = childElement.getAttributeValue(ATTRIBUTE_NAME);
          templateNamesWithReformatOff.add(name);
        }
      }
    }
    
    for (final FTManager manager : myAllManagers) {
      final Element templatesGroup = element.getChild(getXmlElementGroupName(manager));
      if (templatesGroup == null) {
        continue;
      }
      final List children = element.getChildren(ELEMENT_TEMPLATE);
      
      for (final Object elem : children) {
        final Element child = (Element)elem;
        final String qName = child.getAttributeValue(ATTRIBUTE_NAME);
        final FileTemplateBase template = manager.getTemplate(qName);
        if (template == null) {
          continue;
        }
        final boolean reformat = Boolean.TRUE.toString().equals(child.getAttributeValue(ATTRIBUTE_REFORMAT));
        template.setReformatCode(reformat);
        if (template instanceof BundledFileTemplate) {
          final boolean enabled = Boolean.getBoolean(child.getAttributeValue(ATTRIBUTE_ENABLED, "true"));
          ((BundledFileTemplate)template).setEnabled(enabled);
        }
      }
    }

    // apply data loaded from older format
    final boolean hasDeletedDefaultsInOlderFormat = !deletedDefaults.DELETED_DEFAULT_TEMPLATES.isEmpty();
    final boolean hasDeletedincludesinOlderFormat = !deletedIncludes.DELETED_DEFAULT_TEMPLATES.isEmpty();
    final boolean hasTemplatesWithReformatAttibuteAltered = !templateNamesWithReformatOff.isEmpty();
    final boolean hasSettingsInOlderFormat = hasDeletedDefaultsInOlderFormat || 
                                             hasDeletedincludesinOlderFormat || 
                                             hasTemplatesWithReformatAttibuteAltered;
    if (hasSettingsInOlderFormat) {
      final Collection<FileTemplateBase> allDefaults = myDefaultTemplatesManager.getAllTemplates(true);
      if (hasDeletedDefaultsInOlderFormat) {
        applyDeletedState(deletedDefaults, allDefaults);
      }
      if (hasDeletedincludesinOlderFormat) {
        applyDeletedState(deletedIncludes, myPatternsManager.getAllTemplates(true));
      }
      if (hasTemplatesWithReformatAttibuteAltered) {
        applyReformatState(templateNamesWithReformatOff, allDefaults);
        applyReformatState(templateNamesWithReformatOff, myInternalTemplatesManager.getAllTemplates(true));
      }
    }
  }

  // need this to support options from older format
  private static void applyReformatState(Set<String> templateNamesWithReformatOff, Collection<FileTemplateBase> templates) {
    for (FileTemplateBase template : templates) {
      if (templateNamesWithReformatOff.contains(template.getName())) {
        template.setReformatCode(false);
      }
    }
  }

  // need this to support options from older format
  private static void applyDeletedState(DeletedTemplatesManager deletedDefaults, Collection<FileTemplateBase> templates) {
    for (FileTemplateBase template : templates) {
      if (template instanceof BundledFileTemplate && deletedDefaults.contains(template.getQualifiedName())) {
        ((BundledFileTemplate)template).setEnabled(false);
      }
    }
  }

  public void writeExternal(Element element) throws WriteExternalException {
    for (FTManager child : myAllManagers) {
      child.saveTemplates();
    }
    validateRecentNames();
    final Element recentElement = new Element(ELEMENT_RECENT_TEMPLATES);
    element.addContent(recentElement);
    myRecentList.writeExternal(recentElement);

    for (FTManager manager : myAllManagers) {
      final Element templatesGroup = new Element(getXmlElementGroupName(manager));
      element.addContent(templatesGroup);
      for (FileTemplateBase template : manager.getAllTemplates(true)) {
        // save only those settings that differ from defaults
        boolean shouldSave = template.isReformatCode() != FileTemplateBase.DEFAULT_REFORMAT_CODE_VALUE;
        if (template instanceof BundledFileTemplate) {
          shouldSave |= ((BundledFileTemplate)template).isEnabled() != FileTemplateBase.DEFAULT_ENABLED_VALUE;
        }
        if (!shouldSave) {
          continue;
        }
        final Element templateElement = new Element(ELEMENT_TEMPLATE);
        templateElement.setAttribute(ATTRIBUTE_NAME, template.getQualifiedName());
        templateElement.setAttribute(ATTRIBUTE_REFORMAT, Boolean.toString(template.isReformatCode()));
        if (template instanceof BundledFileTemplate) {
          templateElement.setAttribute(ATTRIBUTE_ENABLED, Boolean.toString(((BundledFileTemplate)template).isEnabled()));
        }
        templatesGroup.addContent(templateElement);
      }
    }
    //Element deletedTemplatesElement = new Element(ELEMENT_DELETED_TEMPLATES);
    //element.addContent(deletedTemplatesElement);
    //myDefaultTemplatesManager.getDeletedTemplates().writeExternal(deletedTemplatesElement);
    //
    //Element deletedIncludesElement = new Element(ELEMENT_DELETED_INCLUDES);
    //element.addContent(deletedIncludesElement);
    //myPatternsManager.getDeletedTemplates().writeExternal(deletedIncludesElement);
    //
    //Element recentElement = new Element(ELEMENT_RECENT_TEMPLATES);
    //element.addContent(recentElement);
    //myRecentList.writeExternal(recentElement);
    //
    //Element templatesElement = new Element(ELEMENT_TEMPLATES);
    //element.addContent(templatesElement);
    //myDefaultTemplatesManager.invalidate();
    //
    //for (FileTemplate internal : getInternalTemplates()) {
    //  templatesElement.addContent(createElement(internal, true));
    //}
    //
    //for (FileTemplate fileTemplate : getAllTemplates()) {
    //  templatesElement.addContent(createElement(fileTemplate, false));
    //}
  }

  //private static Element createElement(FileTemplate template, boolean isInternal) {
  //  Element templateElement = new Element(isInternal ? ELEMENT_INTERNAL_TEMPLATE : ELEMENT_TEMPLATE);
  //  templateElement.setAttribute(ATTRIBUTE_NAME, template.getName());
  //  templateElement.setAttribute(ATTRIBUTE_REFORMAT, Boolean.toString(template.isReformatCode()));
  //  return templateElement;
  //}

  private static String getXmlElementGroupName(FTManager manager) {
    return manager.getName().toLowerCase(Locale.US) + "_" + "templates";
  }

  private void validateRecentNames() {
    final Collection<FileTemplateBase> allTemplates = myDefaultTemplatesManager.getAllTemplates(false);
    final List<String> allNames = new ArrayList<String>(allTemplates.size());
    for (FileTemplate fileTemplate : allTemplates) {
      allNames.add(fileTemplate.getName());
    }
    myRecentList.validateNames(allNames);
  }

  @NotNull
  public FileTemplate[] getInternalTemplates() {
    InternalTemplateBean[] internalTemplateBeans = Extensions.getExtensions(InternalTemplateBean.EP_NAME);
    FileTemplate[] result = new FileTemplate[internalTemplateBeans.length];
    for(int i=0; i<internalTemplateBeans.length; i++) {
      result [i] = getInternalTemplate(internalTemplateBeans [i].name);
    }
    return result;
  }

  public FileTemplate getInternalTemplate(@NotNull @NonNls String templateName) {
    LOG.assertTrue(myInternalTemplatesManager != null);
    FileTemplateBase template = myInternalTemplatesManager.findTemplateByName(templateName);

    if (template == null) {
      // todo: review the hack and try to get rid of this weird logic completely
      template = myDefaultTemplatesManager.findTemplateByName(templateName);
    }
      
    if (template == null) {
      template = (FileTemplateBase)getJ2eeTemplate(templateName); // Hack to be able to register class templates from the plugin.
      if (template != null) {
        template.setReformatCode(true);
      }
      else {
        final String text = normalizeText(getDefaultClassTemplateText(templateName));
        template = myInternalTemplatesManager.addTemplate(templateName, "java");
        template.setText(text);
      }
    }
    return template;
  }

  private static String normalizeText(String text) {
    text = StringUtil.convertLineSeparators(text);
    text = StringUtil.replace(text, "$NAME$", "${NAME}");
    text = StringUtil.replace(text, "$PACKAGE_NAME$", "${PACKAGE_NAME}");
    text = StringUtil.replace(text, "$DATE$", "${DATE}");
    text = StringUtil.replace(text, "$TIME$", "${TIME}");
    text = StringUtil.replace(text, "$USER$", "${USER}");
    return text;
  }

  @NonNls
  private String getTestClassTemplateText(@NotNull @NonNls String templateName) {
    return "package $PACKAGE_NAME$;\npublic " + internalTemplateToSubject(templateName) + " $NAME$ { }";
  }

  @NotNull
  public String internalTemplateToSubject(@NotNull @NonNls String templateName) {
    //noinspection HardCodedStringLiteral
    for(InternalTemplateBean bean: Extensions.getExtensions(InternalTemplateBean.EP_NAME)) {
      if (bean.name.equals(templateName) && bean.subject != null) {
        return bean.subject;
      }
    }
    return templateName.toLowerCase();
  }

  @NotNull
  public String localizeInternalTemplateName(@NotNull final FileTemplate template) {
    return template.getName();
  }

  @NonNls
  private String getDefaultClassTemplateText(@NotNull @NonNls String templateName) {
    return IdeBundle.message("template.default.class.comment", ApplicationNamesInfo.getInstance().getFullProductName()) +
           "package $PACKAGE_NAME$;\n" + "public " + internalTemplateToSubject(templateName) + " $NAME$ { }";
  }

  public FileTemplate getCodeTemplate(@NotNull @NonNls String templateName) {
    return getTemplateFromManager(templateName, myCodeTemplatesManager);
  }

  public FileTemplate getJ2eeTemplate(@NotNull @NonNls String templateName) {
    return getTemplateFromManager(templateName, myJ2eeTemplatesManager);
  }

  @Nullable
  private static FileTemplate getTemplateFromManager(final @NotNull String templateName, final @NotNull FTManager ftManager) {
    FileTemplateBase template = ftManager.getTemplate(templateName);
    if (template != null) {
      return template;
    }
    template = ftManager.findTemplateByName(templateName);
    if (template != null) {
      return template;
    }
    if (templateName.endsWith("ForTest") && ApplicationManager.getApplication().isUnitTestMode()) {
      return null;
    }

    String message = "Template not found: " + templateName/*ftManager.templateNotFoundMessage(templateName)*/;
    LOG.error(message);
    return null;
  }

  @NotNull
  public FileTemplate getDefaultTemplate(final @NotNull String name) {
    final String templateQName = myTypeManager.getExtension(name).isEmpty()? FileTemplateBase.getQualifiedName(name, "java") : name;

    for (FTManager manager : myAllManagers) {
      final FileTemplateBase template = manager.getTemplate(templateQName);
      if (template instanceof BundledFileTemplate) {
        final BundledFileTemplate copy = ((BundledFileTemplate)template).clone();
        copy.revertToDefaults();
        return copy;
      }
    }
    
    String message = "Default template not found: " + name;
    LOG.error(message);
    return null;
  }

  @NotNull
  public FileTemplate[] getAllPatterns() {
    final Collection<FileTemplateBase> allTemplates = myPatternsManager.getAllTemplates(false);
    return allTemplates.toArray(new FileTemplate[allTemplates.size()]);
  }

  public FileTemplate getPattern(@NotNull String name) {
    return myPatternsManager.findTemplateByName(name);
  }

  @NotNull
  public FileTemplate[] getAllCodeTemplates() {
    final Collection<FileTemplateBase> templates = myCodeTemplatesManager.getAllTemplates(false);
    return templates.toArray(new FileTemplate[templates.size()]);
  }

  @NotNull
  public FileTemplate[] getAllJ2eeTemplates() {
    final Collection<FileTemplateBase> templates = myJ2eeTemplatesManager.getAllTemplates(false);
    return templates.toArray(new FileTemplate[templates.size()]);
  }

  public void setTemplates(@NotNull String templatesCategory, Collection<FileTemplate> templates) {
    for (FTManager manager : myAllManagers) {
      if (templatesCategory.equals(manager.getName())) {
        manager.updateTemplates(templates);
        break;
      }
    }
  }

  public URL getDefaultTemplateDescription() {
    return null;  // todo
  }

  public URL getDefaultIncludeDescription() {
    return null; // todo
  }

  private static class RecentTemplatesManager implements JDOMExternalizable {
    public JDOMExternalizableStringList RECENT_TEMPLATES = new JDOMExternalizableStringList();

    public void addName(@NotNull @NonNls String name) {
      RECENT_TEMPLATES.remove(name);
      RECENT_TEMPLATES.add(name);
    }

    @NotNull
    public Collection<String> getRecentNames(int max) {
      int size = RECENT_TEMPLATES.size();
      int resultSize = Math.min(max, size);
      return RECENT_TEMPLATES.subList(size - resultSize, size);
    }

    public void validateNames(List<String> validNames) {
      RECENT_TEMPLATES.retainAll(validNames);
    }

    public void readExternal(Element element) throws InvalidDataException {
      DefaultJDOMExternalizer.readExternal(this, element);
    }

    public void writeExternal(Element element) throws WriteExternalException {
      DefaultJDOMExternalizer.writeExternal(this, element);
    }

  }
}
