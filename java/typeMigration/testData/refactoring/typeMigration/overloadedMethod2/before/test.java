// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

class Test {
    public void foo() {
        Foo.valueOf(migrationMethod(), 1);
    }

    private int migrationMethod() {
        return 1;
    }
}

class Foo {
    public static Foo valueOf(int value, int hint){
        return null;
    }

    public static Foo valueOf(long value, long hint){
        return null;
    }
}