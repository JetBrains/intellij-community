package com.siyeh.igtest.performance;

public class InstantiatingObjectToGetClassObjectInspection {
    public void foo()
    {
        new Integer(3).getClass();
    }
}
