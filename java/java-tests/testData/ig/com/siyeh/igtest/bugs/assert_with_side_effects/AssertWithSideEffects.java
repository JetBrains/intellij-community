package com.siyeh.igtest.bugs.assert_with_side_effects;
import java.sql.*;
import java.util.*;
import java.io.*;

public class AssertWithSideEffects {
    private int sideEffect = 0;
    private boolean noEffect = false;
    void foo(boolean b) {
        assert !b && hasNoSideEffects();
    }

    void bar(int i) {
        <warning descr="'assert' has side effects: i++">assert</warning> i++ < 10;
        <warning descr="'assert' has side effects: i += ...">assert</warning> (i+=2) < 10;
    }

    void abc() {
        <warning descr="'assert' has side effects: call to 'isSideEffect()' mutates field 'sideEffect'">assert</warning> isSideEffect();
    }

    boolean isSideEffect() {
        sideEffect = 1;
        return true;
    }

    boolean hasNoSideEffects() {
        assert !noEffect;
        return noEffect;
    }

    void jdbc(ResultSet rs) throws SQLException {
      <warning descr="'assert' has side effects: call to 'last()' mutates 'rs'">assert</warning> rs.last();
    }
    
    void io(InputStream is) throws IOException {
      <warning descr="'assert' has side effects: call to 'read()' performs input/output operation">assert</warning> is.read() != -1;
    }
}