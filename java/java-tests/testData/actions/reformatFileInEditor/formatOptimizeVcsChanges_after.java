import java.util.LinkedHashSet;
import java.util.Set;

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