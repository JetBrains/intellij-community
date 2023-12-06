package com.siyeh.igtest.verbose;

public class ConstantConditionalExpressionInspection {
    public void foo()
    {
        final int i = true ? 3 : 4;
        final int j = false ? 3 : 4;
    }
}
