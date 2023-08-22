package com.siyeh.igtest.errorhandling.error_rethrown;

public class ErrorRethrown
{
    public void foo()
    {
        try {
          System.out.println("foo");
        } catch (Error e) {
          e.printStackTrace();
        }
        try {
          System.out.println("foo");
        } catch (Error e) {
          e.printStackTrace();
          throw e;
        }
        try {
          System.out.println("foo");
        } catch (AssertionError e) {
          e.printStackTrace();
          throw e;
        }
        try {
          System.out.println("foo");
        } catch (AssertionError e) {
          e.printStackTrace();
        }
    }
}
