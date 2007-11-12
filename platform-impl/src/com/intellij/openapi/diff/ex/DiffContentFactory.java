package com.intellij.openapi.diff.ex;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.diff.DiffContent;
import com.intellij.openapi.diff.SimpleDiffRequest;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.Nullable;

public class DiffContentFactory {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.diff.ex.DiffContentFactory");

  private DiffContentFactory() {}

  @Nullable
  public static SimpleDiffRequest compareVirtualFiles(Project project, VirtualFile file1, VirtualFile file2, String title) {
    DiffContent content1 = DiffContent.fromFile(project, file1);
    DiffContent content2 = DiffContent.fromFile(project, file2);
    if (content1 == null || content2 == null) return null;
    SimpleDiffRequest diffRequest = new SimpleDiffRequest(project, title);
    diffRequest.setContents(content1, content2);
    return diffRequest;
  }
}