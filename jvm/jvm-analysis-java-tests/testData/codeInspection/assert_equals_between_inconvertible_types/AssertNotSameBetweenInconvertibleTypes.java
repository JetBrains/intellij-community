package com.siyeh.igtest.junit.assert_equals_between_inconvertible_types;

import static org.junit.Assert.assertNotSame;
import org.junit.Test;

import java.util.*;

public class AssertNotSameBetweenInconvertibleTypes {

    @Test
    public void test() {
        <warning descr="Redundant assertion: incompatible types are compared 'String' and 'int'">assertNotSame</warning>("java", 1);
        <warning descr="Redundant assertion: incompatible types are compared 'int[]' and 'double'">assertNotSame</warning>(new int[0], 1.0);
        assertNotSame(new int[0], new int[1]); //ok
    }
}