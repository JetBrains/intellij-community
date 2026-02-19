import java.io.*;

class Test {
  String m() {
    try (final BufferedReader br = new BufferedReader(new FileReader("foo"))) {
      return br.readLine();
    } catch (final Exception e) {
      e.printStackTrace();
    }
    return null;
  }

  void n() {
    new Runnable() {
      @Override
      public void run() {
        try (InputStream <warning descr="Variable 'is' can have 'final' modifier">is</warning> = new FileInputStream(new File("b"))) {
          System.out.println(is);
        }
        catch (final IOException e) {
          throw new RuntimeException(e);
        }
      }
    };
  }
}