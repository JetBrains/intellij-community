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
package com.intellij.codeInspection;

import com.intellij.analysis.AnalysisScope;
import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInspection.ex.*;
import com.intellij.conversion.ConversionListener;
import com.intellij.conversion.ConversionService;
import com.intellij.ide.impl.PatchProjectUtil;
import com.intellij.ide.impl.ProjectUtil;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ex.ApplicationEx;
import com.intellij.openapi.application.ex.ApplicationInfoEx;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.util.ProgressIndicatorBase;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.profile.codeInspection.InspectionProjectProfileManager;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.GlobalSearchScopesCore;
import com.intellij.psi.search.scope.packageSet.NamedScope;
import com.intellij.psi.search.scope.packageSet.NamedScopesHolder;
import com.thoughtworks.xstream.io.xml.PrettyPrintWriter;
import org.jdom.JDOMException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;

/**
 * @author max
 */
@SuppressWarnings("UseOfSystemOutOrSystemErr")
public class InspectionApplication {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInspection.InspectionApplication");

  public InspectionToolCmdlineOptionHelpProvider myHelpProvider;
  public String myProjectPath;
  public String myOutPath;
  public String mySourceDirectory;
  public String myStubProfile;
  public String myProfileName;
  public String myProfilePath;
  public boolean myRunWithEditorSettings;
  public boolean myRunGlobalToolsOnly;
  private Project myProject;
  private int myVerboseLevel;
  public String myOutputFormat;

  public boolean myErrorCodeRequired = true;

  @NonNls public static final String DESCRIPTIONS = ".descriptions";
  @NonNls public static final String PROFILE = "profile";
  @NonNls public static final String INSPECTIONS_NODE = "inspections";
  @NonNls public static final String XML_EXTENSION = ".xml";

  public void startup() {
    if (myProjectPath == null) {
      logError("Project to inspect is not defined");
      printHelp();
    }

    if (myProfileName == null && myProfilePath == null && myStubProfile == null) {
      logError("Profile to inspect with is not defined");
      printHelp();
    }

    final ApplicationEx application = ApplicationManagerEx.getApplicationEx();
    application.runReadAction(() -> {
      try {
        final ApplicationInfoEx appInfo = (ApplicationInfoEx)ApplicationInfo.getInstance();
        logMessage(1, InspectionsBundle.message("inspection.application.starting.up",
                                                appInfo.getFullApplicationName() + " (build " + appInfo.getBuild().asString() + ")"));
        application.doNotSave();
        logMessageLn(1, InspectionsBundle.message("inspection.done"));

        run();
      }
      catch (Exception e) {
        LOG.error(e);
      }
      finally {
        if (myErrorCodeRequired) application.exit(true, true);
      }
    });
  }

  private void printHelp() {
    assert myHelpProvider != null;

    myHelpProvider.printHelpAndExit();
  }

