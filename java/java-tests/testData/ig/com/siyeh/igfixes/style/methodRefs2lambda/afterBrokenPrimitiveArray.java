// "Replace method reference with lambda" "true-preview"
import java.util.*;

public class MyTest {
  static {
    Runnable r = () -> no<caret>tify();
  }
}
