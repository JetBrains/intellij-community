package com.siyeh.igtest.abstraction;




public abstract class ClassReferencesSubclassInspectionParent
{
    public ClassReferencesSubclassInspection child;

    void foo()
    {
        ClassReferencesSubclassInspection child;
    }
}
