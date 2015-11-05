// "Remove 2nd parameter from method 'foo'" "true"
class Test {

  private List<? extends CharSequence> sequences = null;

  {
    foo(se<caret>quences.map());
  }

  <K> void foo(K s, Object o){}

  interface List<T>  {
    List<? super T> map();
  }
}