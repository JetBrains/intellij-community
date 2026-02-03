package x;

import java.util.ArrayList;
import java.util.List;

class ListTack {
    void f(String <caret>s) {
    }
    void g(List<String> <flown111>l) {
        l.add(1, <flown112>"uuu");

//        f(l.get(0));
//        f(l.remove(0));
        Object[] array = <flown11>l.toArray();
        f((String)<flown1>array[0]);
    }
    void h() {
        List<String> g = <flown111131>new ArrayList<String>();
        g.add(<flown111132>"zzz");


        List<String> list = <flown11111>new ArrayList<String>();
        list.add(<flown11112>"xxx");

        list.addAll(<flown11113>g);

        g(<flown1111>list);
    }

}

