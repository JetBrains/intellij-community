package com.siyeh.igtest.performance.length_one_strings_in_concatenation;

import java.io.IOException;

public class LengthOneStringInConcatenationInspection
{
    public LengthOneStringInConcatenationInspection()
    {
    }

    public void foo() throws IOException
    {
        final String s = "foo" + <warning descr="'\"i\"' can be replaced with 'i'">"i"</warning> + "bar" + <warning descr="'\" \"' can be replaced with ' '">" "</warning> + "baz" + <warning descr="'\"\t\"' can be replaced with '\t'">"\t"</warning>;
        System.out.println(s);
        final StringBuffer buffer = new StringBuffer();
        buffer.append("foo");
        buffer.append(<warning descr="'\"f\"' can be replaced with 'f'">"f"</warning>);
        final StringBuilder buffer2 = new StringBuilder();
        buffer2.append("foo");
        buffer2.append(<warning descr="'\"f\"' can be replaced with 'f'">"f"</warning>);
        System.out.println("asdf" + 1 + <warning descr="'\"a\"' can be replaced with 'a'">"a"</warning>);
        System.out.println("asdf" + <warning descr="'\"b\"' can be replaced with 'b'">"b"</warning> + 2);
        System.out.println(1 + "b" + "asdf");
        System.out.println("a" +<error descr="Expression expected"> </error>);
        System.out.println("asdf" + (<warning descr="'\"b\"' can be replaced with 'b'">"b"</warning>) + 2);
        buffer2.append((<warning descr="'\"p\"' can be replaced with 'p'">"p"</warning>));
    }
}