package com.siyeh.igtest.cloneable.clone_declares_clone_not_supported;

public class CloneDeclaresCloneNonSupportedException implements Cloneable
{
    
    public void foo()
    {
        
    }
    
    protected Object <warning descr="'clone()' does not declare 'CloneNotSupportedException'">clone</warning>()
    {
        try
        {
            return super.clone();
        }
        catch(CloneNotSupportedException e)
        {
            return null;
        }
    }
}
class Normal implements Cloneable {

  @Override
  protected Object clone() throws CloneNotSupportedException {
    return super.clone();
  }
}
class Child extends CloneDeclaresCloneNonSupportedException {

  @Override
  public Object clone() {
    return super.clone();
  }
}
class NoWarnOnPublic implements Cloneable {

  public Object clone() {
    try {
      return super.clone();
    } catch (CloneNotSupportedException e) {
      throw new AssertionError(e);
    }
  }
}
