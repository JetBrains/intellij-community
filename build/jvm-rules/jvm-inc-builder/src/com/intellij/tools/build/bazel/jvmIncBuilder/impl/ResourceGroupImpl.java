package com.intellij.tools.build.bazel.jvmIncBuilder.impl;

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
  }

  private static String normalizePrefix(String prefix) {
    prefix = prefix.replace(File.separatorChar, '/');
    while (prefix.endsWith("/")) {
      prefix = prefix.substring(0, prefix.length() - 1);
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
