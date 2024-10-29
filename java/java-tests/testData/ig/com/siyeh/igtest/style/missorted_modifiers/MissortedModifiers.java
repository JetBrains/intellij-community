import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.annotation.ElementType;
import java.lang.annotation.Target;
import java.util.Hashtable;


class MissortedModifiers {
  private <warning descr="Missorted modifiers 'native static'">native</warning> static int foo2();

  <warning descr="Missorted modifiers 'static private'">static</warning> private int m_bar = 4;
  <warning descr="Missorted modifiers 'static public'">static</warning> public int m_baz = 4;
  <warning descr="Missorted modifiers 'static final public'">static</warning> final public int m_baz2 = 4;
  static final int m_baz3 = 4;

  <warning descr="Missorted modifiers 'static public'">static</warning> public void foo(){}

  <warning descr="Missorted modifiers 'static public'">static</warning> public class Foo
  {

  }

  <warning descr="Missorted modifiers 'public @Deprecated'">public</warning> @Deprecated void foo3(){};
  private @ReadOnly int [] nums;

  private <warning descr="Missorted modifiers 'transient static'">transient</warning> static Hashtable mAttributeMeta;

  interface A {

    <warning descr="Missorted modifiers 'default public'">default</warning> public double f() {
      return 0.0;
    }
  }

  <warning descr="Missorted modifiers 'final public'">final</warning> public class TestQuickFix
  {
    protected <warning descr="Missorted modifiers 'final static'">final</warning> static String A = "a";
    protected <warning descr="Missorted modifiers 'final static'">final</warning> static String B = "b";
    protected <warning descr="Missorted modifiers 'final static'">final</warning> static String C = "c";
    protected <warning descr="Missorted modifiers 'final static'">final</warning> static String D = "d";
  }

  //@Type(type = "org.joda.time.contrib.hibernate.PersistentYearMonthDay")
  //@Column(name = "current_month")
  <warning descr="Missorted modifiers 'final public'">final</warning>
  public
  @Nullable
  // commment
  @NotNull
  int //@Temporal(TemporalType.DATE)
  x() {return -1;}


  <warning descr="Missorted modifiers 'public static @MethodOrTypeAnnotation'">public</warning> static @MethodOrTypeAnnotation void runAwayTrain() {
    // ...
  }
}
@Target(ElementType.TYPE_USE)
@interface ReadOnly {}
@Target({ElementType.METHOD, ElementType.TYPE_USE})
@interface MethodOrTypeAnnotation {
  // empty
}
class TestTarget {
  @TestAnnotation1 private final String foo;
  @TestAnnotation2 private final String bar;
  @TestAnnotation3 private final String baz;

  public TestTarget(String foo, String bar, String baz) {
    this.foo = foo;
    this.bar = bar;
    this.baz = baz;
  }

  public String getFoo() {
    return foo;
  }

  public String getBar() {
    return bar;
  }

  public String getBaz() {
    return baz;
  }

  @Target(ElementType.FIELD)
  public @interface TestAnnotation1 {
  }

  @Target({ElementType.FIELD, ElementType.TYPE_USE})
  public @interface TestAnnotation2 {
  }

  @Target({ElementType.TYPE_USE, ElementType.FIELD})
  public @interface TestAnnotation3 {
  }
}