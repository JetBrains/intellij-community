// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.ide.fileTemplates.impl;

import com.intellij.diagnostic.PluginException;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.fileTemplates.*;
import com.intellij.ide.plugins.DynamicPluginListener;
import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.components.StoragePathMacros;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.ex.FileTypeManagerEx;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.text.Strings;
import com.intellij.project.ProjectKt;
import com.intellij.util.ArrayUtil;
import com.intellij.util.SystemProperties;
import com.intellij.util.text.DateFormatUtil;
import com.intellij.util.xmlb.XmlSerializerUtil;
import com.intellij.util.xmlb.annotations.OptionTag;
import kotlin.Unit;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.function.Supplier;

@State(name = "FileTemplateManagerImpl", storages = @Storage(StoragePathMacros.WORKSPACE_FILE))
public final class FileTemplateManagerImpl extends FileTemplateManager implements PersistentStateComponent<FileTemplateManagerImpl.State> {
  private static final Logger LOG = Logger.getInstance(FileTemplateManagerImpl.class);

  private final State state = new State();
  private final ExportableFileTemplateSettings defaultSettings;
  private final Project project;

  private final FileTemplatesScheme myProjectScheme;
  private FileTemplatesScheme scheme = FileTemplatesScheme.DEFAULT;
  private boolean myInitialized;

  public static FileTemplateManagerImpl getInstanceImpl(@NotNull Project project) {
    return (FileTemplateManagerImpl)getInstance(project);
  }

  FileTemplateManagerImpl(@NotNull Project project) {
    defaultSettings = ApplicationManager.getApplication().getService(ExportableFileTemplateSettings.class);
    this.project = project;

    myProjectScheme = project.isDefault() ? null : new FileTemplatesScheme(IdeBundle.message("project.scheme")) {
      @Override
      public @NotNull String getTemplatesDir() {
        return ProjectKt.getStateStore(project).getProjectFilePath().getParent().resolve(TEMPLATES_DIR).toString();
      }

      @Override
      public @NotNull Project getProject() {
        return project;
      }
    };
    project.getMessageBus().connect().subscribe(DynamicPluginListener.TOPIC, new DynamicPluginListener() {
      @Override
      public void pluginUnloaded(@NotNull IdeaPluginDescriptor pluginDescriptor, boolean isUpdate) {
        ClassLoader pluginClassLoader = pluginDescriptor.getClassLoader();
        for (FileTemplate template : getAllTemplates()) {
          if (FileTemplateUtil.findHandler(template).getClass().getClassLoader() == pluginClassLoader) {
            removeTemplate(template);
          }
        }
      }
    });
  }

  private FileTemplateSettings getSettings() {
    return scheme == FileTemplatesScheme.DEFAULT ? defaultSettings : project.getService(FileTemplateSettings.class);
  }

  @Override
  public @NotNull FileTemplatesScheme getCurrentScheme() {
    return scheme;
  }

  @Override
  public void setCurrentScheme(@NotNull FileTemplatesScheme scheme) {
    for (FTManager child : getAllManagers()) {
      child.saveTemplates();
    }
    setScheme(scheme);
  }

  private void setScheme(@NotNull FileTemplatesScheme scheme) {
    this.scheme = scheme;
    myInitialized = true;
  }

  @Override
  protected @NotNull FileTemplateManager checkInitialized() {
    if (!myInitialized) {
      // loadState() not called; init default scheme
      setScheme(scheme);
    }
    return this;
  }

  @Override
  public @Nullable FileTemplatesScheme getProjectScheme() {
    return myProjectScheme;
  }

  @Override
  public FileTemplate @NotNull [] getTemplates(@NotNull String category) {
    return switch (category) {
      case DEFAULT_TEMPLATES_CATEGORY -> ArrayUtil.mergeArrays(getInternalTemplates(), getAllTemplates());
      case INCLUDES_TEMPLATES_CATEGORY -> getAllPatterns();
      case CODE_TEMPLATES_CATEGORY -> getAllCodeTemplates();
      case J2EE_TEMPLATES_CATEGORY -> getAllJ2eeTemplates();
      default -> throw new IllegalArgumentException("Unknown category: " + category);
    };
  }

