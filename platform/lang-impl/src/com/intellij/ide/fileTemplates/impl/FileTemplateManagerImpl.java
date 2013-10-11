/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.fileTypes.ex.FileTypeManagerEx;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.SystemProperties;
import com.intellij.util.text.DateFormatUtil;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * @author MYakovlev
 *         Date: Jul 24
 * @author 2002
 *
 * locking policy: if the class needs to take a read or write action, the LOCK lock must be taken
 * _inside_, not outside of the read action
 */
public class FileTemplateManagerImpl extends FileTemplateManager implements JDOMExternalizable {
  private static final Logger LOG = Logger.getInstance("#com.intellij.ide.fileTemplates.impl.FileTemplateManagerImpl");

  private final RecentTemplatesManager myRecentList = new RecentTemplatesManager();

  private static final String ELEMENT_DELETED_TEMPLATES = "deleted_templates";
  private static final String ELEMENT_DELETED_INCLUDES = "deleted_includes";
  private static final String ELEMENT_RECENT_TEMPLATES = "recent_templates";
  private static final String ELEMENT_TEMPLATES = "templates";

  private final FileTypeManagerEx myTypeManager;

  private final ExportableFileTemplateSettings myTemplateSettings;
  private final FTManager myInternalTemplatesManager;
  private final FTManager myDefaultTemplatesManager;
  private final FTManager myPatternsManager;
  private final FTManager myCodeTemplatesManager;
  private final FTManager myJ2eeTemplatesManager;
  private final FTManager[] myAllManagers;
  private final URL myDefaultTemplateDescription;
  private final URL myDefaultIncludeDescription;

  public static FileTemplateManagerImpl getInstanceImpl() {
    return (FileTemplateManagerImpl)ServiceManager.getService(FileTemplateManager.class);
  }

  public FileTemplateManagerImpl(@NotNull FileTypeManagerEx typeManager, ProjectManager pm /*need this to ensure disposal of the service _after_ project manager*/) {
    myTypeManager = typeManager;
    myTemplateSettings = ExportableFileTemplateSettings.getInstance();
    assert myTemplateSettings != null : "Can not instantiate " + ExportableFileTemplateSettings.class.getName();

    myInternalTemplatesManager = myTemplateSettings.getInternalTemplatesManager();
    myDefaultTemplatesManager = myTemplateSettings.getDefaultTemplatesManager();
    myPatternsManager = myTemplateSettings.getPatternsManager();
    myCodeTemplatesManager = myTemplateSettings.getCodeTemplatesManager();
    myJ2eeTemplatesManager = myTemplateSettings.getJ2eeTemplatesManager();
    myAllManagers = myTemplateSettings.getAllManagers();
    myDefaultTemplateDescription = myTemplateSettings.getDefaultTemplateDescription();
    myDefaultIncludeDescription = myTemplateSettings.getDefaultIncludeDescription();

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
  @TestOnly
  public FileTemplate addInternal(@NotNull String name, @NotNull String extension) {
    return myInternalTemplatesManager.addTemplate(name, extension);
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

  @Override
  @NotNull
  public Collection<String> getRecentNames() {
    validateRecentNames(); // todo: no need to do it lazily
    return myRecentList.getRecentNames(RECENT_TEMPLATES_SIZE);
  }

  @Override
  public void addRecentName(@NotNull @NonNls String name) {
    myRecentList.addName(name);
  }

  @Override
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
        boolean reformat =
          Boolean.TRUE.toString().equals(childElement.getAttributeValue(ExportableFileTemplateSettings.ATTRIBUTE_REFORMAT));
        if (!reformat) {
          final String name = childElement.getAttributeValue(ExportableFileTemplateSettings.ATTRIBUTE_NAME);
          templateNamesWithReformatOff.add(name);
        }
      }
    }

    if (!myTemplateSettings.isLoaded()) {
      myTemplateSettings.doLoad(element);
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

  @Override
  public void writeExternal(Element element) throws WriteExternalException {
    for (FTManager child : myAllManagers) {
      child.saveTemplates();
    }
    validateRecentNames();
    final Element recentElement = new Element(ELEMENT_RECENT_TEMPLATES);
    element.addContent(recentElement);
    myRecentList.writeExternal(recentElement);

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

  private void validateRecentNames() {
    final Collection<FileTemplateBase> allTemplates = myDefaultTemplatesManager.getAllTemplates(false);
    final List<String> allNames = new ArrayList<String>(allTemplates.size());
    for (FileTemplate fileTemplate : allTemplates) {
      allNames.add(fileTemplate.getName());
    }
    myRecentList.validateNames(allNames);
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

  @Override
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
  public void setTemplates(@NotNull String templatesCategory, Collection<FileTemplate> templates) {
    for (FTManager manager : myAllManagers) {
      if (templatesCategory.equals(manager.getName())) {
        manager.updateTemplates(templates);
        break;
      }
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

    @Override
    public void readExternal(Element element) throws InvalidDataException {
      DefaultJDOMExternalizer.readExternal(this, element);
    }

    @Override
    public void writeExternal(Element element) throws WriteExternalException {
      DefaultJDOMExternalizer.writeExternal(this, element);
    }

  }

}
