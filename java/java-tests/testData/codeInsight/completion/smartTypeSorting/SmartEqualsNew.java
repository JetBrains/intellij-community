public abstract class Foo {

   private class Bar extends Foo {}

    {                                                                                            
        new Foo().equals(new <caret>)
    }

}