
package com.intellij.ide.macro;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NonNls;

import java.io.File;

public abstract class Macro {
  public static final class ExecutionCancelledException extends Exception {
  }

  protected String myCachedPreview;

  @NonNls public abstract String getName();
  public abstract String getDescription();
  public abstract String expand(DataContext dataContext) throws ExecutionCancelledException;

  public void cachePreview(DataContext dataContext) {
    try{
      myCachedPreview = expand(dataContext);
    }
    catch(ExecutionCancelledException e){
      myCachedPreview = "";
    }
  }

  public final String preview() {
    return myCachedPreview;
  }

  /**
   * @return never null
   */
  static String getPath(VirtualFile file) {
    return file.getPath().replace('/', File.separatorChar);
  }

  static File getIOFile(VirtualFile file) {
    return new File(getPath(file));
  }

  public static class Silent extends Macro {
    private final Macro myDelegate;
    private final String myValue;

    public Silent(Macro delegate, String value) {
      myDelegate = delegate;
      myValue = value;
    }

    public String expand(DataContext dataContext) throws ExecutionCancelledException {
      return myValue;
    }

    public String getDescription() {
      return myDelegate.getDescription();
    }

    public String getName() {
      return myDelegate.getName();
    }
  }
}
