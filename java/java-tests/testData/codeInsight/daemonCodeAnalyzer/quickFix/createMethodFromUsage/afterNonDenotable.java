// "Create method 'foo'" "true"
class A {
  private List<? extends CharSequence> sequences = null;

  {
    foo(sequences.map());
  }

    private void foo(List<?> map) {
        
    }

    interface List<T>  {
    List<? super T> map();
  }
}
