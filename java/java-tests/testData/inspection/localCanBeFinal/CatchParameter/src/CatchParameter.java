import java.io.*;

class CatchParameter {

  void m() {
    try (InputStream in  = new FileInputStream("filename")) { // don't warn about 'in' because it is implicitly final
    } catch (FileNotFoundException | RuntimeException e) { // don't warn about 'e' because it is implicitly final
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
}