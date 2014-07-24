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
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.platform.DirectoryProjectGenerator;
import com.intellij.platform.templates.github.GeneratorException;
import com.intellij.platform.templates.github.ZipUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import ru.compscicenter.edide.course.Course;
import ru.compscicenter.edide.ui.StudyNewCourseDialog;

import java.io.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * User: lia
 */
public class StudyDirectoryProjectGenerator implements DirectoryProjectGenerator {
  private static final Logger LOG = Logger.getInstance(StudyDirectoryProjectGenerator.class.getName());
  private static final String REPO_URL = "https://github.com/medvector/initial-python-course/archive/master.zip";
  private static final String USER_NAME = "medvector";
  private static final String COURSE_META_FILE = "course.json";
  private static final String COURSE_NAME_ATTRIBUTE = "name";
  private static final Pattern CACHE_PATTERN = Pattern.compile("(name=(.*)) (path=(.*course.json))");
  private static final String REPOSITORY_NAME = "initial-python-course";
  private final File myCoursesDir = new File(PathManager.getLibPath(), "courses");
  private static final String CACHE_NAME = "courseNames.txt";
  private Map<String, File> myCourses = new HashMap<String, File>();
  private File mySelectedCourseFile;
  private Project myProject;

  @Nls
  @NotNull
  @Override
  public String getName() {
    return "Study project";
  }

  public void setCourses(Map<String, File> courses) {
    myCourses = courses;
  }

  /**
   * Finds selected course in courses by name.
   *
   * @param courseName name of selected course
   */
  public void setSelectedCourse(@NotNull String courseName) {
    File courseFile = myCourses.get(courseName);
    if (courseFile == null) {
      LOG.error("invalid course in list");
    }
    mySelectedCourseFile = courseFile;
  }

  /**
   * Adds course to courses specified in params
   *
   * @param courseDir must be directory containing course file
   * @return added course name or null if course is invalid
   */
  @Nullable
  private String addCourse(Map<String, File> courses, File courseDir) {
    if (courseDir.isDirectory()) {
      File[] courseFiles = courseDir.listFiles(new FilenameFilter() {
        @Override
        public boolean accept(File dir, String name) {
          return name.equals(COURSE_META_FILE);
        }
      });
      if (courseFiles.length != 1) {
        LOG.error("User tried to add course with more than one or without course files");
        return null;
      }
      File courseFile = courseFiles[0];
      String courseName = getCourseName(courseFile);
      if (courseName != null) {
        //appending index to courseName if there is another course with such name already
        String newName = courseName;
        int i = 2;
        File item = courses.get(newName);
        while (item != null && !FileUtil.filesEqual(item, courseFile)) {
          newName = courseName + String.valueOf(i);
          i++;
          item = courses.get(newName);
        }
        courses.put(newName, courseFile);
      }
      return courseName;
    }
    return null;
  }


  /**
   * Adds course from zip archive to courses
   *
   * @return added course name or null if course is invalid
   */
  @Nullable
  public String addLocalCourse(String zipFilePath) {
    File file = new File(zipFilePath);
    try {
      String fileName = file.getName();
      String unzippedName = fileName.substring(0, fileName.indexOf("."));
      File courseDir = new File(myCoursesDir, unzippedName);
      ZipUtil.unzip(null, courseDir, file, null, null, true);
      String courseName = addCourse(myCourses, courseDir);
      flushCache();
      return courseName;
    }
    catch (IOException e) {
      LOG.error("Failed to unzip course archive");
      LOG.error(e);
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
    myProject = project;
    mySelectedCourseFile = null;
    StudyNewCourseDialog dlg = new StudyNewCourseDialog(project, this);
    dlg.show();
    if (dlg.getExitCode() == DialogWrapper.CANCEL_EXIT_CODE) {
      LOG.info("User canceled creation study project");
      Messages.showErrorDialog("Empty project will be created.", "Study Project Creation Was Canceled");
      return;
    }
    Reader reader = null;
    try {
      if (mySelectedCourseFile == null) {
        LOG.error("user didn't choose any course files");
        return;
      }
      reader = new InputStreamReader(new FileInputStream(mySelectedCourseFile));
      Gson gson = new GsonBuilder().setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES).create();
      Course course = gson.fromJson(reader, Course.class);
      course.init(false);
      course.create(baseDir, new File(mySelectedCourseFile.getParent()));
      course.setResourcePath(mySelectedCourseFile.getAbsolutePath());
      VirtualFileManager.getInstance().refreshWithoutFileWatcher(true);
      StudyTaskManager.getInstance(project).setCourse(course);
    }
    catch (FileNotFoundException e) {
      e.printStackTrace();
    }
    finally {
      StudyUtils.closeSilently(reader);
    }
  }

  /**
   * Downloads courses from {@link ru.compscicenter.edide.StudyDirectoryProjectGenerator#REPO_URL}
   * and unzips them into {@link ru.compscicenter.edide.StudyDirectoryProjectGenerator#myCoursesDir}
   */

