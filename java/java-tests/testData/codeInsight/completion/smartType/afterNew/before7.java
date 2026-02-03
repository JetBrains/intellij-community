class AAA{
  interface XXX<T, T1>{}

  class BBB<Z, Z1> implements XXX<Z, Z1>{}
  {
    XXX<String,String[]> aa = new B<caret>
  }
}
