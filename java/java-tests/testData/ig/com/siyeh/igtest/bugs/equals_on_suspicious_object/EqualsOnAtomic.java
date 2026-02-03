import java.util.Objects;
import java.util.concurrent.atomic.*;

public class EqualsOnAtomic {

  public void testString(String sb1, String sb2) {
    if (!sb1.equals(sb2)) {
      System.out.println("Strange");
    }
  }
  public void testAtomicBoolean(AtomicBoolean a1, AtomicBoolean a2) {
    if(a1.<warning descr="Suspicious call to 'equals()' on 'AtomicBoolean' object">equals</warning>(a2)) {
      System.out.println("Strange");
    }
    if(Objects.<warning descr="Suspicious call to 'equals()' on 'AtomicBoolean' object">equals</warning>(a1, a2)) {
      System.out.println("Strange");
    }
    if(java.util.function.Predicate.<warning descr="Suspicious call to 'equals()' on 'AtomicBoolean' object">isEqual</warning>(a1).test(a2)) {
      System.out.println("Strange");
    }
  }
  public void testAtomicInteger(AtomicInteger a1, AtomicInteger a2) {
    if(a1.<warning descr="Suspicious call to 'equals()' on 'AtomicInteger' object">equals</warning>(a2)) {
      System.out.println("Strange");
    }
    if(Objects.<warning descr="Suspicious call to 'equals()' on 'AtomicInteger' object">equals</warning>(a1, a2)) {
      System.out.println("Strange");
    }
    if(java.util.function.Predicate.<warning descr="Suspicious call to 'equals()' on 'AtomicInteger' object">isEqual</warning>(a1).test(a2)) {
      System.out.println("Strange");
    }
  }
  public void testAtomicIntegerArray(AtomicIntegerArray a1, AtomicIntegerArray a2) {
    if(a1.<warning descr="Suspicious call to 'equals()' on 'AtomicIntegerArray' object">equals</warning>(a2)) {
      System.out.println("Strange");
    }
    if(Objects.<warning descr="Suspicious call to 'equals()' on 'AtomicIntegerArray' object">equals</warning>(a1, a2)) {
      System.out.println("Strange");
    }
    if(java.util.function.Predicate.<warning descr="Suspicious call to 'equals()' on 'AtomicIntegerArray' object">isEqual</warning>(a1).test(a2)) {
      System.out.println("Strange");
    }
  }
  public void testAtomicLong(AtomicLong a1, AtomicLong a2) {
    if(a1.<warning descr="Suspicious call to 'equals()' on 'AtomicLong' object">equals</warning>(a2)) {
      System.out.println("Strange");
    }
    if(Objects.<warning descr="Suspicious call to 'equals()' on 'AtomicLong' object">equals</warning>(a1, a2)) {
      System.out.println("Strange");
    }
    if(java.util.function.Predicate.<warning descr="Suspicious call to 'equals()' on 'AtomicLong' object">isEqual</warning>(a1).test(a2)) {
      System.out.println("Strange");
    }
  }
  public void testAtomicLongArray(AtomicLongArray a1, AtomicLongArray a2) {
    if(a1.<warning descr="Suspicious call to 'equals()' on 'AtomicLongArray' object">equals</warning>(a2)) {
      System.out.println("Strange");
    }
    if(Objects.<warning descr="Suspicious call to 'equals()' on 'AtomicLongArray' object">equals</warning>(a1, a2)) {
      System.out.println("Strange");
    }
    if(java.util.function.Predicate.<warning descr="Suspicious call to 'equals()' on 'AtomicLongArray' object">isEqual</warning>(a1).test(a2)) {
      System.out.println("Strange");
    }
  }
  public void testAtomicReference(AtomicReference<Integer> a1, AtomicReference<Integer> a2) {
    if(a1.<warning descr="Suspicious call to 'equals()' on 'AtomicReference' object">equals</warning>(a2)) {
      System.out.println("Strange");
    }
    if(Objects.<warning descr="Suspicious call to 'equals()' on 'AtomicReference' object">equals</warning>(a1, a2)) {
      System.out.println("Strange");
    }
    if(java.util.function.Predicate.<warning descr="Suspicious call to 'equals()' on 'AtomicReference' object">isEqual</warning>(a1).test(a2)) {
      System.out.println("Strange");
    }
  }
  public void testDoubleAccumulator(DoubleAccumulator a1, DoubleAccumulator a2) {
    if(a1.<warning descr="Suspicious call to 'equals()' on 'DoubleAccumulator' object">equals</warning>(a2)) {
      System.out.println("Strange");
    }
    if(Objects.<warning descr="Suspicious call to 'equals()' on 'DoubleAccumulator' object">equals</warning>(a1, a2)) {
      System.out.println("Strange");
    }
    if(java.util.function.Predicate.<warning descr="Suspicious call to 'equals()' on 'DoubleAccumulator' object">isEqual</warning>(a1).test(a2)) {
      System.out.println("Strange");
    }
  }
  public void testDoubleAdder(DoubleAdder a1, DoubleAdder a2) {
    if(a1.<warning descr="Suspicious call to 'equals()' on 'DoubleAdder' object">equals</warning>(a2)) {
      System.out.println("Strange");
    }
    if(Objects.<warning descr="Suspicious call to 'equals()' on 'DoubleAdder' object">equals</warning>(a1, a2)) {
      System.out.println("Strange");
    }
    if(java.util.function.Predicate.<warning descr="Suspicious call to 'equals()' on 'DoubleAdder' object">isEqual</warning>(a1).test(a2)) {
      System.out.println("Strange");
    }
  }
  public void testLongAccumulator(LongAccumulator a1, LongAccumulator a2) {
    if(a1.<warning descr="Suspicious call to 'equals()' on 'LongAccumulator' object">equals</warning>(a2)) {
      System.out.println("Strange");
    }
    if(Objects.<warning descr="Suspicious call to 'equals()' on 'LongAccumulator' object">equals</warning>(a1, a2)) {
      System.out.println("Strange");
    }
    if(java.util.function.Predicate.<warning descr="Suspicious call to 'equals()' on 'LongAccumulator' object">isEqual</warning>(a1).test(a2)) {
      System.out.println("Strange");
    }
  }
  public void testLongAdder(LongAdder a1, LongAdder a2) {
    if(a1.<warning descr="Suspicious call to 'equals()' on 'LongAdder' object">equals</warning>(a2)) {
      System.out.println("Strange");
    }
    if(Objects.<warning descr="Suspicious call to 'equals()' on 'LongAdder' object">equals</warning>(a1, a2)) {
      System.out.println("Strange");
    }
    if(java.util.function.Predicate.<warning descr="Suspicious call to 'equals()' on 'LongAdder' object">isEqual</warning>(a1).test(a2)) {
      System.out.println("Strange");
    }
  }

  public static void testNotError() {
    boolean equals = new Int() {
    }.equals(new Int() {

    });
  }


  interface Int{}
}