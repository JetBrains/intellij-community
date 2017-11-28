import java.io.*;

class CatchParameter {

  void m() {
    try (final InputStream in  = new FileInputStream("filename")) {
    } catch (final FileNotFoundException | RuntimeException e) {
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
}