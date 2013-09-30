import java.io.*;

class Test {
  abstract class Target {
    abstract void call(String f) throws FileNotFoundException, IOException;
    abstract void call(String[] f) throws FileNotFoundException, IOException;
  }

  void use(Target target) throws IOException {
    try {
      target.call("");
    } catch (FileNotFoundException e) {
      System.out.println("file not found");
    } catch (IOException e) {
      System.out.println("failed: " + e);
    }
  }
}
