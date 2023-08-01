package com.siyeh.igtest.verbose;

public class UnnecessaryLabelOnBreakStatementInspection {
    public void foo()
    {
        label:
        while(true)
        {
            break label;
        }
    }
}
