// "Replace with ThreadLocal.withInitial()" "true"
public class Main {
  ThreadLocal<? extends CharSequence> tlr = new Th<caret>readLocal<String>() {
    // comment
    @Override
    protected String initialValue() {
      return "initial";
    }
  };
}