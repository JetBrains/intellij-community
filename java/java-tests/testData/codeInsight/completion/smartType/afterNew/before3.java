interface XXX<T>{}

class BBB<X> implements XXX<X>{}

class AAA{
  {
    XXX aa = new B<caret>
  }
}
