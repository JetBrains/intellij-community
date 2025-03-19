package com.siyeh.igtest.bugs;

public class CovariantComparetoInspection2
{
    public int compareTo(Object foo)
    {
        return -1;
    }

    public int compareTo(CovariantComparetoInspection2 foo)
    {
        return -1;
    }
}
