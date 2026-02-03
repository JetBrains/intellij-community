package pack2;

import pack1.Outer;

import java.util.ArrayList;
import java.util.List;

public class B {
    public static void foo(){
        Outer<ArrayList>.Inner<List> x;
    }
}