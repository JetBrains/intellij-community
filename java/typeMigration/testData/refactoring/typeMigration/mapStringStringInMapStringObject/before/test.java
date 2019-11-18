// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
import java.util.*;

class Test {
    public void foo() {
        setMap(Collections.singletonMap("first", Long.toString(migrationMethod())));
        setMap(Collections.singletonMap("second", new Object()));
    }

    private int migrationMethod() {
        return 1;
    }

    private void setMap(Map<String, Object> map) {

    }
}
