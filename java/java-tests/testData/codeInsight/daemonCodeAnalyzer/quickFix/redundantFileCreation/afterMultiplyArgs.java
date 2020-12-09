// "Fix all 'Redundant File object creation' problems in file" "true"
import java.io.*;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Formatter;
import java.util.Locale;

class Main {
  private static String getSomePathname() {
    return "Some pathname";
  }

  public void main(String[] args) throws IOException {
    new FileOutputStream("in.txt", false);
    new FileReader("in.txt", Charset.defaultCharset());
    new FileWriter("in.txt", true);
    new FileWriter("in.txt", Charset.defaultCharset());
    new FileWriter("in.txt", Charset.defaultCharset(), false);
    new PrintStream("in.txt", StandardCharsets.UTF_16.displayName());
    new PrintStream("in.txt", Charset.defaultCharset());
    new PrintWriter("in.txt", StandardCharsets.UTF_16.displayName());
    new PrintWriter("in.txt", StandardCharsets.UTF_16.displayName());
    new Formatter("in.txt", StandardCharsets.UTF_16.displayName());
    new Formatter("in.txt", StandardCharsets.UTF_16.displayName(), Locale.ENGLISH);
    new Formatter("in.txt", Charset.defaultCharset(), Locale.GERMAN);

    new FileOutputStream(getSomePathname(), false);
    new FileReader(getSomePathname(), Charset.defaultCharset());
    new FileWriter(getSomePathname(), true);
    new FileWriter(getSomePathname(), Charset.defaultCharset());
    new FileWriter(getSomePathname(), Charset.defaultCharset(), false);
    new PrintStream(getSomePathname(), StandardCharsets.UTF_16.displayName());
    new PrintStream(getSomePathname(), Charset.defaultCharset());
    new PrintWriter(getSomePathname(), StandardCharsets.UTF_16.displayName());
    new PrintWriter(getSomePathname(), StandardCharsets.UTF_16.displayName());
    new Formatter(getSomePathname(), StandardCharsets.UTF_16.displayName());
    new Formatter(getSomePathname(), StandardCharsets.UTF_16.displayName(), Locale.ENGLISH);
    new Formatter(getSomePathname(), Charset.defaultCharset(), Locale.GERMAN);
  }
}
