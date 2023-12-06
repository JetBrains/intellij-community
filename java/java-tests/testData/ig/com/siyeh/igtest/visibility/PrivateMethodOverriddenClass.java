package com.siyeh.igtest.visibility;

import java.util.Collection;

public class PrivateMethodOverriddenClass extends PrivateMethodToOverrideClass
{
    public void fooBar()
    {

    }
}
 class A {
    public static class MySet {
        private void add(String message) {
        }

        public void add(Collection coll) {
        }
    }

    public static class SubSet extends MySet {
        public void add(Collection coll) {
        }
    }
}
