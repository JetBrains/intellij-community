package com.siyeh.igtest.serialization.externalizable_without_public_no_arg_constructor;

import java.io.*;

abstract class e implements Externalizable {

  protected e() {}
}
class <warning descr="Externalizable class 'eImpl' has no 'public' no-arg constructor">eImpl</warning> extends e {
  public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
  }

  public void writeExternal(ObjectOutput out) throws IOException {
  }
}
class <warning descr="Externalizable class 'eImpl1' has no 'public' no-arg constructor">eImpl1</warning> extends e {
  private eImpl1() {}
  public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
  }

  public void writeExternal(ObjectOutput out) throws IOException {
  }
}
class <warning descr="Externalizable class 'eImpl2' has no 'public' no-arg constructor">eImpl2</warning> extends e {
  public eImpl2(int i) {
    System.out.print(i);
  }
  public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
  }

  public void writeExternal(ObjectOutput out) throws IOException {
  }
}
class AnonymousTest {
  public static void main(String[] args) throws IOException, ClassNotFoundException {
    final String h = "Hello World";
    final Externalizable externalizable = new <warning descr="Externalizable class 'Externalizable' has no 'public' no-arg constructor">Externalizable</warning>() {

      private String string = h;

      @Override
      public void writeExternal(ObjectOutput out) throws IOException {
        out.writeUTF(string);
      }

      @Override
      public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        string = in.readUTF();
      }
    };
    final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    final ObjectOutputStream out = new ObjectOutputStream(bytes);
    out.writeObject(externalizable);
    out.close();
    final ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()));
    final Object o = in.readObject();
    System.out.println("o = " + o);
  }
}
public class ExternalizableWithoutPublicNoArgConstructor implements Externalizable {

  protected ExternalizableWithoutPublicNoArgConstructor() {}

  @Override
  public void writeExternal(ObjectOutput out) throws IOException {
  }

  @Override
  public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
  }

  public Object writeReplace() throws ObjectStreamException {
    return null;
  }
}

record R(int a, int b) implements Externalizable {
  @Override
  public void writeExternal(ObjectOutput out) throws IOException {
  }

  @Override
  public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
  }
}
