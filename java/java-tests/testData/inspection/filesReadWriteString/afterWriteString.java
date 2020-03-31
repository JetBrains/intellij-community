// "Fix all ''Files.readString()' or 'Files.writeString()' can be used' problems in file" "true"
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

public class Example {
  void test1() throws IOException {
      /*1*/
      /*3*/
      /*4*/
      /*5*/
      Files.writeString(Paths.get("/etc/passwd"), "foo"/*2*/+"bar", StandardCharsets.ISO_8859_1/*6*/, StandardOpenOption.CREATE);
  }

  void test2() throws IOException {
      /*3*/
      /*1*/
      /*2*/
      Files.writeString(Paths.get("/etc/passwd"), "foo", StandardCharsets.UTF_16);
  }

  void test3() throws IOException {
    Files.writeString(Paths.get("/etc/passwd"), "foo", StandardCharsets.UTF_16);
  }
}