package com.siyeh.igtest.verbose;

public class UnnecessaryQualifierForThisInspection {
    public void foo()
    {
         System.out.println(UnnecessaryQualifierForThisInspection.this);
    }

    public class Inner
    {
        public void foo() {
            System.out.println(Inner.this);
            System.out.println(UnnecessaryQualifierForThisInspection.this);
        }
    }
}
