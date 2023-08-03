package com.siyeh.igtest.initialization;

public class InstanceVariableUnitInitializedUse{
    private int foo;

    {
        foo++;
    }

    public InstanceVariableUnitInitializedUse()
    {
        System.out.println(foo);
        foo = 3;
        System.out.println(foo);
    }

}

class AClass {
  int aField;

  AClass() {
    aField = 31;
  }

  AClass(AClass a) {
    aField = a.aField;
  }
}
