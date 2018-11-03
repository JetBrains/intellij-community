// "Replace with 'Files.writeString()'" "WARNING"
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

public class Example {
  private static final Charset CHARSET = Charset.forName("UTF-16");
  
  void test1(String str) throws IOException {
      Files.writeString(Paths.get("/etc/passwd"), str, CHARSET, StandardOpenOption.CREATE);
  }
}