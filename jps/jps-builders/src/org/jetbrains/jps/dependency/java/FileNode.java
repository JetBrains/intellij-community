// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.dependency.java;

import com.intellij.util.SmartList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.dependency.GraphDataInput;
import org.jetbrains.jps.dependency.GraphDataOutput;
import org.jetbrains.jps.dependency.Node;
import org.jetbrains.jps.dependency.Usage;
import org.jetbrains.jps.dependency.diff.DiffCapable;
import org.jetbrains.jps.dependency.diff.Difference;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

public final class FileNode implements Node<FileNode, FileNode.Diff> {
  private final JvmNodeReferenceID myId;
  private final Iterable<Usage> myUsages;

  public FileNode(String name, @NotNull Iterable<Usage> usages) {
    myId = new JvmNodeReferenceID(name);
    myUsages = usages;
  }

  public FileNode(GraphDataInput in) throws IOException {
    myId = new JvmNodeReferenceID(in);

    List<Usage> usages = new SmartList<>();
    try {
      int groupCount = in.readInt();
      while(groupCount-- > 0) {
        in.readGraphElementCollection(usages);
      }
    }
    finally {
      myUsages = usages;
    }
  }

  @Override
  public void write(GraphDataOutput out) throws IOException {
    myId.write(out);

    Map<Class<? extends Usage>, List<Usage>> usageGroups = new HashMap<>();
    for (Usage usage : myUsages) {
      usageGroups.computeIfAbsent(usage.getClass(), k -> new SmartList<>()).add(usage);
    }
    
    out.writeInt(usageGroups.size());
    for (Map.Entry<Class<? extends Usage>, List<Usage>> entry : usageGroups.entrySet()) {
      out.writeGraphElementCollection(entry.getKey(), entry.getValue());
    }
  }

  @Override
  public @NotNull JvmNodeReferenceID getReferenceID() {
    return myId;
  }

  public @NotNull String getName() {
    return myId.getNodeName();
  }

  @Override
  public Iterable<Usage> getUsages() {
    return myUsages;
  }


  @Override
  public boolean isSame(DiffCapable<?, ?> other) {
    if (!(other instanceof FileNode)) {
      return false;
    }

    FileNode that = (FileNode)other;
    return myId.equals(that.myId);
  }

  @Override
  public int diffHashCode() {
    return myId.hashCode();
  }

  @Override
  public Diff difference(FileNode past) {
    return new Diff(past);
  }

  public class Diff implements Difference {
    private final Supplier<Specifier<Usage, ?>> myUsagesDiff;

    public Diff(FileNode past) {
      myUsagesDiff = Utils.lazyValue(() -> Difference.diff(past.getUsages(), getUsages()));
    }

    @Override
    public boolean unchanged() {
      return usages().unchanged();
    }

    public Specifier<Usage, ?> usages() {
      return myUsagesDiff.get();
    }
  }

}
