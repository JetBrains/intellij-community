package com.siyeh.igtest.confusing;

public class BreakInspection
{

    public static void main(String[] args)
    {
        for(int i = 0; i < 4; i++)
        {
            if(i == 2)
            {
                break;
            }
            if(i == 3)
            {
                break;
            }
            System.out.println("i = " + i);
        }
    }
}
