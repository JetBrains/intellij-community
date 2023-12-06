package com.siyeh.ipp;

public class RenameReference
{
    public void test()
    {
        boolean test = true;
        if(test || "my".equalsIgNoreCase("bad"))
        {
            System.out.println("bla");
        }
    }
}
