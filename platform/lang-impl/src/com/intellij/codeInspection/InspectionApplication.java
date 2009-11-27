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

package com.intellij.codeInspection;

import com.intellij.analysis.AnalysisScope;
import com.intellij.codeInspection.ex.GlobalInspectionContextImpl;
import com.intellij.codeInspection.ex.InspectionManagerEx;
import com.intellij.codeInspection.ex.InspectionProfileImpl;
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
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.profile.Profile;
import com.intellij.profile.codeInspection.InspectionProfileManager;
import com.intellij.profile.codeInspection.InspectionProjectProfileManager;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiManager;
import com.intellij.util.StringBuilderSpinAllocator;
import com.thoughtworks.xstream.io.xml.PrettyPrintWriter;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;

/**
 * @author max
 */
@SuppressWarnings({"UseOfSystemOutOrSystemErr"})
public class InspectionApplication {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInspection.InspectionApplication");

  public String myProjectPath = null;
  public String myOutPath = null;
  public String mySourceDirectory = null;
  public String myProfileName = null;
  public boolean myRunWithEditorSettings = false;
  public boolean myRunGlobalToolsOnly = false;
  private Project myProject;
  private int myVerboseLevel = 0;

  public boolean myErrorCodeRequired = true;
  
  @NonNls public static final String DESCRIPTIONS = ".descriptions";
  @NonNls public static final String PROFILE = "profile";
  @NonNls public static final String INSPECTIONS_NODE = "inspections";
  @NonNls public static final String XML_EXTENSION = ".xml";

  public void startup() {
    if (myProjectPath == null || myOutPath == null || myProfileName == null) {
      logError(myProjectPath + myOutPath + myProfileName);
      InspectionMain.printHelp();
    }

    ApplicationManager.getApplication().runReadAction(new Runnable() {
      public void run() {
        ApplicationEx application = ApplicationManagerEx.getApplicationEx();
        try {
          final ApplicationInfoEx applicationInfo = (ApplicationInfoEx)ApplicationInfo.getInstance();
          logMessage(1, InspectionsBundle.message("inspection.application.starting.up", applicationInfo.getFullApplicationName()));
          application.doNotSave();          
          logMessageLn(1, InspectionsBundle.message("inspection.done"));

          InspectionApplication.this.run();
        }
        catch (Exception e) {
          LOG.error(e);
        }
        finally {
          if (myErrorCodeRequired) application.exit(true);
        }
      }
    });
  }

