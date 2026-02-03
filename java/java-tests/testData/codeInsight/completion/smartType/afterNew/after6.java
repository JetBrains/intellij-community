class AAA{
  interface XXX<T>{}

  class BBB<Z> implements XXX<Z>{}
  {
    XXX<String[]> aa = new BBB<String[]>();<caret>
  }
}
