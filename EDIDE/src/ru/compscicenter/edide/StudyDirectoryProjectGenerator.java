package ru.compscicenter.edide;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParser;
import com.intellij.facet.ui.ValidationResult;
import com.intellij.lang.javascript.boilerplate.GithubDownloadUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Log;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.platform.DirectoryProjectGenerator;
import com.intellij.platform.templates.github.GeneratorException;
import com.intellij.platform.templates.github.ZipUtil;
import com.intellij.util.PathUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import ru.compscicenter.edide.model.Course;
import ru.compscicenter.edide.model.Lesson;

import java.io.File;
import java.io.IOException;
import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 * User: lia
 */
class StudyDirectoryProjectGenerator implements DirectoryProjectGenerator {
  private static final Logger LOG = Logger.getInstance(StudyDirectoryProjectGenerator.class.getName());
  private static File myBaseCouseFile;
  private File myDefaultCoursesBaseDir;
  private static Map<String, File> myCourseFiles =  new HashMap<String, File>();

  @Nls
  @NotNull
  @Override
  public String getName() {
    return "Study project";
  }

  public static File getResourcesRoot() {
    @NonNls String jarPath = PathUtil.getJarPathForClass(StudyDirectoryProjectGenerator.class);
    if (jarPath.endsWith(".jar")) {
      final File jarFile = new File(jarPath);
      return jarFile.getParentFile().getParentFile();
    }

    return new File(jarPath);
  }

  public static String getMyBaseCouseFile() {
    return myBaseCouseFile.getName();
  }

  public static void setMyBaseCouseFile(String mymyBaseCouseFile) {
    myBaseCouseFile =myCourseFiles.get(mymyBaseCouseFile);
  }

  @Nullable
  @Override
  public Object showGenerationSettings(VirtualFile baseDir) throws ProcessCanceledException {
    return null;
  }

  //should be invoked in invokeLater method
  void createFile(@NotNull final String name, @NotNull final VirtualFile directory) throws IOException {
    final File root = getResourcesRoot();
    String systemIndependentName = FileUtil.toSystemIndependentName(name);
    final int index = systemIndependentName.lastIndexOf("/");
    if (index > 0) {
      systemIndependentName = systemIndependentName.substring(index + 1);
    }
    FileUtil.copy(new File(root, name), new File(directory.getPath(), systemIndependentName));
  }

  public static ArrayList<String> getCourseFiles() {
    ArrayList<String> fileName = new ArrayList<String>();
    for (String key : myCourseFiles.keySet()) {
     fileName.add(key);
    }
    return fileName;
  }

  @Override
  public void generateProject(@NotNull final Project project, @NotNull final VirtualFile baseDir,
                              @Nullable Object settings, @NotNull Module module) {
  //getting available courses from github
    File outputFile = new File(PathManager.getLibPath() + "/courses.zip");
    try {
      //GithubDownloadUtil.downloadContentToFileWithProgressSynchronously(project, "https://github.com/medvector/initial-python-course/archive/master.zip",
      //                                                                  "downloading courses", outputFile, "medvector", "initial-python-course" , true );
      GithubDownloadUtil.downloadAtomically(null, "https://github.com/medvector/initial-python-course/archive/master.zip",
                                                                         outputFile, "medvector", "initial-python-course");
      myDefaultCoursesBaseDir = new File(PathManager.getLibPath() + "/courses");
      //ZipUtil.unzipWithProgressSynchronously(project, "unzipping", outputFile, myDefaultCoursesBaseDir, true);
      ZipUtil.unzip(null, myDefaultCoursesBaseDir, outputFile, null, null, true);
      File[] files = myDefaultCoursesBaseDir.listFiles();
      for (File f:files) {
        if (f.isDirectory()) {
          File[] filesInCourse = f.listFiles();
          for (File courseFile:filesInCourse) {
            if (courseFile.getName().equals("course.json")) {
              String name = getCourseName(courseFile);
              int i = 1;
              if (name!= null) {
                File item = myCourseFiles.get(name);
                while(item!= null && !FileUtil.filesEqual(item, courseFile)) {
                  name = name + "1";
                  i++;
                }
                myBaseCouseFile = courseFile;
                myCourseFiles.put(name, courseFile);
              }
            }
          }
        }
      }
      files.toString();
    }
    catch (IOException e) {
      e.printStackTrace();
    }
    //select course window
      StudyNewCourseDialog dlg = new StudyNewCourseDialog(project);
      dlg.show();
    InputStream file = null;
    try {
      file = new FileInputStream(myBaseCouseFile);
    }
    catch (FileNotFoundException e) {
      e.printStackTrace();
    }
    Reader reader = new InputStreamReader(file);
    Gson gson = new GsonBuilder().setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES).create();
    Course course = gson.fromJson(reader, Course.class);
    System.out.println();

    ApplicationManager.getApplication().invokeLater(
      new Runnable() {
        @Override
        public void run() {
          ApplicationManager.getApplication().runWriteAction(new Runnable() {
            @Override
            public void run() {
              try {
                //StudyPlugin.createTaskManager(project.getName());
                //TaskManager taskManager = TaskManager.getInstance(project);
                TaskManager taskManager = TaskManager.getInstance(project);
                int tasksNumber = taskManager.getTasksNum();
                for (int task = 0; task < tasksNumber; task++) {
                  VirtualFile taskDirectory = baseDir.createChildDirectory(this, "task" + (task + 1));
                  for (int file = 0; file < taskManager.getTaskFileNum(task); file++) {
                    final String curFileName = taskManager.getFileName(task, file);
                    createFile(curFileName, taskDirectory);
                  }
                  final VirtualFile ideaDir = baseDir.findChild(".idea");
                  if (ideaDir != null) {
                    createFile(taskManager.getTest(task), ideaDir);
                  }
                  else {
                    LOG.error("Could not find .idea directory");
                  }
                }
              }
              catch (IOException e) {
                Log.print("Problems with creating files");
                Log.print(e.toString());
                Log.flush();
              }
              LocalFileSystem.getInstance().refresh(false);
            }
          });
        }
      }
    );
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