  private void run() {

    File tmpDir = null;
    try {
      myProjectPath = myProjectPath.replace(File.separatorChar, '/');
      VirtualFile vfsProject = LocalFileSystem.getInstance().findFileByPath(myProjectPath);
      if (vfsProject == null) {
        logError(InspectionsBundle.message("inspection.application.file.cannot.be.found", myProjectPath));
        printHelp();
      }

      logMessage(1, InspectionsBundle.message("inspection.application.opening.project"));
      final ConversionService conversionService = ConversionService.getInstance();
      if (conversionService.convertSilently(myProjectPath, createConversionListener()).openingIsCanceled()) {
        gracefulExit();
        return;
      }
      myProject = ProjectUtil.openOrImport(myProjectPath, null, false);

      if (myProject == null) {
        logError("Unable to open project");
        gracefulExit();
        return;
      }

      ApplicationManager.getApplication().runWriteAction(() -> VirtualFileManager.getInstance().refreshWithoutFileWatcher(false));

      PatchProjectUtil.patchProject(myProject);

      logMessageLn(1, InspectionsBundle.message("inspection.done"));
      logMessage(1, InspectionsBundle.message("inspection.application.initializing.project"));

      InspectionProfileImpl inspectionProfile = loadInspectionProfile();
      if (inspectionProfile == null) return;

      final InspectionManagerEx im = (InspectionManagerEx)InspectionManager.getInstance(myProject);

      im.createNewGlobalContext(true).setExternalProfile(inspectionProfile);
      im.setProfile(inspectionProfile.getName());

      final AnalysisScope scope;
      if (mySourceDirectory == null) {
        final String scopeName = System.getProperty("idea.analyze.scope");
        final NamedScope namedScope = scopeName != null ? NamedScopesHolder.getScope(myProject, scopeName) : null;
        scope = namedScope != null ? new AnalysisScope(GlobalSearchScopesCore.filterScope(myProject, namedScope), myProject)
                                   : new AnalysisScope(myProject);
      }
      else {
        mySourceDirectory = mySourceDirectory.replace(File.separatorChar, '/');

        VirtualFile vfsDir = LocalFileSystem.getInstance().findFileByPath(mySourceDirectory);
        if (vfsDir == null) {
          logError(InspectionsBundle.message("inspection.application.directory.cannot.be.found", mySourceDirectory));
          printHelp();
        }

        PsiDirectory psiDirectory = PsiManager.getInstance(myProject).findDirectory(vfsDir);
        scope = new AnalysisScope(psiDirectory);
      }

      logMessageLn(1, InspectionsBundle.message("inspection.done"));

      if (!myRunWithEditorSettings) {
        logMessageLn(1, InspectionsBundle.message("inspection.application.chosen.profile.log.message", inspectionProfile.getName()));
      }

      InspectionsReportConverter reportConverter = getReportConverter(myOutputFormat);
      if (reportConverter == null && myOutputFormat != null && myOutputFormat.endsWith(".xsl")) {
        // xslt converter
        reportConverter = new XSLTReportConverter(myOutputFormat);
      }

      final String resultsDataPath;
      if ((reportConverter == null || !reportConverter.useTmpDirForRawData()) // use default xml converter(if null( or don't store default xml report in tmp dir
          && myOutPath != null) {  // and don't use STDOUT stream
        resultsDataPath = myOutPath;
      }
      else {
        try {
          tmpDir = FileUtil.createTempDirectory("inspections", "data");
          resultsDataPath = tmpDir.getPath();
        }
        catch (IOException e) {
          LOG.error(e);
          System.err.println("Cannot create tmp directory.");
          System.exit(1);
          return;
        }
      }

      final List<File> inspectionsResults = new ArrayList<>();
      ProgressManager.getInstance().runProcess(() -> {
        if (!GlobalInspectionContextUtil.canRunInspections(myProject, false)) {
          gracefulExit();
          return;
        }
        im.createNewGlobalContext(true).launchInspectionsOffline(scope, resultsDataPath, myRunGlobalToolsOnly, inspectionsResults);
        logMessageLn(1, "\n" + InspectionsBundle.message("inspection.capitalized.done") + "\n");
        if (!myErrorCodeRequired) {
          closeProject();
        }
      }, new ProgressIndicatorBase() {
        private String lastPrefix = "";
        private int myLastPercent = -1;

        @Override
        public void setText(String text) {
          if (myVerboseLevel == 0) return;

          if (myVerboseLevel == 1) {
            String prefix = getPrefix(text);
            if (prefix == null) return;
            if (prefix.equals(lastPrefix)) {
              logMessage(1, ".");
              return;
            }
            lastPrefix = prefix;
            logMessageLn(1, "");
            logMessageLn(1, prefix);
            return;
          }

          if (myVerboseLevel == 3) {
            if (!isIndeterminate() && getFraction() > 0) {
              final int percent = (int)(getFraction() * 100);
              if (myLastPercent == percent) return;
              String prefix = getPrefix(text);
              myLastPercent = percent;
              String msg = (prefix != null ? prefix : InspectionsBundle.message("inspection.display.name")) + " " + percent + "%";
              logMessageLn(2, msg);
            }
            return;
          }

          logMessageLn(2, text);
        }
      });
      final String descriptionsFile = resultsDataPath + File.separatorChar + DESCRIPTIONS + XML_EXTENSION;
      describeInspections(descriptionsFile,
                          myRunWithEditorSettings ? null : inspectionProfile.getName(),
                          inspectionProfile);
      inspectionsResults.add(new File(descriptionsFile));
      // convert report
      if (reportConverter != null) {
        try {
          reportConverter.convert(resultsDataPath, myOutPath, im.createNewGlobalContext(true).getTools(), inspectionsResults);
        }
        catch (InspectionsReportConverter.ConversionException e) {
          logError("\n" + e.getMessage());
          printHelp();
        }
      }
    }
    catch (IOException e) {
      LOG.error(e);
      logError(e.getMessage());
      printHelp();
    }
    catch (Throwable e) {
      LOG.error(e);
      logError(e.getMessage());
      gracefulExit();
    }
    finally {
      // delete tmp dir
      if (tmpDir != null) {
        FileUtil.delete(tmpDir);
      }
    }
  }

