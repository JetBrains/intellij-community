// "Delete repeated 'I'" "true"
interface I {}
class Test {
  {
    Object o = (I & Runnable) null;
  }
}