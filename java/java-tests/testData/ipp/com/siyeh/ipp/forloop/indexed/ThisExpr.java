package com.siyeh.ipp.forloop.indexed;
import java.util.*;
class NormalForEachLoop implements List<Integer>{
    void foo() {
        <caret>for (Integer i : this) {
            System.out.println(i);
        }
    }
}