package com.intellij.codeInspection.ex;

import com.intellij.analysis.AnalysisScope;
import com.intellij.codeInspection.InspectionMain;
import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.InspectionProfile;
import com.intellij.codeInspection.InspectionsBundle;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.application.ex.ApplicationEx;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.util.ProgressIndicatorBase;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ex.ProjectManagerEx;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.profile.Profile;
import com.intellij.profile.codeInspection.InspectionProfileManager;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiManager;

import javax.swing.*;
import java.io.File;
import java.io.IOException;

/**
 * @author max
 */
public class InspectionApplication {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInspection.ex.InspectionApplication");

  public String myProjectPath = null;
  public String myOutPath = null;
  public String mySourceDirectory = null;
  public String myProfileName = null;
  public boolean myRunWithEditorSettings = false;
  private Project myProject;
  private int myVerboseLevel = 0;

  public void startup() {
    if (myProjectPath == null || myOutPath == null || myProfileName == null) {
      InspectionMain.printHelp();
    }

    SwingUtilities.invokeLater(new Runnable() {
      public void run() {
        ApplicationEx application = ApplicationManagerEx.getApplicationEx();
        try {
          logMessage(1, InspectionsBundle.message("inspection.application.starting.up"));
          application.doNotSave();
          application.load(PathManager.getOptionsPath());
          logMessageLn(1, InspectionsBundle.message("inspection.done"));

          InspectionApplication.this.run();
        }
        catch (Exception e) {
          LOG.error(e);
        }
        finally {
          application.exit(true);
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

      final Profile inspectionProfile = InspectionProfileManager.getInstance().loadProfile(myProfileName);
      if (inspectionProfile == null) {
        logError(InspectionsBundle.message("inspection.application.file.cannot.be.found", myProfileName));
        InspectionMain.printHelp();
      }


      logMessage(1, InspectionsBundle.message("inspection.application.opening.project"));
      myProject = ProjectManagerEx.getInstanceEx().loadAndOpenProject(myProjectPath);
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

      ProgressManager.getInstance().runProcess(new Runnable() {
        public void run() {
          PsiClass psiObjectClass = PsiManager.getInstance(myProject).findClass("java.lang.Object");
          if (psiObjectClass == null) {
            logError(InspectionsBundle.message("inspection.no.jdk.error.message"));
            System.exit(1);
            return;
          }
          inspectionContext.launchInspectionsOffline(scope, myOutPath, myRunWithEditorSettings, im);
          logMessageLn(1, "\n" +
                          InspectionsBundle.message("inspection.capitalized.done") +
                          "\n");
        }
      }, new ProgressIndicatorBase() {
        private String lastPrefix = "";

        public void setText(String text) {
          if (myVerboseLevel == 0) return;

          if (myVerboseLevel == 1) {
            //noinspection HardCodedStringLiteral
            int idx = text.indexOf(" in ");
            if (idx == -1) {
              //noinspection HardCodedStringLiteral
              idx = text.indexOf(" of ");
            }

            if (idx == -1) return;
            String prefix = text.substring(0, idx);
            if (prefix.equals(lastPrefix)) {
              logMessage(1, ".");
              return;
            }
            lastPrefix = prefix;
            logMessageLn(1, "");
            logMessageLn(1, prefix);
            return;
          }

          logMessageLn(2, text);
        }
      });
    }
    catch (IOException e) {
      LOG.error(e);
      InspectionMain.printHelp();
    }
    catch (Throwable e) {
      LOG.error(e);
      System.exit(1);
    }
  }

  public void setVerboseLevel(int verboseLevel) {
    myVerboseLevel = verboseLevel;
  }

  private void logMessage(int minVerboseLevel, String message) {
    if (myVerboseLevel >= minVerboseLevel) {
      System.out.print(message);
    }
  }

  private void logError(String message) {
    System.err.println(message);
  }

  private void logMessageLn(int minVerboseLevel, String message) {
    if (myVerboseLevel >= minVerboseLevel) {
      System.out.println(message);
    }
  }
}
