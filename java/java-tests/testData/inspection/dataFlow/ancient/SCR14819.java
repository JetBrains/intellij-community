public class Test {
  public Test foo(Test t) {
     if (<warning descr="Condition 't instanceof Test' is redundant and can be replaced with a null check">t instanceof Test</warning>) { // redundant instanceof error here. t can be null
       foo(null);
     }
     return null;
  }
  public Object bar(Test t) {
     if (t == null) <error descr="Missing return value">return;</error>
     if (<warning descr="Condition 't instanceof Test' is always 'true'">t instanceof Test</warning>) { // always true error here. t can't be null
       foo(null);
     }

     if (bar(null) instanceof Test) return null;  // no error here.
     if (<warning descr="Condition 'foo(null) instanceof Test' is redundant and can be replaced with a null check">foo(null) instanceof Test</warning>) return null;  // redundant instanceof error here. foo(null) can be null
     return null;
  }
}