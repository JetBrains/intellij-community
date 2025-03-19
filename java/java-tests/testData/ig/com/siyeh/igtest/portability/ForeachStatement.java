package com.siyeh.igtest.portability;

public class ForeachStatement {

    public static void main(String[] args) {

    }

    public int sum(int... var) {
        int total = 0;
        for (int i:var) {
            total += var[i];
        }
        return total;
    }

}
