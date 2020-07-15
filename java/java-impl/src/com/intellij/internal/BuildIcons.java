// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

/*
 * @author max
 */
package com.intellij.internal;

import com.intellij.openapi.util.Couple;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.util.containers.MultiMap;
import gnu.trove.THashSet;

import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Set;

public final class BuildIcons {
  public static void main(String[] args) throws Exception {
    File root = new File("/Users/max/IDEA/out/classes/production/");
    final MultiMap<Couple<Integer>, String> dimToPath = new MultiMap<>();

    walk(root, dimToPath, root);

    ArrayList<Couple<Integer>> keys = new ArrayList<>(dimToPath.keySet());
    keys.sort((o1, o2) -> {
      int d0 = dimToPath.get(o2).size() - dimToPath.get(o1).size();
      if (d0 != 0) return d0;
      int d1 = o1.first - o2.first;
      if (d1 != 0) {
        return d1;
      }
      return o1.second - o2.second;
    });

    int total = 0;
    for (Couple<Integer> key : keys) {
      Collection<String> paths = dimToPath.get(key);
      System.out.println("------------------------   " + key.first + "x" + key.second + "  (total " +paths.size() + " icons) --------------------------------");
      for (String path : paths) {
        System.out.println(path);
        total ++;
      }
      System.out.println();
    }

    System.out.println("Total icons: " + total);
  }

  private static final Set<String> IMAGE_EXTENSIONS =
    new THashSet<>(Arrays.asList("png", "gif", "jpg", "jpeg"), FileUtil.PATH_HASHING_STRATEGY);

  private static void walk(File root, MultiMap<Couple<Integer>, String> dimToPath, File file) throws IOException {
    if (file.isDirectory()) {
      for (File child : file.listFiles()) {
        walk(root, dimToPath, child);
      }
    }
    else {
      if (IMAGE_EXTENSIONS.contains(FileUtilRt.getExtension(file.getName()))) {
        String relativePath = file.getAbsolutePath().substring(root.getAbsolutePath().length() + 1);
        Image image = loadImage(file);
        File target;
        int width = image.getWidth(null);
        int height = image.getHeight(null);
        if (height != width && (height > 100 || width > 100)) {
          target = new File("/Users/max/images/other", relativePath);
        }
        else {
          target = new File("/Users/max/images/icons", relativePath);
          dimToPath.putValue(new Couple<>(width, height), relativePath);
        }
        FileUtil.copy(file, target);
      }
    }
  }

  private static Image loadImage(File path) {
    Image image = Toolkit.getDefaultToolkit().createImage(path.getAbsolutePath());
    waitForImage(image);
    return image;
  }

  private static boolean waitForImage(Image image) {
    if (image == null) return false;
    if (image.getWidth(null) > 0) return true;
    MediaTracker mediatracker = new MediaTracker(new Component() {});
    mediatracker.addImage(image, 1);
    try {
      mediatracker.waitForID(1, 5000);
    } catch (InterruptedException ex) {

    }
    return !mediatracker.isErrorID(1);
  }

}
