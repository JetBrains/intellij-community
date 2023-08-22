package com.siyeh.ipp.forloop.indexed;
import java.util.*;
class NormalForEachLoop implements List<Integer>{
    void foo() {
        for (int j = 0, thisSize = this.size(); j < thisSize; j++) {
            Integer i = this.get(j);
            System.out.println(i);
        }
    }
}