  @Override
  public FileTemplate @NotNull [] getAllTemplates() {
    return getSettings().getDefaultTemplatesManager().getAllTemplates(false).toArray(FileTemplate.EMPTY_ARRAY);
  }

  @Override
  public FileTemplate getTemplate(@NotNull String templateName) {
    return getSettings().getDefaultTemplatesManager().findTemplateByName(templateName);
  }

  @Override
  public @NotNull FileTemplate addTemplate(@NotNull String name, @NotNull String extension) {
    return getSettings().getDefaultTemplatesManager().addTemplate(name, extension);
  }

  @Override
  public void removeTemplate(@NotNull FileTemplate template) {
    final String qName = ((FileTemplateBase)template).getQualifiedName();
    for (FTManager manager : getAllManagers()) {
      manager.removeTemplate(qName);
    }
  }

  @Override
  public @NotNull Properties getDefaultProperties() {
    @NonNls Properties props = new Properties();

    Calendar calendar = Calendar.getInstance();
    Date date = myTestDate == null ? calendar.getTime() : myTestDate;
    SimpleDateFormat sdfMonthNameShort = new SimpleDateFormat("MMM");
    SimpleDateFormat sdfMonthNameFull = new SimpleDateFormat("MMMM");
    SimpleDateFormat sdfDayNameShort = new SimpleDateFormat("EEE");
    SimpleDateFormat sdfDayNameFull = new SimpleDateFormat("EEEE");
    SimpleDateFormat sdfYearFull = new SimpleDateFormat("yyyy");

    props.setProperty("DATE", DateFormatUtil.formatDate(date));
    props.setProperty("TIME", DateFormatUtil.formatTime(date));
    props.setProperty("YEAR", sdfYearFull.format(date));
    props.setProperty("MONTH", getCalendarValue(calendar, Calendar.MONTH));
    props.setProperty("MONTH_NAME_SHORT", sdfMonthNameShort.format(date));
    props.setProperty("MONTH_NAME_FULL", sdfMonthNameFull.format(date));
    props.setProperty("DAY", getCalendarValue(calendar, Calendar.DAY_OF_MONTH));
    props.setProperty("DAY_NAME_SHORT", sdfDayNameShort.format(date));
    props.setProperty("DAY_NAME_FULL", sdfDayNameFull.format(date));
    props.setProperty("HOUR", getCalendarValue(calendar, Calendar.HOUR_OF_DAY));
    props.setProperty("MINUTE", getCalendarValue(calendar, Calendar.MINUTE));
    props.setProperty("SECOND", getCalendarValue(calendar, Calendar.SECOND));

    props.setProperty("USER", SystemProperties.getUserName());
    props.setProperty("PRODUCT_NAME", ApplicationNamesInfo.getInstance().getFullProductName());

    props.setProperty("DS", "$"); // Dollar sign, strongly needed for PHP, JS, etc. See WI-8979

    props.setProperty(PROJECT_NAME_VARIABLE, project.getName());

    return props;
  }

  private static @NotNull String getCalendarValue(final Calendar calendar, final int field) {
    int val = calendar.get(field);
    if (field == Calendar.MONTH) val++;
    final String result = Integer.toString(val);
    if (result.length() == 1) {
      return "0" + result;
    }
    return result;
  }

  @Override
  public @NotNull Collection<String> getRecentNames() {
    validateRecentNames(); // todo: no need to do it lazily
    return state.getRecentNames();
  }

  @Override
  public void addRecentName(@NotNull @NonNls String name) {
    state.addName(name);
  }

  private void validateRecentNames() {
    Collection<? extends FileTemplateBase> allTemplates = getSettings().getDefaultTemplatesManager().getAllTemplates(false);
    final List<String> allNames = new ArrayList<>(allTemplates.size());
    for (FileTemplate fileTemplate : allTemplates) {
      allNames.add(fileTemplate.getName());
    }
    state.validateNames(allNames);
  }

