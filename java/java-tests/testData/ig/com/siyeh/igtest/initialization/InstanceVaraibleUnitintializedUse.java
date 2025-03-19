package com.siyeh.igtest.initialization;

public class InstanceVaraibleUnitintializedUse{
    private int foo;

    {
        foo++;
    }

    public InstanceVaraibleUnitintializedUse()
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

