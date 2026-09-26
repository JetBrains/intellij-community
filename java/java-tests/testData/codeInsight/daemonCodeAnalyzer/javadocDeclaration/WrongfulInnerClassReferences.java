/// [#<error descr="Cannot resolve symbol '#Inner'">Inner</error>]
/// 
/// [Outer#<error descr="Cannot resolve symbol 'Outer#Inner'">Inner</error>]
class Outer {
  class Inner{}
}