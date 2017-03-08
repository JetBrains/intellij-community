class Outer {
  class Inner extends Outer {}
}

class Impl extends <error descr="No enclosing instance of type 'Outer' is in scope">Outer.Inner</error> {}