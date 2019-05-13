// "Fix all ''Files.readString()' or 'Files.writeString()' can be used' problems in file" "true"
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

public class Example {
  void testRead1() throws IOException {
    String s = Files.readString(Paths.get("/etc/passwd"), StandardCharsets.ISO_8859_1);
  }

  void testRead2() throws IOException {
      /*3*/
      /*4*/
      /*8*/
      /*9*/
      /*1*/
      /*2*/
      String s = Files/*5*/./*6*/readString(/*7*/Paths.get("/etc/passwd"));
  }
}