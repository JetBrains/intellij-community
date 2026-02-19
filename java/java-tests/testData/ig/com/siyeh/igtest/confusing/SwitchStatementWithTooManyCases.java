package com.siyeh.igtest.confusing;

public class SwitchStatementWithTooManyCases
{
    public void foo()
    {
        final int x = barangus();
        switch(x)
        {
            case 1:
                break;
            case 2:
                break;
            case 3:
                break;
            case 4:
                break;
            case 5:
                break;
            case 6:
                break;
            case 7:
                break;
            case 8:
                break;
            case 9:
                break;
            case 10:
                break;
            case 11:
                break;
            default:
                break;
        }
    }

    private int barangus()
    {
        return 3;
    }
}
