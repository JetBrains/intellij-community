public class Test {
  public Test foo(Test t) {
     if (t instanceof Test) { // redundant instanceof error here. t can be null
       foo(null);
     }
  }
  public Object bar(Test t) {
     if (t == null) return;
     if (t instanceof Test) { // always true error here. t can't be null
       foo(null);
     }

     if (bar(null) instanceof Test) return null;  // no error here.
     if (foo(null) instanceof Test) return null;  // redundant instanceof error here. foo(null) can be null
     return null;
  }
}