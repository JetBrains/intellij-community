// "Replace with 'Files.writeString()'" "GENERIC_ERROR_OR_WARNING"
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

public class Example {
  private static final Charset CHARSET = StandardCharsets.UTF_16;
  
  void test1(String str) throws IOException {
      Files.writeString(Paths.get("/etc/passwd"), str, CHARSET, StandardOpenOption.CREATE);
  }
}