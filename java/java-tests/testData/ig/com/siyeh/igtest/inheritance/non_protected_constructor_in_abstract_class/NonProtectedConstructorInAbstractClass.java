package com.siyeh.igtest.classlayout;

public abstract class NonProtectedConstructorInAbstractClass
{
  public <warning descr="Constructor 'NonProtectedConstructorInAbstractClass()' of an abstract class should not be declared 'public'">NonProtectedConstructorInAbstractClass</warning>()
    {
    }
    private NonProtectedConstructorInAbstractClass(int foo)
    {
    }

    public <error descr="Identifier expected">void</error>();
}
