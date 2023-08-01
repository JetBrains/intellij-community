package com.siyeh.igtest.verbose;

public class UnnecessaryLabelOnContinueStatementInspection {
    public void foo()
    {
        label:
        while(true)
        {
            continue;
        }
    }
}
