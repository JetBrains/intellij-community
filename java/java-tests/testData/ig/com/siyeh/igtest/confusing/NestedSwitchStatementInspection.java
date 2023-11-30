package com.siyeh.igtest.confusing;

public class NestedSwitchStatementInspection
{
    public NestedSwitchStatementInspection()
    {
    }

    public void foo()
    {
        switch(bar())
        {
            case 3:
                break;
            case 4:
                switch(bar())
                {
                    case 3:
                        break;
                    case 4:
                        break;
                    default:
                        break;
                }
                break;
            default:
                break;
        }
    }

    private int bar()
    {
        return 3;
    }
}
