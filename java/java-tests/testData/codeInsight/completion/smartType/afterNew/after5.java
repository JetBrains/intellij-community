class AAA{
  interface XXX<T>{}

  class BBB<Z> implements XXX<Z>{}
  {
    XXX aa = new BBB();<caret>
  }
}
