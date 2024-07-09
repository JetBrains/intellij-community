package com.siyeh.igtest.inheritance.abstract_class_never_implemented;

public abstract class <warning descr="Abstract class 'AbstractClassNeverImplemented' has no concrete subclass">AbstractClassNeverImplemented</warning> {}
abstract class AbstractClassWithOnlyOneDirectInheritor {}
class Inheritor extends AbstractClassWithOnlyOneDirectInheritor {}
abstract class AbstractClassWithTwoInheritors {}
class Inheritor1 extends AbstractClassWithTwoInheritors {}
class Inheritor2 extends AbstractClassWithTwoInheritors {}

@Deprecated
abstract class DeprecatedAbstractClass {}