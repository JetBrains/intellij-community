import java.util.List;

class Test {
  public static void foo(List<String> args, int i) {
      newMethod("hi");
      newMethod(args.get(i));
  }

    private static void newMethod(String hi) {
        System.out.println(hi);
    }
}