import java.util.List;

class Test {
  public Integer publicMethod() { return <warning descr="'null' is returned by the method which is not declared as @Nullable">null</warning>; }
  public final Integer finalMethod() { return <warning descr="'null' is returned by the method which is not declared as @Nullable">null</warning>; }
  protected Integer protectedMethod() { return <warning descr="'null' is returned by the method which is not declared as @Nullable">null</warning>; }
  Integer packageMethod() { return <warning descr="'null' is returned by the method which is not declared as @Nullable">null</warning>; }
  private Integer privateMethod1() { return 1; }
  private Integer privateMethodNull() { return <warning descr="'null' is returned by the method which is not declared as @Nullable">null</warning>; }
  private static Integer staticMethod() { return <warning descr="'null' is returned by the method which is not declared as @Nullable">null</warning>; }

  {
    var foo = List.<Runnable>of(
      this::publicMethod,
      this::finalMethod,
      this::protectedMethod,
      this::packageMethod,
      this::privateMethod1,
      this::privateMethodNull,
      Test::staticMethod
    );
  }
}