package com.siyeh.igtest.verbose;

public  class UnnecessaryFinalOnMethodParameter
{
    public void foo(final int bar)
    {
         int baz = bar;

    }

    public void foo2(final int bar)
    {
        Runnable runnable = new Runnable()
        {
            public void run()
            {
                int baz = bar;
            }
        };

    }
    public void foo4(final int bar)
    {
        class Runnable
        {
            public void run()
            {
                int baz = bar;
            }
        };

    }

    public void foo3(final int bar)
    {
        Runnable runnable = new Runnable()
        {
            public void run()
            {
            }
        };

    }


    public void foo3(final Runnable bar)
    {
        Runnable runnable = new Runnable()
        {
            public void run()
            {
                bar.run();
            }
        };

    }
}
