package com.siyeh.igtest.finalization;

public class NoExplictCallToFinalizeInspection
{
    public NoExplictCallToFinalizeInspection() throws Throwable
    {
        finalize();
        this.finalize();
        new FinalizeNotProtectedInspection().finalize();
    }

}