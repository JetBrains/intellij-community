// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.dependency.java;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.dependency.ExternalizableGraphElement;
import org.jetbrains.jps.dependency.GraphDataInput;
import org.jetbrains.jps.dependency.GraphDataOutput;
import org.jetbrains.jps.dependency.diff.Difference;
import org.jetbrains.jps.dependency.impl.RW;

import java.io.IOException;
import java.util.Objects;
import java.util.function.Supplier;

public class Proto implements ExternalizableGraphElement {
  private final JVMFlags access;
  private final String signature;
  private final String name;
  private final @NotNull Iterable<ElementAnnotation> annotations;

  public Proto(@NotNull JVMFlags flags, String signature, String name, @NotNull Iterable<ElementAnnotation> annotations) {
    this.access = flags;
    this.signature = signature == null? "" : signature;
    this.name = name == null? "" : name;
    this.annotations = annotations;
  }

  public Proto(GraphDataInput in) throws IOException {
    access = new JVMFlags(in.readInt());
    signature = in.readUTF();
    name = in.readUTF();
    annotations = RW.readCollection(in, () -> new ElementAnnotation(in));
  }

  @Override
  public void write(GraphDataOutput out) throws IOException {
    out.writeInt(access.getValue());
    out.writeUTF(signature);
    out.writeUTF(name);
    RW.writeCollection(out, annotations, t -> t.write(out));
  }

  public final JVMFlags getFlags() {
    return access;
  }

  public final String getSignature() {
    return signature;
  }

  public final String getName() {
    return name;
  }

  public final @NotNull Iterable<ElementAnnotation> getAnnotations() {
    return annotations;
  }

  public final boolean isPublic() {
    return getFlags().isPublic();
  }

  public final boolean isProtected() {
    return getFlags().isProtected();
  }

  public final boolean isPackageLocal() {
    return getFlags().isPackageLocal();
  }

  public final boolean isPrivate() {
    return getFlags().isPrivate();
  }

  public final boolean isAbstract() {
    return getFlags().isAbstract();
  }

  public final boolean isBridge() {
    return getFlags().isBridge();
  }

  public final boolean isSynthetic() {
    return getFlags().isSynthetic();
  }

  public final boolean isAnnotation() {
    return getFlags().isAnnotation();
  }

  public final boolean isFinal() {
    return getFlags().isFinal();
  }

  public final boolean isStatic() {
    return getFlags().isStatic();
  }

  public boolean isLibrary() {
    return getFlags().isLibrary();
  }

  /**
   * tests if the accessibility of this Proto is less restricted than the accessibility of the given Proto
   * @return true means this Proto is less restricted than the proto passed as parameter <br>
   *         false means this Proto has more restricted access than the parameter Proto or they have equal accessibility
   */
  public final boolean isMoreAccessibleThan(Proto anotherProto) {
    if (anotherProto.isPrivate()) {
      return this.isPackageLocal() || this.isProtected() || this.isPublic();
    }
    if (anotherProto.isPackageLocal()) {
      return this.isProtected() || this.isPublic();
    }
    if (anotherProto.isProtected()) {
      return this.isPublic();
    }
    return false;
  }

  public final boolean isWeakerAccessThan(Proto anotherProto) {
    return getFlags().isWeakerAccess(anotherProto.getFlags());
  }

  public class Diff<V extends Proto> implements Difference {
    private final Supplier<Specifier<ElementAnnotation, ElementAnnotation.Diff>> myAnnotationsDiff;
    protected final V myPast;

    public Diff(V past) {
      myPast = past;
      myAnnotationsDiff = Utils.lazyValue(() -> Difference.deepDiff(myPast.getAnnotations(), getAnnotations()));
    }

    @Override
    public boolean unchanged() {
      return !flagsChanged() && !signatureChanged() && annotations().unchanged();
    }

    public boolean flagsChanged() {
      return !myPast.getFlags().equals(getFlags());
    }

    public JVMFlags getAddedFlags() {
      return getFlags().deriveAdded(myPast.getFlags());
    }

    public JVMFlags getRemovedFlags() {
      return getFlags().deriveRemoved(myPast.getFlags());
    }

    public boolean becamePackageLocal() {
      return !myPast.isPackageLocal() && isPackageLocal();
    }

    public boolean accessRestricted() {
      return Proto.this.isWeakerAccessThan(myPast);
    }

    public boolean accessExpanded() {
      return myPast.isWeakerAccessThan(Proto.this);
    }

    public boolean signatureChanged() {
      return !Objects.equals(myPast.getSignature(), getSignature());
    }

    public Specifier<ElementAnnotation, ElementAnnotation.Diff> annotations() {
      return myAnnotationsDiff.get();
    }
  }

}
