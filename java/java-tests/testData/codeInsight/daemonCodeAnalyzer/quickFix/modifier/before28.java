// "Make 'MyInterface.myMethod' static" "false"
import java.io.*;

interface MyInterface
{
    public void myMethod();
}
class MyInterfaceImpl implements MyInterface 
{
    <caret>public static void myMethod() { /* implementation goes here */ }
}
