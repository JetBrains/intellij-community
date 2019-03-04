// "Replace with reduce()" "false"
public class Foo {

  boolean isAnyFalse(boolean[] array) {
    boolean status = true;

    f<caret>or (int i = 0; i < array.length & status; i++) {
      status &= array[i];
    }

    return status;
  }
}
