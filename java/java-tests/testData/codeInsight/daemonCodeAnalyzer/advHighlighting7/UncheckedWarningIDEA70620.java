class UncheckedCastFalsePositive<T> {

  private void <warning descr="Private method 'test(java.lang.Object, java.lang.Object)' is never used">test</warning>( Object one, Object two ) {
    @SuppressWarnings( "unchecked" )
    UncheckedCastFalsePositive<T> outer = ( UncheckedCastFalsePositive<T> ) one;
    System.out.println(outer);

    InnerClass inner = <warning descr="Unchecked cast: 'java.lang.Object' to 'UncheckedCastFalsePositive.InnerClass'">( InnerClass ) two</warning>;
    System.out.println(inner);
  }

  private class InnerClass {}
}


class UncheckedCastFalsePositive1<T> {

  private void <warning descr="Private method 'test(java.lang.Object, java.lang.Object)' is never used">test</warning>( Object one, Object two ) {
    @SuppressWarnings( "unchecked" )
    UncheckedCastFalsePositive1<T> outer = ( UncheckedCastFalsePositive1<T> ) one;
    System.out.println(outer);

    UncheckedCastFalsePositive1<T>.InnerClass inner = <warning descr="Unchecked cast: 'java.lang.Object' to 'UncheckedCastFalsePositive1<T>.InnerClass'">( UncheckedCastFalsePositive1<T>.InnerClass ) two</warning>;
    System.out.println(inner);
  }

  private class InnerClass {}

}