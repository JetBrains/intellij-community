import java.lang.Override;
import java.lang.Runnable;
import java.lang.String;
import java.util.Set;
import java.util.HashSet;
import java.util.LinkedHashSet;

public class Main {


  public static void main(String[] args) {
    Runnable runnable = new Runnable() {
        @Override
        public void run() {
            Set<String> test = new LinkedHashSet<String>();
            if (test.contains("AA")) {
              if (test.contains("AS")) {
                System.out.println("AAAA!");
              }
            }
        }
    };

    runnable.run();
  }

}