// "Fix all ''Files.readString()' or 'Files.writeString()' can be used' problems in file" "true"
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

public class Example {
  void testRead1() throws IOException {
    String s = new String(Files<caret>.readAllBytes(Paths.get("/etc/passwd")), StandardCharsets.ISO_8859_1);
  }

  void testRead2() throws IOException {
    String s = new /*3*/String(/*4*/Files/*5*/./*6*/readAllBytes(/*7*/Paths.get("/etc/passwd"))/*8*/, /*9*/(StandardCharsets./*1*/UTF_8/*2*/));
  }
}