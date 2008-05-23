/*
 * Copyright 2000-2005 JetBrains s.r.o.
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
package com.intellij.profile.codeInspection;

import com.intellij.codeInsight.daemon.InspectionProfileConvertor;
import com.intellij.codeInsight.daemon.impl.SeverityRegistrar;
import com.intellij.codeInspection.InspectionsBundle;
import com.intellij.codeInspection.ex.InspectionProfileImpl;
import com.intellij.codeInspection.ex.InspectionToolRegistrar;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ExportableApplicationComponent;
import com.intellij.openapi.components.RoamingType;
import com.intellij.openapi.components.SettingsSavingComponent;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.options.SchemeReaderWriter;
import com.intellij.openapi.options.SchemesManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.profile.DefaultApplicationProfileManager;
import com.intellij.profile.Profile;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * User: anna
 * Date: 29-Nov-2005
 */
public class InspectionProfileManager extends DefaultApplicationProfileManager implements SeverityProvider,
                                                                                          ExportableApplicationComponent, JDOMExternalizable,
                                                                                          SettingsSavingComponent {
  @NonNls private static final String PROFILE_NAME_TAG = "profile_name";

  private InspectionToolRegistrar myRegistrar;
  private final SchemesManager mySchemesManager;
  private AtomicBoolean myProfilesAreInitialized = new AtomicBoolean(false);
  private SeverityRegistrar mySeverityRegistrar;
  private static final String FILE_SPEC = "$ROOT_CONFIG$/inspection";
  private final SchemeReaderWriter<Profile> myReaderWriter;

  public static InspectionProfileManager getInstance() {
    return ApplicationManager.getApplication().getComponent(InspectionProfileManager.class);
  }

  @SuppressWarnings({"UnusedDeclaration"})
  public InspectionProfileManager(InspectionToolRegistrar registrar, EditorColorsManager manager, SchemesManager schemesManager) {
    super(Profile.INSPECTION,
          new Computable<Profile>() {
            public Profile compute() {
              return new InspectionProfileImpl("Default");
            }
          },
          "inspection");
    myRegistrar = registrar;
    mySchemesManager = schemesManager;
    mySeverityRegistrar = new SeverityRegistrar();
    myReaderWriter = new SchemeReaderWriter<Profile>(){
      public Profile readScheme(final Document document, final File file) throws InvalidDataException, IOException, JDOMException {
        InspectionProfileImpl profile = new InspectionProfileImpl(getProfileName(file), file, myRegistrar, InspectionProfileManager.this);
        profile.load();
        return profile;
      }

      public boolean shouldBeSaved(final Profile scheme) {
        return ((InspectionProfileImpl)scheme).wasInitialized();
      }

      public void showWriteErrorMessage(final Exception e, final String schemeName, final String filePath) {
        LOG.error(e);
      }

      public Document writeScheme(final Profile scheme) throws WriteExternalException {
        return ((InspectionProfileImpl)scheme).saveToDocument();
      }

      public void showReadErrorMessage(final Exception e, final String schemeName, final String filePath) {
        LOG.error(e);
      }
    };
  }

  public void initComponent() {
  }

  public void disposeComponent() {
    save();
  }

  public void save() {
    try {
      mySchemesManager.saveSchemes(getProfiles().values(), FILE_SPEC, myReaderWriter, RoamingType.PER_USER);
    }
    catch (WriteExternalException e) {
      //ignore
    }    
  }

  @NotNull
  public File[] getExportFiles() {
    return new File[]{getProfileDirectory()};
  }

  @NotNull
  public String getPresentableName() {
    return InspectionsBundle.message("inspection.profiles.presentable.name");
  }

  public Map<String, Profile> getProfiles() {
    initProfiles();
    return super.getProfiles();
  }

  public void initProfiles() {
    if (!myProfilesAreInitialized.getAndSet(true)) {
      if (ApplicationManager.getApplication().isUnitTestMode()) return;

      final Collection<Profile> profiles =
          mySchemesManager.loadSchemes(FILE_SPEC, myReaderWriter, RoamingType.PER_USER);


      if (profiles.isEmpty()) {
        createDefaultProfile();
      }
      else {
        for (Profile profile : profiles) {
          addProfile(profile);
        }

      }
    }
  }

  public void createDefaultProfile() {
    final InspectionProfileImpl defaultProfile;
    defaultProfile = (InspectionProfileImpl)createProfile();
    defaultProfile.setBaseProfile(InspectionProfileImpl.getDefaultProfile());
    addProfile(defaultProfile);
  }


  public Profile loadProfile(String path) throws IOException, JDOMException {
    final File file = new File(path);
    if (file.exists()){
      InspectionProfileImpl profile = new InspectionProfileImpl(getProfileName(file), file, myRegistrar, this);
      profile.load();
      return profile;
    }
    return getProfile(path);
  }

  private static String getProfileName(File file) throws JDOMException, IOException {
    String name = getRootElementAttribute(file, PROFILE_NAME_TAG);
    if (name != null) return name;
    return FileUtil.getNameWithoutExtension(file);
  }

  private static String getRootElementAttribute(final File file, @NonNls String name) throws JDOMException, IOException {
    try {
      Document doc = JDOMUtil.loadDocument(file);
      Element root = doc.getRootElement();
      String profileName = root.getAttributeValue(name);
      if (profileName != null) return profileName;
    }
    catch (FileNotFoundException e) {
      //ignore
    }
    return null;
  }

 /*
   @NonNls private static final String BASE_PROFILE_ATTR = "base_profile";
   private static String getBaseProfileName(File file) throws JDOMException, IOException {
    return getRootElementAttribute(file, BASE_PROFILE_ATTR);
  }*/

  @NotNull
  public String getComponentName() {
    return "InspectionProfileManager";
  }


  public void updateProfile(Profile profile) {
    super.updateProfile(profile);
    final Project[] projects = ProjectManager.getInstance().getOpenProjects();
    for (Project project : projects) {
      InspectionProjectProfileManager.getInstance(project).initProfileWrapper(profile);
    }
  }

  public SeverityRegistrar getSeverityRegistrar() {
    return mySeverityRegistrar;
  }

  public SeverityRegistrar getOwnSeverityRegistrar() {
    return mySeverityRegistrar;
  }

  public void readExternal(final Element element) throws InvalidDataException {
    mySeverityRegistrar.readExternal(element);
  }

  public void writeExternal(final Element element) throws WriteExternalException {
    mySeverityRegistrar.writeExternal(element);
  }

  public InspectionProfileConvertor getConverter() {
    return new InspectionProfileConvertor(this);
  }
}
