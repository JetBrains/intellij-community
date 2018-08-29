// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.platform.onair.index;

import com.google.common.base.Charsets;
import com.google.common.io.CharStreams;
import com.google.gson.GsonBuilder;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.platform.onair.storage.api.Address;
import com.intellij.util.indexing.IndexInfrastructure;
import org.jdom.Element;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class RevisionDescriptor {
  public final int R;
  public final int baseR;

  public static class Heads {
    public final Address forwardIndexHead;
    public final Map<String, BTreeIndexStorage.AddressDescriptor> invertedIndicesHeads;
    public final Address vfsHead;

    public Heads(Address forwardIndexHead,
                 Map<String, BTreeIndexStorage.AddressDescriptor> invertedIndicesHeads, Address vfsHead) {
      this.forwardIndexHead = forwardIndexHead;
      this.invertedIndicesHeads = invertedIndicesHeads;
      this.vfsHead = vfsHead;
    }
  }

  @Nullable
  public final Heads heads;

  public RevisionDescriptor(final int r, final int baseR, @Nullable final Heads heads) {
    R = r;
    this.baseR = baseR;
    this.heads = heads;
  }

  @SuppressWarnings("unchecked")
  public static RevisionDescriptor fromRevision(String revision) {
    if (revision == null || revision.isEmpty()) {
      return new RevisionDescriptor(17, -1, null);
    }

    final Map indexMeta = downloadIndexMeta(revision);

    // one table to rule them all
    final Address forwardIndexHead = Address.fromStrings((List<String>)(indexMeta.get("forward-indices")));
    final Map<String, BTreeIndexStorage.AddressDescriptor> invertedIndicesHeads = new HashMap<>();

    ((Map)indexMeta.get("inverted-indices")).forEach((key, value) -> {
      final String name = (String)key;
      final Map m = (Map)value;
      final List<String> invertedAddress = (List<String>)m.get("inverted");
      final List<String> internaryAddress = (List<String>)m.get("internary");
      // final List<String> hashToVirtualFile = (List<String>)m.get("hash-to-file");

      Address internary = internaryAddress != null ? Address.fromStrings(internaryAddress) : null;
      invertedIndicesHeads.put(name, new BTreeIndexStorage.AddressDescriptor(
        internary,
        Address.fromStrings(invertedAddress)/*, Address.fromStrings(hashToVirtualFile)*/
      ));
    });

    final int baseRevision = Integer.parseInt((String)indexMeta.get("revision-int"));
    final Address vfsHead = Address.fromStrings((List<String>)(indexMeta.get("vfs-mapping")));

    return new RevisionDescriptor(17, baseRevision, new Heads(forwardIndexHead, invertedIndicesHeads, vfsHead));
  }

  public static Map downloadIndexMeta(String revision) {
    String bucket = "onair-index-data";
    String region = "eu-central-1";
    try {
      InputStream is = new URL("https://s3." + region + ".amazonaws.com/" + bucket + "/" + revision + "/meta").openStream();

      String str = CharStreams.toString(new InputStreamReader(is, Charsets.UTF_8));

      return new GsonBuilder().create().fromJson(str, Map.class);
    }
    catch (Exception e) {
      throw new RuntimeException("exception downloading index data for revision " + revision, e);
    }
  }

  public static void downloadIndexStubs(String revision) {
    String bucket = "onair-index-data";
    String region = "eu-central-1";
    try {
      InputStream stream =
        new URL("https://s3." + region + ".amazonaws.com/" + bucket + "?prefix=" + revision + "/index_meta").openStream();
      Element element = JDOMUtil.load(stream);

      List<String> files = element.getChildren().stream()
                                  .filter(e -> e.getName().equals("Contents"))
                                  .flatMap(e -> e.getChildren().stream())
                                  .filter(o -> o.getName().equals("Key"))
                                  .map(e -> e.getText())
                                  .map(s -> s.split("/")[2])
                                  .collect(Collectors.toList());

      for (String file : files) {
        String s3url = "https://s3." + region + ".amazonaws.com/" + bucket + "/" + revision + "/index_meta/" + file;
        ReadableByteChannel source = Channels.newChannel(new URL(s3url).openStream());
        File base = IndexInfrastructure.getIndexMeta();
        if (!base.exists()) {
          if (!base.mkdirs()) {
            throw new RuntimeException("can't mkdir " + base.getCanonicalPath());
          }
        }

        try (FileOutputStream fos = new FileOutputStream(new File(base, file))) {
          fos.getChannel().transferFrom(source, 0, Long.MAX_VALUE);
        }
      }
    }
    catch (Exception e) {
      throw new RuntimeException("exception downloading index data for revision " + revision, e);
    }
  }
}