  @Override
  public FileTemplate @NotNull [] getInternalTemplates() {
    List<FileTemplate> result = new ArrayList<>(InternalTemplateBean.EP_NAME.getPoint().size());
    InternalTemplateBean.EP_NAME.processWithPluginDescriptor((bean, pluginDescriptor) -> {
      try {
        result.add(getInternalTemplate(bean.name));
      }
      catch (Exception e) {
        LOG.error("Can't find template " + bean.name, new PluginException(e, pluginDescriptor.getPluginId()));
      }
      return Unit.INSTANCE;
    });
    return result.toArray(FileTemplate.EMPTY_ARRAY);
  }

  @Override
  public @NotNull FileTemplate getInternalTemplate(@NotNull @NonNls String templateName) {
    FileTemplateBase template = (FileTemplateBase)findInternalTemplate(templateName);

    if (template == null) {
      template = (FileTemplateBase)getJ2eeTemplate(templateName); // Hack to be able to register class templates from the plugin.
      template.setReformatCode(true);
    }
    return template;
  }

  @Override
  public FileTemplate findInternalTemplate(@NotNull @NonNls String templateName) {
    FileTemplateBase template = getSettings().getInternalTemplatesManager().findTemplateByName(templateName);

    if (template == null) {
      // todo: review the hack and try to get rid of this weird logic completely
      template = getSettings().getDefaultTemplatesManager().findTemplateByName(templateName);
    }
    return template;
  }

  @Override
  public @NotNull String internalTemplateToSubject(@NotNull @NonNls String templateName) {
    for(InternalTemplateBean bean: InternalTemplateBean.EP_NAME.getExtensionList()) {
      if (bean.name.equals(templateName) && bean.subject != null) {
        return bean.subject;
      }
    }
    return Strings.toLowerCase(templateName);
  }

  @Override
  public @NotNull FileTemplate getCodeTemplate(@NotNull @NonNls String templateName) {
    return getTemplateFromManager(templateName, getSettings().getCodeTemplatesManager());
  }

  @Override
  public @NotNull FileTemplate getJ2eeTemplate(@NotNull @NonNls String templateName) {
    return getTemplateFromManager(templateName, getSettings().getJ2eeTemplatesManager());
  }

  private static @NotNull FileTemplate getTemplateFromManager(@NotNull String templateName, @NotNull FTManager ftManager) {
    FileTemplateBase template = ftManager.getTemplate(templateName);
    if (template != null) {
      return template;
    }
    template = ftManager.findTemplateByName(templateName);
    if (template != null) {
      return template;
    }

    throw new IllegalStateException("Template not found: " + templateName);
  }

  @Override
  public @NotNull FileTemplate getDefaultTemplate(@NotNull String name) {
    String templateQName = getQualifiedName(name);
    for (FTManager manager : getSettings().getAllManagers()) {
      FileTemplateBase template = manager.getTemplate(templateQName);
      if (template != null) {
        if (template instanceof BundledFileTemplate) {
          template = ((BundledFileTemplate)template).clone();
          ((BundledFileTemplate)template).revertToDefaults();
        }
        return template;
      }
    }

    String message = "Default template not found: " + name;
    LOG.error(message);
    throw new RuntimeException(message);
  }

  private static @NotNull String getQualifiedName(@NotNull String name) {
    return FileTypeManagerEx.getInstanceEx().getExtension(name).isEmpty() ? FileTemplateBase.getQualifiedName(name, "java") : name;
  }

  @Override
  public FileTemplate @NotNull [] getAllPatterns() {
    Collection<? extends FileTemplateBase> allTemplates = getSettings().getPatternsManager().getAllTemplates(false);
    return allTemplates.toArray(FileTemplate.EMPTY_ARRAY);
  }

