package com.siyeh.igtest.j2me.abstract_class_with_only_one_direct_inheritor;

public abstract class <warning descr="Abstract class 'AbstractClassWithOnlyOneDirectInheritor' has only one direct inheritor">AbstractClassWithOnlyOneDirectInheritor</warning> {}
class Inheritor extends AbstractClassWithOnlyOneDirectInheritor {}
abstract class AbstractClassWithoutInheritor {}
abstract class AbstractClassWithTwoInheritors {}
class Inheritor1 extends AbstractClassWithTwoInheritors {}
class Inheritor2 extends AbstractClassWithTwoInheritors {}