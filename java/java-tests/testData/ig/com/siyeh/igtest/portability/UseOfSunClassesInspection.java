package com.siyeh.igtest.portability;

import sun.instrument.TransformerManager;

public class UseOfSunClassesInspection {
    TransformerManager foo = new TransformerManager();
}
