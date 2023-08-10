package com.siyeh.igtest.bugs;
public class ArrayTest {

    private String[] array; // <-- warning!

    public void setArray(String[] array) {
        this.array = (String[]) array.clone();
    }

    void doSomething() {
        for (int x = 0; x < array.length; x++) {
            System.out.println(array[x]);
        }
    }
}

