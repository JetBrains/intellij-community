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
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.newvfs.BulkFileListener;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Function;
import com.intellij.util.SystemProperties;
import com.intellij.util.messages.MessageBus;
import com.intellij.util.text.DateFormatUtil;
import gnu.trove.THashSet;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

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
  private static final FileTemplateManagerImpl[] EMPTY_ARRAY = new FileTemplateManagerImpl[0];
  private static final Logger LOG = Logger.getInstance("#com.intellij.ide.fileTemplates.impl.FileTemplateManagerImpl");
  @NonNls private static final String DEFAULT_TEMPLATE_EXTENSION = "ft";
  @NonNls private static final String TEMPLATES_DIR = "fileTemplates";
  @NonNls private static final String DEFAULT_TEMPLATES_TOP_DIR = TEMPLATES_DIR;
  @NonNls private static final String INTERNAL_DIR = "internal";
  @NonNls private static final String INCLUDES_DIR = "includes";
  @NonNls private static final String CODETEMPLATES_DIR = "code";
  @NonNls private static final String J2EE_TEMPLATES_DIR = "j2ee";

  private final String myName;
  @NonNls private final String myDefaultTemplatesDir;
  @NonNls private final String myTemplatesDir;
  private MyTemplates myTemplates;
  private final RecentTemplatesManager myRecentList = new RecentTemplatesManager();
  private final Set<String> notAdjusted = new HashSet<String>();
  private volatile boolean myLoaded = false;
  private final FileTemplateManagerImpl myInternalTemplatesManager;
  private final FileTemplateManagerImpl myPatternsManager;
  private final FileTemplateManagerImpl myCodeTemplatesManager;
  private final FileTemplateManagerImpl myJ2eeTemplatesManager;
  private final MyDeletedTemplatesManager myDeletedTemplatesManager = new MyDeletedTemplatesManager();
  private VirtualFile myDefaultDescription;

  private static VirtualFile[] ourTopDirs;
  private final FileTypeManagerEx myTypeManager;
  @NonNls private static final String ELEMENT_DELETED_TEMPLATES = "deleted_templates";
  @NonNls private static final String ELEMENT_DELETED_INCLUDES = "deleted_includes";
  @NonNls private static final String ELEMENT_RECENT_TEMPLATES = "recent_templates";
  @NonNls private static final String ELEMENT_TEMPLATES = "templates";
  @NonNls private static final String ELEMENT_INTERNAL_TEMPLATE = "internal_template";
  @NonNls private static final String ELEMENT_TEMPLATE = "template";
  @NonNls private static final String ATTRIBUTE_NAME = "name";
  @NonNls private static final String ATTRIBUTE_REFORMAT = "reformat";

  private final Object LOCK = new Object();
  private static final Object TOP_DIRS_LOCK = new Object();

  private final FileTemplateManagerImpl[] myChildren;
  public static FileTemplateManagerImpl getInstanceImpl() {
    return (FileTemplateManagerImpl)ServiceManager.getService(FileTemplateManager.class);
  }

  public FileTemplateManagerImpl(@NotNull FileTypeManagerEx typeManager, @NotNull MessageBus bus) {
    this("Default", ".", typeManager,
         new FileTemplateManagerImpl("Internal", INTERNAL_DIR, typeManager, null, null, null, null),
         new FileTemplateManagerImpl("Includes", INCLUDES_DIR, typeManager, null, null, null, null),
         new FileTemplateManagerImpl("Code", CODETEMPLATES_DIR, typeManager, null, null, null, null),
         new FileTemplateManagerImpl("J2EE", J2EE_TEMPLATES_DIR, typeManager, null, null, null, null));

    bus.connect().subscribe(VirtualFileManager.VFS_CHANGES, new BulkFileListener() {
      public void before(final List<? extends VFileEvent> events) {
      }

      public void after(final List<? extends VFileEvent> events) {
        refreshTopDirs();
      }
    });
  }

  private FileTemplateManagerImpl(@NotNull @NonNls String name,
                                  @NotNull @NonNls String defaultTemplatesDirName,
                                  @NotNull FileTypeManagerEx fileTypeManagerEx,
                                  FileTemplateManagerImpl internalTemplatesManager,
                                  FileTemplateManagerImpl patternsManager,
                                  FileTemplateManagerImpl codeTemplatesManager,
                                  FileTemplateManagerImpl j2eeTemplatesManager) {
    myName = name;
    myDefaultTemplatesDir = defaultTemplatesDirName;
    myTemplatesDir = TEMPLATES_DIR + (defaultTemplatesDirName.equals(".") ? "" : File.separator + defaultTemplatesDirName);
    myTypeManager = fileTypeManagerEx;
    myInternalTemplatesManager = internalTemplatesManager;
    myPatternsManager = patternsManager;
    myCodeTemplatesManager = codeTemplatesManager;
    myJ2eeTemplatesManager = j2eeTemplatesManager;
    myChildren = internalTemplatesManager == null ? EMPTY_ARRAY : new FileTemplateManagerImpl[]{internalTemplatesManager,patternsManager,codeTemplatesManager,j2eeTemplatesManager};

    if (ApplicationManager.getApplication().isUnitTestMode() && defaultTemplatesDirName.equals(INTERNAL_DIR)) {
      for (String tname : Arrays.asList("Class", "AnnotationType", "Enum", "Interface")) {
        for (FileTemplate template : getAllTemplates()) {
          if (template.getName().equals(tname)) {
            myTemplates.removeTemplate(template);
            break;
          }
        }
        FileTemplateImpl fileTemplate = new FileTemplateImpl(normalizeText(getTestClassTemplateText(tname)), tname, "java");
        fileTemplate.setReadOnly(true);
        fileTemplate.setModified(false);
        myTemplates.addTemplate(fileTemplate);
        fileTemplate.setInternal(true);
      }
    }
  }

  @NotNull
  public File[] getExportFiles() {
    return new File[]{getParentDirectory(false), PathManager.getDefaultOptionsFile()};
  }

  @NotNull
  public String getPresentableName() {
    return IdeBundle.message("item.file.templates");
  }

  @NotNull
  public FileTemplate[] getAllTemplates() {
    ensureTemplatesAreLoaded();
    synchronized (LOCK) {
      return myTemplates.getAllTemplates();
    }
  }

  public FileTemplate getTemplate(@NotNull @NonNls String templateName) {
    ensureTemplatesAreLoaded();
    synchronized (LOCK) {
      return myTemplates.findByName(templateName);
    }
  }

  @NotNull
  public FileTemplate addTemplate(@NotNull @NonNls String name, @NotNull @NonNls String extension) {
    invalidate();
    ensureTemplatesAreLoaded();
    synchronized (LOCK) {

      LOG.assertTrue(name.length() > 0);
      if (myTemplates.findByName(name) != null) {
        LOG.error("Duplicate template " + name);
      }

      FileTemplate fileTemplate = new FileTemplateImpl("", name, extension);
      myTemplates.addTemplate(fileTemplate);
      return fileTemplate;
    }
  }

  public void removeTemplate(@NotNull FileTemplate template, boolean fromDiskOnly) {
    ensureTemplatesAreLoaded();
    synchronized (LOCK) {
      myTemplates.removeTemplate(template);
      try {
        ((FileTemplateImpl)template).removeFromDisk();
      }
      catch (Exception e) {
        LOG.error("Unable to remove template", e);
      }

      if (!fromDiskOnly) {
        myDeletedTemplatesManager.addName(template.getName() + "." + template.getExtension() + "." + DEFAULT_TEMPLATE_EXTENSION);
      }

      invalidate();
    }
  }

  public void removeInternal(@NotNull FileTemplate template) {
    LOG.assertTrue(myInternalTemplatesManager != null);
    myInternalTemplatesManager.removeTemplate(template, true);
  }
  public FileTemplate addInternal(@NotNull @NonNls String name, @NotNull @NonNls String extension) {
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

  private File getParentDirectory(boolean create) {
    File configPath = new File(PathManager.getConfigPath());
    File templatesPath = new File(configPath, myTemplatesDir);
    if (!templatesPath.exists()) {
      if (create) {
        final boolean created = templatesPath.mkdirs();
        if (!created) {
          LOG.error("Cannot create directory: " + templatesPath.getAbsolutePath());
        }
      }
    }
    return templatesPath;
  }

  private void ensureTemplatesAreLoaded() {
    if (myLoaded) {
      return;
    }
    ApplicationManager.getApplication().runReadAction(new Runnable() {
      public void run() {
        synchronized (LOCK) {
          if (myLoaded) {
            return;
          }
          loadTemplates();
          for (FileTemplate template : myTemplates.getAllTemplates()) {
            template.setAdjust(!notAdjusted.contains(template.getName()));
          }
        }
        myLoaded = true;
      }
    });
  }

  private void loadTemplates() {
    Collection<VirtualFile> defaultTemplates = getDefaultTemplates();
    for (VirtualFile file : defaultTemplates) {
      //noinspection HardCodedStringLiteral
      if (file.getName().equals("default.html")) {
        myDefaultDescription = file;           //todo[myakovlev]
      }
    }

    File templateDir = getParentDirectory(false);
    File[] files = templateDir.listFiles();
    if (files == null) {
      files = new File[0];
    }

    if (myTemplates == null) {
      myTemplates = new MyTemplates();
    }
    List<FileTemplate> existingTemplates = new ArrayList<FileTemplate>();
    // Read user-defined templates
    for (File file : files) {
      if (file.isDirectory() || FileTypeManagerEx.getInstance().isFileIgnored(file.getName())) {
        continue;
      }
      String name = file.getName();
      String extension = myTypeManager.getExtension(name);
      name = name.substring(0, name.length() - extension.length() - 1);
      if (file.isHidden() || name.length() == 0) {
        continue;
      }
      FileTemplate existing = myTemplates.findByName(name);
      if (existing == null || existing.isDefault()) {
        if (existing != null) {
          myTemplates.removeTemplate(existing);
        }
        FileTemplateImpl fileTemplate = new FileTemplateImpl(file, name, extension, false);
        //fileTemplate.setDescription(myDefaultDescription);   default description will be shown
        myTemplates.addTemplate(fileTemplate);
        existingTemplates.add(fileTemplate);
      }
      else {
        // it is a user-defined template, revalidate it
        LOG.assertTrue(!((FileTemplateImpl)existing).isModified());
        ((FileTemplateImpl)existing).invalidate();
        existingTemplates.add(existing);
      }
    }
    LOG.debug("FileTemplateManagerImpl.loadTemplates() reading default templates...");
    // Read default templates
    for (VirtualFile file : defaultTemplates) {
      if(FileTypeManagerEx.getInstance().isFileIgnored(file.getName())) continue;
      String name = file.getName();                                                       //name.extension.ft  , e.g.  "NewClass.java.ft"
      @NonNls String extension = myTypeManager.getExtension(name);
      name = name.substring(0, name.length() - extension.length() - 1);                   //name="NewClass.java"   extension="ft"
      if (extension.equals("html")) {
        continue;
      }
      if (!extension.equals(DEFAULT_TEMPLATE_EXTENSION)) {
        LOG.error(file.toString() + " should have *." + DEFAULT_TEMPLATE_EXTENSION + " extension!");
      }
      extension = myTypeManager.getExtension(name);
      name = name.substring(0, name.length() - extension.length() - 1);                   //name="NewClass"   extension="java"
      FileTemplate aTemplate = myTemplates.findByName(name);
      if (aTemplate == null) {
        FileTemplate fileTemplate = new FileTemplateImpl(file, name, extension);
        myTemplates.addTemplate(fileTemplate);
        aTemplate = fileTemplate;
      }
      VirtualFile description = getDescriptionForTemplate(file);
      if (description != null) {
        ((FileTemplateImpl)aTemplate).setDescription(description);
      }
      /*else{
        ((FileTemplateImpl)aTemplate).setDescription(myDefaultDescription);
      }*/
    }
    FileTemplate[] allTemplates = myTemplates.getAllTemplates();
    for (FileTemplate template : allTemplates) {
      FileTemplateImpl templateImpl = (FileTemplateImpl)template;
      if (!templateImpl.isDefault()) {
        if (!existingTemplates.contains(templateImpl)) {
          if (!templateImpl.isNew()) {
            myTemplates.removeTemplate(templateImpl);
            templateImpl.removeFromDisk();
          }
        }
      }
    }
  }


  private void saveTemplates() {
    try {
      if (myTemplates != null) {
        for (FileTemplate template : myTemplates.getAllTemplates()) {
          FileTemplateImpl templateImpl = (FileTemplateImpl)template;
          if (templateImpl.isModified()) {
            templateImpl.writeExternal(getParentDirectory(true));
          }
        }
      }
      for (FileTemplateManagerImpl child : myChildren) {
        child.saveTemplates();
      }
    }
    catch (IOException e) {
      LOG.error("Unable to save templates", e);
    }
  }

  @NotNull
  public Collection<String> getRecentNames() {
    ensureTemplatesAreLoaded();
    synchronized (LOCK) {
      validateRecentNames();
      return myRecentList.getRecentNames(RECENT_TEMPLATES_SIZE);
    }
  }

  public void addRecentName(@NotNull @NonNls String name) {
    synchronized (LOCK) {
      myRecentList.addName(name);
    }
  }

  public void readExternal(Element element) throws InvalidDataException {
    Element deletedTemplatesElement = element.getChild(ELEMENT_DELETED_TEMPLATES);
    if (deletedTemplatesElement != null) {
      myDeletedTemplatesManager.readExternal(deletedTemplatesElement);
    }

    Element deletedIncludesElement = element.getChild(ELEMENT_DELETED_INCLUDES);
    if (deletedIncludesElement != null) {
      myPatternsManager.myDeletedTemplatesManager.readExternal(deletedIncludesElement);
    }

    Element recentElement = element.getChild(ELEMENT_RECENT_TEMPLATES);
    if (recentElement != null) {
      myRecentList.readExternal(recentElement);
    }

    Element templatesElement = element.getChild(ELEMENT_TEMPLATES);
    if (templatesElement != null) {
      invalidate();
      List children = templatesElement.getChildren();
      notAdjusted.clear();
      for (final Object aChildren : children) {
        Element child = (Element)aChildren;
        String name = child.getAttributeValue(ATTRIBUTE_NAME);
        boolean reformat = Boolean.TRUE.toString().equals(child.getAttributeValue(ATTRIBUTE_REFORMAT));
        if (!reformat) {
          notAdjusted.add(name);
        }
      }
    }
  }

  public void writeExternal(Element element) throws WriteExternalException {
    saveTemplates();
    validateRecentNames();

    Element deletedTemplatesElement = new Element(ELEMENT_DELETED_TEMPLATES);
    element.addContent(deletedTemplatesElement);
    myDeletedTemplatesManager.writeExternal(deletedTemplatesElement);

    Element deletedIncludesElement = new Element(ELEMENT_DELETED_INCLUDES);
    element.addContent(deletedIncludesElement);
    myPatternsManager.myDeletedTemplatesManager.writeExternal(deletedIncludesElement);

    Element recentElement = new Element(ELEMENT_RECENT_TEMPLATES);
    element.addContent(recentElement);
    myRecentList.writeExternal(recentElement);

    Element templatesElement = new Element(ELEMENT_TEMPLATES);
    element.addContent(templatesElement);
    invalidate();
    FileTemplate[] internals = getInternalTemplates();
    for (FileTemplate internal : internals) {
      templatesElement.addContent(createElement(internal, true));
    }

    FileTemplate[] allTemplates = getAllTemplates();
    for (FileTemplate fileTemplate : allTemplates) {
      templatesElement.addContent(createElement(fileTemplate, false));
    }
  }

  private static Element createElement(FileTemplate template, boolean isInternal) {
    Element templateElement = new Element(isInternal ? ELEMENT_INTERNAL_TEMPLATE : ELEMENT_TEMPLATE);
    templateElement.setAttribute(ATTRIBUTE_NAME, template.getName());
    templateElement.setAttribute(ATTRIBUTE_REFORMAT, Boolean.toString(template.isAdjust()));
    return templateElement;
  }

  private void validateRecentNames() {
    if (myTemplates != null) {
      List<String> allNames = new ArrayList<String>(myTemplates.size());
      FileTemplate[] allTemplates = myTemplates.getAllTemplates();
      for (FileTemplate fileTemplate : allTemplates) {
        allNames.add(fileTemplate.getName());
      }
      myRecentList.validateNames(allNames);
    }
  }

  private void invalidate() {
    synchronized (LOCK) {
      saveAll();
      myLoaded = false;
      if (myTemplates != null) {
        FileTemplate[] allTemplates = myTemplates.getAllTemplates();
        for (FileTemplate template : allTemplates) {
          ((FileTemplateImpl)template).invalidate();
        }
      }
    }
  }

  public void saveAll() {
    synchronized (LOCK) {
      saveTemplates();
    }
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
    synchronized (LOCK) {
      LOG.assertTrue(myInternalTemplatesManager != null);

      FileTemplateImpl template = (FileTemplateImpl)myInternalTemplatesManager.getTemplate(templateName);

      if (template == null) {
        template = (FileTemplateImpl)getTemplate(templateName);
      }
      
      if (template == null) {
        template = (FileTemplateImpl)getJ2eeTemplate(templateName); // Hack to be able to register class templates from the plugin.
        if (template != null) {
          template.setAdjust(true);
        }
        else {
          String text = normalizeText(getDefaultClassTemplateText(templateName));

          template = (FileTemplateImpl)myInternalTemplatesManager.addTemplate(templateName, "java");
          template.setText(text);
        }
      }

      template.setInternal(true);
      return template;
    }
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

  private static FileTemplate getTemplateFromManager(@NotNull @NonNls String templateName, @NotNull FileTemplateManagerImpl templatesManager) {
    String name = templateName;
    String extension = templatesManager.myTypeManager.getExtension(name);
    if (extension.length() > 0) {
      name = name.substring(0, name.length() - extension.length() - 1);
    }
    FileTemplate template = templatesManager.getTemplate(name);
    if (template != null) {
      if (extension.equals(template.getExtension())) {
        return template;
      }
    }
    else {
      if (ApplicationManager.getApplication().isUnitTestMode() && templateName.endsWith("ForTest")) return null;

      String message = templatesManager.templateNotFoundMessage(templateName);
      LOG.error(message);
    }
    return null;
  }

  private String templateNotFoundMessage(String templateName) {
    Collection<VirtualFile> defaultTemplates = getDefaultTemplates();
    @NonNls String message =
      "Unable to find template '" + templateName + "' in " + this +
      "\n   Default templates are: " + toString(defaultTemplates);
    message+= "\n   Default template dir: '"+ myDefaultTemplatesDir+"'";
    for (VirtualFile topDir : getTopTemplatesDir()) {
      VirtualFile parentDir = myDefaultTemplatesDir.equals(".") ? topDir : topDir.findChild(myDefaultTemplatesDir);
      if (parentDir == null) {
        message += "\n   No templates in '" + topDir.getPath() + "'";
      }
      else {
        message += "\n   " + parentDir.getPath() + ": " + toString(listDir(parentDir));
      }
    }

    message += "\n   Deleted templates: " + myDeletedTemplatesManager.DELETED_DEFAULT_TEMPLATES;

    return message;
  }

  private static String toString(Collection<VirtualFile> defaultTemplates) {
    return StringUtil.join(defaultTemplates, new Function<VirtualFile, String>() {
      public String fun(VirtualFile virtualFile) {
        return virtualFile.getPresentableUrl();
      }
    }, ", ");
  }


  @SuppressWarnings({"HardCodedStringLiteral"})
  private VirtualFile getDescriptionForTemplate(VirtualFile vfile) {
    if (vfile != null) {
      VirtualFile parent = vfile.getParent();
      assert parent != null;
      String name = vfile.getName();                                                    //name.extension.ft  , f.e.  "NewClass.java.ft"
      String extension = myTypeManager.getExtension(name);
      if (extension.equals(DEFAULT_TEMPLATE_EXTENSION)) {
        name = name.substring(0, name.length() - extension.length() - 1);                   //name="NewClass.java"   extension="ft"

        Locale locale = Locale.getDefault();
        String descName = MessageFormat.format("{0}_{1}_{2}.html", name, locale.getLanguage(), locale.getCountry());
        VirtualFile descFile = parent.findChild(descName);
        if (descFile != null && descFile.isValid()) {
          return descFile;
        }

        descName = MessageFormat.format("{0}_{1}.html", name, locale.getLanguage());
        descFile = parent.findChild(descName);
        if (descFile != null && descFile.isValid()) {
          return descFile;
        }

        descFile = parent.findChild(name + ".html");
        if (descFile != null && descFile.isValid()) {
          return descFile;
        }
      }
    }
    return null;
  }

  private static List<VirtualFile> listDir(VirtualFile vfile) {
    List<VirtualFile> result = new ArrayList<VirtualFile>();
    if (vfile != null && vfile.isDirectory()) {
      VirtualFile[] children = vfile.getChildren();
      for (VirtualFile child : children) {
        if (!child.isDirectory()) {
          result.add(child);
        }
      }
    }
    return result;
  }

  private void removeDeletedTemplates(Set<VirtualFile> files) {
    Iterator<VirtualFile> iterator = files.iterator();
    while (iterator.hasNext()) {
      VirtualFile file = iterator.next();
      String nameWithExtension = file.getName();
      if (myDeletedTemplatesManager.contains(nameWithExtension)) {
        iterator.remove();
      }
    }
  }

  private static VirtualFile getDefaultFromManager(@NotNull @NonNls String name,
                                                   @NotNull @NonNls String extension,
                                                   @NotNull FileTemplateManagerImpl manager) {
    Collection<VirtualFile> files = manager.getDefaultTemplates();
    for (VirtualFile file : files) {
      if (DEFAULT_TEMPLATE_EXTENSION.equals(file.getExtension())) {
        String fullName = file.getNameWithoutExtension(); //Strip .ft
        if (fullName.equals(name + "." + extension)) return file;
      }
    }
    return null;
  }

  public VirtualFile getDefaultTemplate(@NotNull @NonNls String name, @NotNull @NonNls String extension) {
    VirtualFile result;
    if ((result = getDefaultFromManager(name, extension, this)) != null) return result;
    for (FileTemplateManagerImpl child : myChildren) {
      if ((result = getDefaultFromManager(name, extension, child)) != null) return result;
    }
    return null;
  }

  @NotNull
  public FileTemplate getDefaultTemplate(@NotNull @NonNls String name) {
    @NonNls String extension = myTypeManager.getExtension(name);
    String nameWithoutExtension = StringUtil.trimEnd(name, "." + extension);
    if (extension.length() == 0) {
      extension = "java";
    }
    VirtualFile file = getDefaultTemplate(nameWithoutExtension, extension);
    if (file == null) {
      String message = "";
      for (FileTemplateManagerImpl child : ArrayUtil.append(myChildren,this)) {
        message += child.templateNotFoundMessage(name) + "\n";
      }
      LOG.error(message);
      return null;
    }
    return new FileTemplateImpl(file, nameWithoutExtension, extension);
  }

  @NotNull
  private Collection<VirtualFile> getDefaultTemplates() {
    LOG.assertTrue(!StringUtil.isEmpty(myDefaultTemplatesDir), myDefaultTemplatesDir);
    VirtualFile[] topDirs = getTopTemplatesDir();
    if (LOG.isDebugEnabled()) {
      @NonNls String message = "Top dirs found: ";
      for (int i = 0; i < topDirs.length; i++) {
        VirtualFile topDir = topDirs[i];
        message += (i > 0 ? ", " : "") + topDir.getPresentableUrl();
      }
      LOG.debug(message);
    }
    Set<VirtualFile> templatesList = new THashSet<VirtualFile>();
    for (VirtualFile topDir : topDirs) {
      VirtualFile parentDir = myDefaultTemplatesDir.equals(".") ? topDir : topDir.findChild(myDefaultTemplatesDir);
      if (parentDir != null) {
        templatesList.addAll(listDir(parentDir));
      }
    }
    removeDeletedTemplates(templatesList);

    return templatesList;
  }

  private static void refreshTopDirs() {
    synchronized (TOP_DIRS_LOCK) {
      if (ourTopDirs != null) {
        for (VirtualFile dir : ourTopDirs) {
          if (!dir.exists()) {
            ourTopDirs = null;
            break;
          }
        }
      }
    }
  }

  @NotNull
  private static VirtualFile[] getTopTemplatesDir() {
    synchronized (TOP_DIRS_LOCK) {
      if (ourTopDirs != null) {
        return ourTopDirs;
      }

      Set<VirtualFile> dirList = new THashSet<VirtualFile>();

      PluginDescriptor[] plugins = ApplicationManager.getApplication().getPlugins();
      for (PluginDescriptor plugin : plugins) {
        if (plugin instanceof IdeaPluginDescriptorImpl && ((IdeaPluginDescriptorImpl)plugin).isEnabled()) {
          final ClassLoader loader = plugin.getPluginClassLoader();
          if (loader instanceof PluginClassLoader && ((PluginClassLoader)loader).getUrls().isEmpty()) {
            continue; // development mode, when IDEA_CORE's loader contains all the classpath
          }
          appendDefaultTemplatesDirFromClassloader(loader, dirList);
        }
      }

      ourTopDirs = VfsUtil.toVirtualFileArray(dirList);
      for (VirtualFile topDir : ourTopDirs) {
        topDir.refresh(true,true);
      }
      return ourTopDirs;
    }
  }

  private static void appendDefaultTemplatesDirFromClassloader(ClassLoader classLoader, Set<VirtualFile> dirList) {
    try {
      Enumeration systemResources = classLoader.getResources(DEFAULT_TEMPLATES_TOP_DIR);
      if (systemResources != null && systemResources.hasMoreElements()) {
        Set<URL> urls = new HashSet<URL>();
        while (systemResources.hasMoreElements()) {
          URL nextURL = (URL)systemResources.nextElement();
          if (!urls.contains(nextURL)) {
            urls.add(nextURL);
            String vfUrl = VfsUtil.convertFromUrl(nextURL);
            VirtualFile dir = VirtualFileManager.getInstance().refreshAndFindFileByUrl(vfUrl);
            if (dir == null) {
              LOG.error("Cannot find file by URL: " + nextURL);
            }
            else {
              if (LOG.isDebugEnabled()) {
                LOG.debug("Top directory: " + dir.getPresentableUrl());
              }
              dirList.add(dir);
            }
          }
        }
      }
    }
    catch (IOException e) {
      LOG.error(e);
    }
  }

  @NotNull
  public FileTemplate[] getAllPatterns() {
    return myPatternsManager.getAllTemplates();
  }

  public FileTemplate getPattern(@NotNull @NonNls String name) {
    return myPatternsManager.getTemplate(name);
  }

  public FileTemplate addPattern(@NotNull @NonNls String name, @NotNull @NonNls String extension) {
    LOG.assertTrue(myPatternsManager != null);
    return myPatternsManager.addTemplate(name, extension);
  }

  public void removePattern(@NotNull FileTemplate template, boolean fromDiskOnly) {
    LOG.assertTrue(myPatternsManager != null);
    myPatternsManager.removeTemplate(template, fromDiskOnly);
  }

  @NotNull
  public FileTemplate[] getAllCodeTemplates() {
    LOG.assertTrue(myCodeTemplatesManager != null);
    return myCodeTemplatesManager.getAllTemplates();
  }

  @NotNull
  public FileTemplate[] getAllJ2eeTemplates() {
    LOG.assertTrue(myJ2eeTemplatesManager != null);
    return myJ2eeTemplatesManager.getAllTemplates();
  }

  @NotNull
  public FileTemplate addCodeTemplate(@NotNull @NonNls String name, @NotNull @NonNls String extension) {
    LOG.assertTrue(myCodeTemplatesManager != null);
    return myCodeTemplatesManager.addTemplate(name, extension);
  }

  @NotNull
  public FileTemplate addJ2eeTemplate(@NotNull @NonNls String name, @NotNull @NonNls String extension) {
    LOG.assertTrue(myJ2eeTemplatesManager != null);
    return myJ2eeTemplatesManager.addTemplate(name, extension);
  }

  public void removeCodeTemplate(@NotNull FileTemplate template, boolean fromDiskOnly) {
    LOG.assertTrue(myCodeTemplatesManager != null);
    myCodeTemplatesManager.removeTemplate(template, fromDiskOnly);
  }

  public void removeJ2eeTemplate(@NotNull FileTemplate template, boolean fromDiskOnly) {
    LOG.assertTrue(myJ2eeTemplatesManager != null);
    myJ2eeTemplatesManager.removeTemplate(template, fromDiskOnly);
  }

  public VirtualFile getDefaultTemplateDescription() {
    return myDefaultDescription;
  }

  public VirtualFile getDefaultIncludeDescription() {
    return myPatternsManager.myDefaultDescription;
  }

  private static class MyTemplates {
    private final List<FileTemplate> myTemplatesList = new ArrayList<FileTemplate>();

    public int size() {
      return myTemplatesList.size();
    }

    public void removeTemplate(FileTemplate template) {
      myTemplatesList.remove(template);
    }

    @NotNull
    public FileTemplate[] getAllTemplates() {
      return myTemplatesList.toArray(new FileTemplate[myTemplatesList.size()]);
    }

    public FileTemplate findByName(@NotNull @NonNls String name) {
      for (FileTemplate template : myTemplatesList) {
        if (template.getName().equals(name)) {
          return template;
        }
      }
      return null;
    }

    public void addTemplate(@NotNull FileTemplate newTemplate) {
      String newName = newTemplate.getName();

      for (FileTemplate template : myTemplatesList) {
        if (template == newTemplate) {
          return;
        }
        if (template.getName().compareToIgnoreCase(newName) > 0) {
          myTemplatesList.add(myTemplatesList.indexOf(template), newTemplate);
          return;
        }
      }
      myTemplatesList.add(newTemplate);
    }
  }

  private static class MyDeletedTemplatesManager implements JDOMExternalizable {
    public JDOMExternalizableStringList DELETED_DEFAULT_TEMPLATES = new JDOMExternalizableStringList();

    public void addName(@NotNull @NonNls String nameWithExtension) {
      DELETED_DEFAULT_TEMPLATES.remove(nameWithExtension);
      DELETED_DEFAULT_TEMPLATES.add(nameWithExtension);
    }

    public boolean contains(@NotNull @NonNls String nameWithExtension) {
      return DELETED_DEFAULT_TEMPLATES.contains(nameWithExtension);
    }

    public void readExternal(Element element) throws InvalidDataException {
      DefaultJDOMExternalizer.readExternal(this, element);
    }

    public void writeExternal(Element element) throws WriteExternalException {
      DefaultJDOMExternalizer.writeExternal(this, element);
    }
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

  @NonNls
  @Override
  public String toString() {
    return myName + " file template manager";
  }
}
