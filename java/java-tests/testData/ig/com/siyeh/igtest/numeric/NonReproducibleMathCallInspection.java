package com.siyeh.igtest.numeric;

public class NonReproducibleMathCallInspection {

    public void foo()
    {
        Math.sin(3.0);
    }
}
