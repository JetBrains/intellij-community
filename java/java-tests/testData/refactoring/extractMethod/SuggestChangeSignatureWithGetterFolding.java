import java.util.List;

class Test {
  public static void foo(List<String> args, int i) {
    <selection>System.out.println("hi");</selection>
    System.out.println(args.get(i));
  }
}