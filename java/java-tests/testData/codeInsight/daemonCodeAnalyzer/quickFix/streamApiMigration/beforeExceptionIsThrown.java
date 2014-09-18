// "Replace with collect" "false"
import java.util.*;

public class Test {
  public void foo() throws Exception {}

  public static void bar(List<Test> currentImportLine) throws Exception {
    for (Test test : currentImp<caret>ortLine) {
      if (true) {
        test.foo();
      }
    }
  }
}
