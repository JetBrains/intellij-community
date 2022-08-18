// "Replace call with method body" "true-preview"

import java.util.function.IntSupplier;

public class Main {
  public void call() {
    switch((new IntSupplier() {public int getAsInt() { return 0;}}.getA<caret>sInt())) {}
  }
}
