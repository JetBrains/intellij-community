public class Subject {
  private int myInt;

  public void wp(int <caret>p) {
    myInt += p;
  }
}

class User {
  public static final int OUR_CONST = 2;
  public static final int OUR_CONST2 = 2;

  private void oper() {
    Subject subj = new Subject();
    subj.wp(OUR_CONST);
    subj.wp(OUR_CONST2);
  }
}
