package com.siyeh.igtest.internationalization;

import java.io.IOException;
import java.sql.Time;

public class TimeToStringInspection
{
    public TimeToStringInspection()
    {
    }

    public void foo()
    {
        final Time time = new Time(0L);
        time.toString();
    }
}