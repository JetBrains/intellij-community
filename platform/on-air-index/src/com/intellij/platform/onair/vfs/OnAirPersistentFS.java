// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.platform.onair.vfs;

import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.vfs.newvfs.persistent.FSRecordsImpl;
import com.intellij.openapi.vfs.newvfs.persistent.FSRecords;
import com.intellij.openapi.vfs.newvfs.persistent.PersistentFSImpl;
import com.intellij.util.messages.MessageBus;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.util.List;
import java.util.stream.Collectors;

public class OnAirPersistentFS extends PersistentFSImpl {
  public OnAirPersistentFS(@NotNull MessageBus bus, @NotNull FSRecordsImpl fsRecords) {
    super(bus, fsRecords);
  }

  public static void download(String revision) {
    String bucket = "onair-index-data";
    String region = "eu-central-1";
    try {
      InputStream stream = new URL("https://s3." + region + ".amazonaws.com/" + bucket + "?prefix=" + revision + "/vfs").openStream();
      Element element = JDOMUtil.load(stream);
      List<String> files = element.getChildren().stream()
                                  .filter(e -> e.getName().equals("Contents"))
                                  .flatMap(e -> e.getChildren().stream())
                                  .filter(o -> o.getName().equals("Key"))
                                  .map(e -> e.getText())
                                  .map(s -> s.split("/")[2])
                                  .collect(Collectors.toList());
      for (String file : files) {
        String s3url = "https://s3." + region + ".amazonaws.com/" + bucket + "/" + revision + "/vfs/" + file;
        ReadableByteChannel source = Channels.newChannel(new URL(s3url).openStream());
        File basePath = FSRecords.getInstance().basePath(); // TODO: myFSRecords
        basePath.mkdirs();
        try (FileOutputStream fos = new FileOutputStream(new File(basePath, file))) {
          FileChannel dest = fos.getChannel();
          dest.transferFrom(source, 0, Long.MAX_VALUE);
        }
      }
    }
    catch (Exception e) {
      throw new RuntimeException("exception downloading vfs data for revision " + revision, e);
    }
  }

  @Override
  public void initComponent() {
    String revision = System.getProperty("onair.index.revision");
    if (revision != null && !revision.trim().isEmpty()) {
      download(revision);
    }
    super.initComponent();
  }

}
