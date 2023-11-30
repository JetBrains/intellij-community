package com.siyeh.igtest.performance;

import java.io.IOException;

public class SingleCharacterStartsWithInspection
{
    public SingleCharacterStartsWithInspection()
    {
    }

    public void foo() throws IOException
    {
        "foo".startsWith("f");
        "foo".startsWith("f", 0);

        "foo".endsWith("f");
    }
}