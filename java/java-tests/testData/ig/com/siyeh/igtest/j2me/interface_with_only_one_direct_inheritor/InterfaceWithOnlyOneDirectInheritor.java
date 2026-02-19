package com.siyeh.igtest.j2me.interface_with_only_one_direct_inheritor;

public interface <warning descr="Interface 'InterfaceWithOnlyOneDirectInheritor' has only one direct inheritor">InterfaceWithOnlyOneDirectInheritor</warning> {}
class Inheritor implements InterfaceWithOnlyOneDirectInheritor {}
interface InterfaceWithoutInheritor {}
interface InterfaceWithTwoInheritors {}
class Inheritor1 implements InterfaceWithTwoInheritors {}
class Inheritor2 implements InterfaceWithTwoInheritors {}