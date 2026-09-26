import java.io.IOException;

interface I {

  default void x() throws IOException {}

  void y() throws IOException;

  void z() throws IOException;
}
abstract class C implements I {

  public void z() throws IOException {
    System.out.println("breathe");
  }
}