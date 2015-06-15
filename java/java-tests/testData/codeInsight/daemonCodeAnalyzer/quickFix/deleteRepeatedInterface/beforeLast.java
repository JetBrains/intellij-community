// "Delete repeated 'I'" "true"
interface I {}
class Test {
  {
    Object o = (I & Runnable & <caret>I) null;
  }
}