  public void downloadAndUnzip(boolean needProgressBar) {
    File outputFile = new File(PathManager.getLibPath(), "courses.zip");
    try {
      if (!needProgressBar) {
        GithubDownloadUtil.downloadAtomically(null, REPO_URL,
                                              outputFile, USER_NAME, REPOSITORY_NAME);
      }
      else {
        GithubDownloadUtil.downloadContentToFileWithProgressSynchronously(myProject, REPO_URL, "downloading courses", outputFile, USER_NAME,
                                                                          REPOSITORY_NAME, false);
      }
      if (outputFile.exists()) {
        ZipUtil.unzip(null, myCoursesDir, outputFile, null, null, true);
        if (!outputFile.delete()) {
          LOG.error("Failed to delete", outputFile.getName());
        }
        File[] files = myCoursesDir.listFiles();
        if (files != null) {
          for (File file : files) {
            String fileName = file.getName();
            if (StudyUtils.isZip(fileName)) {
              ZipUtil.unzip(null, new File(myCoursesDir, fileName.substring(0, fileName.indexOf("."))), file, null, null, true);
              if (!file.delete()) {
                LOG.error("Failed to delete", fileName);
              }
            }
          }
        }
      }
    }
    catch (IOException e) {
      e.printStackTrace();
    }
    catch (GeneratorException e) {
      e.printStackTrace();
    }
  }

  public Map<String, File> getLoadedCourses() {
    return myCourses;
  }

  /**
   * Parses courses located in {@link ru.compscicenter.edide.StudyDirectoryProjectGenerator#myCoursesDir}
   * to {@link ru.compscicenter.edide.StudyDirectoryProjectGenerator#myCourses}
   *
   * @return map with course names and course files location
   */
  public Map<String, File> loadCourses() {
    Map<String, File> courses = new HashMap<String, File>();
    if (myCoursesDir.exists()) {
      File[] courseDirs = myCoursesDir.listFiles(new FileFilter() {
        @Override
        public boolean accept(File pathname) {
          return pathname.isDirectory();
        }
      });
      for (File courseDir : courseDirs) {
        addCourse(courses, courseDir);
      }
    }
    return courses;
  }

  /**
   * Parses course json meta file and finds course name
   *
   * @return course name or null if course file is invalid
   */
  @Nullable
  private String getCourseName(File courseFile) {
    String courseName = null;
    BufferedReader reader = null;
    try {
      if (courseFile.getName().equals(COURSE_META_FILE)) {
        reader = new BufferedReader(new InputStreamReader(new FileInputStream(courseFile)));
        com.google.gson.stream.JsonReader r = new com.google.gson.stream.JsonReader(reader);
        JsonParser parser = new JsonParser();
        com.google.gson.JsonElement el = parser.parse(r);
        courseName = el.getAsJsonObject().get(COURSE_NAME_ATTRIBUTE).getAsString();
      }
    }
    catch (FileNotFoundException e) {
      e.printStackTrace();
    }
    finally {
      StudyUtils.closeSilently(reader);
    }
    return courseName;
  }

  @NotNull
  @Override
  public ValidationResult validate(@NotNull String s) {
    return ValidationResult.OK;
  }

  /**
   * @return courses from memory or from cash file or parses course directory
   */
  public Map<String, File> getCourses() {
    if (!myCourses.isEmpty()) {
      return myCourses;
    }
    if (myCoursesDir.exists()) {
      File cacheFile = new File(myCoursesDir, CACHE_NAME);
      if (cacheFile.exists()) {
        myCourses = getCoursesFromCache(cacheFile);
        if (!myCourses.isEmpty()) {
          return myCourses;
        }
      }
      myCourses = loadCourses();
      if (!myCourses.isEmpty()) {
        return myCourses;
      }
    }
    downloadAndUnzip(false);
    myCourses = loadCourses();
    flushCache();
    return myCourses;
  }

  /**
   * Writes courses to cash file {@link ru.compscicenter.edide.StudyDirectoryProjectGenerator#CACHE_NAME}
   */
  public void flushCache() {
    File cashFile = new File(myCoursesDir, CACHE_NAME);
    PrintWriter writer = null;
    try {
      writer = new PrintWriter(cashFile);
      for (Map.Entry<String, File> course : myCourses.entrySet()) {
        String line = String.format("name=%s path=%s", course.getKey(), course.getValue());
        writer.println(line);
      }
    }
    catch (FileNotFoundException e) {
      e.printStackTrace();
    }
    finally {
      StudyUtils.closeSilently(writer);
    }
  }

  /**
   * Loads courses from {@link ru.compscicenter.edide.StudyDirectoryProjectGenerator#CACHE_NAME} file
   *
   * @return map of course names and course files
   */
  private Map<String, File> getCoursesFromCache(File cashFile) {
    Map<String, File> coursesFromCash = new HashMap<String, File>();
    try {
      BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(cashFile)));
      String line;

      while ((line = reader.readLine()) != null) {
        Matcher matcher = CACHE_PATTERN.matcher(line);
        if (matcher.matches()) {
          String courseName = matcher.group(2);
          File file = new File(matcher.group(4));
          if (file.exists()) {
            coursesFromCash.put(courseName, file);
          }
        }
      }
    }
    catch (FileNotFoundException e) {
      e.printStackTrace();
    }
    catch (IOException e) {
      e.printStackTrace();
    }
    return coursesFromCash;
  }
}
