package com.siyeh.igtest.style;

import javax.swing.*;

public final class MyFoo{
    public static void main(String[] args){

    }

    public static class InputVerifier
            extends javax.swing.InputVerifier{
        public boolean verify(JComponent input){
            return false;
        }
    }

} 