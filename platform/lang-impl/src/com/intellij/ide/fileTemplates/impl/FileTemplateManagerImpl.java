/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
import com.intellij.ide.fileTemplates.FileTemplatesScheme;
import com.intellij.ide.fileTemplates.InternalTemplateBean;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.components.StoragePathMacros;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.fileTypes.ex.FileTypeManagerEx;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.SystemProperties;
import com.intellij.util.text.DateFormatUtil;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.io.File;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.*;

@State(
  name = "FileTemplateManagerImpl",
  storages = @Storage(StoragePathMacros.WORKSPACE_FILE)
)
public class FileTemplateManagerImpl extends FileTemplateManager implements PersistentStateComponent<FileTemplateManagerImpl.State> {
  private static final Logger LOG = Logger.getInstance("#com.intellij.ide.fileTemplates.impl.FileTemplateManagerImpl");

  private final State myState = new State();
  private final FileTypeManagerEx myTypeManager;
  private final Project myProject;

  /** Null for default project. */
  @Nullable
  private final FileTemplatesScheme myProjectScheme;
  private FileTemplatesScheme myScheme = FileTemplatesScheme.DEFAULT;
  private boolean myInitialized;

  private final FTManager myInternalTemplatesManager;
  private final FTManager myDefaultTemplatesManager;
  private final FTManager myPatternsManager;
  private final FTManager myCodeTemplatesManager;
  private final FTManager myJ2eeTemplatesManager;
  private final FTManager[] myAllManagers;
  private final URL myDefaultTemplateDescription;
  private final URL myDefaultIncludeDescription;

  public static FileTemplateManagerImpl getInstanceImpl(Project project) {
    return (FileTemplateManagerImpl)getInstance(project);
  }

  public FileTemplateManagerImpl(@NotNull FileTypeManagerEx typeManager,
                                 FileTemplatesLoader loader,
                                 /*need this to ensure disposal of the service _after_ project manager*/
                                 @SuppressWarnings("UnusedParameters") ProjectManager pm,
                                 final Project project) {
    myTypeManager = typeManager;
    myProject = project;

    myInternalTemplatesManager = loader.getInternalTemplatesManager();
    myDefaultTemplatesManager = loader.getDefaultTemplatesManager();
    myPatternsManager = loader.getPatternsManager();
    myCodeTemplatesManager = loader.getCodeTemplatesManager();
    myJ2eeTemplatesManager = loader.getJ2eeTemplatesManager();
    myAllManagers = new FTManager[] { myInternalTemplatesManager, myDefaultTemplatesManager, myPatternsManager, myCodeTemplatesManager, myJ2eeTemplatesManager };

    myDefaultTemplateDescription = loader.getDefaultTemplateDescription();
    myDefaultIncludeDescription = loader.getDefaultIncludeDescription();

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

    myProjectScheme = project.isDefault() ? null : new FileTemplatesScheme("Project") {
      @NotNull
      @Override
      public String getTemplatesDir() {
        return new File(project.getBasePath(), Project.DIRECTORY_STORE_FOLDER + "/" + TEMPLATES_DIR).getPath();
      }
    };
  }

  @NotNull
  @Override
  public FileTemplatesScheme getCurrentScheme() {
    return myScheme;
  }

  @Override
  public void setCurrentScheme(@NotNull FileTemplatesScheme scheme) {
    for (FTManager child : myAllManagers) {
      child.saveTemplates();
    }
    setScheme(scheme);
  }

  private void setScheme(@NotNull FileTemplatesScheme scheme) {
    myScheme = scheme;
    for (FTManager manager : myAllManagers) {
      manager.setScheme(scheme);
    }
    myInitialized = true;
  }

  @Override
  protected FileTemplateManager checkInitialized() {
    if (!myInitialized) {
      // loadState() not called; init default scheme
      setScheme(myScheme);
    }
    return this;
  }

  @Nullable
  @Override
  public FileTemplatesScheme getProjectScheme() {
    return myProjectScheme;
  }

  @Override
  public FileTemplate[] getTemplates(String category) {
    if (DEFAULT_TEMPLATES_CATEGORY.equals(category)) return ArrayUtil.mergeArrays(getInternalTemplates(), getAllTemplates());
    if (INCLUDES_TEMPLATES_CATEGORY.equals(category)) return getAllPatterns();
    if (CODE_TEMPLATES_CATEGORY.equals(category)) return getAllCodeTemplates();
    if (J2EE_TEMPLATES_CATEGORY.equals(category)) return getAllJ2eeTemplates();
    throw new IllegalArgumentException("Unknown category: " + category);
  }