  @Override
  public FileTemplate getPattern(@NotNull String name) {
    return getSettings().getPatternsManager().findTemplateByName(name);
  }

  @Override
  public FileTemplate @NotNull [] getAllCodeTemplates() {
    Collection<? extends FileTemplateBase> templates = getSettings().getCodeTemplatesManager().getAllTemplates(false);
    return templates.toArray(FileTemplate.EMPTY_ARRAY);
  }

  @Override
  public FileTemplate @NotNull [] getAllJ2eeTemplates() {
    Collection<? extends FileTemplateBase> templates = getSettings().getJ2eeTemplatesManager().getAllTemplates(false);
    return templates.toArray(FileTemplate.EMPTY_ARRAY);
  }

  @Override
  public void setTemplates(@NotNull String templatesCategory, @NotNull Collection<? extends FileTemplate> templates) {
    for (FTManager manager : getAllManagers()) {
      if (templatesCategory.equals(manager.getName())) {
        manager.updateTemplates(templates);
        break;
      }
    }
  }

  @Override
  public void saveAllTemplates() {
    for (FTManager manager : getAllManagers()) {
      manager.saveTemplates();
    }
  }

  public Supplier<String> getDefaultTemplateDescription() {
    return defaultSettings.getDefaultTemplateDescription();
  }

  Supplier<String> getDefaultIncludeDescription() {
    return defaultSettings.getDefaultIncludeDescription();
  }

  private Date myTestDate;

  @TestOnly
  public void setTestDate(Date testDate) {
    myTestDate = testDate;
  }

  @Override
  public @NotNull State getState() {
    state.SCHEME = scheme.getName();
    return state;
  }

  @Override
  public void loadState(@NotNull State state) {
    XmlSerializerUtil.copyBean(state, this.state);
    FileTemplatesScheme scheme = myProjectScheme != null && myProjectScheme.getName().equals(state.SCHEME) ? myProjectScheme : FileTemplatesScheme.DEFAULT;
    setScheme(scheme);
  }

  private Collection<FTManager> getAllManagers() {
    return getSettings().getAllManagers();
  }

  @TestOnly
  public void setDefaultFileIncludeTemplateTextTemporarilyForTest(String simpleName, String text, @NotNull Disposable parentDisposable) {
    FTManager defaultTemplatesManager = getSettings().getPatternsManager();
    String qName = getQualifiedName(simpleName);
    FileTemplateBase oldTemplate = defaultTemplatesManager.getTemplate(qName);
    Map<String, FileTemplateBase> templates = defaultTemplatesManager.getTemplates();
    templates.put(qName, new FileTemplateBase() {
      @Override
      public @NotNull String getName() {
        return simpleName;
      }

      @Override
      public void setName(@NotNull String name) {
        throw new AbstractMethodError();
      }

      @Override
      public boolean isDefault() {
        return true;
      }

      @Override
      public @NotNull String getDescription() {
        throw new AbstractMethodError();
      }

      @Override
      public @NotNull String getExtension() {
        return qName.substring(simpleName.length());
      }

      @Override
      public void setExtension(@NotNull String extension) {
        throw new AbstractMethodError();
      }

      @Override
      protected @NotNull String getDefaultText() {
        return text;
      }
    });
    Disposer.register(parentDisposable, () -> templates.put(qName, oldTemplate));
  }

  public static final class State {
    @OptionTag("RECENT_TEMPLATES")
    public final List<String> recentTemplates = new ArrayList<>();
    public String SCHEME = FileTemplatesScheme.DEFAULT.getName();

    private void addName(@NotNull @NonNls String name) {
      recentTemplates.remove(name);
      recentTemplates.add(name);
    }

    private @NotNull Collection<String> getRecentNames() {
      int size = recentTemplates.size();
      int resultSize = Math.min(FileTemplateManager.RECENT_TEMPLATES_SIZE, size);
      return recentTemplates.subList(size - resultSize, size);
    }

    private void validateNames(List<String> validNames) {
      recentTemplates.retainAll(validNames);
    }
  }
}
