/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.siyeh.igtest.style.unnecessary_super_qualifier;

public class IgnoreClarificationSuperQualifier {
    int f;
    void m() {}
    class Base {
        int f;
        void m(){}
    }

    class Child extends Base {
        {
            System.out.println(super.f);
            super.m();
        }
    }
}