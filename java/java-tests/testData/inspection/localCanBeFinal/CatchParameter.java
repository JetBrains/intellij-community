import java.io.*;

class CatchParameter {

  void m() {
    try (final InputStream in  = new FileInputStream("filename")) {
    } catch (final FileNotFoundException | RuntimeException e) {
    } catch (IOException <warning descr="Parameter 'e' can have 'final' modifier">e</warning>) {
      throw new RuntimeException(e);
    }
  }
}