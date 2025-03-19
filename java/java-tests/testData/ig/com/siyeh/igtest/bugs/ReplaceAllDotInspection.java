package com.siyeh.igtest.bugs;

public class ReplaceAllDotInspection {
    public void foo()
    {
        final String replace = "foo.bar.baz".replaceAll(".", "/");
        final String DOT = ".";
        final String replace2 = "foo.bar.baz".replaceAll(DOT, "/");
        final String replace3 = "foo.bar.baz".replaceAll(",", "/");
    }
}
