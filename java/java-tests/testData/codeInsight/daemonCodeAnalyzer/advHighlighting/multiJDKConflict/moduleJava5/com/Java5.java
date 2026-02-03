package com;
import java.util.List;

public class Java5 {
    void test(Java4 foo) {
        List list = foo.getList();
        for (Object o : list) {
        }
        for (Object o : foo.getList()) {
        }
    }
}
