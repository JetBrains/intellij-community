package com.siyeh.igtest.bugs;

public class ForLoopWithMissingComponentsInspection
{

    public ForLoopWithMissingComponentsInspection()
    {
    }

    private void foo() throws Exception
    {

        for(;;)
        {
            if(bar())
            {
                break;
            }
        }
        for(int i = 0;;)
        {
            if(bar())
            {
                break;
            }
        }
        for(;true;)
        {
            if(bar())
            {
                break;
            }
        }
        for(int i = 0;;i++)
        {
            if(bar())
            {
                break;
            }
        }
        for(int i = 0;true;i++)
        {
            if(bar())
            {
                break;
            }
        }
    }

    private boolean bar()
    {
        return true;
    }

}
