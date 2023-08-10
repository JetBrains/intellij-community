package com.siyeh.igtest.confusing;

public class ContinueInspection
{

    public static void main(String[] args)
    {
        Label:
        while(true)
        {
            for(int i = 0;i<4;i++)
            {
                if(i == 2)
                {
                    continue;
                }
                System.out.println("i = " + i);
            }
        }
    }
}
