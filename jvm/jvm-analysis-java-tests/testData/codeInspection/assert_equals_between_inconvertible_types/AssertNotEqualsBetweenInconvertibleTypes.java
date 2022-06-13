package com.siyeh.igtest.junit.assert_equals_between_inconvertible_types;

import static org.junit.Assert.assertNotEquals;
import org.junit.Test;

import java.util.*;

public class AssertNotEqualsBetweenInconvertibleTypes {

    @Test
    public void test() {
        <weak_warning descr="Possibly redundant assertion: incompatible types are compared 'String' and 'int'">assertNotEquals</weak_warning>("java", 1);
        <weak_warning descr="Possibly redundant assertion: incompatible types are compared 'int[]' and 'double'">assertNotEquals</weak_warning>(new int[0], 1.0);
        assertNotEquals(new int[0], new int[1]); //ok
    }
}