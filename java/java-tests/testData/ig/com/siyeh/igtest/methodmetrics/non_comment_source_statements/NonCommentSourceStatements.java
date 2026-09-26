package com.siyeh.igtest.methodmetrics;

import java.util.*;
import java.io.IOException;

public class NonCommentSourceStatements
{
    public void <warning descr="'fooBar' is too long (# Non-comment source statements = 31)">fooBar</warning>()
    {
        System.out.println("1");
        System.out.println("2");
        System.out.println("3");
        System.out.println("4");
        System.out.println("5");
        System.out.println("6");
        System.out.println("7");
        System.out.println("8");
        System.out.println("9");
        System.out.println("10");
        System.out.println("1");
        System.out.println("2");
        System.out.println("3");
        System.out.println("4");
        System.out.println("5");
        System.out.println("6");
        System.out.println("7");
        System.out.println("8");
        System.out.println("9");
        System.out.println("10");
        System.out.println("1");
        System.out.println("2");
        System.out.println("3");
        System.out.println("4");
        System.out.println("5");
        System.out.println("6");
        System.out.println("7");
        System.out.println("8");
        System.out.println("9");
        System.out.println("10");
        System.out.println("31");
    }

    public final void copyValuesFrom(int[][] otherMatrix, int startHeight, int startWidth) {
        System.out.println("copyValuesFrom(otherMatrix, {}, {})");
        System.out.println(startHeight);
        int otherMatrixHeight = otherMatrix.length;
        int otherMatrixWidth = otherMatrix[0].length;
        for (int i = 0; i < otherMatrixHeight; i++) {
            for (int j = 0; j < otherMatrixWidth; j++) {
                System.out.println(i + startHeight);
            }
        }
    }

    public final void dump(Appendable appendable, List<Iterable<String>> list) {
        System.out.println(appendable);
        try {
            for (Iterable<String> row : list) {
                appendable.append("\n| ");
                for (String cell : row) {
                    appendable.append(String.valueOf(cell)).append(" \t| ");
                }
            }
            appendable.append("\n");
            System.out.println("exit");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        class X {
            void m() {
                System.out.println();
                System.out.println();
                System.out.println();
                System.out.println();
                System.out.println();
            }
        }
    }

}
