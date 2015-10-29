// "Remove 2nd parameter from method 'foo'" "true"
class Test {

  private List<? extends CharSequence> sequences = null;

  {
    foo(sequences.map());
  }

  <K> void foo(K s){}

  interface List<T>  {
    List<? super T> map();
  }
}