package com.intellij.codeInspection.ex;

import com.intellij.analysis.AnalysisScope;
import com.intellij.codeInspection.*;
import com.intellij.ide.highlighter.XmlFileType;
import com.intellij.ide.impl.convert.ProjectConversionUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ex.ApplicationEx;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.ModifiableModuleModel;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.util.ProgressIndicatorBase;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ex.ProjectManagerEx;
import com.intellij.openapi.projectRoots.ProjectJdk;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.roots.ex.ProjectRootManagerEx;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.profile.Profile;
import com.intellij.profile.codeInspection.InspectionProfileManager;
import com.intellij.profile.codeInspection.InspectionProjectProfileManager;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.ResourceUtil;
import com.intellij.util.StringBuilderSpinAllocator;
import com.thoughtworks.xstream.io.xml.PrettyPrintWriter;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URL;
import java.util.*;

/**
 * @author max
 */
@SuppressWarnings({"UseOfSystemOutOrSystemErr"})
public class InspectionApplication {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInspection.ex.InspectionApplication");

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

  public void startup() {
    if (myProjectPath == null || myOutPath == null || myProfileName == null) {
      logError(myProjectPath + myOutPath + myProfileName);
      InspectionMain.printHelp();
    }

    ApplicationManager.getApplication().runReadAction(new Runnable() {
      public void run() {
        ApplicationEx application = ApplicationManagerEx.getApplicationEx();
        try {
          logMessage(1, InspectionsBundle.message("inspection.application.starting.up"));
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
      if (!ProjectConversionUtil.convertSilently(myProjectPath, createConversionListener())) {
        System.exit(1);
      }
      myProject = ProjectManagerEx.getInstanceEx().loadAndOpenProject(myProjectPath, false);

      //fetch profile by name from project file (project profiles can be disabled)
      Profile inspectionProfile = InspectionProjectProfileManager.getInstance(myProject).getProfiles().get(myProfileName);

      //otherwise look for profile file or use default
      if (inspectionProfile == null) {
        inspectionProfile = InspectionProfileManager.getInstance().loadProfile(myProfileName);
      }

      if (inspectionProfile == null) {
        logError(InspectionsBundle.message("inspection.application.file.cannot.be.found", myProfileName));
        InspectionMain.printHelp();
      }

      ideaProjectPreparations();

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
          PsiClass psiObjectClass = PsiManager.getInstance(myProject).findClass("java.lang.Object", GlobalSearchScope.allScope(myProject));
          if (psiObjectClass == null) {
            if (ModuleManager.getInstance(myProject).getModules().length == 0) {
              logError(InspectionsBundle.message("inspection.no.modules.error.message"));
              if (myErrorCodeRequired) System.exit(1);
              return;
            }
            logError(InspectionsBundle.message("inspection.no.jdk.error.message"));
            logError(InspectionsBundle.message("offline.inspections.jdk.not.found",
                                               ProjectRootManager.getInstance(myProject).getProjectJdkName()));
            if (myErrorCodeRequired) System.exit(1);
            return;
          }
          final Module[] modules = ModuleManager.getInstance(myProject).getModules();
          for (Module module : modules) {
            final ModuleRootManager rootManager = ModuleRootManager.getInstance(module);
            final ProjectJdk jdk = rootManager.getJdk();
            final OrderEntry[] entries = rootManager.getOrderEntries();
            for (OrderEntry entry : entries) {
              if (entry instanceof JdkOrderEntry) {
                if (jdk == null) {
                  logError(InspectionsBundle.message("offline.inspections.module.jdk.not.found",
                                                     ((JdkOrderEntry)entry).getJdkName(),
                                                     module.getName()));
                  if (myErrorCodeRequired) System.exit(1);
                  return;
                }
              } else if (entry instanceof LibraryOrderEntry) {
                final LibraryOrderEntry libraryOrderEntry = (LibraryOrderEntry)entry;
                final Library library = libraryOrderEntry.getLibrary();
                if (library == null || library.getFiles(OrderRootType.CLASSES).length != library.getUrls(OrderRootType.CLASSES).length) {
                  logError(InspectionsBundle.message("offline.inspections.library.was.not.resolved",
                                                     libraryOrderEntry.getLibraryName(),
                                                     module.getName()));
                }
              }
            }
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
      describeInspections(myOutPath + File.separatorChar + DESCRIPTIONS + XmlFileType.DOT_DEFAULT_EXTENSION, !myRunWithEditorSettings ? inspectionProfile.getName() : null);
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

  private ProjectConversionUtil.ConversionListener createConversionListener() {
    return new ProjectConversionUtil.ConversionListener() {
      public void conversionNeeded() {
        logMessageLn(1, InspectionsBundle.message("inspection.application.project.has.older.format.and.will.be.converted"));
      }

      public void succesfullyConverted(final File backupDir) {
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

  @SuppressWarnings({"HardCodedStringLiteral"})
  private void ideaProjectPreparations() { //ignore test data
    if (myProject.getName().compareTo("idea") == 0) {
      final ModifiableModuleModel modulesModel = ModuleManager.getInstance(myProject).getModifiableModel();
      final Module[] modules = modulesModel.getModules();
      final ModifiableRootModel[] models = new ModifiableRootModel[modules.length];
      int idx = 0;
      for (Module module : modules) {
        final ModifiableRootModel model = ModuleRootManager.getInstance(module).getModifiableModel();
        final ContentEntry[] entries = model.getContentEntries();
        models[idx++] = model;
        for (ContentEntry entry : entries) {
          final VirtualFile virtualFile = entry.getFile();
          if (virtualFile == null) continue;
          if (virtualFile.getName().compareToIgnoreCase("testData") == 0) {
            entry.addExcludeFolder(virtualFile);
            break;
          }
          if (virtualFile.isDirectory()) {
            final VirtualFile[] children = virtualFile.getChildren();
            for (VirtualFile child : children) {
              if (child.getName().compareToIgnoreCase("testData") == 0) {
                entry.addExcludeFolder(child);
              }
            }
          }
        }
      }
      ApplicationManager.getApplication().runWriteAction(new Runnable() {
        public void run() {
          ProjectRootManagerEx.getInstanceEx(myProject).multiCommit(modulesModel, models);
        }
      });
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

  private static void logError(String message) {
    System.err.println(message);
  }

  private void logMessageLn(int minVerboseLevel, String message) {
    if (myVerboseLevel >= minVerboseLevel) {
      System.out.println(message);
    }
  }

  private static void describeInspections(@NonNls String myOutputPath, final String name) throws IOException {
    final InspectionProfileEntry[] profileEntries = InspectionProfileImpl.getDefaultProfile().getInspectionTools();
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
          final URL descriptionUrl = InspectionToolRegistrar.getDescriptionUrl(entry);
          if (descriptionUrl!=null) {
            final String description = ResourceUtil.loadText(descriptionUrl);
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
