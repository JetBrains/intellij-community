class UnnecessaryLocalVariable {
  void m() throws Exception {
    String <warning descr="Local variable 's1' is redundant">s1</warning> = null;
    String <warning descr="Local variable 's2' is redundant">s2</warning> = s1, s3 = null;
    System.out.println(s2 + s3);

    AutoCloseable <warning descr="Local variable 'r1' is redundant">r1</warning> = null;
    try (AutoCloseable r2 = r1; AutoCloseable r3 = null) {
      System.out.println(r2.toString() + r3.toString());
    }
  }

  void n() throws Exception {
    String s1 = null;
    String <warning descr="Local variable 's2' is redundant">s2</warning> = s1, <warning descr="Local variable 's3' is redundant">s3</warning> = s1;
    System.out.println(s2 + s3);

    AutoCloseable r1 = null;
    try (AutoCloseable r2 = r1; AutoCloseable r3 = r1) {
      System.out.println(<error descr="Operator '+' cannot be applied to 'java.lang.AutoCloseable', 'java.lang.AutoCloseable'">r2 + r3</error>);
    }
  }

  int boxing(Long l) {
    long ll = l;
    return (int) ll;
  }

  public int foo() {
    int <warning descr="Local variable 'a' is redundant">a</warning> = 2;
    int <warning descr="Local variable 'b' is redundant">b</warning> = a;
    return b;
  }

  public int bar() {
    int <warning descr="Local variable 'b' is redundant">b</warning> = 3;
    return b;
  }

  public int bar2() throws Exception{
    final Exception <warning descr="Local variable 'b' is redundant">b</warning> = new Exception();
    throw b;
  }

  public int baz() {
    int a;
    int <warning descr="Local variable 'b' is redundant">b</warning> = 3;
    a = b;
    return a;
  }

  public int bazoom() {
    final int i = foo();
    bar();
    final int <warning descr="Local variable 'value' is redundant">value</warning> = i;
    System.out.println(value);
    return 3;
  }

  double time() {
    double time = 0.0, dt = time - 1.0;
    System.out.println(time);
    return dt;
  }

  double time2() {
    double time = 0.0, dt = time - 1.0;
    return time;
  }

  void time3() {
    double time =  0.0, dt = time - 1.0;
    double time2 = time;
    time2 += 1;
  }

  void through() throws Exception {
    Exception e2 = instance(), e3 = new RuntimeException(e2);
    throw e2;
  }

  Exception instance() {
    return null;
  }

  public void neededResourceVariable(java.io.InputStream in) throws java.io.IOException {
    try (java.io.InputStream inn = in) {
      final int read = inn.read();
      // do stuff with in
    }
  }

  int parenthesized() {
    final int  <warning descr="Local variable 'i' is redundant">i</warning> = 1 + 2;
    return (i);
  }

  void parenthesized2() {
    final RuntimeException  <warning descr="Local variable 't' is redundant">t</warning> = new RuntimeException();
    throw (t);
  }

  void parenthesized3(int i) {
    int j = (i);
  }

  void unusedString() {
    String str = "i";
  }

  void parenthesized4(int k) {
    final int <warning descr="Local variable 'j' is redundant">j</warning> = 1;
    k = (j);
  }

  void parenthesized5() {
    final int <warning descr="Local variable 'j' is redundant">j</warning> = 1;
    int <warning descr="Local variable 'k' is redundant">k</warning> = (j);
    System.out.println(k);
  }

  void usedIn8Inner(int j) {
    for (int i = 0; i < 7; i++) {
      int k = i;
      int <warning descr="Local variable 'n' is redundant">n</warning> = j;
      class F {
        {
          System.out.println(k + n);
        }
      }
    }
  }

  void nameShadow(final String name) {
    final String child = name;
    class A {
      void foo(String s){}
    }
    
    A a = new A() {
      void foo(String name) {
        System.out.println(child);
      }
    };
  }

  abstract class AbstractSchedulingElement<T extends AbstractSchedulingElement<T>>  {
    private String scenarioSettings;
    public String scenarioSettings1;

    public T copyInto(T target) {
      AbstractSchedulingElement<T> targetAsAbstractSchedulingElement = target;
      targetAsAbstractSchedulingElement.scenarioSettings = scenarioSettings;
      return target;
    }
    
    public T copyInto1(T target) {
      AbstractSchedulingElement<T> <warning descr="Local variable 'targetAsAbstractSchedulingElement' is redundant">targetAsAbstractSchedulingElement</warning> = target;
      targetAsAbstractSchedulingElement.scenarioSettings1 = scenarioSettings1;
      return target;
    }

    public SchedulingElement copyInto2(SchedulingElement target) {
      AbstractSchedulingElement<SchedulingElement> targetAsAbstractSchedulingElement = target;
      targetAsAbstractSchedulingElement.scenarioSettings = scenarioSettings;
      return target;
    }
    
    public SchedulingElement copyInto3(SchedulingElement target) {
      AbstractSchedulingElement<SchedulingElement> <warning descr="Local variable 'targetAsAbstractSchedulingElement' is redundant">targetAsAbstractSchedulingElement</warning> = target;
      targetAsAbstractSchedulingElement.scenarioSettings1 = scenarioSettings1;
      return target;
    }
  }

  class SchedulingElement extends AbstractSchedulingElement<SchedulingElement>{}
}
class UsingConstant {

  final int YES = 1;

  void foo() {
    int <warning descr="Local variable 'yes' is redundant">yes</warning> = YES;
    System.out.println(yes);
  }

  void bar() {
    int yes = YES;
    yes++;
  }
}

class Test {
  final int x = (int)(Math.random() * 10);

  int test() {
    Test t = new Test();
    int <warning descr="Local variable 'xx' is redundant">xx</warning> = this.x;
    t = null;
    return xx;
  }

  int test2() {
    Test t = new Test();
    int xx = t.x;
    t = null;
    return xx;
  }

  int test(Test[] arr) {
    // copying of the field from array element is conservatively not considered safe to inline as array may change
    int res = arr[0].x;
    arr[0] = null;
    return res;
  }
}
class FooBar {
  private final long myVar = 0;
  void bar() {
    final long local_var = myVar;    // local to prevent capture of this
    Runnable r = () -> doSmth(local_var);
  }

  private static void doSmth(long var) {}
}
abstract class RedundantLocalInFor {

  void iterate() {
    for (RedundantLocalInFor child = this, parent; (parent = child.getParent()) != null; child = parent) {
      System.out.println("q");
    }
  }

  protected abstract RedundantLocalInFor getParent();

  void incompleteLambda() {
    <error descr="Cannot infer type: lambda expression requires an explicit target type">var</error> z = s -> {
      final Object s1  = s;
      return x(s1) ;
    };
  }

  boolean x(Object o) {
    return false;
  }
}
