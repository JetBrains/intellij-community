// "Delete repeated 'I'" "true"
interface I {}
class Test {
  {
    Object o = (I & <caret>I & Runnable) null;
  }
}