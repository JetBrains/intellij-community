import java.util.Set;

@SuppressWarnings("unused")
public class InvalidCast {
  public static void main(String[] args) {

  }

  static class X1 {
  }

  static class X2 extends X1 {
  }

  public void t1(Set<Set<X1>> t) {
    Set<Set<X1>> t2 = (Set<Set<X1>>) t;
  }

  public void t2(Set<Set<X2>> t) {
    Set<Set<X2>> t2 = (Set<Set<X2>>) t;
  }

  public void t3(Set<Set<X1>> t) {
    Set<Set<X2>> t2 = <error descr="Inconvertible types; cannot cast 'java.util.Set<java.util.Set<InvalidCast.X1>>' to 'java.util.Set<java.util.Set<InvalidCast.X2>>'">(Set<Set<X2>>) t</error>; //error
  }

  public void t4(Set<Set<X2>> t) {
    Set<Set<X1>> t2 = <error descr="Inconvertible types; cannot cast 'java.util.Set<java.util.Set<InvalidCast.X2>>' to 'java.util.Set<java.util.Set<InvalidCast.X1>>'">(Set<Set<X1>>) t</error>;  //error
  }

  public void t5(Set<Set<? extends X1>> t) {
    Set<Set<? extends X2>> t2 = <error descr="Inconvertible types; cannot cast 'java.util.Set<java.util.Set<? extends InvalidCast.X1>>' to 'java.util.Set<java.util.Set<? extends InvalidCast.X2>>'">(Set<Set<? extends X2>>) t</error>;  //error
  }

  public void t6(Set<Set<? extends X2>> t) {
    Set<Set<? extends X1>> t2 = <error descr="Inconvertible types; cannot cast 'java.util.Set<java.util.Set<? extends InvalidCast.X2>>' to 'java.util.Set<java.util.Set<? extends InvalidCast.X1>>'">(Set<Set<? extends X1>>) t</error>;  //error
  }

  public void t7(Set<Set<? extends X1>> t) {
    Set<Set<? extends X1>> t2 = (Set<Set<? extends X1>>) t;
  }

  public void t8(Set<Set<? extends X2>> t) {
    Set<Set<? extends X2>> t2 = (Set<Set<? extends X2>>) t;
  }

  public void t9(Set<Set<X1>> t) {
    Set<Set<? extends X2>> t2 = <error descr="Inconvertible types; cannot cast 'java.util.Set<java.util.Set<InvalidCast.X1>>' to 'java.util.Set<java.util.Set<? extends InvalidCast.X2>>'">(Set<Set<? extends X2>>) t</error>;  //error
  }

  public void t10(Set<Set<X2>> t) {
    Set<Set<? extends X1>> t2 = <error descr="Inconvertible types; cannot cast 'java.util.Set<java.util.Set<InvalidCast.X2>>' to 'java.util.Set<java.util.Set<? extends InvalidCast.X1>>'">(Set<Set<? extends X1>>) t</error>;  //error
  }

  public void t11(Set<Set<? extends X1>> t) {
    Set<Set<X2>> t2 = <error descr="Inconvertible types; cannot cast 'java.util.Set<java.util.Set<? extends InvalidCast.X1>>' to 'java.util.Set<java.util.Set<InvalidCast.X2>>'">(Set<Set<X2>>) t</error>;  //error
  }

  public void t12(Set<Set<? extends X2>> t) {
    Set<Set<X1>> t2 = <error descr="Inconvertible types; cannot cast 'java.util.Set<java.util.Set<? extends InvalidCast.X2>>' to 'java.util.Set<java.util.Set<InvalidCast.X1>>'">(Set<Set<X1>>) t</error>;  //error
  }

  public void t13(Set<Set<? extends X1>> t) {
    Set<Set<X1>> t2 = <error descr="Inconvertible types; cannot cast 'java.util.Set<java.util.Set<? extends InvalidCast.X1>>' to 'java.util.Set<java.util.Set<InvalidCast.X1>>'">(Set<Set<X1>>) t</error>;  //error
  }

  public void t14(Set<Set<X1>> t) {
    Set<Set<? extends X1>> t2 = <error descr="Inconvertible types; cannot cast 'java.util.Set<java.util.Set<InvalidCast.X1>>' to 'java.util.Set<java.util.Set<? extends InvalidCast.X1>>'">(Set<Set<? extends X1>>) t</error>;  //error
  }

  public void t15(Set<Set<?>> t) {
    Set<Set<? extends X1>> t2 = <error descr="Inconvertible types; cannot cast 'java.util.Set<java.util.Set<?>>' to 'java.util.Set<java.util.Set<? extends InvalidCast.X1>>'">(Set<Set<? extends X1>>) t</error>;  // error
  }

  public void t16(Set<Set<? extends X1>> t) {
    Set<Set<?>> t2 = <error descr="Inconvertible types; cannot cast 'java.util.Set<java.util.Set<? extends InvalidCast.X1>>' to 'java.util.Set<java.util.Set<?>>'">(Set<Set<?>>)  t</error>;  // error
  }

  public void t17(Set<Set<?>> t) {
    Set<Set<?>> t2 = (Set<Set<?>>) t;
  }

  public void t18(Set<Set<? extends int[]>> t) {
    Set<Set<?>> t2 = <error descr="Inconvertible types; cannot cast 'java.util.Set<java.util.Set<? extends int[]>>' to 'java.util.Set<java.util.Set<?>>'">(Set<Set<?>>)  t</error>;  // error
  }
}
