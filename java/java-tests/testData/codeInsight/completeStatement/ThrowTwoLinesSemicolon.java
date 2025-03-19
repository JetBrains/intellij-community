
class Scratch {
  public static void main(String[] args) {
    throw
      new <caret>RuntimeException("aaa")
  }
}