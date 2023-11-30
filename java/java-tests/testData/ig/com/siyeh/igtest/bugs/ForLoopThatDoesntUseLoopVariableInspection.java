package com.siyeh.igtest.bugs;

public class ForLoopThatDoesntUseLoopVariableInspection
{

    public ForLoopThatDoesntUseLoopVariableInspection()
    {
    }

    private void foo() throws Exception
    {
         int j = 0;
        for(int i = 0; j < 100; i++)
        {
            System.out.println("i" + i);
            if(bar())
                break;
        }

        for(int i = 0; i < 100; j++)
        {
            System.out.println("i" + i);
            if(bar())
                break;
        }

        for(int i = 0; j < 100; j++)
        {
            System.out.println("i" + i);
            if(bar())
                break;
        }
        for(int i = 0, k = 0; j < 100; j++)
        {
            System.out.println("i" + i);
            if(bar())
                break;
        }
        for(int i = 0; i < 100; i++)
        {
            System.out.println("i" + i);
            if(bar())
                break;
        }
    }

    private boolean bar()
    {
        return true;
    }

}
