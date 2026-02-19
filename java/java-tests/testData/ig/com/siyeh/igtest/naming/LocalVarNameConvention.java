package com.siyeh.igtest.naming;

public class LocalVarNameConvention
{
    public LocalVarNameConvention()
    {
    }

    public int foobar()
    {
        final int x32 = 0;
        final int loooooooooooooooooooooooooooooooooooooooooooooooongvarName = x32;
        return loooooooooooooooooooooooooooooooooooooooooooooooongvarName;
    }
}
