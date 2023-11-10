// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.dependency.java;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.dependency.Node;
import org.jetbrains.jps.dependency.Usage;
import org.jetbrains.jps.dependency.diff.DiffCapable;
import org.jetbrains.jps.dependency.diff.Difference;
import org.jetbrains.jps.dependency.impl.RW;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

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

  public JVMClassNode(DataInput in) throws IOException {
    super(in);
    myId = new JvmNodeReferenceID(in);
    outFilePath = in.readUTF();
    myUsages = RW.readCollection(in, () -> JvmNodeElementExternalizer.<Usage>getMultitypeExternalizer().load(in));
  }

  @Override
  public void write(DataOutput out) throws IOException {
    super.write(out);
    myId.write(out);
    out.writeUTF(outFilePath);
    RW.writeCollection(out, myUsages,  u -> JvmNodeElementExternalizer.getMultitypeExternalizer().save(out, u));
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

}
