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
    new FileOutputStream(new Fi<caret>le("in.txt"), false);
    new FileReader(new File("in.txt"), Charset.defaultCharset());
    new FileWriter(new File("in.txt"), true);
    new FileWriter(new File("in.txt"), Charset.defaultCharset());
    new FileWriter(new File("in.txt"), Charset.defaultCharset(), false);
    new PrintStream(new File("in.txt"), StandardCharsets.UTF_16.displayName());
    new PrintStream(new File("in.txt"), Charset.defaultCharset());
    new PrintWriter(new File("in.txt"), StandardCharsets.UTF_16.displayName());
    new PrintWriter(new File("in.txt"), StandardCharsets.UTF_16.displayName());
    new Formatter(new File("in.txt"), StandardCharsets.UTF_16.displayName());
    new Formatter(new File("in.txt"), StandardCharsets.UTF_16.displayName(), Locale.ENGLISH);
    new Formatter(new File("in.txt"), Charset.defaultCharset(), Locale.GERMAN);

    new FileOutputStream(new File(getSomePathname()), false);
    new FileReader(new File(getSomePathname()), Charset.defaultCharset());
    new FileWriter(new File(getSomePathname()), true);
    new FileWriter(new File(getSomePathname()), Charset.defaultCharset());
    new FileWriter(new File(getSomePathname()), Charset.defaultCharset(), false);
    new PrintStream(new File(getSomePathname()), StandardCharsets.UTF_16.displayName());
    new PrintStream(new File(getSomePathname()), Charset.defaultCharset());
    new PrintWriter(new File(getSomePathname()), StandardCharsets.UTF_16.displayName());
    new PrintWriter(new File(getSomePathname()), StandardCharsets.UTF_16.displayName());
    new Formatter(new File(getSomePathname()), StandardCharsets.UTF_16.displayName());
    new Formatter(new File(getSomePathname()), StandardCharsets.UTF_16.displayName(), Locale.ENGLISH);
    new Formatter(new File(getSomePathname()), Charset.defaultCharset(), Locale.GERMAN);
  }
}
