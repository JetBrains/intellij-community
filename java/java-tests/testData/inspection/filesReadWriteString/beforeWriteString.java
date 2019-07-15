// "Fix all ''Files.readString()' or 'Files.writeString()' can be used' problems in file" "true"
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

public class Example {
  void test1() throws IOException {
    byte[] /*1*/bytes = ("foo"/*2*/+"bar")./*3*/getBytes(StandardCharsets.ISO_8859_1/*4*/)/*5*/;
    Files.write<caret>(Paths.get("/etc/passwd"), bytes/*6*/, StandardOpenOption.CREATE);
  }

  void test2() throws IOException {
    byte[] bytes = "foo".getBytes(StandardCharsets.UTF_16/*3*/);
    Files.write(Paths.get("/etc/passwd"), (/*1*/bytes/*2*/));
  }

  void test3() throws IOException {
    Files.write(Paths.get("/etc/passwd"), (("foo").getBytes(StandardCharsets.UTF_16)));
  }
}