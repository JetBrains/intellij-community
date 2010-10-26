package zzzzz;

public class Holder {
  {
    Foo foo = null;
    if (foo instanceof <caret>)
  }

}

interface Foo {}
interface IFoo extends Foo {}
abstract class Goo implements IFoo {}
class Bar extends Goo {}