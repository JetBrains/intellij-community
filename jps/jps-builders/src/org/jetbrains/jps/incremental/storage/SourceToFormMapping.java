package org.jetbrains.jps.incremental.storage;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.io.EnumeratorStringDescriptor;

import java.io.File;
import java.io.IOException;

/**
 * @author Eugene Zhuravlev
 *         Date: 12/29/11
 */
public class SourceToFormMapping extends AbstractStateStorage<String, String>{

  public SourceToFormMapping(File storePath) throws IOException {
    super(storePath, new EnumeratorStringDescriptor(), new EnumeratorStringDescriptor());
  }

  public void update(String srcPath, String formPath) throws IOException {
    super.update(FileUtil.toSystemIndependentName(srcPath), FileUtil.toSystemIndependentName(formPath));
  }

  public final void appendData(String srcPath, String formPath) throws IOException {
    update(srcPath, formPath);
  }

  public void remove(String srcPath) throws IOException {
    super.remove(FileUtil.toSystemIndependentName(srcPath));
  }

  public String getState(String srcPath) throws IOException {
    return super.getState(FileUtil.toSystemIndependentName(srcPath));
  }
}
