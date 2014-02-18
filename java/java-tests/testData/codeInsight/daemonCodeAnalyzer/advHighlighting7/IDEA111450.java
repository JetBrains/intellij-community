import java.lang.Character;
import java.util.List;

class Test {
  public static void test() {
    Character c1 = '1';
    Character c2 = '2';
    Object o = '3';

    // A cast on either operand is required to force a primitive comparison; not redundant.
    System.out.println((char) c1 == c2);
    System.out.println(c1 == (char) c2);

    // If one operand is a primitive, and the other is a wrapper, the wrapper need not be cast.
    System.out.println((<warning descr="Casting 'c1' to 'char' is redundant">char</warning>) c1 == '*');
    System.out.println('*' == (<warning descr="Casting 'c1' to 'char' is redundant">char</warning>) c1);

    // The cast on the Object is required to force a primitive comparison; not redundant.
    System.out.println((char) o == '*');
    System.out.println('*' == (char) o);

    // The cast on the Object is required to force a primitive comparison; not redundant.
    System.out.println((Character) o == '*');
    System.out.println('*' == (Character) o);

    // The cast on the Object triggers an implicit unboxing of the wrapper; not redundant.
    System.out.println((char) o == c1);
    System.out.println(c1 == (char) o);

    // A cast on the Object is required for a primitive comparison, but the wrapper cast is redundant.
    System.out.println((char) o == (<warning descr="Casting 'c1' to 'char' is redundant">char</warning>) c1);
    System.out.println((<warning descr="Casting 'c1' to 'char' is redundant">char</warning>) c1 == (char) o);

    // Although a reference comparison, the cast on the wrapper has a side effect; not redundant.
    System.out.println(o == (char) c1);
    System.out.println((char) c1 == o);
  }
}