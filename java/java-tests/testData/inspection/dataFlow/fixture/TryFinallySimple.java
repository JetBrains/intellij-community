// IDEA-191876
import org.jetbrains.annotations.*;

class Test {
  public static void runXyz() {
    String output = null;

    try {
      Runnable shutdownHook = () -> { };

      try {
        output = "foo";
      }
      finally {
        Runtime.getRuntime().addShutdownHook(new Thread(shutdownHook));
        shutdownHook.run();
      }
    }
    finally {
      System.out.println(<warning descr="Condition 'output != null' is always 'true'">output != null</warning> ? output.trim() : "");
    }
    System.out.println(output.trim());
  }

  @NotNull
  public static String getString() {
    return "foo";
  }

  public static void test() {
    String s = null;
    try {
      s = getString();
    }
    finally {
      System.out.println(s == null ? null : s.trim());
    }
    System.out.println(s.trim());
  }

  public static void test2(@Nullable String s) {
    if (s == null) return;

    try {
      System.out.println("Hello");
    }
    finally {
      System.out.println(s.trim());
    }
  }
}