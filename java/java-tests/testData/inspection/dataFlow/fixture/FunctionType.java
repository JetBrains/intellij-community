import java.util.*;
import java.util.function.*;
import java.io.Serializable;

public class FunctionType {
  void test() {
    Runnable r1 = () -> {};
    Runnable r2 = System.out::println;
    Runnable r3 = (Runnable & Serializable) (() -> {});
    Runnable r4 = (Runnable & Serializable) (System.out::println);
    if (<warning descr="Condition 'r1 instanceof Thread' is always 'false'">r1 instanceof Thread</warning>) {}
    if (<warning descr="Condition 'r2 instanceof Thread' is always 'false'">r2 instanceof Thread</warning>) {}
    if (<warning descr="Condition 'r1 instanceof Serializable' is always 'false'">r1 instanceof Serializable</warning>) {}
    if (<warning descr="Condition 'r2 instanceof Serializable' is always 'false'">r2 instanceof Serializable</warning>) {}
    if (<warning descr="Condition 'r3 instanceof Serializable' is always 'true'">r3 instanceof Serializable</warning>) {}
    if (<warning descr="Condition 'r4 instanceof Serializable' is always 'true'">r4 instanceof Serializable</warning>) {}
    // Questionable: not mandated by specification
    if (<warning descr="Condition 'r1.getClass() == r2.getClass()' is always 'false'">r1.getClass() == r2.getClass()</warning>) {}
  }
}