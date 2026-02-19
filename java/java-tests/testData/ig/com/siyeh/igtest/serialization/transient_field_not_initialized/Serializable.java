import java.io.*;

class C implements Serializable {
  static transient int a = 1;
  transient int <warning descr="Transient field 'b' not initialized on deserialization">b</warning> = 2;
}

class D implements Serializable {
  transient int <warning descr="Transient field 'a' not initialized on deserialization">a</warning>;
  transient int <warning descr="Transient field 'b' not initialized on deserialization">b</warning>;

  {
    a = 1;
  }

  D() {
    b = 2;
  }
}

class E implements Serializable {
  transient int a;

  ObjectInputStream readObject() {
    return null;
  }
}

record R(int a) implements Serializable {
  static transient int b = 1;
}