package com;

import package1.*;
import package2.*;

public class B {
    public A get() { // resolves to "public package1.A get();" or "public package2.A get();" depending on where A is
        return null;
    }
}