  public void run() {
    try {
      myProjectPath = myProjectPath.replace(File.separatorChar, '/');
      VirtualFile vfsProject = LocalFileSystem.getInstance().findFileByPath(myProjectPath);
      if (vfsProject == null) {
        logError(InspectionsBundle.message("inspection.application.file.cannot.be.found", myProjectPath));
        InspectionMain.printHelp();
      }

      logMessage(1, InspectionsBundle.message("inspection.application.opening.project"));
      if (ConversionService.getInstance().convertSilently(myProjectPath, createConversionListener()).openingIsCanceled()) {
        if (myErrorCodeRequired) System.exit(1);
        return;
      }
      myProject = ProjectUtil.openOrImport(myProjectPath, null, false);

      if (myProject == null) {
        logError("Unable to open project");
        if (myErrorCodeRequired) System.exit(1);
        return;
      }

      ApplicationManager.getApplication().runWriteAction(new Runnable() {
          public void run(){
              VirtualFileManager.getInstance().refreshWithoutFileWatcher(false);
          }
      });

      //fetch profile by name from project file (project profiles can be disabled)
      Profile inspectionProfile = InspectionProjectProfileManager.getInstance(myProject).getProfile(myProfileName, false);

      //check if ide profile is used for project
      if (inspectionProfile == null) {
        final Collection<Profile> profiles = InspectionProjectProfileManager.getInstance(myProject).getProfiles();
        for (Profile profile : profiles) {
          if (Comparing.strEqual(profile.getName(), myProfileName)) {
            inspectionProfile = profile;
            break;
          }
        }
      }

      //otherwise look for profile file or use default
      if (inspectionProfile == null) {
        inspectionProfile = InspectionProfileManager.getInstance().loadProfile(myProfileName);
      }

      if (inspectionProfile == null) {
        inspectionProfile = InspectionProjectProfileManager.getInstance(myProject).getInspectionProfile();
      }

      PatchProjectUtil.patchProject(myProject);

      logMessageLn(1, InspectionsBundle.message("inspection.done"));
      logMessage(1, InspectionsBundle.message("inspection.application.initializing.project"));

      final InspectionManagerEx im = (InspectionManagerEx)InspectionManager.getInstance(myProject);
      final AnalysisScope scope;

      final GlobalInspectionContextImpl inspectionContext = im.createNewGlobalContext(true);
      inspectionContext.setExternalProfile((InspectionProfile)inspectionProfile);
      im.setProfile(inspectionProfile.getName());

      if (mySourceDirectory == null) {
        scope = new AnalysisScope(myProject);
      }
      else {
        mySourceDirectory = mySourceDirectory.replace(File.separatorChar, '/');

        VirtualFile vfsDir = LocalFileSystem.getInstance().findFileByPath(mySourceDirectory);
        if (vfsDir == null) {
          logError(InspectionsBundle.message("inspection.application.directory.cannot.be.found", mySourceDirectory));
          InspectionMain.printHelp();
        }

        PsiDirectory psiDirectory = PsiManager.getInstance(myProject).findDirectory(vfsDir);
        scope = new AnalysisScope(psiDirectory);
      }

      logMessageLn(1, InspectionsBundle.message("inspection.done"));

      if (!myRunWithEditorSettings) {
        logMessageLn(1, InspectionsBundle.message("inspection.application.chosen.profile.log message", inspectionProfile.getName()));
      }

      ProgressManager.getInstance().runProcess(new Runnable() {
        public void run() {
          if (!InspectionManagerEx.canRunInspections(myProject, false)) {
            if (myErrorCodeRequired) System.exit(1);
            return;
          }
          inspectionContext.launchInspectionsOffline(scope, myOutPath, myRunWithEditorSettings, myRunGlobalToolsOnly, im);
          logMessageLn(1, "\n" +
                          InspectionsBundle.message("inspection.capitalized.done") +
                          "\n");
        }
      }, new ProgressIndicatorBase() {
        private String lastPrefix = "";
        private int myLastPercent = -1;

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
              final StringBuilder buf = StringBuilderSpinAllocator.alloc();
              try {
                final int percent = (int)(getFraction() * 100);
                if (myLastPercent == percent) return;
                myLastPercent = percent;
                buf.append(InspectionsBundle.message("inspection.display.name")).append(" ").append(percent).append("%");
                logMessageLn(2, buf.toString());
              }
              finally {
                StringBuilderSpinAllocator.dispose(buf);
              }
            }
            return;
          }

          logMessageLn(2, text);
        }
      });
      describeInspections(myOutPath + File.separatorChar + DESCRIPTIONS + XML_EXTENSION, !myRunWithEditorSettings ? inspectionProfile.getName() : null);
    }
    catch (IOException e) {
      LOG.error(e);
      logError(e.getMessage());
      InspectionMain.printHelp();
    }
    catch (Throwable e) {
      LOG.error(e);
      logError(e.getMessage());
      if (myErrorCodeRequired) System.exit(1);
    }
  }

  private ConversionListener createConversionListener() {
    return new ConversionListener() {
      public void conversionNeeded() {
        logMessageLn(1, InspectionsBundle.message("inspection.application.project.has.older.format.and.will.be.converted"));
      }

      public void successfullyConverted(final File backupDir) {
        logMessageLn(1, InspectionsBundle.message(
          "inspection.application.project.was.succesfully.converted.old.project.files.were.saved.to.0",
                                                  backupDir.getAbsolutePath()));
      }

      public void error(final String message) {
        logError(InspectionsBundle.message("inspection.application.cannot.convert.project.0", message));
      }

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
    String prefix = null;
    //noinspection HardCodedStringLiteral
    int idx = text.indexOf(" in ");
    if (idx == -1) {
      //noinspection HardCodedStringLiteral
      idx = text.indexOf(" of ");
    }

    if (idx != -1){
      prefix = text.substring(0, idx);
    }
    return prefix;
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

  private static void describeInspections(@NonNls String myOutputPath, final String name) throws IOException {
    final InspectionProfileEntry[] profileEntries = InspectionProfileImpl.getDefaultProfile().getInspectionTools(null);
    final Map<String, Set<InspectionProfileEntry>> map = new HashMap<String, Set<InspectionProfileEntry>>();
    for (InspectionProfileEntry entry : profileEntries) {
      final String groupName = entry.getGroupDisplayName();
      Set<InspectionProfileEntry> groupInspections = map.get(groupName);
      if (groupInspections == null) {
        groupInspections = new HashSet<InspectionProfileEntry>();
        map.put(groupName, groupInspections);
      }
      groupInspections.add(entry);
    }

    FileWriter fw = null;
    try {
      fw = new FileWriter(myOutputPath);
      @NonNls final PrettyPrintWriter xmlWriter = new PrettyPrintWriter(fw);
      xmlWriter.startNode(INSPECTIONS_NODE);
      if (name != null) {
        xmlWriter.addAttribute(PROFILE, name);
      }
      for (String groupName : map.keySet()) {
        xmlWriter.startNode("group");
        xmlWriter.addAttribute("name", groupName);
        final Set<InspectionProfileEntry> entries = map.get(groupName);
        for (InspectionProfileEntry entry : entries) {
          xmlWriter.startNode("inspection");
          xmlWriter.addAttribute("shortName", entry.getShortName());
          xmlWriter.addAttribute("displayName", entry.getDisplayName());
          final String description = entry.loadDescription();
          if (description != null) {
            xmlWriter.setValue(description);
          } else {
            LOG.error(entry.getShortName() + " descriptionUrl==null");
          }
          xmlWriter.endNode();
        }
        xmlWriter.endNode();
      }
      xmlWriter.endNode();
    }
    finally {
      if (fw != null) {
        fw.close();
      }
    }
  }
}
