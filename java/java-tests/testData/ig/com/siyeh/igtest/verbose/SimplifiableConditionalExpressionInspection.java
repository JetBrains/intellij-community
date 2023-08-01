package com.siyeh.igtest.verbose;

public class SimplifiableConditionalExpressionInspection {
    public void foo()
    {
        boolean a = bar();
        boolean b = bar();
        final boolean i = a ? b : true;
        final boolean j = a ? true : b;
        final boolean k = a ? b : false;
        final boolean l = a ? false : b;
    }

    private boolean bar(){
        return true;
    }
}
