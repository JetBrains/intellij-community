// "Delete repeated 'I'" "true-preview"
interface I {}
class Test {
  {
    Object o = (I & <caret>I & Runnable) null;
  }
}