package com.siyeh.igtest.style;

import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NotNull;

import java.util.Hashtable;

final public class MissortedModifiersInspection
{
    private native static int foo2();

    static private int m_bar = 4;
    static public int m_baz = 4;
    static final public int m_baz2 = 4;
    static final int m_baz3 = 4;

    static public void foo(){}

    static public class Foo
    {

    }

    public @Deprecated void foo3(){};

      private transient static Hashtable mAttributeMeta;

    final public class TestQuickFix
    {
       protected final static String A = "a";
       protected final static String B = "b";
       protected final static String C = "c";
       protected final static String D = "d";
    }

    //@Type(type = "org.joda.time.contrib.hibernate.PersistentYearMonthDay")
    //@Column(name = "current_month")
    final
    public
    @Nullable
    // commment
    @NotNull
    int //@Temporal(TemporalType.DATE)
    x() {return -1;}
}