  @Override
  @NotNull
  public FileTemplate[] getAllTemplates() {
    final Collection<FileTemplateBase> templates = myDefaultTemplatesManager.getAllTemplates(false);
    return templates.toArray(new FileTemplate[templates.size()]);
  }

  @Override
  public FileTemplate getTemplate(@NotNull String templateName) {
    return myDefaultTemplatesManager.findTemplateByName(templateName);
  }

  @Override
  @NotNull
  public FileTemplate addTemplate(@NotNull String name, @NotNull String extension) {
    return myDefaultTemplatesManager.addTemplate(name, extension);
  }

  @Override
  public void removeTemplate(@NotNull FileTemplate template) {
    final String qName = ((FileTemplateBase)template).getQualifiedName();
    for (FTManager manager : myAllManagers) {
      manager.removeTemplate(qName);
    }
  }

  @Override
  @NotNull
  public Properties getDefaultProperties() {
    @NonNls Properties props = new Properties();

    Calendar calendar = Calendar.getInstance();
    Date date = myTestDate == null ? calendar.getTime() : myTestDate;
    SimpleDateFormat sdfMonthNameShort = new SimpleDateFormat("MMM");
    SimpleDateFormat sdfMonthNameFull = new SimpleDateFormat("MMMM");
    SimpleDateFormat sdfYearFull = new SimpleDateFormat("yyyy");

    props.setProperty("DATE", DateFormatUtil.formatDate(date));
    props.setProperty("TIME", DateFormatUtil.formatTime(date));
    props.setProperty("YEAR", sdfYearFull.format(date));
    props.setProperty("MONTH", getCalendarValue(calendar, Calendar.MONTH));
    props.setProperty("MONTH_NAME_SHORT", sdfMonthNameShort.format(date));
    props.setProperty("MONTH_NAME_FULL", sdfMonthNameFull.format(date));
    props.setProperty("DAY", getCalendarValue(calendar, Calendar.DAY_OF_MONTH));
    props.setProperty("HOUR", getCalendarValue(calendar, Calendar.HOUR_OF_DAY));
    props.setProperty("MINUTE", getCalendarValue(calendar, Calendar.MINUTE));

    props.setProperty("USER", SystemProperties.getUserName());
    props.setProperty("PRODUCT_NAME", ApplicationNamesInfo.getInstance().getFullProductName());

    props.setProperty("DS", "$"); // Dollar sign, strongly needed for PHP, JS, etc. See WI-8979

    props.setProperty(PROJECT_NAME_VARIABLE, myProject.getName());

    return props;
  }

  @NotNull
  private static String getCalendarValue(final Calendar calendar, final int field) {
    int val = calendar.get(field);
    if (field == Calendar.MONTH) val++;
    final String result = Integer.toString(val);
    if (result.length() == 1) {
      return "0" + result;
    }
    return result;
  }

  @Override
  @NotNull
  public Collection<String> getRecentNames() {
    validateRecentNames(); // todo: no need to do it lazily
    return myState.getRecentNames(RECENT_TEMPLATES_SIZE);
  }

  @Override
  public void addRecentName(@NotNull @NonNls String name) {
    myState.addName(name);
  }

  private void validateRecentNames() {
    final Collection<FileTemplateBase> allTemplates = myDefaultTemplatesManager.getAllTemplates(false);
    final List<String> allNames = new ArrayList<String>(allTemplates.size());
    for (FileTemplate fileTemplate : allTemplates) {
      allNames.add(fileTemplate.getName());
    }
    myState.validateNames(allNames);
  }

  @Override
  @NotNull
  public FileTemplate[] getInternalTemplates() {
    InternalTemplateBean[] internalTemplateBeans = Extensions.getExtensions(InternalTemplateBean.EP_NAME);
    FileTemplate[] result = new FileTemplate[internalTemplateBeans.length];
    for(int i=0; i<internalTemplateBeans.length; i++) {
      result [i] = getInternalTemplate(internalTemplateBeans [i].name);
    }
    return result;
  }

