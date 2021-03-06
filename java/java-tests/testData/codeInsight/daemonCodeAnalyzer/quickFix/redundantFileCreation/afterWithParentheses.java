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
    new FileInputStream((("in.txt")));
    new FileOutputStream((("in.txt")));
    new FileReader((("in.txt")));
    new FileWriter((("in.txt")));
    new PrintStream((("in.txt")));
    new PrintWriter((("in.txt")));
    new Formatter((("in.txt")));

    new FileInputStream(((getSomePathname())));
    new FileOutputStream(((getSomePathname())));
    new FileReader(((getSomePathname())));
    new FileWriter(((getSomePathname())));
    new PrintStream(((getSomePathname())));
    new PrintWriter(((getSomePathname())));
    new Formatter(((getSomePathname())));

    new FileOutputStream((getSomePathname()), false);
    new FileReader((getSomePathname()), Charset.defaultCharset());
    new FileWriter((getSomePathname()), true);
    new FileWriter((getSomePathname()), Charset.defaultCharset());
    new FileWriter((getSomePathname()), Charset.defaultCharset(), false);
    new PrintStream((getSomePathname()), StandardCharsets.UTF_16.displayName());
    new PrintStream((getSomePathname()), Charset.defaultCharset());
    new PrintWriter((getSomePathname()), StandardCharsets.UTF_16.displayName());
    new PrintWriter((getSomePathname()), StandardCharsets.UTF_16.displayName());
    new Formatter((getSomePathname()), StandardCharsets.UTF_16.displayName());
    new Formatter((getSomePathname()), StandardCharsets.UTF_16.displayName(), Locale.ENGLISH);
    new Formatter((getSomePathname()), Charset.defaultCharset(), Locale.GERMAN);
  }
}