  private void gracefulExit() {
    if (myErrorCodeRequired) {
      System.exit(1);
    }
    else {
      closeProject();
      throw new RuntimeException("Failed to proceed");
    }
  }

  private void closeProject() {
    if (myProject != null && !myProject.isDisposed()) {
      ProjectUtil.closeAndDispose(myProject);
      myProject = null;
    }
  }

  @Nullable
  private InspectionProfileImpl loadInspectionProfile() throws IOException, JDOMException {
    InspectionProfileImpl inspectionProfile = null;

    //fetch profile by name from project file (project profiles can be disabled)
    if (myProfileName != null) {
      inspectionProfile = loadProfileByName(myProfileName);
      if (inspectionProfile == null) {
        logError("Profile with configured name (" + myProfileName + ") was not found (neither in project nor in config directory)");
        gracefulExit();
        return null;
      }
      return inspectionProfile;
    }

    if (myProfilePath != null) {
      inspectionProfile = loadProfileByPath(myProfilePath);
      if (inspectionProfile == null) {
        logError("Failed to load profile from \'" + myProfilePath + "\'");
        gracefulExit();
        return null;
      }
      return inspectionProfile;
    }

    if (myStubProfile != null) {
      if (!myRunWithEditorSettings) {
        inspectionProfile = loadProfileByName(myStubProfile);
        if (inspectionProfile != null) return inspectionProfile;

        inspectionProfile = loadProfileByPath(myStubProfile);
        if (inspectionProfile != null) return inspectionProfile;
      }

      inspectionProfile = InspectionProjectProfileManager.getInstance(myProject).getCurrentProfile();
      logError("Using default project profile");
    }
    return inspectionProfile;
  }

  @Nullable
  private InspectionProfileImpl loadProfileByPath(final String profilePath) throws IOException, JDOMException {
    InspectionProfileImpl inspectionProfile = ApplicationInspectionProfileManager.getInstanceImpl().loadProfile(profilePath);
    if (inspectionProfile != null) {
      logMessageLn(1, "Loaded profile \'" + inspectionProfile.getName() + "\' from file \'" + profilePath + "\'");
    }
    return inspectionProfile;
  }

  @Nullable
  private InspectionProfileImpl loadProfileByName(final String profileName) {
    InspectionProfileImpl inspectionProfile = InspectionProjectProfileManager.getInstance(myProject).getProfile(profileName, false);
    if (inspectionProfile != null) {
      logMessageLn(1, "Loaded shared project profile \'" + profileName + "\'");
    }
    else {
      //check if ide profile is used for project
      for (InspectionProfileImpl profile : InspectionProjectProfileManager.getInstance(myProject).getProfiles()) {
        if (Comparing.strEqual(profile.getName(), profileName)) {
          inspectionProfile = profile;
          logMessageLn(1, "Loaded local profile \'" + profileName + "\'");
          break;
        }
      }
    }

    return inspectionProfile;
  }


