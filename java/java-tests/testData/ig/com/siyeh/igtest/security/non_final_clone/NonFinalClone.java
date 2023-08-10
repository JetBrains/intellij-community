package com.siyeh.igtest.security;


public class NonFinalClone implements Cloneable{
    public Object <warning descr="Non-final 'clone()' method, compromising security">clone</warning>() throws CloneNotSupportedException {
        return super.clone();
    }
}
