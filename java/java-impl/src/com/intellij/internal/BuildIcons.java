/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

/*
 * @author max
 */
package com.intellij.internal;

import com.intellij.openapi.util.Couple;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;

import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.util.*;

public class BuildIcons {
  public static void main(String[] args) throws Exception {
    File root = new File("/Users/max/IDEA/out/classes/production/");
    final MultiMap<Couple<Integer>, String> dimToPath = new MultiMap<>();

    walk(root, dimToPath, root);

    ArrayList<Couple<Integer>> keys = new ArrayList<>(dimToPath.keySet());
    Collections.sort(keys, (o1, o2) -> {
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
      System.out.println("");
    }

    System.out.println("Total icons: " + total);
  }

  private static final Set<String> IMAGE_EXTENSIONS = ContainerUtil.newTroveSet(FileUtil.PATH_HASHING_STRATEGY,
                                                                                "png", "gif", "jpg", "jpeg");

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
