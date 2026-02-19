package com.siyeh.igtest.performance;

import java.util.Random;

public class RandomDoubleForRandomIntegerInspection {
    public int foo()
    {
        return (int)(new Random().nextDouble() *3.0);
    }
}
