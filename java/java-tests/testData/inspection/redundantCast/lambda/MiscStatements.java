
import java.util.List;

class MyTest {

  void m() {
    assert (<warning descr="Casting '(boolean)(boolean)(new Object() != null)' to 'boolean' is redundant">boolean</warning>)(<warning descr="Casting '(boolean)(new Object() != null)' to 'boolean' is redundant">boolean</warning>)(<warning descr="Casting '(new Object() != null)' to 'boolean' is redundant">boolean</warning>) (new Object() != null) : (<warning descr="Casting '(CharSequence)(String)\"message\"' to 'String' is redundant">String</warning>)(<warning descr="Casting '(String)\"message\"' to 'CharSequence' is redundant">CharSequence</warning>)(<warning descr="Casting '\"message\"' to 'String' is redundant">String</warning>)"message";
    if ((<warning descr="Casting '(boolean)(boolean)(1 != 2)' to 'boolean' is redundant">boolean</warning>)(<warning descr="Casting '(boolean)(1 != 2)' to 'boolean' is redundant">boolean</warning>)(<warning descr="Casting '(1 != 2)' to 'boolean' is redundant">boolean</warning>) (1 != 2)) {
      for(String string : ((<warning descr="Casting 'new String[] {...}' to 'String[]' is redundant">String[]</warning>)new String[] {"a", "b", (<warning descr="Casting '\"c\"' to 'String' is redundant">String</warning>)"c"})) {
        do {
          while ((<warning descr="Casting '(boolean)(boolean)(1 != 2)' to 'boolean' is redundant">boolean</warning>)(<warning descr="Casting '(boolean)(1 != 2)' to 'boolean' is redundant">boolean</warning>)(<warning descr="Casting '(1 != 2)' to 'boolean' is redundant">boolean</warning>) (1 != 2)) {
            <error descr="Not a statement">(<warning descr="Casting 'MyTest.class.toString()' to 'String' is redundant">String</warning>)MyTest.class.toString();</error>
          }
        } while ((<warning descr="Casting '(boolean)(boolean)(1 != 2)' to 'boolean' is redundant">boolean</warning>)(<warning descr="Casting '(boolean)(1 != 2)' to 'boolean' is redundant">boolean</warning>)(<warning descr="Casting '(1 != 2)' to 'boolean' is redundant">boolean</warning>) (1 != 2));
      }
    }
    CharSequence sequence = "null";
    I ii = () -> (<warning descr="Casting 'sequence' to 'String' is redundant">String</warning>) sequence;
  }

  interface I {
    CharSequence foo();
  }
}
