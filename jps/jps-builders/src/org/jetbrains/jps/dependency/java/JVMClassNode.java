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
import org.jetbrains.jps.dependency.impl.RW;
import org.jetbrains.jps.javac.Iterators;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public abstract class JVMClassNode<T extends JVMClassNode<T, D>, D extends Difference> extends Proto implements Node<T, D> {
  private final JvmNodeReferenceID myId;
  private final String outFilePath;
  private final Iterable<Usage> myUsages;
  private final Iterable<JvmMetadata<?, ?>> myMetadata;

  public JVMClassNode(JVMFlags flags, String signature, String name, String outFilePath, @NotNull Iterable<ElementAnnotation> annotations, @NotNull Iterable<Usage> usages, @NotNull Iterable<JvmMetadata<?, ?>> metadata) {
    super(flags, signature, name, annotations);
    myId = new JvmNodeReferenceID(name);
    this.outFilePath = outFilePath;
    myUsages = usages;
    myMetadata = metadata;
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

    myMetadata = RW.readCollection(in, in::readGraphElement, new SmartList<>());
  }

  @Override
  public void write(GraphDataOutput out) throws IOException {
    super.write(out);
    myId.write(out);
    out.writeUTF(outFilePath);

    Map<Class<? extends Usage>, List<Usage>> usageGroups = new HashMap<>();
    for (Usage usage : myUsages) {
      usageGroups.computeIfAbsent(usage.getClass(), k -> new SmartList<>()).add(usage);
    }
    
    out.writeInt(usageGroups.size());
    for (Map.Entry<Class<? extends Usage>, List<Usage>> entry : usageGroups.entrySet()) {
      out.writeGraphElementCollection(entry.getKey(), entry.getValue());
    }

    RW.writeCollection(out, myMetadata, out::writeGraphElement);
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

  public Iterable<JvmMetadata<?, ?>> getMetadata() {
    return myMetadata;
  }

  public <MT extends JvmMetadata<MT, ?>> Iterable<MT> getMetadata(Class<MT> metaClass) {
    return Iterators.filter(Iterators.map(myMetadata, m -> metaClass.isInstance(m)? metaClass.cast(m) : null), Objects::nonNull);
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
      return super.unchanged() && usages().unchanged() && !metadataChanged();
    }

    public Specifier<Usage, ?> usages() {
      return Difference.diff(myPast.getUsages(), getUsages());
    }

    public boolean metadataChanged() {
      //noinspection unchecked
      return Iterators.find(
        Iterators.unique(Iterators.map(Iterators.flat(myPast.getMetadata(), getMetadata()), m -> m.getClass())), metaClass -> !metadata(metaClass).unchanged()
      ) != null;
    }

    public <MT extends JvmMetadata<MT, MD>, MD extends Difference> Specifier<MT, MD> metadata(Class<MT> metaClass) {
      return Difference.deepDiff(myPast.getMetadata(metaClass), getMetadata(metaClass));
    }
  }

}
