package ru.compscicenter.edide;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParser;
import com.intellij.facet.ui.ValidationResult;
import com.intellij.lang.javascript.boilerplate.GithubDownloadUtil;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.platform.DirectoryProjectGenerator;
import com.intellij.platform.templates.github.ZipUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import ru.compscicenter.edide.course.Course;
import ru.compscicenter.edide.ui.StudyNewCourseDialog;

import java.io.File;
import java.io.IOException;
import java.io.*;
import java.util.*;

/**
 * User: lia
 */
public class StudyDirectoryProjectGenerator implements DirectoryProjectGenerator {
  private static final Logger LOG = Logger.getInstance(StudyDirectoryProjectGenerator.class.getName());
  public static final String REPO_URL = "https://github.com/medvector/initial-python-course/archive/master.zip";
  public static final String USER_NAME = "medvector";
  public static final String REPOSITORY_NAME = "initial-python-course";
  private File myDefaultCoursesBaseDir;
  private Map<String, File> myDefaultCourseFiles =  new HashMap<String, File>();
  private String myLocalCourseBaseFileName;
  private String myDefaultSelectedCourseName;

  @Nls
  @NotNull
  @Override
  public String getName() {
    return "Study project";
  }

  public void setMyDefaultCourseFiles(Map<String, File> defaultCourseFiles) {
    myDefaultCourseFiles = defaultCourseFiles;
  }

  public void setMyLocalCourseBaseFileName(String fileName) {
    myLocalCourseBaseFileName = fileName;
  }

  public void setMyDefaultSelectedCourseName(String defaultSelectedCourseName) {
    myDefaultSelectedCourseName = defaultSelectedCourseName;
  }

  public StudyDirectoryProjectGenerator() {
    myDefaultCoursesBaseDir = new File(PathManager.getLibPath() + "/courses");
  }


  public File getBaseCourseFile() {
    if (myLocalCourseBaseFileName != null) {
      File file = new File(myLocalCourseBaseFileName);
      if (file.exists()) {
        return file;
      } else {
        LOG.error("such course file doesn't exist");
      }
    } else {
      if (myDefaultSelectedCourseName != null) {
        File file = myDefaultCourseFiles.get(myDefaultSelectedCourseName);
        if (file!=null && file.exists()) {
          return file;
        }
      } else {
        if (myDefaultCourseFiles.size() > 0) {
          return myDefaultCourseFiles.entrySet().iterator().next().getValue();
        }
      }
    }
    return null;
  }



  @Nullable
  @Override
  public Object showGenerationSettings(VirtualFile baseDir) throws ProcessCanceledException {
    return null;
  }


  @Override
  public void generateProject(@NotNull final Project project, @NotNull final VirtualFile baseDir,
                              @Nullable Object settings, @NotNull Module module) {
    StudyNewCourseDialog dlg = new StudyNewCourseDialog(project, this);
    dlg.show();

    File baseCourseFile = getBaseCourseFile();
    if (baseCourseFile == null) {
      LOG.error("user didn't choose any course files");
      return;
    }
    try {
      Reader reader = new InputStreamReader(new FileInputStream(baseCourseFile));
      Gson gson = new GsonBuilder().setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES).create();
      Course course = gson.fromJson(reader, Course.class);
      course.setParents();
      course.create(project, baseDir, new File(baseCourseFile.getParent()));
      VirtualFileManager.getInstance().refreshWithoutFileWatcher(true);
      StudyTaskManager tm = StudyTaskManager.getInstance(project);
      tm.setCourse(course);
      System.out.println("test");
    }
    catch (FileNotFoundException e) {
      e.printStackTrace();
    }
  }

  public boolean downloadCoursesFromGithub() {
    File outputFile = new File(PathManager.getLibPath() + "/courses.zip");
      try {
        GithubDownloadUtil.downloadAtomically(null, REPO_URL,
                                              outputFile, USER_NAME, REPOSITORY_NAME);
        if (outputFile.exists()) {
          ZipUtil.unzip(null, myDefaultCoursesBaseDir, outputFile, null, null, true);
          if (myDefaultCoursesBaseDir.exists()) {
            return true;
          }
        }
        return false;
      }
      catch (IOException e) {
        e.printStackTrace();
        return false;
      }
  }

  public Map<String, File> getMyDefaultCourseFiles() {
    return myDefaultCourseFiles;
  }

  //TODO: cash course names
  public Map<String, File> getDefaultCourses() {
    Map<String, File> defaultCourseFiles = new HashMap<String, File>();
    if (!myDefaultCoursesBaseDir.exists()) {
      return defaultCourseFiles;
    }
    try {
      File[] files = myDefaultCoursesBaseDir.listFiles();
      if (files != null) {
        for (File f:files) {
          if (f.isDirectory()) {
            File[] filesInCourse = f.listFiles();
            if (filesInCourse != null) {
              for (File courseFile:filesInCourse) {
                if (courseFile.getName().equals("course.json")) {
                  String name = getCourseName(courseFile);
                  int i = 2;
                  if (name!= null) {
                    File item = defaultCourseFiles.get(name);
                    while(item!= null && !FileUtil.filesEqual(item, courseFile)) {
                      if (i>2)  {
                        name = name.substring(0, name.length() - 2);
                      }
                      name = name + Integer.toString(i);
                      i++;
                      item = defaultCourseFiles.get(name);
                    }
                    defaultCourseFiles.put(name, courseFile);
                  }
                }
              }
            }
          }
        }
        return defaultCourseFiles;
      }

    }
    catch (NullPointerException e) {
      LOG.error("default course folder doesn't exist");
    }
    return myDefaultCourseFiles;
  }

  private String getCourseName(File file) {
    InputStream metaIS = null;
    String name = null;
    try {
      metaIS = new FileInputStream(file);
      BufferedReader reader = new BufferedReader(new InputStreamReader(metaIS));
      com.google.gson.stream.JsonReader r = new com.google.gson.stream.JsonReader(reader);
      JsonParser parser = new JsonParser();
      com.google.gson.JsonElement el = parser.parse(r);
      name  = el.getAsJsonObject().get("name").getAsString();
    }
    catch (FileNotFoundException e) {
      e.printStackTrace();
    }
    return name;
  }

  @NotNull
  @Override
  public ValidationResult validate(@NotNull String s) {
    return ValidationResult.OK;
  }

}
