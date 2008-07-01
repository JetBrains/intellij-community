package com.intellij.ide.fileTemplates.impl;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.fileTemplates.FileTemplate;
import com.intellij.ide.fileTemplates.FileTemplateManager;
import com.intellij.ide.fileTemplates.InternalTemplateBean;
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
import com.intellij.util.SystemProperties;
import com.intellij.util.messages.MessageBus;
import gnu.trove.THashSet;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.text.DateFormat;
import java.text.MessageFormat;
import java.util.*;

/**
 * @author MYakovlev
 *         Date: Jul 24
 * @author 2002
 */
public class FileTemplateManagerImpl extends FileTemplateManager implements ExportableComponent, JDOMExternalizable {
  private static final Logger LOG = Logger.getInstance("#com.intellij.ide.fileTemplates.impl.FileTemplateManagerImpl");
  @NonNls private static final String DEFAULT_TEMPLATE_EXTENSION = "ft";
  @NonNls private static final String TEMPLATES_DIR = "fileTemplates";
  @NonNls private static final String DEFAULT_TEMPLATES_TOP_DIR = TEMPLATES_DIR;
  @NonNls private static final String INTERNAL_DIR = "internal";
  @NonNls private static final String INCLUDES_DIR = "includes";
  @NonNls private static final String CODETEMPLATES_DIR = "code";
  @NonNls private static final String J2EE_TEMPLATES_DIR = "j2ee";

  @NonNls private final String myDefaultTemplatesDir;
  @NonNls private final String myTemplatesDir;
  private MyTemplates myTemplates;
  private final RecentTemplatesManager myRecentList = new RecentTemplatesManager();
  private boolean myLoaded = false;
  private final FileTemplateManagerImpl myInternalTemplatesManager;
  private final FileTemplateManagerImpl myPatternsManager;
  private final FileTemplateManagerImpl myCodeTemplatesManager;
  private final FileTemplateManagerImpl myJ2eeTemplatesManager;
  private final MyDeletedTemplatesManager myDeletedTemplatesManager = new MyDeletedTemplatesManager();
  private VirtualFile myDefaultDescription;

  private static VirtualFile[] ourTopDirs;
  private final VirtualFileManager myVirtualFileManager;
  private final FileTypeManagerEx myTypeManager;
  @NonNls private static final String ELEMENT_DELETED_TEMPLATES = "deleted_templates";
  @NonNls private static final String ELEMENT_DELETED_INCLUDES = "deleted_includes";
  @NonNls private static final String ELEMENT_RECENT_TEMPLATES = "recent_templates";
  @NonNls private static final String ELEMENT_TEMPLATES = "templates";
  @NonNls private static final String ELEMENT_INTERNAL_TEMPLATE = "internal_template";
  @NonNls private static final String ELEMENT_TEMPLATE = "template";
  @NonNls private static final String ATTRIBUTE_NAME = "name";
  @NonNls private static final String ATTRIBUTE_REFORMAT = "reformat";

  private final Map<String, String> myLocalizedTemplateNames = new HashMap<String, String>();
  private final Object LOCK = new Object();

  public static FileTemplateManagerImpl getInstance() {
    return (FileTemplateManagerImpl)ServiceManager.getService(FileTemplateManager.class);
  }

