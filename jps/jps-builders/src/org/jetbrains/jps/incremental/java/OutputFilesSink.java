package org.jetbrains.jps.incremental.java;

import com.intellij.openapi.util.io.FileUtil;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.incremental.CompileContext;
import org.jetbrains.jps.incremental.messages.BuildMessage;
import org.jetbrains.jps.incremental.messages.CompilerMessage;
import org.jetbrains.jps.incremental.messages.FileGeneratedEvent;
import org.jetbrains.jps.incremental.messages.ProgressMessage;
import org.jetbrains.jps.javac.OutputFileConsumer;
import org.jetbrains.jps.javac.OutputFileObject;

import javax.tools.*;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.*;

/**
* @author Eugene Zhuravlev
*         Date: 2/16/12
*/
class OutputFilesSink implements OutputFileConsumer {
  private final CompileContext myContext;
  private final Set<File> mySuccessfullyCompiled = new THashSet<File>(FileUtil.FILE_HASHING_STRATEGY);
  private final Set<File> myProblematic = new THashSet<File>(FileUtil.FILE_HASHING_STRATEGY);
  private final List<OutputFileObject> myFileObjects = new ArrayList<OutputFileObject>();
  private final Map<String, OutputFileObject> myCompiledClasses = new HashMap<String, OutputFileObject>();

  public OutputFilesSink(CompileContext context) {
    myContext = context;
  }

  public void save(final @NotNull OutputFileObject fileObject) {
    if (fileObject.getKind() == JavaFileObject.Kind.CLASS) {
      final String className = fileObject.getClassName();
      if (className != null) {
        final OutputFileObject.Content content = fileObject.getContent();
        if (content != null) {
          synchronized (myCompiledClasses) {
            myCompiledClasses.put(className, fileObject);
          }
        }
      }
    }

    synchronized (myFileObjects) {
      myFileObjects.add(fileObject);
    }
  }

  @Nullable
  public OutputFileObject.Content lookupClassBytes(String className) {
    synchronized (myCompiledClasses) {
      final OutputFileObject object = myCompiledClasses.get(className);
      return object != null ? object.getContent() : null;
    }
  }

  public List<OutputFileObject> getFileObjects() {
    return Collections.unmodifiableList(myFileObjects);
  }

  public void writePendingData() {
    try {
      if (!myFileObjects.isEmpty()) {
        final FileGeneratedEvent event = new FileGeneratedEvent();
        try {
          for (OutputFileObject fileObject : myFileObjects) {
            try {
              writeToDisk(fileObject);
              final File rootFile = fileObject.getOutputRoot();
              if (rootFile != null) {
                event.add(rootFile.getPath(), fileObject.getRelativePath());
              }
            }
            catch (IOException e) {
              myContext.processMessage(new CompilerMessage(JavaBuilder.BUILDER_NAME, BuildMessage.Kind.ERROR, e.getMessage()));
            }
          }
        }
        finally {
          myContext.processMessage(event);
        }
      }
    }
    finally {
      myFileObjects.clear();
      myCompiledClasses.clear();
    }
  }

  public Set<File> getSuccessfullyCompiled() {
    return Collections.unmodifiableSet(mySuccessfullyCompiled);
  }

  private void writeToDisk(@NotNull OutputFileObject fileObject) throws IOException {
    final File file = fileObject.getFile();
    final OutputFileObject.Content content = fileObject.getContent();
    if (content == null) {
      throw new IOException("Missing content for file " + file);
    }

    try {
      _writeToFile(file, content);
    }
    catch (IOException e) {
      // assuming the reason is non-existing parent
      final File parentFile = file.getParentFile();
      if (parentFile == null) {
        throw e;
      }
      if (!parentFile.mkdirs()) {
        throw e;
      }
      // second attempt
      _writeToFile(file, content);
    }
    
    final File source = fileObject.getSourceFile();
    if (!fileObject.isTemp() && source != null && !myProblematic.contains(source)) {
      mySuccessfullyCompiled.add(source);
      final String className = fileObject.getClassName();
      if (className != null) {
        myContext.processMessage(new ProgressMessage("Compiled " + className));
      }
    }
  }

  private static void _writeToFile(final File file, OutputFileObject.Content content) throws IOException {
    final OutputStream stream = new FileOutputStream(file);
    try {
      stream.write(content.getBuffer(), content.getOffset(), content.getLength());
    }
    finally {
      stream.close();
    }
  }

  public void markError(OutputFileObject outputClassFile) {
    final File source = outputClassFile.getSourceFile();
    if (source != null) {
      myProblematic.add(source);
    }
  }
}
