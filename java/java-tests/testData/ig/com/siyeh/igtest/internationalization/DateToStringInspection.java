package com.siyeh.igtest.internationalization;

import java.io.IOException;
import java.util.Date;

public class DateToStringInspection
{
    public DateToStringInspection()
    {
    }

    public void foo() throws IOException
    {
        final Date date = new Date();
        date.toString();
    }
}