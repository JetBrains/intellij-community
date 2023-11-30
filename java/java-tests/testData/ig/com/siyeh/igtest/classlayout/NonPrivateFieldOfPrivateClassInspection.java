package com.siyeh.igtest.classlayout;

public class NonPrivateFieldOfPrivateClassInspection{

    public PrivateInner foo;

    private class PrivateInner
    {

    }
}
