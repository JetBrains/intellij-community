// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
public class Test {

    public void bar(){
        long[] migrationVariable = new long[]{0,0};
        baz(migrationVariable);
        baz(0, 0);
    }

    public void baz(long... values){}
}