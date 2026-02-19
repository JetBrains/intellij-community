// "Replace method reference with lambda" "true-preview"
import java.util.*;

public class MyTest {
  static {
    Runnable r = int[]::n<caret>otify;
  }
}
