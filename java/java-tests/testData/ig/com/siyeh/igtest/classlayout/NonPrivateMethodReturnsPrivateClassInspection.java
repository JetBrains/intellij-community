package com.siyeh.igtest.classlayout;

public class NonPrivateMethodReturnsPrivateClassInspection{

    public PrivateInner foo()
    {
        return null;
    }                   

    private class PrivateInner
    {

    }
}
