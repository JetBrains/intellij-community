class Outer {
  class User {
    public class Subject {
      public void withClass(Object <caret>o) {
        System.out.println(o.toString());
      }
    }

    private void oper() {
      Subject subj = new Subject();
      subj.withClass(Outer.this);
    }
  }
}