  @Nullable
  private static InspectionsReportConverter getReportConverter(@Nullable final String outputFormat) {
    for (InspectionsReportConverter converter : InspectionsReportConverter.EP_NAME.getExtensions()) {
      if (converter.getFormatName().equals(outputFormat)) {
        return converter;
      }
    }
    return null;
  }

  private ConversionListener createConversionListener() {
    return new ConversionListener() {
      @Override
      public void conversionNeeded() {
        logMessageLn(1, InspectionsBundle.message("inspection.application.project.has.older.format.and.will.be.converted"));
      }

      @Override
      public void successfullyConverted(final File backupDir) {
        logMessageLn(1, InspectionsBundle.message(
          "inspection.application.project.was.succesfully.converted.old.project.files.were.saved.to.0",
                                                  backupDir.getAbsolutePath()));
      }

      @Override
      public void error(final String message) {
        logError(InspectionsBundle.message("inspection.application.cannot.convert.project.0", message));
      }

      @Override
      public void cannotWriteToFiles(final List<File> readonlyFiles) {
        StringBuilder files = new StringBuilder();
        for (File file : readonlyFiles) {
          files.append(file.getAbsolutePath()).append("; ");
        }
        logError(InspectionsBundle.message("inspection.application.cannot.convert.the.project.the.following.files.are.read.only.0", files.toString()));
      }
    };
  }

  @Nullable
  private static String getPrefix(final String text) {
    //noinspection HardCodedStringLiteral
    int idx = text.indexOf(" in ");
    if (idx == -1) {
      //noinspection HardCodedStringLiteral
      idx = text.indexOf(" of ");
    }

    return idx == -1 ? null : text.substring(0, idx);
  }

  public void setVerboseLevel(int verboseLevel) {
    myVerboseLevel = verboseLevel;
  }

  private void logMessage(int minVerboseLevel, String message) {
    if (myVerboseLevel >= minVerboseLevel) {
      System.out.print(message);
    }
  }

  private static void logError(String message) {
    System.err.println(message);
  }

  private void logMessageLn(int minVerboseLevel, String message) {
    if (myVerboseLevel >= minVerboseLevel) {
      System.out.println(message);
    }
  }

  private static void describeInspections(@NonNls String myOutputPath, final String name, final InspectionProfile profile) throws IOException {
    final InspectionToolWrapper[] toolWrappers = profile.getInspectionTools(null);
    final Map<String, Set<InspectionToolWrapper>> map = new HashMap<>();
    for (InspectionToolWrapper toolWrapper : toolWrappers) {
      final String groupName = toolWrapper.getGroupDisplayName();
      Set<InspectionToolWrapper> groupInspections = map.computeIfAbsent(groupName, __ -> new HashSet<>());
      groupInspections.add(toolWrapper);
    }

    try (FileWriter fw = new FileWriter(myOutputPath)) {
      @NonNls final PrettyPrintWriter xmlWriter = new PrettyPrintWriter(fw);
      xmlWriter.startNode(INSPECTIONS_NODE);
      if (name != null) {
        xmlWriter.addAttribute(PROFILE, name);
      }
      for (Map.Entry<String, Set<InspectionToolWrapper>> entry : map.entrySet()) {
        xmlWriter.startNode("group");
        String groupName = entry.getKey();
        xmlWriter.addAttribute("name", groupName);
        final Set<InspectionToolWrapper> entries = entry.getValue();
        for (InspectionToolWrapper toolWrapper : entries) {
          xmlWriter.startNode("inspection");
          final String shortName = toolWrapper.getShortName();
          xmlWriter.addAttribute("shortName", shortName);
          xmlWriter.addAttribute("displayName", toolWrapper.getDisplayName());
          final boolean toolEnabled = profile.isToolEnabled(HighlightDisplayKey.find(shortName));
          xmlWriter.addAttribute("enabled", Boolean.toString(toolEnabled));
          final String description = toolWrapper.loadDescription();
          if (description != null) {
            xmlWriter.setValue(description);
          }
          else {
            LOG.error(shortName + " descriptionUrl==" + toolWrapper);
          }
          xmlWriter.endNode();
        }
        xmlWriter.endNode();
      }
      xmlWriter.endNode();
    }
  }
}
