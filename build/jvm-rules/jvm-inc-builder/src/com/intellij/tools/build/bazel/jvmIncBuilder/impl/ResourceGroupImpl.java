package com.intellij.tools.build.bazel.jvmIncBuilder.impl;

import com.dynatrace.hash4j.hashing.HashStream64;
import com.dynatrace.hash4j.hashing.Hashing;
import com.intellij.tools.build.bazel.jvmIncBuilder.ResourceGroup;
import org.jetbrains.jps.dependency.DataReader;
import org.jetbrains.jps.dependency.GraphDataInput;
import org.jetbrains.jps.dependency.GraphDataOutput;
import org.jetbrains.jps.dependency.NodeSource;

import java.io.File;
import java.io.IOException;
import java.util.Map;

public class ResourceGroupImpl extends SourceSnapshotImpl implements ResourceGroup {
  private final String myStripPrefix;
  private final String myAddPrefix;

  public ResourceGroupImpl(Map<NodeSource, String> digestSources, String stripPrefix, String addPrefix) {
    super(digestSources);
    myStripPrefix = normalizePrefix(stripPrefix);
    myAddPrefix = normalizePrefix(addPrefix);
    
    HashStream64 hash = Hashing.xxh3_64().hashStream();
    for (Map.Entry<NodeSource, String> entry : digestSources.entrySet()) {
      entry.setValue(Long.toHexString(hash.reset().putString(myStripPrefix).putString(myAddPrefix).putString(entry.getValue()).getAsLong()));
    }
  }

  private static String normalizePrefix(String prefix) {
    if (!prefix.isEmpty()) {
      prefix = prefix.replace(File.separatorChar, '/');

      int start = 0;
      while (start < prefix.length() && prefix.charAt(start) == '/') start += 1;

      int end = prefix.length() - 1;
      while (end > start && prefix.charAt(end) == '/') end -= 1;

      prefix = start <= end? prefix.substring(start, end + 1) : "";
    }
    return prefix;
  }

  public ResourceGroupImpl(GraphDataInput in, DataReader<? extends NodeSource> sourceReader) throws IOException {
    super(in, sourceReader);
    myStripPrefix = in.readUTF();
    myAddPrefix = in.readUTF();
  }

  @Override
  public String getStripPrefix() {
    return myStripPrefix;
  }

  @Override
  public String getAddPrefix() {
    return myAddPrefix;
  }

  @Override
  public void write(GraphDataOutput out) throws IOException {
    super.write(out);
    out.writeUTF(myStripPrefix);
    out.writeUTF(myAddPrefix);
  }
}
