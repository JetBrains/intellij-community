// "Replace with expression lambda" "true-preview"

import java.io.File;
import java.util.function.Supplier;

class Test {

  {
    runWriteCommandAction(null, () -> System.out.println());
  }


  public static void runWriteCommandAction(String project, final Runnable runnable) {}
  public static <T> void runWriteCommandAction(String project, final Supplier<T> runnable) {}
  public static void runWriteCommandAction(String project, final String commandName, final String groupID, final Runnable runnable, File... files) {}
}