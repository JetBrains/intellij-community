/*
 * Copyright 2000-2012 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.internal;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.search.FilenameIndex;

import java.io.InputStream;
import java.security.MessageDigest;
import java.util.*;

/**
 * @author Konstantin Bulenkov
 */
public class ShowImageDuplicatesAction extends AnAction {
  //FileTypeManager.getInstance().getFileTypeByExtension("png").getAllPossibleExtensions() ?
  private static final List<String> IMAGE_EXTENSIONS = Arrays.asList("png", "jpg", "jpeg", "gif", "tiff", "bmp");

  @Override
  public void actionPerformed(AnActionEvent e) {
    final Project project = getEventProject(e);
    assert project != null;
    ProgressManager.getInstance().runProcessWithProgressSynchronously(new Runnable() {
      @Override
      public void run() {
        collectAndShowDuplicates(project);
      }
    }, "Gathering images", true, project);
  }

  private static void collectAndShowDuplicates(final Project project) {
    final ProgressIndicator indicator = ProgressManager.getInstance().getProgressIndicator();
    if (indicator != null && !indicator.isCanceled()) {
      indicator.setText("Collecting project images...");
      indicator.setIndeterminate(false);
      final List<VirtualFile> images = new ArrayList<VirtualFile>();
      for (String ext : IMAGE_EXTENSIONS) {
        images.addAll(FilenameIndex.getAllFilesByExt(project, ext));
      }

      final Map<Long, Set<VirtualFile>> duplicates = new HashMap<Long, Set<VirtualFile>>();
      final Map<Long, VirtualFile> all = new HashMap<Long, VirtualFile>();
      for (int i = 0; i < images.size(); i++) {
        indicator.setFraction((double)(i + 1) / (double)images.size());
        final VirtualFile file = images.get(i);
        if (!(file.getFileSystem() instanceof LocalFileSystem)) continue;
        final long length = file.getLength();
        if (all.containsKey(length)) {
          if (!duplicates.containsKey(length)) {
            final HashSet<VirtualFile> files = new HashSet<VirtualFile>();
            files.add(all.get(length));
            duplicates.put(length, files);
          }
          duplicates.get(length).add(file);
        } else {
          all.put(length, file);
        }
        indicator.checkCanceled();
      }
      showResults(project, images, duplicates, all);
    }
  }

  private static void showResults(final Project project, final List<VirtualFile> images,
                                  Map<Long, Set<VirtualFile>> duplicates,
                                  Map<Long, VirtualFile> all) {
    final ProgressIndicator indicator = ProgressManager.getInstance().getProgressIndicator();
    if (indicator == null || indicator.isCanceled()) return;
    indicator.setText("MD5 check");

    int count = 0;
    for (Set set : duplicates.values()) count+=set.size();
    final Map<String, Set<VirtualFile>> realDuplicates = new HashMap<String, Set<VirtualFile>>();
    int seek = 0;
    for (Set<VirtualFile> files : duplicates.values()) {
      for (VirtualFile file : files) {
        seek++;
        indicator.setFraction((double)seek / (double)count);
        try {
          final String md5 = getMD5Checksum(file.getInputStream());
          if (realDuplicates.containsKey(md5)) {
            realDuplicates.get(md5).add(file);
          } else {
            final HashSet<VirtualFile> set = new HashSet<VirtualFile>();
            set.add(file);
            realDuplicates.put(md5, set);
          }
        }
        catch (Exception ignored) {
        }
      }
    }
    count = 0;
    for (String key : new ArrayList<String>(realDuplicates.keySet())) {
      final int size = realDuplicates.get(key).size();
      if (size == 1) {
        realDuplicates.remove(key);
      } else {
        count+=size;
      }
    }

    ApplicationManager.getApplication().invokeLater(new Runnable() {
      @Override
      public void run() {
        new ImageDuplicateResultsDialog(project, images, realDuplicates).show();
      }
    });

  }

  @Override
  public void update(AnActionEvent e) {
    e.getPresentation().setEnabledAndVisible(getEventProject(e) != null);
  }

  public static byte[] createChecksum(InputStream fis) throws Exception {
      byte[] buffer = new byte[1024];
      MessageDigest md5 = MessageDigest.getInstance("MD5");
      int read;

      while ((read = fis.read(buffer)) > 0) md5.update(buffer, 0, read);

      fis.close();
      return md5.digest();
  }

  public static String getMD5Checksum(InputStream fis) throws Exception {
    byte[] bytes = createChecksum(fis);
    String md5 = "";

    for (byte b : bytes) md5 += Integer.toString((b & 0xff) + 0x100, 16).substring(1);
    return md5;
  }
}