  @Override
  public FileTemplate getInternalTemplate(@NotNull @NonNls String templateName) {
    FileTemplateBase template = (FileTemplateBase)findInternalTemplate(templateName);

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

  @Override
  public FileTemplate findInternalTemplate(@NotNull @NonNls String templateName) {
    LOG.assertTrue(myInternalTemplatesManager != null);
    FileTemplateBase template = myInternalTemplatesManager.findTemplateByName(templateName);

    if (template == null) {
      // todo: review the hack and try to get rid of this weird logic completely
      template = myDefaultTemplatesManager.findTemplateByName(templateName);
    }
    return template;
  }

  @NotNull
  private static String normalizeText(@NotNull String text) {
    text = StringUtil.convertLineSeparators(text);
    text = StringUtil.replace(text, "$NAME$", "${NAME}");
    text = StringUtil.replace(text, "$PACKAGE_NAME$", "${PACKAGE_NAME}");
    text = StringUtil.replace(text, "$DATE$", "${DATE}");
    text = StringUtil.replace(text, "$TIME$", "${TIME}");
    text = StringUtil.replace(text, "$USER$", "${USER}");
    return text;
  }

  @NonNls
  @NotNull
  private String getTestClassTemplateText(@NotNull @NonNls String templateName) {
    return "package $PACKAGE_NAME$;\npublic " + internalTemplateToSubject(templateName) + " $NAME$ { }";
  }

  @Override
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

  @Override
  @NotNull
  public String localizeInternalTemplateName(@NotNull final FileTemplate template) {
    return template.getName();
  }

  @NonNls
  @NotNull
  private String getDefaultClassTemplateText(@NotNull @NonNls String templateName) {
    return IdeBundle.message("template.default.class.comment", ApplicationNamesInfo.getInstance().getFullProductName()) +
           "package $PACKAGE_NAME$;\n" + "public " + internalTemplateToSubject(templateName) + " $NAME$ { }";
  }

  @Override
  public FileTemplate getCodeTemplate(@NotNull @NonNls String templateName) {
    return getTemplateFromManager(templateName, myCodeTemplatesManager);
  }

  @Override
  public FileTemplate getJ2eeTemplate(@NotNull @NonNls String templateName) {
    return getTemplateFromManager(templateName, myJ2eeTemplatesManager);
  }

  @Nullable
  private static FileTemplate getTemplateFromManager(@NotNull final String templateName, @NotNull final FTManager ftManager) {
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

  @Override
  @NotNull
  public FileTemplate getDefaultTemplate(@NotNull final String name) {
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

  @Override
  @NotNull
  public FileTemplate[] getAllPatterns() {
    final Collection<FileTemplateBase> allTemplates = myPatternsManager.getAllTemplates(false);
    return allTemplates.toArray(new FileTemplate[allTemplates.size()]);
  }

  @Override
  public FileTemplate getPattern(@NotNull String name) {
    return myPatternsManager.findTemplateByName(name);
  }

  @Override
  @NotNull
  public FileTemplate[] getAllCodeTemplates() {
    final Collection<FileTemplateBase> templates = myCodeTemplatesManager.getAllTemplates(false);
    return templates.toArray(new FileTemplate[templates.size()]);
  }

  @Override
  @NotNull
  public FileTemplate[] getAllJ2eeTemplates() {
    final Collection<FileTemplateBase> templates = myJ2eeTemplatesManager.getAllTemplates(false);
    return templates.toArray(new FileTemplate[templates.size()]);
  }

  @Override
  public void setTemplates(@NotNull String templatesCategory, @NotNull Collection<FileTemplate> templates) {
    for (FTManager manager : myAllManagers) {
      if (templatesCategory.equals(manager.getName())) {
        manager.updateTemplates(templates);
        manager.saveTemplates();
        break;
      }
    }
  }

  @Override
  public void saveAllTemplates() {
    for (FTManager manager : myAllManagers) {
      manager.saveTemplates();
    }
  }

  public URL getDefaultTemplateDescription() {
    return myDefaultTemplateDescription;
  }

  public URL getDefaultIncludeDescription() {
    return myDefaultIncludeDescription;
  }

  private Date myTestDate;

  @TestOnly
  public void setTestDate(Date testDate) {
    myTestDate = testDate;
  }

  @Nullable
  @Override
  public State getState() {
    myState.SCHEME = myScheme.getName();
    return myState;
  }

  @Override
  public void loadState(State state) {
    ExportableFileTemplateSettings.getInstance(myProject);
    XmlSerializerUtil.copyBean(state, myState);
    FileTemplatesScheme scheme = myProjectScheme != null && myProjectScheme.getName().equals(state.SCHEME) ? myProjectScheme : FileTemplatesScheme.DEFAULT;
    setScheme(scheme);
  }

  FTManager[] getAllManagers() {
    return myAllManagers;
  }

  public static class State {
    public List<String> RECENT_TEMPLATES = new ArrayList<String>();
    public String SCHEME = FileTemplatesScheme.DEFAULT.getName();

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
  }
}
