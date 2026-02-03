package com.siyeh.igtest.bugs.constant_assert_condition;




public class ConstantAssertCondition {

    void foo() {
        assert <warning descr="Assert condition 'true' is constant">true</warning>;
    }

    void bar(int i) {
        assert i == 10;
    }

    void noWarn() {
        try {
            
        } catch (RuntimeException e) {
            // should never happen
            assert (false);
        }
    }
}