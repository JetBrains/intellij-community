package com.siyeh.igtest.performance;

import java.util.ArrayList;
import java.util.List;

public class CallToSimpleGetterInClassInspection {
    private int intVal;
    private ArrayList listVal;

    public int getIntVal() {
        return this.intVal;
    }

    public int returnIntVal()
    {
        final int val = getIntVal();
        final CallToSimpleGetterInClassInspection foo = new CallToSimpleGetterInClassInspection();
        final int val2 = foo.getIntVal();
        return val;
    }

    public List getListVal() {
        return listVal;
    }

    public List returnListVal()
    {
        return getListVal();
    }

}
