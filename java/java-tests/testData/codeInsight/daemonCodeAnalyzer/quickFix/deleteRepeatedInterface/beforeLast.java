// "Delete repeated 'I'" "true-preview"
interface I {}
class Test {
  {
    Object o = (I & Runnable & <caret>I) null;
  }
}