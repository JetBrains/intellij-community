// "Create method 'foo'" "true-preview"
class A {
  private List<? extends CharSequence> sequences = null;

  {
    f<caret>oo(sequences.map());
  }

  interface List<T>  {
    List<? super T> map();
  }
}
