public class ExpData {

  private Object provideObject() {
    return new Object();
  }

  public static void useStatic(Object p) {
    System.out.println(p);
  }

  public void context() {
    inlineE(new DD());
  }

  public static void inlineE(Object <caret>subj) {
    useStatic(subj);
  }

  class DD {}
}
