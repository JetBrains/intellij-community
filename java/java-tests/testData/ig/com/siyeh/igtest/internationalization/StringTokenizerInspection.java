package com.siyeh.igtest.internationalization;

import java.util.StringTokenizer;

public class StringTokenizerInspection
{
    StringTokenizer m_c;
    StringTokenizer n_d;

    static final StringTokenizer s_c = new StringTokenizer("c");
    static final StringTokenizer s_d = new StringTokenizer("d");

    public StringTokenizerInspection()
    {
    }

    public void foo()
    {
        StringTokenizer c = new StringTokenizer("c") ;
        StringTokenizer d = new StringTokenizer("d");
        c.toString();
        d.toString();
    }

}