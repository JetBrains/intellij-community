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
    new FileInputStream((new Fi<caret>le(("in.txt"))));
    new FileOutputStream((new File(("in.txt"))));
    new FileReader((new File(("in.txt"))));
    new FileWriter((new File(("in.txt"))));
    new PrintStream((new File(("in.txt"))));
    new PrintWriter((new File(("in.txt"))));
    new Formatter((new File(("in.txt"))));

    new FileInputStream((new File((getSomePathname()))));
    new FileOutputStream((new File((getSomePathname()))));
    new FileReader((new File((getSomePathname()))));
    new FileWriter((new File((getSomePathname()))));
    new PrintStream((new File((getSomePathname()))));
    new PrintWriter((new File((getSomePathname()))));
    new Formatter((new File((getSomePathname()))));

    new FileOutputStream((new File(getSomePathname())), false);
    new FileReader((new File(getSomePathname())), Charset.defaultCharset());
    new FileWriter((new File(getSomePathname())), true);
    new FileWriter((new File(getSomePathname())), Charset.defaultCharset());
    new FileWriter((new File(getSomePathname())), Charset.defaultCharset(), false);
    new PrintStream((new File(getSomePathname())), StandardCharsets.UTF_16.displayName());
    new PrintStream((new File(getSomePathname())), Charset.defaultCharset());
    new PrintWriter((new File(getSomePathname())), StandardCharsets.UTF_16.displayName());
    new PrintWriter((new File(getSomePathname())), StandardCharsets.UTF_16.displayName());
    new Formatter((new File(getSomePathname())), StandardCharsets.UTF_16.displayName());
    new Formatter((new File(getSomePathname())), StandardCharsets.UTF_16.displayName(), Locale.ENGLISH);
    new Formatter((new File(getSomePathname())), Charset.defaultCharset(), Locale.GERMAN);
  }
}
