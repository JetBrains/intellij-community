// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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

public abstract class JVMClassNode<T extends JVMClassNode<T, D>, D extends Difference> extends Proto implements Node<T, D> {
  private final JvmNodeReferenceID myId;
  private final String outFilePath;
  private final Iterable<Usage> myUsages;

  public JVMClassNode(JVMFlags flags, String signature, String name, String outFilePath, @NotNull Iterable<TypeRepr.ClassType> annotations, @NotNull Iterable<Usage> usages) {
    super(flags, signature, name, annotations);
    myId = new JvmNodeReferenceID(name);
    this.outFilePath = outFilePath;
    myUsages = usages;
  }

  public JVMClassNode(GraphDataInput in) throws IOException {
    super(in);
    myId = new JvmNodeReferenceID(in);
    outFilePath = in.readUTF();

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
    super.write(out);
    myId.write(out);
    out.writeUTF(outFilePath);

    Map<Class<? extends Usage>, List<Usage>> usageGroups = new HashMap<>();
    for (Usage usage : myUsages) {
      Class<? extends Usage> uClass = usage.getClass();
      List<Usage> acc = usageGroups.get(uClass);
      if (acc == null) {
        usageGroups.put(uClass, acc = new SmartList<>());
      }
      acc.add(usage);
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

  public String getOutFilePath() {
    return outFilePath;
  }

  @Override
  public Iterable<Usage> getUsages() {
    return myUsages;
  }

  @Override
  public boolean isSame(DiffCapable<?, ?> other) {
    if (!(other instanceof JVMClassNode)) {
      return false;
    }

    if (!this.getClass().equals(other.getClass())) {
      return false;
    }
    JVMClassNode<?, ?> that = (JVMClassNode<?, ?>)other;
    return myId.equals(that.myId) && outFilePath.equals(that.outFilePath);
  }

  @Override
  public int diffHashCode() {
    return 31 * outFilePath.hashCode() + myId.hashCode();
  }

  public class Diff extends Proto.Diff<T> {

    public Diff(T past) {
      super(past);
    }

    @Override
    public boolean unchanged() {
      return super.unchanged() && usages().unchanged();
    }

    public Specifier<Usage, ?> usages() {
      return Difference.diff(myPast.getUsages(), getUsages());
    }
  }

}
