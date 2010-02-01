public class Subject {
  private int myInt;

  public void wp(int <caret>p) {
    myInt += p;
  }
}

class User {
  public static int ourStatic = 0;
  public static final int OUR_CONST = 0;

  private void oper() {
    Subject subj = new Subject();
    subj.wp(OUR_CONST);
    subj.wp(ourStatic);
  }
}
