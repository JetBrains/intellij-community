// "Convert to atomic" "true"
class X {
  private static int co<caret>unt = 0; // convert me
  private final int index;

  X() {
    count++;
    index = count;
  }
}