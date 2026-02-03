class AAA{
  interface XXX<T>{}
  interface ZZZ<T>{}
  class BBB<Z> implements XXX<ZZZ<Z>>{}
  {
    XXX<ZZZ<String>> aa = new B<caret>
  }
}
