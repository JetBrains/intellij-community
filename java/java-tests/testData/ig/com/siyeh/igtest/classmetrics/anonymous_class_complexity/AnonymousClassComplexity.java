package com.siyeh.igtest.classmetrics.anonymous_class_complexity;

public class AnonymousClassComplexity {
    private boolean bar;
    private boolean baz;
    private boolean bazoom;
    public void foo()
    {
        Runnable runnable = new <warning descr="Overly complex anonymous class (cyclomatic complexity = 4)">Runnable</warning>() {
            public void run() {
                if(bar)
                {

                }else if(baz)
                {

                }else if(bazoom)
                {

                }
                foo();
            }
        };
    }
}