  public FileTemplateManagerImpl(@NotNull VirtualFileManager virtualFileManager, @NotNull FileTypeManagerEx typeManager, MessageBus bus) {
    this(".", TEMPLATES_DIR, virtualFileManager, typeManager,
         new FileTemplateManagerImpl(INTERNAL_DIR, TEMPLATES_DIR + File.separator + INTERNAL_DIR, virtualFileManager, typeManager, null, null, null, null),
         new FileTemplateManagerImpl(INCLUDES_DIR, TEMPLATES_DIR + File.separator + INCLUDES_DIR, virtualFileManager, typeManager, null, null, null, null),
         new FileTemplateManagerImpl(CODETEMPLATES_DIR, TEMPLATES_DIR + File.separator + CODETEMPLATES_DIR, virtualFileManager, typeManager, null, null, null, null),
         new FileTemplateManagerImpl(J2EE_TEMPLATES_DIR, TEMPLATES_DIR + File.separator + J2EE_TEMPLATES_DIR, virtualFileManager, typeManager, null, null, null, null));

    bus.connect().subscribe(VirtualFileManager.VFS_CHANGES, new BulkFileListener() {
      public void before(final List<? extends VFileEvent> events) {
      }

      public void after(final List<? extends VFileEvent> events) {
        synchronized (LOCK) {
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
    });
  }

  private FileTemplateManagerImpl(@NotNull @NonNls String defaultTemplatesDir,
                                  @NotNull @NonNls String templatesDir,
                                  @NotNull VirtualFileManager virtualFileManager,
                                  @NotNull FileTypeManagerEx fileTypeManagerEx, FileTemplateManagerImpl internalTemplatesManager,
                                  FileTemplateManagerImpl patternsManager, FileTemplateManagerImpl codeTemplatesManager,
                                  FileTemplateManagerImpl j2eeTemplatesManager) {
    myDefaultTemplatesDir = defaultTemplatesDir;
    myTemplatesDir = templatesDir;
    myVirtualFileManager = virtualFileManager;
    myTypeManager = fileTypeManagerEx;
    myInternalTemplatesManager = internalTemplatesManager;
    myPatternsManager = patternsManager;
    myCodeTemplatesManager = codeTemplatesManager;
    myJ2eeTemplatesManager = j2eeTemplatesManager;
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
    synchronized (LOCK) {
      ensureTemplatesAreLoaded();
      return myTemplates.getAllTemplates();
    }
  }

  public FileTemplate getTemplate(@NotNull @NonNls String templateName) {
    synchronized (LOCK) {
      ensureTemplatesAreLoaded();
      return myTemplates.findByName(templateName);
    }
  }

  @NotNull
  public FileTemplate addTemplate(@NotNull @NonNls String name, @NotNull @NonNls String extension) {
    synchronized (LOCK) {
      invalidate();
      ensureTemplatesAreLoaded();

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
    synchronized (LOCK) {
      ensureTemplatesAreLoaded();
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

    Date date = new Date();
    props.setProperty("DATE", DateFormat.getDateInstance().format(date));
    props.setProperty("TIME", DateFormat.getTimeInstance().format(date));
    Calendar calendar = Calendar.getInstance();
    props.setProperty("YEAR", Integer.toString(calendar.get(Calendar.YEAR)));
    props.setProperty("MONTH", Integer.toString(calendar.get(Calendar.MONTH) + 1)); //to correct Calendar bias to 0
    props.setProperty("DAY", Integer.toString(calendar.get(Calendar.DAY_OF_MONTH)));
    props.setProperty("HOUR", Integer.toString(calendar.get(Calendar.HOUR_OF_DAY)));
    props.setProperty("MINUTE", Integer.toString(calendar.get(Calendar.MINUTE)));

    props.setProperty("USER", SystemProperties.getUserName());

    return props;
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
        loadTemplates();
        myLoaded = true;
      }
    });
  }

  private void loadTemplates() {
    VirtualFile[] defaultTemplates = getDefaultTemplates();
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
      if (file.isDirectory()) {
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
      if (myInternalTemplatesManager != null) {
        myInternalTemplatesManager.saveTemplates();
      }
      if (myPatternsManager != null) {
        myPatternsManager.saveTemplates();
      }
      if (myCodeTemplatesManager != null) {
        myCodeTemplatesManager.saveTemplates();
      }
      if (myJ2eeTemplatesManager != null) {
        myJ2eeTemplatesManager.saveTemplates();
      }
    }
    catch (IOException e) {
      LOG.error("Unable to save templates", e);
    }
  }

  @NotNull
  public Collection<String> getRecentNames() {
    synchronized (LOCK) {
      ensureTemplatesAreLoaded();
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
      FileTemplate[] internals = getInternalTemplates();
      List children = templatesElement.getChildren();
      for (final Object aChildren : children) {
        Element child = (Element)aChildren;
        String name = child.getAttributeValue(ATTRIBUTE_NAME);
        boolean reformat = Boolean.TRUE.toString().equals(child.getAttributeValue(ATTRIBUTE_REFORMAT));
        if (child.getName().equals(ELEMENT_INTERNAL_TEMPLATE)) {
          for (FileTemplate internal : internals) {
            if (name.equals(internal.getName())) internal.setAdjust(reformat);
          }
        }
        else if (child.getName().equals(ELEMENT_TEMPLATE)) {
          FileTemplate template = getTemplate(name);
          if (template != null) {
            template.setAdjust(reformat);
          }
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
    saveAll();
    myLoaded = false;
    if (myTemplates != null) {
      FileTemplate[] allTemplates = myTemplates.getAllTemplates();
      for (FileTemplate template : allTemplates) {
        ((FileTemplateImpl)template).invalidate();
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
      //noinspection HardCodedStringLiteral
      String actualTemplateName = ApplicationManager.getApplication().isUnitTestMode() ? templateName + "ForTest" : templateName;
      FileTemplateImpl template = (FileTemplateImpl)myInternalTemplatesManager.getTemplate(actualTemplateName);

      if (template == null) {
        template = (FileTemplateImpl)getTemplate(actualTemplateName);
      }
      if (template == null) {
        template = (FileTemplateImpl)getJ2eeTemplate(actualTemplateName); // Hack to be able to register class templates from the plugin.
        if (template != null) {
          template.setAdjust(true);
        }
        else {
          String text;
          if (ApplicationManager.getApplication().isUnitTestMode()) {
            text = getTestClassTemplateText(templateName);
          }
          else {
            text = getDefaultClassTemplateText(templateName);
          }

          text = StringUtil.convertLineSeparators(text);
          text = StringUtil.replace(text, "$NAME$", "${NAME}");
          text = StringUtil.replace(text, "$PACKAGE_NAME$", "${PACKAGE_NAME}");
          text = StringUtil.replace(text, "$DATE$", "${DATE}");
          text = StringUtil.replace(text, "$TIME$", "${TIME}");
          text = StringUtil.replace(text, "$USER$", "${USER}");

          template = (FileTemplateImpl)myInternalTemplatesManager.addTemplate(actualTemplateName, "java");
          template.setText(text);
        }
      }

      template.setInternal(true);
      return template;
    }
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
    String localizedName = myLocalizedTemplateNames.get(template.getName());
    if (localizedName == null) {
      localizedName = myLocalizedTemplateNames.get(template.getName() + "." + template.getExtension());
    }
    return localizedName != null ? localizedName : template.getName();
  }

  @NonNls
  private String getDefaultClassTemplateText(@NotNull @NonNls String templateName) {
    return IdeBundle.message("template.default.class.comment", ApplicationNamesInfo.getInstance().getFullProductName()) +
           "package $PACKAGE_NAME$;\n" + "public " + internalTemplateToSubject(templateName) + " $NAME$ { }";
  }

  public FileTemplate getCodeTemplate(@NotNull @NonNls String templateName) {
    return getTemplateFromManager(templateName, myCodeTemplatesManager, "Code");
  }

  public FileTemplate getJ2eeTemplate(@NotNull @NonNls String templateName) {
    return getTemplateFromManager(templateName, myJ2eeTemplatesManager, "J2EE");
  }

  private FileTemplate getTemplateFromManager(@NotNull @NonNls String templateName,
                                              @NotNull FileTemplateManagerImpl templatesManager,
                                              @NotNull @NonNls String templateType) {
    String name = templateName;
    String extension = myTypeManager.getExtension(name);
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

      VirtualFile[] defaultTemplates = templatesManager.getDefaultTemplates();
      @NonNls String message =
        "Unable to find " + templateType + " Template '" + templateName + "'! Default " + templateType + " Templates are: ";
      for (int i = 0; i < defaultTemplates.length; i++) {
        VirtualFile defaultTemplate = defaultTemplates[i];
        if (i != 0) {
          message += ", ";
        }
        message += defaultTemplate.getPresentableUrl();
      }
      LOG.error(message);
    }
    return null;
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
    Set<VirtualFile> removedSet = new HashSet<VirtualFile>();

    for (VirtualFile file : files) {
      String nameWithExtension = file.getName();
      if (myDeletedTemplatesManager.contains(nameWithExtension)) {
        removedSet.add(file);
      }
    }

    files.removeAll(removedSet);
  }

  private static VirtualFile getDefaultFromManager(@NotNull @NonNls String name,
                                                   @NotNull @NonNls String extension,
                                                   FileTemplateManagerImpl manager) {
    if (manager == null) return null;
    VirtualFile[] files = manager.getDefaultTemplates();
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
    if ((result = getDefaultFromManager(name, extension, myInternalTemplatesManager)) != null) return result;
    if ((result = getDefaultFromManager(name, extension, myPatternsManager)) != null) return result;
    if ((result = getDefaultFromManager(name, extension, myJ2eeTemplatesManager)) != null) return result;
    return getDefaultFromManager(name, extension, myCodeTemplatesManager);
  }

  public FileTemplate getDefaultTemplate(@NotNull @NonNls String name) {
    @NonNls String extension = myTypeManager.getExtension(name);
    String nameWithoutExtension = StringUtil.trimEnd(name, "." + extension);
    if (extension.length() == 0) {
      extension = "java";
    }
    VirtualFile file = getDefaultTemplate(nameWithoutExtension, extension);
    if (file == null) return null;
    return new FileTemplateImpl(file, nameWithoutExtension, extension);
  }

  @NotNull
  private VirtualFile[] getDefaultTemplates() {
    if (myDefaultTemplatesDir == null || myDefaultTemplatesDir.length() == 0) {
      return VirtualFile.EMPTY_ARRAY;
    }
    VirtualFile[] topDirs = getTopTemplatesDir();
    if (LOG.isDebugEnabled()) {
      @NonNls String message = "Top dirs found: ";
      for (int i = 0; i < topDirs.length; i++) {
        VirtualFile topDir = topDirs[i];
        message += (i > 0 ? ", " : "") + topDir.getPresentableUrl();
      }
      LOG.debug(message);
    }
    Set<VirtualFile> templatesList = new HashSet<VirtualFile>();
    for (VirtualFile topDir : topDirs) {
      VirtualFile parentDir = myDefaultTemplatesDir.equals(".") ? topDir : topDir.findChild(myDefaultTemplatesDir);
      if (parentDir != null) {
        templatesList.addAll(listDir(parentDir));
      }
    }
    removeDeletedTemplates(templatesList);

    return templatesList.toArray(new VirtualFile[templatesList.size()]);
  }

  @NotNull
  private VirtualFile[] getTopTemplatesDir() {
    synchronized (LOCK) {
      if (ourTopDirs != null) {
        return ourTopDirs;
      }

      Set<VirtualFile> dirList = new THashSet<VirtualFile>();

      appendDefaultTemplatesFromClassloader(FileTemplateManagerImpl.class.getClassLoader(), dirList);
      PluginDescriptor[] plugins = ApplicationManager.getApplication().getPlugins();
      for (PluginDescriptor plugin : plugins) {
        appendDefaultTemplatesFromClassloader(plugin.getPluginClassLoader(), dirList);
      }

      ourTopDirs = dirList.toArray(new VirtualFile[dirList.size()]);
      return ourTopDirs;
    }
  }

  private void appendDefaultTemplatesFromClassloader(ClassLoader classLoader, Set<VirtualFile> dirList) {
    try {
      Enumeration systemResources = classLoader.getResources(DEFAULT_TEMPLATES_TOP_DIR);
      if (systemResources != null && systemResources.hasMoreElements()) {
        Set<URL> urls = new HashSet<URL>();
        while (systemResources.hasMoreElements()) {
          URL nextURL = (URL)systemResources.nextElement();
          if (!urls.contains(nextURL)) {
            urls.add(nextURL);
            VirtualFile dir = VfsUtil.findFileByURL(nextURL, myVirtualFileManager);
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
      for (FileTemplate template : myTemplatesList) {
        if (template == newTemplate) {
          return;
        }
        if (template.getName().compareToIgnoreCase(newTemplate.getName()) > 0) {
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
}
