import java.io.*;

public abstract class A {
  protected Object readResolve() throws ObjectStreamException {
    return null;
  }
}

class AImpl extends A implements Serializable {
  public static void main(String[] args) {
    System.out.println(new AImpl().toString());
  }
}