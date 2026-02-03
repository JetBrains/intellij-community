package com.siyeh.igtest.maturity;

import javax.swing.*;
import java.io.IOException;
import java.io.PrintStream;
import java.util.*;

class Test
{
    private <warning descr="Obsolete collection type 'Hashtable' used">Hashtable</warning> m_bar;

    public void foo(<warning descr="Obsolete collection type 'Stack' used">Stack</warning> stack) throws IOException
    {
        Map bar = new <warning descr="Obsolete collection type 'Hashtable' used">Hashtable</warning>(0);
    }
}