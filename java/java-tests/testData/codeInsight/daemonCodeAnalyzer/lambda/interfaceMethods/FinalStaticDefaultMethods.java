interface A {
  final <error descr="Illegal combination of modifiers: 'static' and 'final'">static</error> void foo() {}
  final <error descr="Illegal combination of modifiers: 'default' and 'final'">default</error> void foo() {}
}
