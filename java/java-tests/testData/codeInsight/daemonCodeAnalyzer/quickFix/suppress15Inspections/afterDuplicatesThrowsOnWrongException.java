// "Suppress for method" "true"
import java.io.FileNotFoundException;
import java.io.IOException;

class Main {
  @SuppressWarnings("RedundantThrowsDeclaration")
  public void test() throws Exception, FileNotFoundException, IOException {}
}