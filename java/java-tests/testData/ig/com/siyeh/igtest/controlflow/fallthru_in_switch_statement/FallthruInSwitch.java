package com.siyeh.igtest.controlflow.fallthru_in_switch_statement;

public class FallthruInSwitch
{
    private int m_bar;

    public FallthruInSwitch()
    {
        m_bar = 0;
    }

    public void foo()
    {
        final int bar = m_bar;
        switch(bar)
        {
            case 2:
                // fall-through
            case (3):
              System.out.println();
              // Falls through
            case (4):
                System.out.println("3");
            <warning descr="Fallthrough in 'switch' statement">case (5):</warning>
            case (6): // don't warn here
                System.out.println("4");
        }
    }
}
class Z1 {
    static int x(String param, int i) {
        label:
        switch (i) {
            case 0:
                switch (param) {
                    case "a":
                        if (i == 0) break label;
                        return 1;
                    default:
                        return 3;
                }
            default: // should not report here
                return 2;
        }
        return -1;
    }

    public static void main(String[] args) {
        final int x = x("a", 0);
        System.out.println("x = " + x);
    }
}
