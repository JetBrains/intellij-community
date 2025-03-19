package com.siyeh.igtest.classlayout.noop_method_in_abstract_class;

abstract class NoopMethodInAbstractClass {

  void <warning descr="No-op Method 'foo()' should be made abstract">foo</warning>() {}

  native int bar();

  final void noop() {}
}