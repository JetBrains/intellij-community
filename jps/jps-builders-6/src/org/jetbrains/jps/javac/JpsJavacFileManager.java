// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.jps.javac;

import org.jetbrains.annotations.NotNull;

import javax.tools.*;
import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

/**
 * @author Eugene Zhuravlev
 * Date: 01-Oct-18
 */
public abstract class JpsJavacFileManager extends ForwardingJavaFileManager<StandardJavaFileManager> implements StandardJavaFileManager {
  protected final Context myContext;
  protected Map<File, Set<File>> myOutputsMap = Collections.emptyMap();

  public JpsJavacFileManager(Context context) {
    super(context.getStandardFileManager());
    myContext = context;
  }

  interface Context {
    boolean isCanceled();

    @NotNull
    StandardJavaFileManager getStandardFileManager();

    void consumeOutputFile(@NotNull OutputFileObject obj);

    void reportMessage(final Diagnostic.Kind kind, String message);
  }

  public final Context getContext() {
    return myContext;
  }

  @NotNull
  protected StandardJavaFileManager getStdManager() {
    return fileManager;
  }

  @Override
  public void close() {
    try {
      super.close();
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
    finally {
      myOutputsMap = Collections.emptyMap();
    }
  }

  public void setOutputDirectories(final Map<File, Set<File>> outputDirToSrcRoots) throws IOException {
    for (File outputDir : outputDirToSrcRoots.keySet()) {
      // this will validate output dirs
      setLocation(StandardLocation.CLASS_OUTPUT, Collections.singleton(outputDir));
    }
    myOutputsMap = outputDirToSrcRoots;
  }
}
