package com.siyeh.igtest.naming.exception_name_doesnt_end_with_exception;

public class ExceptionNameDoesntEndWithException {

    public <E extends Exception> void method() { }

    class <warning descr="Exception class name 'X' does not end with 'Exception'">X</warning> extends IllegalArgumentException {}
}